package br.tvbox.launcher

import android.graphics.Canvas
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.max

/**
 * Radio na internet (gratis, sem conta): lista do Radio Browser (radio-browser.info),
 * filtrada para radios do Brasil por genero, mais favoritas e busca (a busca vem do celular).
 * OK toca | OK segurado ou MENU: favoritar | ▲ nas abas, ◀▶ troca a categoria.
 */
class TelaRadio(private val v: LauncherView) : Painel {

    private class Estacao(val nome: String, val url: String, val info: String)

    companion object {
        /** Busca enviada pelo celular: a tela abre direto nela. */
        var buscaPendente: String? = null

        private val SERVIDORES = listOf(
            "https://all.api.radio-browser.info",
            "https://de1.api.radio-browser.info",
            "https://fi1.api.radio-browser.info",
            "http://all.api.radio-browser.info"
        )
    }

    private val ctx = v.context
    private val cats = arrayListOf(
        "FAVORITAS" to "", "MAIS OUVIDAS" to "*", "POP" to "pop", "SERTANEJO" to "sertanejo", "ROCK" to "rock",
        "GOSPEL" to "gospel", "NOTÍCIAS" to "news", "FUNK" to "funk", "MPB" to "mpb", "PAGODE" to "pagode",
        "FORRÓ" to "forro", "ELETRÔNICA" to "electronic", "ANOS 80" to "80s"
    )
    private var cat = 1
    private var naAbas = false
    private var sel = 0
    private var lista: List<Estacao> = emptyList()
    private var carregando = false
    private var aviso = ""
    private var pedido = 0
    private val ouvinte: () -> Unit = { v.invalidate() }

    // Urls das favoritas (consultado a cada quadro: nao pode ler o JSON toda vez).
    private val favUrls = HashSet<String>()

    init {
        Rede.iniciar(ctx)
        lerFavUrls()
        Tocador.ouvintes.add(ouvinte)
        val b = buscaPendente
        buscaPendente = null
        if (b != null) buscar(b)
        else {
            if (Config.favoritas(ctx).length() > 0) cat = 0
            carregar()
        }
    }

    override fun aoFechar() {
        Tocador.ouvintes.remove(ouvinte)
    }

    fun buscar(termo: String) {
        val nome = "BUSCA: " + Desenho.limpar(termo, 14)
        val i = cats.indexOfFirst { it.first.startsWith("BUSCA") }
        if (i >= 0) cats[i] = nome to "?$termo" else cats.add(nome to "?$termo")
        cat = cats.indexOfFirst { it.first == nome }
        carregar()
    }

    private fun favoritasComoLista(): List<Estacao> {
        val a = Config.favoritas(ctx)
        return (0 until a.length()).map { val o = a.getJSONObject(it); Estacao(o.optString("nome"), o.optString("url"), o.optString("info")) }
    }

    private fun lerFavUrls() {
        favUrls.clear()
        val a = Config.favoritas(ctx)
        for (i in 0 until a.length()) favUrls.add(a.getJSONObject(i).optString("url"))
    }

    private fun ehFavorita(e: Estacao): Boolean = e.url in favUrls

