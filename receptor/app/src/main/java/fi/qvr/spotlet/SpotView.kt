package fi.qvr.spotlet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.KeyEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Tela inteira do TV Spotify numa unica View pixelada (mesma estetica do Retro Launcher e do Vita Link):
 * estado do receptor, musica tocando, controles e ajustes, tudo navegavel pelo controle remoto.
 */
class SpotView(ctx: Context, private val prefs: Prefs) : View(ctx) {

    /** Chamado quando o usuario escolhe "SAIR DO APP". */
    var aoSair: (() -> Unit)? = null

    // Foco: fila 0 = botoes de reproducao (coluna 0..2), filas 1..6 = ajustes.
    private var fila = 0
    private var col = 1

    private val t0 = System.currentTimeMillis()
    private val tinta = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val tintaBmp = Paint().apply { isAntiAlias = false; isFilterBitmap = false }

    // Paleta: preto esverdeado com o verde do Spotify.
    private val corTopo = 0xFF020A05.toInt()
    private val corBase = 0xFF0A2412.toInt()
    private val verde = 0xFF1DB954.toInt()
    private val neon = 0xFF33FF66.toInt()
    private val branco = 0xFFE8FFEF.toInt()
    private val apagado = 0xFF5E8F6C.toInt()
    private val painel = 0xFF06160B.toInt()
    private val ambar = 0xFFFFB000.toInt()
    private val vermelho = 0xFFFF5A5A.toInt()
    private val sombra = 0xFF010503.toInt()

    private val nLinhas = 6

    // ------------------------------------------------------------------ logo (disco com tres arcos), 24x24
    private val disco: Bitmap by lazy {
        val n = 24
        val g = Array(n) { IntArray(n) } // 0 vazio, 1 verde, 2 arco escuro, 3 borda
        val cx = (n - 1) / 2f
        val cy = (n - 1) / 2f + 0.5f
        for (y in 0 until n) for (x in 0 until n) {
            val d = hypot(x - cx, y - cy)
            if (d <= 10.2f) g[y][x] = 1 else if (d <= 11.2f) g[y][x] = 3
        }
        fun arco(y0: Float, curva: Float, esp: Int) {
            for (x in 4..n - 5) {
                val t = (x - cx) / 8f
                val y = Math.round(y0 - curva * (1 - t * t))
                for (e in 0 until esp) if (y + e in 0 until n && g[y + e][x] == 1) g[y + e][x] = 2
            }
        }
        arco(8.2f, 1.6f, 2)
        arco(12.2f, 1.4f, 2)
        arco(15.8f, 1.0f, 1)
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        for (y in 0 until n) for (x in 0 until n) {
            bmp.setPixel(
                x, y, when (g[y][x]) {
                    1 -> verde
                    2 -> corTopo
                    3 -> neon
                    else -> Color.TRANSPARENT
                }
            )
        }
        bmp
    }

    // ------------------------------------------------------------------ itens

    private fun linhas(): List<String> = listOf(
        "RECEPTOR: " + (if (prefs.enabled) "LIGADO" else "DESLIGADO"),
        "QUALIDADE: ${prefs.bitrateKbps} KBPS",
        "VOLUME INICIAL: ${prefs.startupVolumePercent}%",
        "VOLUME JUNTO COM O SISTEMA: " + (if (prefs.linkVolume) "SIM" else "NÃO"),
        "INICIAR COM A TV: " + (if (prefs.startOnBoot) "SIM" else "NÃO"),
        "SAIR DO APP"
    )

    private fun ehOk(k: Int) =
        k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun reiniciarSeLigado() {
        if (prefs.enabled) ReceiverService.start(context)
    }

    /** Devolve true se a tecla foi tratada aqui. */
    fun tecla(k: Int): Boolean {
        val total = nLinhas + 1
        when {
            k == KeyEvent.KEYCODE_DPAD_UP -> fila = (fila + total - 1) % total
            k == KeyEvent.KEYCODE_DPAD_DOWN -> fila = (fila + 1) % total
            fila == 0 && k == KeyEvent.KEYCODE_DPAD_LEFT -> col = (col + 2) % 3
            fila == 0 && k == KeyEvent.KEYCODE_DPAD_RIGHT -> col = (col + 1) % 3
            fila == 0 && ehOk(k) -> comandoDaTela()
            fila > 0 && k == KeyEvent.KEYCODE_DPAD_LEFT -> ajustar(fila - 1, -1)
            fila > 0 && (k == KeyEvent.KEYCODE_DPAD_RIGHT || ehOk(k)) -> ajustar(fila - 1, 1)
            else -> return false
        }
        invalidate()
        return true
    }

