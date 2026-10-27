package br.tvbox.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Servidor web minimo do controle pelo celular (so na rede local).
 * O celular abre http://<ip-da-caixa>:8080, digita o PIN que aparece na TV e ganha:
 * direcional, OK, voltar, home, volume, texto, envio de arquivos, busca de radio, podcasts,
 * lembretes e cidade do painel. Os arquivos sao gravados direto no disco em blocos (sem ocupar a RAM).
 */
object Servidor {
    const val PORTA = 8080

    /** PIN muda a cada vez que o launcher abre. */
    val pin: String = "%04d".format(Random.nextInt(0, 10000))

    var porta = PORTA
        private set
    var rodando = false
        private set

    /** Ultimo envio de arquivo: nome, bytes recebidos, total. */
    @Volatile var envioNome = ""
    @Volatile var envioRecebido = 0L
    @Volatile var envioTotal = 0L

    private var socket: ServerSocket? = null
    private val pool = Executors.newFixedThreadPool(4)
    private var app: Context? = null
    private val tentativasErradas = HashMap<String, Int>()

    /** Quem executa os comandos na tela (a MainActivity). Roda na thread principal. */
    var executor: ((String, Map<String, String>) -> String)? = null

    fun pastaRecebidos(ctx: Context): File =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "Recebidos").apply { mkdirs() }

    fun enderecos(): List<String> {
        val r = ArrayList<String>()
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (a in ni.inetAddresses) if (a is Inet4Address && !a.isLoopbackAddress) r.add("http://${a.hostAddress}:$porta")
            }
        } catch (e: Exception) {
        }
        return r
    }

    fun iniciar(ctx: Context) {
        if (rodando) return
        app = ctx.applicationContext
        Thread {
            for (p in PORTA..PORTA + 9) {
                try {
                    socket = ServerSocket(p)
                    porta = p
                    rodando = true
                    break
                } catch (e: Exception) {
                }
            }
            val s = socket ?: return@Thread
            while (rodando) {
                try {
                    val c = s.accept()
                    pool.execute { atender(c) }
                } catch (e: Exception) {
                    if (!rodando) break
                }
            }
        }.apply { isDaemon = true; name = "controle" }.start()
    }

    fun parar() {
        rodando = false
        try { socket?.close() } catch (e: Exception) { }
        socket = null
    }

    // ---------------------------------------------------------------- HTTP

    private class Pedido(val metodo: String, val caminho: String, val q: Map<String, String>, val cab: Map<String, String>, val corpo: InputStream)

    private fun lerLinha(e: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = e.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) return null
        }
    }

    private fun query(s: String): Map<String, String> {
        val m = HashMap<String, String>()
        for (par in s.split("&")) {
            if (par.isEmpty()) continue
            val i = par.indexOf('=')
            val k = URLDecoder.decode(if (i < 0) par else par.substring(0, i), "UTF-8")
            val v = if (i < 0) "" else URLDecoder.decode(par.substring(i + 1), "UTF-8")
            m[k] = v
        }
        return m
    }

    private fun atender(c: Socket) {
        try {
            c.soTimeout = 30_000
            val ent = BufferedInputStream(c.getInputStream(), 65536)
            val sai = c.getOutputStream()
            val primeira = lerLinha(ent) ?: return
            val partes = primeira.split(" ")
            if (partes.size < 2) return
            val cab = HashMap<String, String>()
            while (true) {
                val l = lerLinha(ent) ?: break
                if (l.isEmpty()) break
                val i = l.indexOf(':')
                if (i > 0) cab[l.substring(0, i).trim().lowercase()] = l.substring(i + 1).trim()
            }
            val alvo = partes[1]
            val qi = alvo.indexOf('?')
            val caminho = if (qi < 0) alvo else alvo.substring(0, qi)
            val q = if (qi < 0) emptyMap() else query(alvo.substring(qi + 1))
            val tamanho = cab["content-length"]?.toLongOrNull() ?: 0L
            val corpo = LimitadoInputStream(ent, tamanho)
            responder(Pedido(partes[0], caminho, q, cab, corpo), sai, c.inetAddress.hostAddress ?: "")
            sai.flush()
        } catch (e: Exception) {
        } finally {
            try { c.close() } catch (e: Exception) { }
        }
    }

    private class LimitadoInputStream(private val e: InputStream, private var resta: Long) : InputStream() {
        override fun read(): Int {
            if (resta <= 0) return -1
            val b = e.read()
            if (b >= 0) resta--
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (resta <= 0) return -1
            val n = e.read(b, off, minOf(len.toLong(), resta).toInt())
            if (n > 0) resta -= n
            return n
        }
    }

    private fun enviar(sai: OutputStream, codigo: Int, tipo: String, corpo: ByteArray) {
        val status = when (codigo) { 200 -> "OK"; 401 -> "Unauthorized"; 404 -> "Not Found"; 429 -> "Too Many Requests"; else -> "Error" }
        val cab = "HTTP/1.1 $codigo $status\r\nContent-Type: $tipo\r\nContent-Length: ${corpo.size}\r\n" +
            "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
        sai.write(cab.toByteArray())
        sai.write(corpo)
    }

    private fun json(sai: OutputStream, o: Any, codigo: Int = 200) =
        enviar(sai, codigo, "application/json; charset=utf-8", o.toString().toByteArray())

    private fun lerCorpo(p: Pedido, max: Int = 65536): String {
        val buf = java.io.ByteArrayOutputStream()
        val b = ByteArray(8192)
        while (buf.size() < max) {
            val n = p.corpo.read(b)
            if (n < 0) break
            buf.write(b, 0, n)
        }
        return String(buf.toByteArray(), Charsets.UTF_8)
    }

    /** Executa um comando na thread principal e espera a resposta (ate 8 s). */
    private fun naTela(cmd: String, args: Map<String, String>): String {
        val ex = executor ?: return "LAUNCHER FECHADO"
        var r = "OK"
        val fim = CountDownLatch(1)
        Rede.naPrincipal {
            try { r = ex(cmd, args) } catch (e: Exception) { r = "ERRO" }
            fim.countDown()
        }
        fim.await(8, TimeUnit.SECONDS)
        return r
    }

    private fun responder(p: Pedido, sai: OutputStream, ip: String) {
        val ctx = app ?: return
        if (p.caminho == "/" || p.caminho == "/index.html") {
            val html = ctx.assets.open("controle.html").use { it.readBytes() }
            enviar(sai, 200, "text/html; charset=utf-8", html)
            return
        }
        if (!p.caminho.startsWith("/api/")) {
            enviar(sai, 404, "text/plain", "nao encontrado".toByteArray())
            return
        }
        // PIN em toda chamada; muitas tentativas erradas bloqueiam aquele aparelho ate o launcher reabrir.
        val erros = synchronized(tentativasErradas) { tentativasErradas[ip] ?: 0 }
        if (erros >= 10) { json(sai, JSONObject().put("erro", "BLOQUEADO"), 429); return }
        if (p.cab["x-pin"] != pin && p.q["pin"] != pin) {
            synchronized(tentativasErradas) { tentativasErradas[ip] = erros + 1 }
            json(sai, JSONObject().put("erro", "PIN"), 401)
            return
        }
        val api = p.caminho.removePrefix("/api/")
        when (api) {
            "estado" -> json(sai, JSONObject(naTela("estado", emptyMap())))
            "tecla", "abrir", "texto", "radio", "volume", "tema" -> {
                val args = HashMap(p.q)
                if (api == "texto" && p.metodo == "POST") args["t"] = lerCorpo(p, 4096)
                json(sai, JSONObject().put("ok", naTela(api, args)))
            }
            "podcast" -> {
                val termo = p.q["add"] ?: lerCorpo(p, 2048)
                val fim = CountDownLatch(1)
                var r = ""
                Rede.naPrincipal { TelaPodcasts.adicionar(ctx, termo.trim()) { msg -> r = msg; fim.countDown() } }
                fim.await(30, TimeUnit.SECONDS)
                json(sai, JSONObject().put("ok", r.ifEmpty { "DEMOROU DEMAIS" }))
            }
            "podcasts" -> {
                if (p.metodo == "POST") Config.setPodcasts(ctx, JSONArray(lerCorpo(p)))
                json(sai, Config.podcasts(ctx) ?: JSONArray())
            }
            "lembretes" -> {
                if (p.metodo == "POST") {
                    Config.setLembretes(ctx, JSONArray(lerCorpo(p)))
                    naTela("redesenhar", emptyMap())
                }
                json(sai, Config.lembretes(ctx))
            }
            "noticias" -> {
                if (p.metodo == "POST") Config.setFeedsNoticias(ctx, JSONArray(lerCorpo(p)))
                json(sai, Config.feedsNoticias(ctx))
            }
            "cidade" -> {
                p.q["c"]?.takeIf { it.isNotBlank() }?.let { Config.setCidade(ctx, it.trim()); naTela("painel-atualizar", emptyMap()) }
                json(sai, JSONObject().put("cidade", Config.cidade(ctx)))
            }
            "enviar" -> receberArquivo(p, sai, ctx)
            "recebidos" -> {
                val a = JSONArray()
                pastaRecebidos(ctx).listFiles()?.sortedByDescending { it.lastModified() }?.forEach {
                    a.put(JSONObject().put("nome", it.name).put("bytes", it.length()))
                }
                json(sai, a)
            }
            else -> json(sai, JSONObject().put("erro", "?"), 404)
        }
    }

    private fun nomeSeguro(n: String): String {
        val limpo = n.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9._ ()-]"), "_").trim()
        return limpo.ifEmpty { "arquivo" }.take(120)
    }

    private fun receberArquivo(p: Pedido, sai: OutputStream, ctx: Context) {
        val total = p.cab["content-length"]?.toLongOrNull() ?: 0L
        val pasta = pastaRecebidos(ctx)
        if (total <= 0) { json(sai, JSONObject().put("erro", "VAZIO"), 400); return }
        if (pasta.usableSpace < total + 20L * 1024 * 1024) { json(sai, JSONObject().put("erro", "SEM ESPAÇO NA CAIXA"), 400); return }
        var nome = nomeSeguro(p.q["nome"] ?: "arquivo")
        var destino = File(pasta, nome)
        var n = 1
        while (destino.exists()) {
            val ponto = nome.lastIndexOf('.')
            val base = if (ponto > 0) nome.substring(0, ponto) else nome
            val ext = if (ponto > 0) nome.substring(ponto) else ""
            destino = File(pasta, "$base ($n)$ext")
            n++
        }
        nome = destino.name
        val parcial = File(pasta, ".$nome.parcial")
        envioNome = nome
        envioRecebido = 0
        envioTotal = total
        Rede.naPrincipal { executor?.invoke("redesenhar", emptyMap()) }
        try {
            parcial.outputStream().buffered(65536).use { out ->
                val b = ByteArray(65536)
                while (true) {
                    val k = p.corpo.read(b)
                    if (k < 0) break
                    out.write(b, 0, k)
                    envioRecebido += k
                }
            }
            if (envioRecebido != total) throw java.io.IOException("incompleto")
            parcial.renameTo(destino)
            Rede.naPrincipal { executor?.invoke("recebido", mapOf("nome" to nome)) }
            json(sai, JSONObject().put("ok", nome))
        } catch (e: Exception) {
            parcial.delete()
            envioNome = ""
            json(sai, JSONObject().put("erro", "ENVIO INTERROMPIDO"), 500)
        }
    }
}
