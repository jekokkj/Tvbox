package br.tvbox.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Descansos de tela pixelados (qualquer tecla fecha; quem trata e a MainActivity):
 *  1 CAMPO    dia virando noite, montanhas em camadas, o casal correndo com um cachorrinho e passaros
 *  2 ESTRELAS viagem pelo hiperespaco com nebulosa, estrelas cadentes, planeta com anel e nave
 *  3 RELOGIO  relogio grande sobre chuva de pixels
 *  4 PS2      homenagem a abertura do console: torres de luz girando em 3D (sem logo nem nome da Sony)
 *  5 CIDADE   skyline a noite: janelas acendendo, trem, corredor na calcada, chuva e neon
 * Tudo anda pelo relogio (ms), nao por quadro: fica suave mesmo quando a caixa engasga.
 * Nada e alocado dentro do desenho (vetores pre-calculados no inicio).
 */
class Descanso(private val v: LauncherView, private val modo: Int) : Painel {

    override val usaFundo: Boolean = false

    private val inicio = System.currentTimeMillis()
    private val tinta = Paint().apply { isAntiAlias = false; isFilterBitmap = false; style = Paint.Style.FILL }
    private var sprite: Bitmap? = null
    private val dest = RectF()

    private var semente = 12345L
    private fun aleatorio(): Float {
        semente = (semente * 1103515245L + 12345L) and 0x7fffffffL
        return (semente % 10000L) / 10000f
    }

    // Estrelas (ESTRELAS, CAMPO a noite, PS2).
    private val nEstrelas = 140
    private val ex = FloatArray(nEstrelas)
    private val ey = FloatArray(nEstrelas)
    private val ez = FloatArray(nEstrelas)

    // Chuva de pixels (RELOGIO) e chuva (CIDADE).
    private val nGotas = 70
    private val gx = FloatArray(nGotas)
    private val gv = FloatArray(nGotas)
    private val gf = FloatArray(nGotas)

    // Cidade: predios (x, largura, altura) em duas camadas e semente das janelas.
    private val nPredios = 34
    private val px0 = FloatArray(nPredios)
    private val pw = FloatArray(nPredios)
    private val ph = FloatArray(nPredios)
    private val pCamada = IntArray(nPredios)

    // PS2: torres no plano (x, z, altura) e ordem de desenho.
    private val nTorres = 28
    private val tx = FloatArray(nTorres)
    private val tz = FloatArray(nTorres)
    private val th = FloatArray(nTorres)
    private val tProf = FloatArray(nTorres)
    private val ordem = IntArray(nTorres)

    init {
        for (i in 0 until nEstrelas) {
            ex[i] = aleatorio() * 2f - 1f
            ey[i] = aleatorio() * 2f - 1f
            ez[i] = aleatorio()
        }
        for (i in 0 until nGotas) {
            gx[i] = aleatorio()
            gv[i] = 0.25f + aleatorio() * 0.6f
            gf[i] = aleatorio()
        }
        var x = 0f
        for (i in 0 until nPredios) {
            pCamada[i] = if (i % 2 == 0) 0 else 1
            pw[i] = 0.05f + aleatorio() * 0.07f
            ph[i] = (if (pCamada[i] == 0) 0.30f else 0.18f) + aleatorio() * 0.32f
            px0[i] = x
            x += pw[i] * (if (pCamada[i] == 0) 0.55f else 0.75f)
        }
        for (i in 0 until nTorres) {
            val ang = i * 2.3999632f // angulo de ouro: torres espalhadas sem padrao visivel
            val r = 0.35f + 1.65f * kotlin.math.sqrt((i + 0.5f) / nTorres)
            tx[i] = cos(ang) * r
            tz[i] = sin(ang) * r
            th[i] = 0.35f + aleatorio() * 1.2f
            ordem[i] = i
        }
        if (modo == 1) sprite = v.decodificar("sprite_casal", Bitmap.Config.ARGB_8888)
    }

    override fun aoFechar() {
        sprite?.recycle()
        sprite = null
    }

    override fun tecla(k: Int): Boolean = false

    override fun desenhar(cv: Canvas, w: Float, h: Float, t: Tema) {
        val ms = System.currentTimeMillis() - inicio
        when (modo) {
            1 -> campo(cv, w, h, t, ms)
            2 -> estrelas(cv, w, h, t, ms)
            3 -> relogio(cv, w, h, t, ms)
            4 -> ps2(cv, w, h, ms)
            else -> cidade(cv, w, h, t, ms)
        }
        v.postInvalidateDelayed(33)
    }

