package br.tvbox.launcher

import android.graphics.Canvas
import android.graphics.RectF
import android.util.Xml
import android.view.KeyEvent
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Painel de casa: relogio e data grandes, clima (Open-Meteo, sem chave), cotacoes (AwesomeAPI),
 * manchetes (RSS) e lembretes (escritos pelo celular). Guarda a ultima resposta de cada servico
 * para continuar mostrando algo sem internet. Atualiza a cada 15 minutos enquanto esta aberto.
 */
class TelaPainel(private val v: LauncherView) : Painel {

    private val ctx = v.context
    private val inicio = System.currentTimeMillis()
    private var clima: JSONObject? = null
    private var cidadeNome = ""
    private var cotacoes: JSONObject? = null
    private var manchetes: List<String> = emptyList()
    private var ultimaBusca = 0L
    private var status = ""

    init {
        Rede.iniciar(ctx)
        Config.cache(ctx, "clima")?.let { clima = try { JSONObject(it) } catch (e: Exception) { null } }
        Config.cache(ctx, "cotacoes")?.let { cotacoes = try { JSONObject(it) } catch (e: Exception) { null } }
        Config.cache(ctx, "manchetes")?.let { manchetes = it.split("\n").filter { s -> s.isNotBlank() } }
        cidadeNome = Config.cidadeGeo(ctx)?.split(",", limit = 3)?.getOrNull(2) ?: Config.cidade(ctx)
        atualizar()
    }

    override fun aoRetomar() {
        if (System.currentTimeMillis() - ultimaBusca > 15 * 60_000L) atualizar()
    }

    fun atualizar() {
        ultimaBusca = System.currentTimeMillis()
        status = "ATUALIZANDO"
        // Clima: primeiro acha a cidade (uma vez), depois a previsao.
        Rede.fundo({
            var geo = Config.cidadeGeo(ctx)
            if (geo == null) {
                val q = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=pt&name=" + URLEncoder.encode(Config.cidade(ctx), "UTF-8")
                val r = JSONObject(Rede.texto(q)).optJSONArray("results")?.optJSONObject(0) ?: throw Exception("cidade")
                geo = "${r.getDouble("latitude")},${r.getDouble("longitude")},${r.optString("name")}"
            }
            val p = geo.split(",", limit = 3)
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${p[0]}&longitude=${p[1]}" +
                "&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=4"
            geo to Rede.texto(url)
        }) { r, _ ->
            if (r != null) {
                Config.setCidadeGeo(ctx, r.first)
                cidadeNome = r.first.split(",", limit = 3).getOrNull(2) ?: cidadeNome
                clima = try { JSONObject(r.second) } catch (e: Exception) { clima }
                Config.setCache(ctx, "clima", r.second)
            }
            status = ""
            v.invalidate()
        }
        Rede.fundo({ Rede.texto("https://economia.awesomeapi.com.br/json/last/USD-BRL,EUR-BRL,BTC-BRL") }) { r, _ ->
            if (r != null) try { cotacoes = JSONObject(r); Config.setCache(ctx, "cotacoes", r) } catch (e: Exception) { }
            v.invalidate()
        }
        val feeds = Config.feedsNoticias(ctx)
        Rede.fundo({
            val todas = ArrayList<String>()
            for (i in 0 until feeds.length()) try { todas.addAll(titulosRss(feeds.getString(i), 12)) } catch (e: Exception) { }
            todas
        }) { r, _ ->
            if (r != null && r.isNotEmpty()) {
                manchetes = r
                Config.setCache(ctx, "manchetes", r.joinToString("\n"))
            }
            v.invalidate()
        }
    }

    private fun titulosRss(url: String, limite: Int): List<String> {
        val c = Rede.abrir(url)
        try {
            val p = Xml.newPullParser()
            p.setInput(c.inputStream, null)
            val r = ArrayList<String>()
            var item = false
            var ev = p.eventType
            while (ev != XmlPullParser.END_DOCUMENT && r.size < limite) {
                if (ev == XmlPullParser.START_TAG) {
                    if (p.name == "item" || p.name == "entry") item = true
                    else if (p.name == "title" && item) r.add(p.nextText().trim())
                }
                ev = p.next()
            }
            return r
        } finally {
            c.disconnect()
        }
    }

    // ---------------------------------------------------------------- desenho