    private fun comandoDaTela() {
        if (ReceiverService.situacao() != 3) return
        val tocando = ReceiverService.live()?.playing == true
        val nome = when (col) {
            0 -> "prev"
            1 -> if (tocando) "pause" else "play"
            else -> "next"
        }
        ReceiverService.comando(context, nome)
    }

    private fun ajustar(i: Int, dir: Int) {
        when (i) {
            0 -> {
                prefs.enabled = !prefs.enabled
                if (prefs.enabled) ReceiverService.start(context) else ReceiverService.stop(context)
            }
            1 -> {
                val lista = Prefs.BITRATES
                val atual = max(0, lista.indexOf(prefs.bitrateKbps))
                prefs.bitrateKbps = lista[(atual + dir + lista.size) % lista.size]
                reiniciarSeLigado()
            }
            2 -> {
                val v = prefs.startupVolumePercent + 5 * dir
                prefs.startupVolumePercent = if (v > 100) 0 else if (v < 0) 100 else v
                reiniciarSeLigado()
            }
            3 -> {
                prefs.linkVolume = !prefs.linkVolume
                reiniciarSeLigado()
            }
            4 -> prefs.startOnBoot = !prefs.startOnBoot
            5 -> aoSair?.invoke()
        }
    }

    // ------------------------------------------------------------------ desenho

    private fun rect(cv: Canvas, x: Float, y: Float, largura: Float, altura: Float, cor: Int) {
        tinta.color = cor
        cv.drawRect(x, y, x + largura, y + altura, tinta)
    }