    // ------------------------------------------------------------ utilidades

    private fun rect(cv: Canvas, l: Float, t: Float, r: Float, b: Float, cor: Int) {
        tinta.color = cor
        cv.drawRect(l, t, r, b, tinta)
    }

    private fun mod(a: Float, m: Float): Float = ((a % m) + m) % m

    private fun mistura(a: Int, b: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        val ra = (a shr 16) and 0xFF; val ga = (a shr 8) and 0xFF; val ba = a and 0xFF
        val rb = (b shr 16) and 0xFF; val gb = (b shr 8) and 0xFF; val bb = b and 0xFF
        return (0xFF shl 24) or ((ra + (rb - ra) * k).toInt() shl 16) or ((ga + (gb - ga) * k).toInt() shl 8) or (ba + (bb - ba) * k).toInt()
    }

    private fun alfa(cor: Int, a: Int): Int = (a.coerceIn(0, 255) shl 24) or (cor and 0xFFFFFF)

    /** Desenha um padrao de pixels ('1' = cor). */
    private fun padrao(cv: Canvas, p: Array<String>, x: Float, y: Float, u: Float, cor: Int, espelhar: Boolean = false) {
        tinta.color = cor
        for (r in p.indices) {
            val linha = p[r]
            for (c in linha.indices) if (linha[c] == '1') {
                val cc = if (espelhar) linha.length - 1 - c else c
                cv.drawRect(x + cc * u, y + r * u, x + (cc + 1) * u, y + (r + 1) * u, tinta)
            }
        }
    }

    // Cachorrinho correndo (2 quadros) e corredor (2 quadros), 12x8 e 8x12.
    private val CAO = arrayOf(
        arrayOf("000000000110", "000000001111", "100000001110", "011111111100", "011111111100", "010000001000", "100000000100", "000000000000"),
        arrayOf("000000000110", "000000001111", "010000001110", "011111111100", "011111111100", "001000010000", "000100100000", "000000000000")
    )
    private val CORREDOR = arrayOf(
        arrayOf("00011000", "00011000", "00010000", "00111100", "01011010", "00011000", "00011000", "00100100", "01000010", "10000001", "00000000", "00000000"),
        arrayOf("00011000", "00011000", "00010000", "00111000", "00111100", "00011000", "00011000", "00011000", "00101000", "00100100", "00000000", "00000000")
    )
    private val PASSARO = arrayOf(
        arrayOf("1000001", "0100010", "0011100"),
        arrayOf("0000000", "0011100", "1100011")
    )
    private val NAVE = arrayOf(
        "0000110000000", "0001111000000", "0011111110000", "1111111111110", "1111111111111",
        "1111111111110", "0011111110000", "0001111000000", "0000110000000"
    )

    // ------------------------------------------------------------ 1. CAMPO

