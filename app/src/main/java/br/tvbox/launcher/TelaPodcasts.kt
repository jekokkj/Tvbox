package br.tvbox.launcher

import android.content.Context
import android.graphics.Canvas
import android.util.Xml
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URLEncoder
import kotlin.math.max

/**
 * Podcasts por feed RSS (como Pocket Casts): lista de podcasts assinados -> episodios -> tocar.
 * Retoma de onde parou, marca o que ja foi ouvido, pula 15/30 s e muda a velocidade.
 * Novos podcasts entram pelo celular (busca no diretorio do iTunes ou url do feed).
 */
class TelaPodcasts(private val v: LauncherView) : Painel {

    class Episodio(val titulo: String, val url: String, val data: String, val duracao: String)

    companion object {
        /** Nomes buscados no diretorio do iTunes na primeira vez (o feed e descoberto na hora). */
        val INICIAIS = listOf(
            "Nerdcast", "Café da Manhã", "O Assunto", "Podpah", "Mano a Mano", "Xadrez Verbal",
            "Braincast", "Não Inviabilize", "Medo e Delírio em Brasília", "Flow Podcast"
        )

        /** Procura um podcast pelo nome e devolve {nome, feed} (roda em segundo plano). */
        fun procurar(termo: String): JSONObject? {
            val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&country=BR&limit=1&term=" +
                URLEncoder.encode(termo, "UTF-8")
            val r = JSONObject(Rede.texto(url)).optJSONArray("results") ?: return null
            if (r.length() == 0) return null
            val o = r.getJSONObject(0)
            val feed = o.optString("feedUrl")
            if (feed.isEmpty()) return null
            return JSONObject().put("nome", o.optString("collectionName", termo)).put("feed", feed)
        }

        /** Adiciona um podcast (nome ou url de feed) — usado pelo controle do celular. */
        fun adicionar(ctx: Context, termoOuFeed: String, pronto: (String) -> Unit) {
            Rede.fundo({
                if (termoOuFeed.startsWith("http")) {
                    val eps = lerFeed(termoOuFeed, 1)
                    JSONObject().put("nome", eps.first.ifEmpty { termoOuFeed }).put("feed", termoOuFeed)
                } else procurar(termoOuFeed)
            }) { o, _ ->
                if (o == null) { pronto("NAO ENCONTRADO"); return@fundo }
                val a = Config.podcasts(ctx) ?: JSONArray()
                for (i in 0 until a.length()) if (a.getJSONObject(i).optString("feed") == o.optString("feed")) { pronto("JA ESTAVA NA LISTA"); return@fundo }
                a.put(o)
                Config.setPodcasts(ctx, a)
                pronto("ADICIONADO: " + o.optString("nome"))
            }
        }

        /** Le ate [limite] episodios do feed (leitura em fluxo: feeds grandes nao vao inteiros para a memoria). */
        fun lerFeed(feed: String, limite: Int): Pair<String, List<Episodio>> {
            val c = Rede.abrir(feed, 20000)
            try {
                val p = Xml.newPullParser()
                p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                p.setInput(c.inputStream, null)
                val eps = ArrayList<Episodio>()
                var nomeCanal = ""
                var dentroItem = false
                var titulo = ""
                var url = ""
                var data = ""
                var dur = ""
                var ev = p.eventType
                while (ev != XmlPullParser.END_DOCUMENT && eps.size < limite) {
                    if (ev == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "item" -> { dentroItem = true; titulo = ""; url = ""; data = ""; dur = "" }
                            "title" -> { val s = p.nextText().trim(); if (dentroItem) titulo = s else if (nomeCanal.isEmpty()) nomeCanal = s }
                            "enclosure" -> if (dentroItem) url = p.getAttributeValue(null, "url") ?: ""
                            "pubDate" -> if (dentroItem) data = p.nextText().trim()
                            "itunes:duration" -> if (dentroItem) dur = p.nextText().trim()
                        }
                    } else if (ev == XmlPullParser.END_TAG && p.name == "item") {
                        dentroItem = false
                        if (url.isNotEmpty()) eps.add(Episodio(titulo, url, dataCurta(data), duracaoCurta(dur)))
                    }
                    ev = p.next()
                }
                return nomeCanal to eps
            } finally {
                c.disconnect()
            }
        }