    private fun mistura(a: Int, b: Int, f: Float): Int {
        val ff = f.coerceIn(0f, 1f)
        val r = (((a shr 16) and 0xFF) + ((((b shr 16) and 0xFF) - ((a shr 16) and 0xFF)) * ff)).toInt()
        val g = (((a shr 8) and 0xFF) + ((((b shr 8) and 0xFF) - ((a shr 8) and 0xFF)) * ff)).toInt()
        val bl = ((a and 0xFF) + (((b and 0xFF) - (a and 0xFF)) * ff)).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    /** Menor tamanho de letra (a partir de [c]) em que [texto] cabe em [larguraMax]. */
    private fun cabe(texto: String, c: Int, larguraMax: Float): Int {
        var t = c
        while (t > 1 && PixelFont.largura(texto, t) > larguraMax) t--
        return t
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val ms = System.currentTimeMillis() - t0
        val sit = ReceiverService.situacao()
        val live = ReceiverService.live()

        desenharFundo(cv, w, h, ms)
        desenharCabecalho(cv, w, h, sit, live, ms)
        if (sit == 3) desenharTocando(cv, w, h, live, ms) else desenharEspera(cv, w, h, sit, ms)
        desenharTransporte(cv, w, h, sit == 3, live?.playing == true)
        desenharAjustes(cv, w, h)
        val cc = max(2, (w * 0.0055f).toInt())
        Desenho.centro(
            cv, "◀▶ AJUSTAR | OK ESCOLHER | VOLTAR FECHA (O RECEPTOR CONTINUA LIGADO)",
            w / 2f, h * 0.955f, cabe("◀▶ AJUSTAR | OK ESCOLHER | VOLTAR FECHA (O RECEPTOR CONTINUA LIGADO)", max(1, (cc * 0.5f).toInt()), w * 0.92f), apagado
        )
        postInvalidateDelayed(70)
    }

    private fun desenharFundo(cv: Canvas, w: Float, h: Float, ms: Long) {
        val faixas = 16
        val fh = h / faixas
        for (i in 0 until faixas) rect(cv, 0f, i * fh, w, fh + 1f, mistura(corTopo, corBase, i / (faixas - 1f)))
        // Quadradinhos subindo, bem discretos.
        val px = max(3f, w / 200f)
        for (i in 0 until 22) {
            val vel = 0.015f + (i % 5) * 0.007f
            val x = ((i * 379) % 1000) / 1000f * w
            val y = h - ((ms * vel + i * 137f) % (h + 60f)) + 20f
            val lado = px * (2 + i % 3)
            val a = (28 + (i % 4) * 10) shl 24
            rect(cv, x, y, lado, lado, a or (verde and 0xFFFFFF))
        }
    }

    private fun desenharCabecalho(cv: Canvas, w: Float, h: Float, sit: Int, live: ReceiverService.Companion.Live?, ms: Long) {
        val cc = max(2, (w * 0.0055f).toInt())
        val tc = max(2, (cc * 0.75f).toInt())
        val esc = max(1, (h * 0.075f / 24f).toInt())
        val topo = h * 0.04f
        cv.drawBitmap(disco, null, Rect(
            (w * 0.06f).toInt(), topo.toInt(), (w * 0.06f).toInt() + 24 * esc, topo.toInt() + 24 * esc
        ), tintaBmp)
        PixelFont.desenhar(cv, "TV SPOTIFY", w * 0.06f + 24 * esc + tc * 3f, topo + (24 * esc - 7 * tc) / 2f, tc, neon, sombra)

        val situacao: String
        val cor: Int
        when {
            sit == -1 -> { situacao = "ERRO NO MOTOR"; cor = vermelho }
            sit == 0 -> { situacao = "DESLIGADO"; cor = apagado }
            sit == 1 -> { situacao = "INICIANDO"; cor = ambar }
            sit == 2 -> { situacao = "AGUARDANDO"; cor = branco }
            live?.playing == true -> { situacao = "TOCANDO"; cor = neon }
            else -> { situacao = "CONECTADO"; cor = verde }
        }
        val larg = PixelFont.largura(situacao, tc)
        val ty = topo + (24 * esc - 7 * tc) / 2f
        val xTxt = w * 0.94f - larg
        PixelFont.desenhar(cv, situacao, xTxt, ty, tc, cor, sombra)
        // Quadradinho que pisca quando esta esperando ou iniciando.
        val pisca = (sit != 2 && sit != 1) || (ms / 400L) % 2L == 0L
        if (pisca) rect(cv, xTxt - tc * 4f, ty + tc * 1.5f, tc * 3f, tc * 3f, cor)
    }

    // ---------------- caixa (moldura pixelada)

    private fun caixa(cv: Canvas, x: Float, y: Float, l: Float, a: Float, c: Float, cor: Int) {
        tinta.color = cor
        cv.drawRoundRect(RectF(x, y, x + l, y + a), c * 1.5f, c * 1.5f, tinta)
        tinta.color = painel
        cv.drawRoundRect(RectF(x + c, y + c, x + l - c, y + a - c), c * 1.2f, c * 1.2f, tinta)
    }

    // ---------------- esperando conexao

    private fun desenharEspera(cv: Canvas, w: Float, h: Float, sit: Int, ms: Long) {
        val cc = max(2, (w * 0.0055f).toInt())
        val x0 = w * 0.06f
        val y0 = h * 0.17f
        val larg = w * 0.88f
        val alt = h * 0.29f
        caixa(cv, x0, y0, larg, alt, max(2f, cc * 0.6f), apagado)

        // Disco com ondas de "transmissao" saindo dele.
        val esc = max(1, (alt * 0.62f / 24f).toInt())
        val dx = x0 + larg * 0.13f
        val dy = y0 + alt / 2f
        if (sit == 2) {
            val px = max(2f, esc * 1.4f)
            for (k in 0 until 3) {
                val fase = ((ms / 1400f) + k / 3f) % 1f
                val r = 12f * esc + fase * esc * 12f
                val a = ((1f - fase) * 160).toInt().coerceIn(0, 255)
                val passos = 56
                for (s in 0 until passos) {
                    val ang = s * (2 * Math.PI / passos)
                    val ex = dx + (r * cos(ang)).toFloat()
                    val ey = dy + (r * sin(ang)).toFloat()
                    if (ex > x0 + px && ex < x0 + larg * 0.30f && ey > y0 + px && ey < y0 + alt - px) {
                        rect(cv, ex - px / 2f, ey - px / 2f, px, px, (a shl 24) or (neon and 0xFFFFFF))
                    }
                }
            }
        }
        val bal = if (sit == 2) ((ms / 500L) % 2L).toInt() * esc else 0 // "respira" quando esta pronto
        cv.drawBitmap(disco, null, Rect(
            (dx - 12 * esc).toInt(), (dy - 12 * esc - bal / 2).toInt(), (dx + 12 * esc).toInt(), (dy + 12 * esc - bal / 2).toInt()
        ), tintaBmp)

        // Texto.
        val tx = x0 + larg * 0.30f
        val maxW = x0 + larg - tx - cc * 3f
        val pontos = ".".repeat(((ms / 450L) % 4L).toInt())
        val principal: String
        val corP: Int
        when (sit) {
            -1 -> { principal = "MOTOR DE ÁUDIO NÃO CARREGOU"; corP = vermelho }
            0 -> { principal = "RECEPTOR DESLIGADO"; corP = apagado }
            1 -> { principal = "INICIANDO$pontos"; corP = ambar }
            else -> { principal = "AGUARDANDO CONEXÃO$pontos"; corP = branco }
        }
        val c1 = cabe(principal.trimEnd('.') + "...", max(3, cc), maxW)
        var y = y0 + alt * 0.12f
        PixelFont.desenhar(cv, principal, tx, y, c1, corP, sombra)
        y += 7 * c1 + c1 * 3.5f

        val menor = max(1, (cc * 0.62f).toInt())
        val linhasTxt: List<Pair<String, Int>> = when (sit) {
            -1 -> listOf("ESTE APARELHO NÃO TEM O MOTOR NATIVO." to apagado, "REINSTALE O APK DO RECEPTOR." to apagado)
            0 -> listOf("LIGUE O RECEPTOR NA LISTA ABAIXO" to apagado, "(RECEPTOR: LIGADO)" to apagado)
            else -> listOf(
                "NO CELULAR ABRA O SPOTIFY" to branco,
                "TOQUE EM DISPOSITIVOS DISPONÍVEIS" to branco,
                "ESCOLHA:" to apagado,
                Desenho.limpar(prefs.deviceName, 40) to neon,
                "PRECISA DE SPOTIFY PREMIUM NO CELULAR" to apagado
            )
        }
        for ((t, cor) in linhasTxt) {
            val c = if (cor == neon) cabe(t, max(2, (cc * 0.9f).toInt()), maxW) else cabe(t, menor, maxW)
            PixelFont.desenhar(cv, t, tx, y, c, cor, if (cor == neon) sombra else 0)
            y += 7 * c + c * 2.8f
        }
    }

    // ---------------- tocando

    private fun marquee(cv: Canvas, s: String, x: Float, y: Float, c: Int, maxW: Float, cor: Int, ms: Long, sombraCor: Int = 0) {
        val texto = Desenho.limpar(s, 240)
        val lw = PixelFont.largura(texto, c).toFloat()
        cv.save()
        cv.clipRect(x, y - c, x + maxW, y + 8f * c)
        if (lw <= maxW) {
            PixelFont.desenhar(cv, texto, x, y, c, cor, sombraCor)
        } else {
            val ciclo = lw + 10f * c * 3f
            val off = (((ms / 45L).toInt() * c).toFloat()) % ciclo
            PixelFont.desenhar(cv, texto, x - off, y, c, cor, sombraCor)
            PixelFont.desenhar(cv, texto, x - off + ciclo, y, c, cor, sombraCor)
        }
        cv.restore()
    }

    private fun desenharTocando(cv: Canvas, w: Float, h: Float, live: ReceiverService.Companion.Live?, ms: Long) {
        val cc = max(2, (w * 0.0055f).toInt())
        val x0 = w * 0.06f
        val y0 = h * 0.17f
        val larg = w * 0.88f
        val alt = h * 0.29f
        caixa(cv, x0, y0, larg, alt, max(2f, cc * 0.6f), neon)

        val tx = x0 + cc * 5f
        val maxW = larg - cc * 10f
        val menor = max(1, (cc * 0.62f).toInt())
        val usuario = Desenho.limpar(live?.user ?: "", 40)
        PixelFont.desenhar(cv, if (usuario.isEmpty()) "CONECTADO" else "CONECTADO: $usuario", tx, y0 + alt * 0.09f, menor, verde, sombra)

        val titulo = live?.title?.takeIf { it.isNotBlank() } ?: "ESCOLHA UMA MÚSICA NO CELULAR"
        val artista = live?.artist?.takeIf { it.isNotBlank() } ?: ""
        val ct = max(3, (cc * 1.25f).toInt())
        marquee(cv, titulo, tx, y0 + alt * 0.24f, ct, maxW, branco, ms, sombra)
        if (artista.isNotEmpty()) marquee(cv, artista, tx, y0 + alt * 0.24f + 7 * ct + ct * 2.5f, max(2, (cc * 0.9f).toInt()), maxW, apagado, ms)

        // Equalizador: mexe quando esta tocando, fica baixinho quando pausado.
        val tocando = live?.playing == true
        val barras = 20
        val bw = max(3f, cc * 2.2f)
        val gap = max(2f, cc * 0.8f)
        val total = barras * bw + (barras - 1) * gap
        val bx = x0 + larg - total - cc * 5f
        val base = y0 + alt - cc * 3f
        val maxH = alt * 0.30f
        for (i in 0 until barras) {
            val f = if (tocando) 0.18f + 0.82f * abs(sin(ms / (170.0 + i * 29.0) + i * 1.3)).toFloat() else 0.08f
            val bh = max(bw, maxH * f)
            rect(cv, bx + i * (bw + gap), base - bh, bw, bh, if (f > 0.7f) neon else verde)
        }
    }

    // ---------------- botoes de reproducao

    private val iconePrev = listOf("100000011", "100001111", "100011111", "100111111", "100011111", "100001111", "100000011")
    private val iconeNext = iconePrev.map { it.reversed() }
    private val iconePlay = listOf("100000000", "111000000", "111110000", "111111100", "111110000", "111000000", "100000000")
    private val iconePausa = listOf("011001100", "011001100", "011001100", "011001100", "011001100", "011001100", "011001100")

    private fun iconePixel(cv: Canvas, p: List<String>, cx: Float, cy: Float, u: Float, cor: Int) {
        val larg = p[0].length * u
        val alt = p.size * u
        for (r in p.indices) for (c in p[r].indices) {
            if (p[r][c] == '1') rect(cv, cx - larg / 2f + c * u, cy - alt / 2f + r * u, u, u, cor)
        }
    }

    private fun desenharTransporte(cv: Canvas, w: Float, h: Float, ativo: Boolean, tocando: Boolean) {
        val bh = h * 0.115f
        val bw = w * 0.13f
        val gap = w * 0.025f
        val total = 3 * bw + 2 * gap
        val y = h * 0.49f
        val u = max(2f, bh / 11f)
        for (i in 0 until 3) {
            val x = w / 2f - total / 2f + i * (bw + gap)
            val foco = fila == 0 && col == i
            val c = max(2f, u * 0.8f)
            tinta.color = if (foco) neon else apagado
            cv.drawRoundRect(RectF(x, y, x + bw, y + bh), c * 1.5f, c * 1.5f, tinta)
            tinta.color = if (foco) verde else 0xFF0A2412.toInt()
            cv.drawRoundRect(RectF(x + c, y + c, x + bw - c, y + bh - c), c * 1.2f, c * 1.2f, tinta)
            val cor = when {
                !ativo -> 0xFF3C5A45.toInt()
                foco -> branco
                else -> 0xFFB6E6C5.toInt()
            }
            val padrao = when (i) {
                0 -> iconePrev
                1 -> if (tocando) iconePausa else iconePlay
                else -> iconeNext
            }
            iconePixel(cv, padrao, x + bw / 2f, y + bh / 2f, u, cor)
        }
        if (!ativo) {
            Desenho.centro(cv, "OS BOTÕES FUNCIONAM QUANDO O CELULAR ESTIVER CONECTADO", w / 2f, y + bh + h * 0.012f, max(1, (w * 0.0055f * 0.5f).toInt()), apagado)
        }
    }

    // ---------------- ajustes

    private fun desenharAjustes(cv: Canvas, w: Float, h: Float) {
        val lista = linhas()
        val topo = h * 0.655f
        val area = h * 0.275f
        val linhaAlt = area / lista.size
        val c = max(1, min((linhaAlt / 11f).toInt(), (w * 0.0042f).toInt()))
        val larg = w * 0.62f
        val x0 = w / 2f - larg / 2f
        for ((i, s) in lista.withIndex()) {
            val y = topo + i * linhaAlt
            val foco = fila == i + 1
            if (foco) {
                rect(cv, x0, y, larg, linhaAlt - c * 1.5f, verde)
                rect(cv, x0, y, c * 2f, linhaAlt - c * 1.5f, neon)
            } else {
                rect(cv, x0, y, larg, linhaAlt - c * 1.5f, 0x66000000)
            }
            val texto = if (foco && i in 1..3) "◀ $s ▶" else s
            val tc = cabe(texto, c, larg - c * 8f)
            PixelFont.desenhar(
                cv, texto, x0 + c * 5f, y + (linhaAlt - c * 1.5f - 7 * tc) / 2f, tc,
                if (foco) branco else apagado, if (foco) sombra else 0
            )
        }
    }
}