    private fun campo(cv: Canvas, w: Float, h: Float, t: Tema, ms: Long) {
        val px = max(3f, w / 200f)
        val chao = h * 0.76f
        // Ciclo de 90 s: dia (0..0.45), por do sol, noite (0.55..0.95), amanhecer.
        val ciclo = (ms % 90_000L) / 90_000f
        val noite = when {
            ciclo < 0.40f -> 0f
            ciclo < 0.55f -> (ciclo - 0.40f) / 0.15f
            ciclo < 0.90f -> 1f
            else -> 1f - (ciclo - 0.90f) / 0.10f
        }
        val ceuDiaTopo = 0xFF3D8BFF.toInt(); val ceuDiaBase = 0xFFAEE3FF.toInt()
        val ceuNoiteTopo = 0xFF050816.toInt(); val ceuNoiteBase = 0xFF1B2450.toInt()
        val porDoSol = 0xFFFF8A4A.toInt()
        val faixas = 14
        val fh = chao / faixas
        val tarde = 1f - abs(noite - 0.5f) * 2f // pico no meio da transicao
        for (i in 0 until faixas) {
            val f = i / (faixas - 1f)
            var c = mistura(mistura(ceuDiaTopo, ceuDiaBase, f), mistura(ceuNoiteTopo, ceuNoiteBase, f), noite)
            if (tarde > 0f) c = mistura(c, porDoSol, tarde * f * 0.7f)
            rect(cv, 0f, i * fh, w, (i + 1) * fh + 1f, c)
        }
        // Estrelas aparecem a noite.
        if (noite > 0.2f) for (i in 0 until 60) {
            val x = (ex[i] + 1f) / 2f * w
            val y = (ey[i] + 1f) / 2f * chao * 0.6f
            if ((ms / 300 + i * 7) % 11L != 0L) rect(cv, x, y, x + px * 0.7f, y + px * 0.7f, alfa(0xFFFFFF, (noite * 220).toInt()))
        }
        // Sol e lua fazendo arco no ceu.
        val angSol = (ciclo / 0.5f) * Math.PI.toFloat()
        if (ciclo < 0.55f) {
            val sx = w * 0.1f + (w * 0.8f) * (ciclo / 0.55f)
            val sy = chao * 0.75f - sin(angSol.coerceAtMost(Math.PI.toFloat())) * chao * 0.6f
            val lado = px * 9f
            rect(cv, sx - lado * 0.7f, sy - lado * 0.7f, sx + lado * 0.7f, sy + lado * 0.7f, alfa(0xFFE066, 70))
            rect(cv, sx - lado / 2f, sy - lado / 2f, sx + lado / 2f, sy + lado / 2f, 0xFFFFD23A.toInt())
        }
        if (noite > 0.5f) {
            val f = ((ciclo - 0.5f) / 0.45f).coerceIn(0f, 1f)
            val lx = w * 0.15f + w * 0.7f * f
            val ly = chao * 0.7f - sin(f * Math.PI.toFloat()) * chao * 0.55f
            val lado = px * 7f
            rect(cv, lx - lado / 2f, ly - lado / 2f, lx + lado / 2f, ly + lado / 2f, 0xFFEFF3FF.toInt())
            rect(cv, lx - lado / 2f + px * 2f, ly - lado / 2f, lx + lado / 2f, ly + lado / 2f - px * 2f, alfa(0x1B2450, 180))
        }
        // Montanhas ao fundo (parallax lento) e morros (mais rapido).
        val corMontanha = mistura(0xFF5C7FB0.toInt(), 0xFF141B33.toInt(), noite)
        val corMorro = mistura(0xFF3E9A4A.toInt(), 0xFF10301A.toInt(), noite)
        val passo = px * 2f
        var x = 0f
        while (x < w + passo) {
            val m = mod(x + ms * 0.012f, w * 2f) / (w * 2f) * 6.2832f
            val altM = chao * (0.30f + 0.12f * sin(m * 3f) + 0.06f * sin(m * 7f + 1f))
            rect(cv, x, chao - altM, x + passo, chao, corMontanha)
            val n = mod(x + ms * 0.045f, w * 1.5f) / (w * 1.5f) * 6.2832f
            val altH = chao * (0.10f + 0.05f * sin(n * 4f) + 0.03f * sin(n * 9f + 2f))
            rect(cv, x, chao - altH, x + passo, chao, corMorro)
            x += passo
        }
        // Nuvens.
        val corNuvem = mistura(0xFFFFFFFF.toInt(), 0xFF39406A.toInt(), noite)
        for (i in 0 until 4) {
            val cx = mod(w * (0.15f + 0.3f * i) - ms * (0.015f + 0.006f * i), w + px * 50f) - px * 25f
            val cy = h * (0.10f + 0.07f * i)
            rect(cv, cx, cy, cx + px * 16f, cy + px * 3f, corNuvem)
            rect(cv, cx + px * 3f, cy - px * 2f, cx + px * 11f, cy + px, corNuvem)
            rect(cv, cx + px * 6f, cy - px * 4f, cx + px * 9f, cy, corNuvem)
        }
        // Passaros de dia.
        if (noite < 0.6f) for (i in 0 until 3) {
            val bx = mod(w * 0.9f - ms * (0.06f + 0.015f * i) + i * w * 0.3f, w + px * 20f) - px * 10f
            val by = h * (0.22f + 0.05f * i) + sin(ms / 600.0 + i).toFloat() * px * 3f
            padrao(cv, PASSARO[((ms / 220 + i) % 2).toInt()], bx, by, px * 0.8f, alfa(0x202020, (255 * (1f - noite)).toInt()))
        }
        // Chao com grama, flores e terra correndo.
        rect(cv, 0f, chao, w, h, mistura(0xFF3B7A2A.toInt(), 0xFF0E2410.toInt(), noite))
        rect(cv, 0f, chao, w, chao + px * 2f, mistura(0xFF7BD64F.toInt(), 0xFF1F5A28.toInt(), noite))
        rect(cv, 0f, h * 0.88f, w, h, mistura(0xFF6B4A2A.toInt(), 0xFF22160C.toInt(), noite))
        val cores = intArrayOf(0xFFFF5A8A.toInt(), 0xFFFFE066.toInt(), 0xFFFFFFFF.toInt())
        for (i in 0 until 22) {
            val fx = mod(i * (w / 21f) - ms * 0.35f, w + px * 10f) - px * 5f
            val fy = chao + px * (4f + (i * 7) % 9)
            rect(cv, fx, fy, fx + px, fy + px * 2f, mistura(0xFF2E6B22.toInt(), 0xFF0B1D0C.toInt(), noite))
            if (i % 3 == 0) rect(cv, fx - px * 0.5f, fy - px, fx + px * 1.5f, fy, mistura(cores[i % 3], 0xFF333333.toInt(), noite * 0.6f))
        }
        // O casal correndo + o cachorrinho atras.
        val bmp = sprite
        val sh = h * 0.30f
        var cxCasal = w * 0.5f
        if (bmp != null) {
            val sw = bmp.width * sh / bmp.height
            val fase = ms / 150.0
            val pulo = (abs(sin(fase)) * sh * 0.09).toFloat()
            cxCasal = (w * 0.52 + sin(ms / 3100.0) * w * 0.08).toFloat()
            val encolhe = 1f - pulo / (sh * 0.5f)
            rect(cv, cxCasal - sw * 0.30f * encolhe, chao - px, cxCasal + sw * 0.30f * encolhe, chao + px, 0x55000000)
            cv.save()
            cv.rotate((sin(fase) * 3.0).toFloat(), cxCasal, chao)
            dest.set(cxCasal - sw / 2f, chao - sh - pulo, cxCasal + sw / 2f, chao - pulo)
            cv.drawBitmap(bmp, null, dest, tinta)
            cv.restore()
            // Coracao subindo.
            val c = (ms % 2400L) / 2400f
            val hc = max(2, (w * 0.004f).toInt())
            PixelFont.desenhar(cv, "♥", cxCasal - PixelFont.largura("♥", hc) / 2f, chao - sh - h * 0.04f - c * h * 0.12f, hc, alfa(0xFF5A8A, (255 * (1f - c)).toInt()))
        }
        val u = px * 0.9f
        val caoX = cxCasal - sh * 0.75f - 12 * u + sin(ms / 900.0).toFloat() * px * 6f
        val caoPulo = abs(sin(ms / 110.0)).toFloat() * px * 2f
        rect(cv, caoX, chao - px * 0.6f, caoX + 12 * u, chao + px * 0.6f, 0x44000000)
        padrao(cv, CAO[((ms / 110) % 2).toInt()], caoX, chao - 8 * u - caoPulo, u, mistura(0xFFC98A4B.toInt(), 0xFF6B4A2A.toInt(), noite * 0.5f))
    }