    private val DIAS = arrayOf("DOMINGO", "SEGUNDA-FEIRA", "TERÇA-FEIRA", "QUARTA-FEIRA", "QUINTA-FEIRA", "SEXTA-FEIRA", "SÁBADO")
    private val MESES = arrayOf("JANEIRO", "FEVEREIRO", "MARÇO", "ABRIL", "MAIO", "JUNHO", "JULHO", "AGOSTO", "SETEMBRO", "OUTUBRO", "NOVEMBRO", "DEZEMBRO")
    private val DIAS_CURTOS = arrayOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SAB")

    /** Codigo WMO -> (texto, icone). */
    private fun tempo(cod: Int): Pair<String, Int> = when (cod) {
        0 -> "CÉU LIMPO" to 0
        1, 2 -> "POUCAS NUVENS" to 1
        3 -> "NUBLADO" to 2
        45, 48 -> "NEBLINA" to 2
        51, 53, 55, 56, 57 -> "GAROA" to 3
        61, 63, 65, 66, 67, 80, 81, 82 -> "CHUVA" to 3
        71, 73, 75, 77, 85, 86 -> "NEVE" to 2
        95, 96, 99 -> "TEMPESTADE" to 4
        else -> "--" to 2
    }

    /** Icones de clima 12x12 (0 sol, 1 sol com nuvem, 2 nuvem, 3 chuva, 4 raio). */
    private val ICONES = arrayOf(
        arrayOf("000010010000", "000000000000", "010001100010", "000111111000", "000111111000", "101111111101",
            "101111111101", "000111111000", "000111111000", "010001100010", "000000000000", "000010010000"),
        arrayOf("010010000000", "001111000000", "111111100000", "011111100000", "011111110000", "001111111110",
            "011111111111", "111111111111", "111111111111", "011111111110", "000000000000", "000000000000"),
        arrayOf("000000000000", "000000000000", "000011110000", "000111111000", "011111111100", "111111111110",
            "111111111111", "111111111111", "011111111110", "000000000000", "000000000000", "000000000000"),
        arrayOf("000011110000", "000111111000", "011111111100", "111111111110", "111111111111", "011111111110",
            "000000000000", "010010010010", "100100100100", "000000000000", "010010010010", "100100100100"),
        arrayOf("000011110000", "000111111000", "011111111100", "111111111110", "111111111111", "011111111110",
            "000001100000", "000011000000", "000111110000", "000001100000", "000011000000", "000010000000")
    )

    private fun icone(cv: Canvas, tipo: Int, x: Float, y: Float, u: Float, t: Tema) {
        val p = ICONES[tipo.coerceIn(0, ICONES.size - 1)]
        val cor = when (tipo) { 0 -> 0xFFFFC23A.toInt(); 4 -> 0xFFFFE066.toInt(); else -> t.texto }
        for (r in p.indices) for (c in p[r].indices) if (p[r][c] == '1') {
            val cc = if (tipo == 1 && r < 6 && c < 8 && p[r][c] == '1' && r + c < 9) 0xFFFFC23A.toInt()
                else if (tipo == 3 && r >= 7) 0xFF5AD1FF.toInt() else cor
            Desenho.retangulo(cv, x + c * u, y + r * u, x + (c + 1) * u, y + (r + 1) * u, cc)
        }
    }

    private fun cartao(cv: Canvas, r: RectF, t: Tema, titulo: String, lc: Int) {
        Desenho.caixa(cv, r, lc * 2f, t.borda, t.painel, 2f)
        PixelFont.desenhar(cv, titulo, r.left + lc * 4f, r.top + lc * 3f, lc, t.destaque, t.contorno)
    }