        private val MESES = mapOf(
            "Jan" to "JAN", "Feb" to "FEV", "Mar" to "MAR", "Apr" to "ABR", "May" to "MAI", "Jun" to "JUN",
            "Jul" to "JUL", "Aug" to "AGO", "Sep" to "SET", "Oct" to "OUT", "Nov" to "NOV", "Dec" to "DEZ"
        )

        /** "Tue, 21 Oct 2026 10:00:00 GMT" -> "21 OUT". */
        private fun dataCurta(s: String): String {
            val p = s.split(" ").filter { it.isNotBlank() }
            return if (p.size >= 4) "${p[1].padStart(2, '0')} ${MESES[p[2]] ?: p[2].uppercase()}" else ""
        }

        /** "3725" ou "01:02:05" -> "62 MIN". */
        private fun duracaoCurta(s: String): String {
            if (s.isEmpty()) return ""
            val partes = s.split(":").mapNotNull { it.trim().toIntOrNull() }
            val seg = when (partes.size) {
                1 -> partes[0]
                2 -> partes[0] * 60 + partes[1]
                3 -> partes[0] * 3600 + partes[1] * 60 + partes[2]
                else -> 0
            }
            return if (seg > 0) "${max(1, seg / 60)} MIN" else ""
        }
    }

    private val ctx = v.context
    private var podcasts = JSONArray()
    private var selPod = 0
    private var aberto: JSONObject? = null
    private var episodios: List<Episodio> = emptyList()
    private var selEp = 0
    private var carregando = false
    private var aviso = ""
    private var pedido = 0
    private val ouvinte: () -> Unit = { v.invalidate() }

    init {
        Rede.iniciar(ctx)
        Tocador.ouvintes.add(ouvinte)
        val salvos = Config.podcasts(ctx)
        if (salvos != null) podcasts = salvos else prepararIniciais()
    }

    override fun aoFechar() {
        Tocador.ouvintes.remove(ouvinte)
    }

    override fun aoRetomar() {
        Config.podcasts(ctx)?.let { podcasts = it }
    }

    private fun prepararIniciais() {
        carregando = true
        aviso = "PREPARANDO A LISTA DE PODCASTS"
        Rede.fundo({
            val a = JSONArray()
            for (n in INICIAIS) try { procurar(n)?.let { a.put(it) } } catch (e: Exception) { }
            a
        }) { a, _ ->
            carregando = false
            if (a != null && a.length() > 0) {
                podcasts = a
                Config.setPodcasts(ctx, a)
                aviso = ""
            } else aviso = "SEM INTERNET. OK TENTA DE NOVO"
            v.invalidate()
        }
    }

    private fun abrir(o: JSONObject) {
        aberto = o
        episodios = emptyList()
        selEp = 0
        carregando = true
        aviso = ""
        val meu = ++pedido
        val feed = o.optString("feed")
        Rede.fundo({ lerFeed(feed, 60).second }) { eps, _ ->
            if (meu != pedido) return@fundo
            carregando = false
            if (eps != null && eps.isNotEmpty()) {
                episodios = eps
                // Comeca no episodio que estava tocando, se for deste podcast.
                selEp = max(0, eps.indexOfFirst { it.url == Tocador.chave })
            } else aviso = "NAO FOI POSSIVEL LER OS EPISÓDIOS"
            v.invalidate()
        }
        v.invalidate()
    }

    private fun situacao(e: Episodio): String {
        if (e.url == Tocador.chave && Tocador.estado != Tocador.PARADO) return "▶ AGORA"
        val p = Config.posicao(ctx, e.url)
        return when {
            p < 0 -> "OUVIDO"
            p > 0 -> "PAROU EM " + Lista.tempo(p)
            else -> listOf(e.data, e.duracao).filter { it.isNotEmpty() }.joinToString("  ")
        }
    }

    override fun desenhar(cv: Canvas, w: Float, h: Float, t: Tema) {
        val temBarra = Tocador.estado != Tocador.PARADO
        val base = if (temBarra) h * 0.72f else h * 0.86f
        val ab = aberto
        if (ab == null) {
            Desenho.titulo(cv, w, h, t, "PODCASTS", "${podcasts.length()} ASSINADOS")
            val nomes = (0 until podcasts.length()).map { podcasts.getJSONObject(it).optString("nome") }
            Lista.desenhar(cv, w, t, nomes, null, selPod, h * 0.17f, base)
        } else {
            Desenho.titulo(cv, w, h, t, Desenho.limpar(ab.optString("nome"), 28), if (carregando) "CARREGANDO..." else "${episodios.size} EPISÓDIOS")
            Lista.desenhar(cv, w, t, episodios.map { it.titulo }, episodios.map { situacao(it) }, selEp, h * 0.17f, base)
        }
        val cc = Desenho.escala(w)
        if (aviso.isNotEmpty()) Desenho.centro(cv, aviso + if (carregando) ".".repeat(((System.currentTimeMillis() / 400) % 4).toInt()) else "",
            w / 2f, h * 0.45f, max(1, (cc * 0.55f).toInt()), t.texto, t.contorno)
        if (temBarra) Lista.barraTocando(cv, w, h, t, h * 0.74f)
        Desenho.rodape(
            cv, w, h, t,
            if (ab == null) "OK ABRIR | ADICIONE PODCASTS PELO CELULAR (CONTROLE) | VOLTAR SAIR"
            else "OK TOCAR | ◀ -15S ▶ +30S | MENU VELOCIDADE | SEGURE OK MARCAR OUVIDO"
        )
        if (temBarra || carregando) v.postInvalidateDelayed(150)
    }

    private fun ehOk(k: Int) =
        k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER

    override fun tecla(k: Int): Boolean {
        when (k) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> Tocador.pausarOuContinuar()
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_LEFT -> Tocador.pular(-15_000)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT -> Tocador.pular(30_000)
            KeyEvent.KEYCODE_MENU -> Tocador.trocarVelocidade()
            KeyEvent.KEYCODE_DPAD_UP -> if (aberto == null) { if (selPod > 0) selPod-- } else if (selEp > 0) selEp--
            KeyEvent.KEYCODE_DPAD_DOWN -> if (aberto == null) { if (selPod < podcasts.length() - 1) selPod++ } else if (selEp < episodios.size - 1) selEp++
            else -> {
                if (!ehOk(k)) return false
                val ab = aberto
                if (ab == null) {
                    if (podcasts.length() == 0) { if (!carregando) prepararIniciais() }
                    else abrir(podcasts.getJSONObject(selPod))
                } else {
                    val e = episodios.getOrNull(selEp)
                    if (e == null) { if (!carregando) abrir(ab) }
                    else if (e.url == Tocador.chave && Tocador.estado != Tocador.PARADO) Tocador.pausarOuContinuar()
                    else {
                        val p = Config.posicao(ctx, e.url)
                        Tocador.tocar(ctx, e.url, e.titulo, ab.optString("nome"), "podcast", e.url, if (p > 0) max(0, p - 3000) else 0)
                    }
                }
            }
        }
        v.invalidate()
        return true
    }

    override fun teclaLonga(): Boolean {
        val e = episodios.getOrNull(selEp) ?: return true
        if (aberto == null) return true
        Config.setPosicao(ctx, e.url, if (Config.posicao(ctx, e.url) < 0) 0 else -1)
        v.invalidate()
        return true
    }

    /** VOLTAR: dos episodios volta para a lista de podcasts. */
    override fun voltar(): Boolean {
        if (aberto != null) {
            aberto = null
            pedido++
            carregando = false
            aviso = ""
            v.invalidate()
            return false
        }
        return true
    }
}