    // ------------------------------------------------------------ 2. ESTRELAS

    private fun estrelas(cv: Canvas, w: Float, h: Float, t: Tema, ms: Long) {
        cv.drawColor(0xFF03040C.toInt())
        val px = max(2f, w / 320f)
        val cx = w / 2f
        val cy = h / 2f
        // Nebulosa: blocos grandes e translucidos que respiram.
        for (i in 0 until 7) {
            val nx = w * (0.15f + 0.12f * i) + sin(ms / 7000.0 + i).toFloat() * w * 0.03f
            val ny = h * (0.25f + 0.08f * ((i * 3) % 6)) + cos(ms / 9000.0 + i).toFloat() * h * 0.03f
            val r = w * (0.07f + 0.02f * (i % 3))
            val cor = if (i % 2 == 0) t.destaque else 0xFF5A3AFF.toInt()
            rect(cv, nx - r, ny - r * 0.6f, nx + r, ny + r * 0.6f, alfa(cor, 14))
            rect(cv, nx - r * 0.6f, ny - r * 0.35f, nx + r * 0.6f, ny + r * 0.35f, alfa(cor, 18))
        }
        // Planeta com anel, passando bem devagar.
        val plx = w * 0.82f - (ms % 120_000L) / 120_000f * w * 0.25f
        val ply = h * 0.30f
        val pr = h * 0.09f
        val passoP = max(2f, pr / 10f)
        var yy = -pr
        while (yy <= pr) {
            val meia = kotlin.math.sqrt(max(0f, pr * pr - yy * yy))
            val sombra = (yy + pr) / (2 * pr)
            rect(cv, plx - meia, ply + yy, plx + meia, ply + yy + passoP, mistura(0xFFE0A060.toInt(), 0xFF4A2A18.toInt(), sombra))
            yy += passoP
        }
        rect(cv, plx - pr * 1.7f, ply - passoP * 0.5f, plx + pr * 1.7f, ply + passoP * 0.8f, alfa(0xFFE6C8, 200))
        // Estrelas vindo em direcao a tela (hiperespaco), com rastro.
        for (i in 0 until nEstrelas) {
            val d = mod(ez[i] - ms * 0.00025f, 1f)
            val k = 1f / (d * 2.6f + 0.12f)
            val x = cx + ex[i] * w * 0.09f * k
            val y = cy + ey[i] * h * 0.09f * k
            if (x < -px * 8f || x > w + px * 8f || y < -px * 8f || y > h + px * 8f) continue
            val tam = px * (1f + (1f - d) * 3.5f)
            val cor = if (d < 0.25f) 0xFFFFFFFF.toInt() else if (d < 0.6f) 0xFFBFD7FF.toInt() else 0xFF5A6A99.toInt()
            if (d < 0.35f) {
                // rastro na direcao do centro
                val rx = (x - cx) * 0.06f
                val ry = (y - cy) * 0.06f
                rect(cv, min(x, x - rx), min(y, y - ry), max(x, x - rx) + tam * 0.5f, max(y, y - ry) + tam * 0.5f, alfa(cor, 90))
            }
            rect(cv, x, y, x + tam, y + tam, cor)
        }
        // Estrela cadente a cada ~6 s.
        val ec = (ms % 6000L) / 6000f
        if (ec < 0.25f) {
            val f = ec / 0.25f
            val sx = w * 0.1f + f * w * 0.6f
            val sy = h * 0.1f + f * h * 0.25f
            for (j in 0 until 8) rect(cv, sx - j * px * 3f, sy - j * px * 1.25f, sx - j * px * 3f + px * 2f, sy - j * px * 1.25f + px * 2f, alfa(0xFFFFFF, 230 - j * 28))
        }
        // Nave cruzando a cada 20 s.
        val nc = (ms % 20_000L) / 20_000f
        if (nc < 0.5f) {
            val f = nc / 0.5f
            val u = px * 1.6f
            val nx = -13 * u + f * (w + 26 * u)
            val ny = h * 0.72f + sin(ms / 500.0).toFloat() * px * 4f
            val chama = if ((ms / 70) % 2L == 0L) 0xFFFFB000.toInt() else 0xFFFF5A2A.toInt()
            rect(cv, nx - u * 4f, ny + u * 3f, nx, ny + u * 6f, chama)
            padrao(cv, NAVE, nx, ny, u, 0xFFD8E2F0.toInt())
            rect(cv, nx + u * 6f, ny + u * 3f, nx + u * 9f, ny + u * 5f, 0xFF5AD1FF.toInt())
        }
    }