    override fun desenhar(cv: Canvas, w: Float, h: Float, t: Tema) {
        val cc = Desenho.escala(w)
        val lc = max(2, (cc * 0.6f).toInt())
        val agora = Calendar.getInstance()
        val ms = System.currentTimeMillis()

        // ---- relogio grande (esquerda em cima)
        val rc = max(6, (w * 0.021f).toInt())
        val pisca = (ms / 500) % 2L == 0L
        val hora = "%02d%s%02d".format(agora.get(Calendar.HOUR_OF_DAY), if (pisca) ":" else " ", agora.get(Calendar.MINUTE))
        val x0 = w * 0.06f
        PixelFont.desenhar(cv, hora, x0, h * 0.07f, rc, t.destaque, t.contorno)
        val seg = "%02d".format(agora.get(Calendar.SECOND))
        PixelFont.desenhar(cv, seg, x0 + PixelFont.largura(hora, rc) + rc * 2f, h * 0.07f + 7 * rc - 7 * (rc / 3), max(2, rc / 3), t.texto)
        val data = "${DIAS[agora.get(Calendar.DAY_OF_WEEK) - 1]}, ${agora.get(Calendar.DAY_OF_MONTH)} DE ${MESES[agora.get(Calendar.MONTH)]}"
        val dc = max(2, (cc * 0.8f).toInt())
        PixelFont.desenhar(cv, data, x0, h * 0.07f + 7 * rc + rc * 2.5f, dc, t.texto, t.contorno)

        // ---- clima (esquerda embaixo)
        val colEsq = RectF(x0, h * 0.36f, w * 0.56f, h * 0.80f)
        cartao(cv, colEsq, t, "CLIMA - " +Desenho.limpar(cidadeNome, 22), lc)
        val cl = clima
        val atual = cl?.optJSONObject("current")
        if (atual != null) {
            val (txt, ic) = tempo(atual.optInt("weather_code", -1))
            val u = (colEsq.height() * 0.30f / 12f)
            icone(cv, ic, colEsq.left + lc * 5f, colEsq.top + lc * 12f, u, t)
            val temp = "${atual.optDouble("temperature_2m").roundToInt()}°"
            val tc = max(4, (u * 1.1f).toInt())
            val xt = colEsq.left + lc * 5f + 13 * u + lc * 4f
            PixelFont.desenhar(cv, temp.replace("°", ""), xt, colEsq.top + lc * 12f, tc, t.texto, t.contorno)
            // simbolo de grau desenhado como quadradinho
            val xg = xt + PixelFont.largura(temp.replace("°", ""), tc) + tc
            Desenho.retangulo(cv, xg, colEsq.top + lc * 12f, xg + tc * 2f, colEsq.top + lc * 12f + tc * 2f, t.texto)
            PixelFont.desenhar(cv, txt, xt, colEsq.top + lc * 12f + 7 * tc + lc * 2f, lc, t.texto)
            val det = "UMIDADE ${atual.optInt("relative_humidity_2m")}%  VENTO ${atual.optDouble("wind_speed_10m").roundToInt()} KM/H"
            PixelFont.desenhar(cv, det, xt, colEsq.top + lc * 12f + 7 * tc + lc * 12f, max(1, lc - 1), t.apagado)

            // proximos dias
            val d = cl.optJSONObject("daily")
            val cods = d?.optJSONArray("weather_code")
            val maxs = d?.optJSONArray("temperature_2m_max")
            val mins = d?.optJSONArray("temperature_2m_min")
            if (cods != null && maxs != null && mins != null) {
                val n = min(4, cods.length())
                val larg = colEsq.width() / max(1, n)
                val yd = colEsq.top + colEsq.height() * 0.60f
                val ud = max(1f, min(larg * 0.30f / 12f, colEsq.height() * 0.17f / 12f))
                for (i in 0 until n) {
                    val cx = colEsq.left + larg * (i + 0.5f)
                    val nome = if (i == 0) "HOJE" else DIAS_CURTOS[(agora.get(Calendar.DAY_OF_WEEK) - 1 + i) % 7]
                    Desenho.centro(cv, nome, cx, yd, lc, t.texto)
                    icone(cv, tempo(cods.optInt(i)).second, cx - 6 * ud, yd + lc * 10f, ud, t)
                    val faixa = "${mins.optDouble(i).roundToInt()}/${maxs.optDouble(i).roundToInt()}"
                    Desenho.centro(cv, faixa, cx, yd + lc * 10f + 12 * ud + lc * 2f, lc, t.apagado)
                }
            }
        } else {
            PixelFont.desenhar(cv, if (status.isNotEmpty()) "BUSCANDO A PREVISÃO..." else "SEM DADOS (SEM INTERNET?)", colEsq.left + lc * 5f, colEsq.top + lc * 14f, lc, t.apagado)
        }

        // ---- cotacoes (direita em cima)
        val colDir = RectF(w * 0.60f, h * 0.07f, w * 0.94f, h * 0.40f)
        cartao(cv, colDir, t, "COTAÇÕES", lc)
        val cot = cotacoes
        val pares = listOf("USDBRL" to "DÓLAR", "EURBRL" to "EURO", "BTCBRL" to "BITCOIN")
        var yc = colDir.top + lc * 13f
        val passo = (colDir.bottom - yc - lc * 3f) / 3f
        for ((cod, nome) in pares) {
            val o = cot?.optJSONObject(cod)
            PixelFont.desenhar(cv, nome, colDir.left + lc * 4f, yc, lc, t.texto)
            if (o != null) {
                val valor = o.optString("bid").toDoubleOrNull() ?: 0.0
                val txt = "R$ " + if (valor >= 1000) String.format(Locale.US, "%,.0f", valor).replace(',', '.')
                    else String.format(Locale.US, "%.2f", valor).replace('.', ',')
                val pct = o.optString("pctChange").toDoubleOrNull() ?: 0.0
                val corP = if (pct >= 0) 0xFF5ADB7A.toInt() else 0xFFFF6A6A.toInt()
                val sp = (if (pct >= 0) "↑ " else "↓ ") + String.format(Locale.US, "%.1f", kotlin.math.abs(pct)).replace('.', ',') + "%"
                val xv = colDir.right - lc * 4f
                PixelFont.desenhar(cv, sp, xv - PixelFont.largura(sp, lc), yc, lc, corP)
                PixelFont.desenhar(cv, txt, xv - PixelFont.largura(sp, lc) - lc * 4f - PixelFont.largura(txt, lc), yc, lc, t.destaque)
            } else PixelFont.desenhar(cv, "--", colDir.right - lc * 4f - PixelFont.largura("--", lc), yc, lc, t.apagado)
            yc += passo
        }

        // ---- lembretes (direita embaixo)
        val colLem = RectF(w * 0.60f, h * 0.44f, w * 0.94f, h * 0.80f)
        cartao(cv, colLem, t, "LEMBRETES", lc)
        val lem = Config.lembretes(ctx)
        if (lem.length() == 0) {
            PixelFont.desenhar(cv, "ESCREVA PELO CELULAR", colLem.left + lc * 4f, colLem.top + lc * 14f, max(1, lc - 1), t.apagado)
            PixelFont.desenhar(cv, "(CARTÃO CONTROLE)", colLem.left + lc * 4f, colLem.top + lc * 24f, max(1, lc - 1), t.apagado)
        } else {
            val maxC = max(6, ((colLem.width() - lc * 8f) / (6f * lc)).toInt())
            var yl = colLem.top + lc * 13f
            for (i in 0 until lem.length()) {
                if (yl > colLem.bottom - lc * 10f) break
                val o = lem.getJSONObject(i)
                val quando = o.optString("quando")
                PixelFont.desenhar(cv, "■", colLem.left + lc * 4f, yl, lc, t.destaque)
                PixelFont.desenhar(cv, Desenho.limpar(o.optString("texto"), maxC - 2), colLem.left + lc * 10f, yl, lc, t.texto)
                if (quando.isNotBlank()) {
                    yl += lc * 9f
                    PixelFont.desenhar(cv, Desenho.limpar(quando, maxC - 2), colLem.left + lc * 10f, yl, max(1, lc - 1), t.apagado)
                }
                yl += lc * 11f
            }
        }

        // ---- manchetes (rodape rolando)
        if (manchetes.isNotEmpty()) {
            val i = ((ms - inicio) / 8000L % manchetes.size).toInt()
            val txt = "NOTÍCIAS: " + Desenho.limpar(manchetes[i], 200)
            val nc = lc
            val yN = h * 0.845f
            Desenho.retangulo(cv, 0f, yN - nc * 2f, w, yN + 7 * nc + nc * 2f, t.painel)
            val larg = PixelFont.largura(txt, nc)
            val x = if (larg <= w * 0.88f) x0 else x0 - ((ms - inicio) % 8000L) / 8000f * max(0f, larg - w * 0.88f)
            cv.save()
            cv.clipRect(x0, yN - nc * 2f, w * 0.94f, yN + 7 * nc + nc * 2f)
            PixelFont.desenhar(cv, txt, x, yN, nc, t.texto)
            cv.restore()
        }
        Desenho.rodape(cv, w, h, t, "OK ATUALIZAR | VOLTAR SAIR" + if (status.isNotEmpty()) " | ATUALIZANDO..." else "")
        v.postInvalidateDelayed(250)
    }

    override fun tecla(k: Int): Boolean {
        if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER) {
            atualizar()
            v.invalidate()
            return true
        }
        return false
    }
}