    private fun alternarFavorita(e: Estacao) {
        val a = Config.favoritas(ctx)
        val nova = JSONArray()
        var tinha = false
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.optString("url") == e.url) tinha = true else nova.put(o)
        }
        if (!tinha) nova.put(JSONObject().put("nome", e.nome).put("url", e.url).put("info", e.info))
        Config.setFavoritas(ctx, nova)
        lerFavUrls()
        aviso = if (tinha) "REMOVIDA DAS FAVORITAS" else "★ ADICIONADA AS FAVORITAS"
        if (cat == 0) {
            lista = favoritasComoLista()
            sel = sel.coerceIn(0, max(0, lista.size - 1))
        }
        v.invalidate()
    }

    private fun consulta(filtro: String): String {
        val base = "/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=120"
        return when {
            filtro == "*" -> "$base&countrycode=BR"
            filtro.startsWith("?") -> "$base&name=" + URLEncoder.encode(filtro.substring(1), "UTF-8")
            else -> "$base&countrycode=BR&tag=" + URLEncoder.encode(filtro, "UTF-8")
        }
    }

    private fun interpretar(json: String): List<Estacao> {
        val a = JSONArray(json)
        val r = ArrayList<Estacao>()
        val vistas = HashSet<String>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val resolvida = o.optString("url_resolved")
            val original = o.optString("url")
            // Prefere http quando existe: o Android 7 recusa muitos certificados de radio.
            val url = when {
                resolvida.startsWith("http://") -> resolvida
                original.startsWith("http://") -> original
                resolvida.isNotEmpty() -> resolvida
                else -> original
            }
            val nome = o.optString("name").trim()
            if (url.isEmpty() || nome.isEmpty() || !vistas.add(nome.lowercase())) continue
            val codec = o.optString("codec").uppercase()
            val kbps = o.optInt("bitrate", 0)
            val info = listOf(o.optString("state"), if (kbps > 0) "$kbps KBPS" else "", codec).filter { it.isNotBlank() }.joinToString(" ")
            r.add(Estacao(nome, url, info))
        }
        return r
    }

    private fun carregar() {
        val filtro = cats[cat].second
        sel = 0
        aviso = ""
        if (filtro.isEmpty()) {
            lista = favoritasComoLista()
            if (lista.isEmpty()) aviso = "SEM FAVORITAS: SEGURE OK NUMA RADIO PARA FAVORITAR"
            carregando = false
            v.invalidate()
            return
        }
        val chaveCache = "radio:$filtro"
        Config.cache(ctx, chaveCache)?.let { lista = try { interpretar(it) } catch (e: Exception) { emptyList() } }
        carregando = true
        val meu = ++pedido
        v.invalidate()
        Rede.fundo({
            var ultimoErro: Exception? = null
            var json: String? = null
            for (s in SERVIDORES) {
                try {
                    json = Rede.texto(s + consulta(filtro))
                    break
                } catch (e: Exception) {
                    ultimoErro = e
                }
            }
            json ?: throw (ultimoErro ?: Exception("sem resposta"))
        }) { json, erro ->
            if (meu != pedido) return@fundo
            carregando = false
            if (json != null) {
                val nova = try { interpretar(json) } catch (e: Exception) { emptyList() }
                if (nova.isNotEmpty()) {
                    lista = nova
                    Config.setCache(ctx, chaveCache, json)
                } else if (lista.isEmpty()) aviso = "NENHUMA RADIO ENCONTRADA"
            } else if (lista.isEmpty()) {
                aviso = "SEM INTERNET OU SERVIDOR FORA DO AR. OK TENTA DE NOVO"
            }
            sel = sel.coerceIn(0, max(0, lista.size - 1))
            v.invalidate()
        }
    }

    override fun desenhar(cv: Canvas, w: Float, h: Float, t: Tema) {
        Desenho.titulo(cv, w, h, t, "RÁDIO", if (carregando) "CARREGANDO..." else "${lista.size} RÁDIOS")
        Lista.abas(cv, w, t, cats.map { it.first }, cat, h * 0.15f, naAbas)
        val temBarra = Tocador.estado != Tocador.PARADO
        val base = if (temBarra) h * 0.72f else h * 0.86f
        val tocandoUrl = Tocador.chave
        Lista.desenhar(
            cv, w, t, lista.map { it.nome }, lista.map { if (it.url == tocandoUrl) "▶ TOCANDO" else it.info },
            sel, h * 0.24f, base, !naAbas, lista.map { ehFavorita(it) }
        )
        val cc = Desenho.escala(w)
        if (aviso.isNotEmpty()) Desenho.centro(cv, aviso, w / 2f, if (lista.isEmpty()) h * 0.45f else h * 0.875f, max(1, (cc * 0.55f).toInt()), t.texto, t.contorno)
        if (temBarra) Lista.barraTocando(cv, w, h, t, h * 0.74f)
        Desenho.rodape(cv, w, h, t, if (naAbas) "◀▶ CATEGORIA | ↓ RÁDIOS | VOLTAR SAIR" else "OK TOCAR | SEGURE OK FAVORITAR | PLAY/PAUSE | VOLTAR SAIR")
        if (temBarra || carregando) v.postInvalidateDelayed(120)
    }

    private fun ehOk(k: Int) =
        k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER

    override fun tecla(k: Int): Boolean {
        when {
            k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MEDIA_PAUSE ->
                Tocador.pausarOuContinuar()
            k == KeyEvent.KEYCODE_MEDIA_STOP -> Tocador.parar(ctx)
            naAbas -> when {
                k == KeyEvent.KEYCODE_DPAD_LEFT -> { cat = (cat + cats.size - 1) % cats.size; carregar() }
                k == KeyEvent.KEYCODE_DPAD_RIGHT -> { cat = (cat + 1) % cats.size; carregar() }
                k == KeyEvent.KEYCODE_DPAD_DOWN || ehOk(k) -> if (lista.isNotEmpty()) naAbas = false else carregar()
                else -> return false
            }
            k == KeyEvent.KEYCODE_DPAD_UP -> if (sel == 0) naAbas = true else sel--
            k == KeyEvent.KEYCODE_DPAD_DOWN -> if (sel < lista.size - 1) sel++
            k == KeyEvent.KEYCODE_DPAD_LEFT -> { cat = (cat + cats.size - 1) % cats.size; carregar() }
            k == KeyEvent.KEYCODE_DPAD_RIGHT -> { cat = (cat + 1) % cats.size; carregar() }
            k == KeyEvent.KEYCODE_MENU -> lista.getOrNull(sel)?.let { alternarFavorita(it) }
            ehOk(k) -> {
                val e = lista.getOrNull(sel)
                if (e == null) carregar()
                else if (Tocador.chave == e.url && Tocador.estado != Tocador.ERRO) Tocador.pausarOuContinuar()
                else Tocador.tocar(ctx, e.url, e.nome, e.info, "radio", e.url)
            }
            else -> return false
        }
        v.invalidate()
        return true
    }

    override fun teclaLonga(): Boolean {
        if (!naAbas) lista.getOrNull(sel)?.let { alternarFavorita(it) }
        return true
    }
}