    // ------------------------------------------------------------ 3. RELOGIO

    private fun relogio(cv: Canvas, w: Float, h: Float, t: Tema, ms: Long) {
        cv.drawColor(t.fundo)
        val px = max(3f, w / 180f)
        // Chuva de pixels caindo em colunas.
        for (i in 0 until nGotas) {
            val x = gx[i] * w
            val y = mod(gf[i] * h + ms * gv[i] * 0.35f, h + px * 20f) - px * 10f
            for (j in 0 until 6) rect(cv, x, y - j * px * 1.4f, x + px, y - j * px * 1.4f + px, alfa(t.texto, 150 - j * 25))
            rect(cv, x, y, x + px, y + px, alfa(0xFFFFFF, 200))
        }
        val cal = Calendar.getInstance()
        val pisca = (System.currentTimeMillis() / 500) % 2L == 0L
        val hora = "%02d%s%02d".format(cal.get(Calendar.HOUR_OF_DAY), if (pisca) ":" else " ", cal.get(Calendar.MINUTE))
        val c = max(6, (w * 0.028f).toInt())
        val tw = PixelFont.largura(hora, c).toFloat()
        // Flutua devagar pela tela (evita marcar a imagem em TVs antigas).
        val fx = (sin(ms / 23_000.0) * 0.18 * w).toFloat()
        val fy = (cos(ms / 31_000.0) * 0.14 * h).toFloat()
        val x = (w - tw) / 2f + fx
        val y = h * 0.36f + fy
        val pad = c * 2.5f
        rect(cv, x - pad, y - pad, x + tw + pad, y + 7 * c + pad * 3f + c * 2f, alfa(t.fundo, 225))
        PixelFont.desenhar(cv, hora, x, y, c, t.destaque, t.contorno)
        // Barra dos segundos.
        val seg = (cal.get(Calendar.SECOND) + (System.currentTimeMillis() % 1000) / 1000f) / 60f
        rect(cv, x, y + 7 * c + c * 1.5f, x + tw, y + 7 * c + c * 2.2f, t.borda)
        rect(cv, x, y + 7 * c + c * 1.5f, x + tw * seg, y + 7 * c + c * 2.2f, t.destaque)
        val dias = arrayOf("DOMINGO", "SEGUNDA", "TERÇA", "QUARTA", "QUINTA", "SEXTA", "SÁBADO")
        val meses = arrayOf("JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ")
        val data = "${dias[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${"%02d".format(cal.get(Calendar.DAY_OF_MONTH))} ${meses[cal.get(Calendar.MONTH)]}"
        val dc = max(2, c / 3)
        PixelFont.desenhar(cv, data, x + (tw - PixelFont.largura(data, dc)) / 2f, y + 7 * c + c * 3.4f, dc, t.texto, t.contorno)
        // Linhas de varredura (estilo CRT).
        var ly = 0f
        while (ly < h) { rect(cv, 0f, ly, w, ly + 1f, 0x22000000); ly += px }
    }

    // ------------------------------------------------------------ 4. PS2 (homenagem)

    private fun ps2(cv: Canvas, w: Float, h: Float, ms: Long) {
        // Fundo azul-escuro com degrade.
        val faixas = 12
        for (i in 0 until faixas) rect(cv, 0f, h * i / faixas, w, h * (i + 1) / faixas + 1f, mistura(0xFF00020A.toInt(), 0xFF0A1440.toInt(), i / (faixas - 1f)))
        val cx = w / 2f
        val horizonte = h * 0.58f
        val foco = w * 0.55f
        // Ciclo de 16 s: aproxima, gira e se afasta (como a abertura).
        val ciclo = (ms % 16_000L) / 16_000f
        val dist = 3.4f - 1.2f * sin(ciclo * Math.PI.toFloat())
        val ang = ms * 0.00016f
        val ca = cos(ang)
        val sa = sin(ang)
        for (i in 0 until nTorres) {
            val xr = tx[i] * ca - tz[i] * sa
            val zr = tx[i] * sa + tz[i] * ca
            tProf[i] = zr + dist
            ordem[i] = i
        }
        // Ordena do fundo para a frente (insercao: sem alocar).
        for (i in 1 until nTorres) {
            val a = ordem[i]
            var j = i - 1
            while (j >= 0 && tProf[ordem[j]] < tProf[a]) { ordem[j + 1] = ordem[j]; j-- }
            ordem[j + 1] = a
        }
        // Chao: linhas de luz em perspectiva.
        for (k in 1..9) {
            val z = k * 0.55f
            val y = horizonte + (0.55f * foco) / (z + 0.4f)
            if (y < h) rect(cv, 0f, y, w, y + 1.5f, alfa(0x3A6BFF, 30 + k * 5))
        }
        val px = max(2f, w / 320f)
        for (o in 0 until nTorres) {
            val i = ordem[o]
            val prof = tProf[i]
            if (prof < 0.4f) continue
            val xr = tx[i] * ca - tz[i] * sa
            val esc = foco / prof
            val sx = cx + xr * esc
            val base = horizonte + 0.55f * esc
            val alt = th[i] * esc * 0.9f
            val larg = max(px, 0.16f * esc)
            val brilho = (255 * (1.6f / prof)).toInt().coerceIn(30, 210)
            // Coluna translucida com faixas, topo brilhante e reflexo no chao.
            rect(cv, sx - larg / 2f, base - alt, sx + larg / 2f, base, alfa(0x2A5BFF, brilho / 3))
            rect(cv, sx - larg / 4f, base - alt, sx + larg / 4f, base, alfa(0x8FB8FF, brilho / 3))
            rect(cv, sx - larg / 2f, base - alt, sx + larg / 2f, base - alt + max(px, larg * 0.25f), alfa(0xFFFFFF, brilho))
            rect(cv, sx - larg / 2f, base, sx + larg / 2f, base + alt * 0.25f, alfa(0x2A5BFF, brilho / 8))
            // Pulso de luz subindo pela torre.
            val pulso = mod(ms / 1800f + i * 0.37f, 1f)
            val yp = base - alt * pulso
            rect(cv, sx - larg / 2f, yp, sx + larg / 2f, yp + max(px, larg * 0.15f), alfa(0xBFE0FF, (brilho * (1f - pulso)).toInt()))
        }
        // Particulas subindo.
        for (i in 0 until 50) {
            val x = (ex[i] + 1f) / 2f * w
            val y = h - mod(ms * (0.03f + ez[i] * 0.05f) + ey[i] * h, h * 1.1f)
            rect(cv, x, y, x + px, y + px, alfa(0xBFE0FF, (60 + 120 * ez[i]).toInt()))
        }
        // No fim do ciclo, o nome aparece e some.
        val fase = ((ciclo - 0.62f) / 0.3f).coerceIn(0f, 1f)
        if (fase > 0f) {
            val a = (255 * sin(fase * Math.PI)).toInt()
            val c = max(4, (w * 0.012f).toInt())
            Desenho.centro(cv, "TV BOX", cx, h * 0.80f, c, alfa(0xFFFFFF, a))
            Desenho.centro(cv, "RETRO LAUNCHER", cx, h * 0.80f + 7 * c + c * 2f, max(1, c / 3), alfa(0x8FB8FF, a))
        }
    }

    // ------------------------------------------------------------ 5. CIDADE

    private fun cidade(cv: Canvas, w: Float, h: Float, t: Tema, ms: Long) {
        val faixas = 12
        for (i in 0 until faixas) rect(cv, 0f, h * 0.8f * i / faixas, w, h * 0.8f * (i + 1) / faixas + 1f,
            mistura(0xFF060818.toInt(), 0xFF2A1A4A.toInt(), i / (faixas - 1f)))
        val px = max(3f, w / 220f)
        // Lua e estrelas.
        rect(cv, w * 0.8f, h * 0.08f, w * 0.8f + px * 9f, h * 0.08f + px * 9f, 0xFFF4F0D8.toInt())
        rect(cv, w * 0.8f + px * 3f, h * 0.08f, w * 0.8f + px * 9f, h * 0.08f + px * 6f, 0xFF0E0C24.toInt())
        for (i in 0 until 40) {
            val x = (ex[i] + 1f) / 2f * w
            val y = (ey[i] + 1f) / 2f * h * 0.35f
            if ((ms / 400 + i) % 9L != 0L) rect(cv, x, y, x + px * 0.6f, y + px * 0.6f, 0x99FFFFFF.toInt())
        }
        val rua = h * 0.86f
        // Predios em duas camadas (a de tras anda mais devagar).
        for (camada in 0..1) {
            val vel = if (camada == 0) 0.006f else 0.018f
            val corPredio = if (camada == 0) 0xFF141432.toInt() else 0xFF0A0A1C.toInt()
            val largTotal = w * 2.2f
            for (i in 0 until nPredios) {
                if (pCamada[i] != camada) continue
                val x = mod(px0[i] * w - ms * vel, largTotal) - w * 0.1f
                if (x > w) continue
                val bw = pw[i] * w
                val bh = ph[i] * h * (if (camada == 0) 1f else 0.9f)
                val topo = rua - bh - (if (camada == 0) h * 0.05f else 0f)
                rect(cv, x, topo, x + bw, rua, corPredio)
                // Janelas que acendem e apagam devagar.
                val jw = px * 1.4f
                var jy = topo + px * 3f
                var linha = 0
                while (jy < rua - px * 3f) {
                    var jx = x + px * 2f
                    var col = 0
                    while (jx < x + bw - px * 2f) {
                        val s = (i * 131 + linha * 17 + col * 7)
                        val acesa = ((s + ms / 4000) % 7L) < 3
                        if (acesa) rect(cv, jx, jy, jx + jw, jy + jw * 1.3f, if (s % 5 == 0) 0xFF8FD8FF.toInt() else 0xFFFFD27A.toInt())
                        jx += px * 3.2f
                        col++
                    }
                    jy += px * 3.6f
                    linha++
                }
                // Antena piscando nos predios altos.
                if (ph[i] > 0.45f && (ms / 600 + i) % 2L == 0L) rect(cv, x + bw / 2f, topo - px * 3f, x + bw / 2f + px, topo - px * 2f, 0xFFFF3A3A.toInt())
            }
        }
        // Letreiros neon piscando.
        val neonC = max(2, (w * 0.004f).toInt())
        if ((ms / 900) % 4L != 0L) PixelFont.desenhar(cv, "24H", w * 0.18f, h * 0.55f, neonC, 0xFFFF4FD8.toInt())
        if ((ms / 1300) % 3L != 0L) PixelFont.desenhar(cv, "RETRO", w * 0.62f, h * 0.5f, neonC, 0xFF4FF0FF.toInt())
        // Trilho elevado e o trem passando.
        val trilho = h * 0.70f
        rect(cv, 0f, trilho, w, trilho + px, 0xFF2A2A44.toInt())
        var pil = mod(-ms * 0.05f, w / 6f)
        while (pil < w) { rect(cv, pil, trilho, pil + px * 1.5f, rua, 0xFF1C1C30.toInt()); pil += w / 6f }
        val trem = (ms % 14_000L) / 14_000f
        val vagao = w * 0.14f
        val tremX = w * 1.1f - trem * (w * 1.1f + vagao * 5f)
        for (k in 0 until 4) {
            val vx = tremX + k * (vagao + px)
            if (vx > w || vx + vagao < 0) continue
            rect(cv, vx, trilho - px * 9f, vx + vagao, trilho, 0xFFB8C0D0.toInt())
            rect(cv, vx, trilho - px * 9f, vx + vagao, trilho - px * 8f, 0xFF6A7088.toInt())
            var jx = vx + px * 2f
            while (jx < vx + vagao - px * 3f) { rect(cv, jx, trilho - px * 6.5f, jx + px * 2.5f, trilho - px * 3.5f, 0xFFFFE6A0.toInt()); jx += px * 4f }
        }
        if (tremX > -vagao && tremX < w) rect(cv, tremX - px * 2f, trilho - px * 4f, tremX, trilho - px * 2f, 0xFFFFFFC0.toInt())
        // Rua, calcada e o corredor.
        rect(cv, 0f, rua, w, h, 0xFF0C0C14.toInt())
        rect(cv, 0f, rua, w, rua + px, 0xFF3A3A55.toInt())
        var faixa = mod(-ms * 0.25f, px * 16f)
        while (faixa < w) { rect(cv, faixa, h * 0.94f, faixa + px * 7f, h * 0.94f + px * 0.8f, 0xFFE0D070.toInt()); faixa += px * 16f }
        val u = px * 0.9f
        val corX = mod(ms * 0.11f, w + 20 * u) - 10 * u
        padrao(cv, CORREDOR[((ms / 120) % 2).toInt()], corX, rua - 12 * u + px * 0.5f, u, 0xFFE8E8F0.toInt())
        // Farois de carro.
        val carro = mod(-ms * 0.4f, w * 1.6f) - w * 0.3f
        rect(cv, carro, h * 0.91f, carro + px * 12f, h * 0.93f, 0xFF3A2A6A.toInt())
        rect(cv, carro - px * 6f, h * 0.915f, carro, h * 0.925f, alfa(0xFFF6C0, 120))
        // Chuva fina na diagonal.
        for (i in 0 until nGotas) {
            val gxp = mod(gx[i] * w + ms * 0.08f * gv[i], w)
            val gy = mod(gf[i] * h + ms * 0.6f * gv[i], h)
            rect(cv, gxp, gy, gxp + 1.5f, gy + px * 2.5f, 0x55AFC8FF)
        }
    }
}
