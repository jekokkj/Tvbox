package br.tvbox.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/**
 * Descanso de tela pixelado. Modos: 1 = CAMPO (o casal correndo), 2 = ESTRELAS (viagem espacial),
 * 3 = RELOGIO (relogio quicando pela tela). Qualquer tecla fecha (quem trata isso e a MainActivity).
 * Leve de proposito: poucas formas por quadro, sem alocar objetos a cada desenho.
 */
class Descanso(private val v: LauncherView, private val modo: Int) : Painel {

    override val usaFundo: Boolean = false

    private val inicio = System.currentTimeMillis()
    private val tinta = Paint().apply { isAntiAlias = false; isFilterBitmap = false; style = Paint.Style.FILL }
    private var sprite: Bitmap? = null
    private val dest = RectF()

    // Estrelas: posicao (-1..1) e fase de profundidade.
    private val nEstrelas = 110
    private val ex = FloatArray(nEstrelas)
    private val ey = FloatArray(nEstrelas)
    private val ez = FloatArray(nEstrelas)

    // Relogio quicando.
    private var rx = 0.25f
    private var ry = 0.25f
    private var rvx = 1f
    private var rvy = 1f
    private var rcor = 0
    private var ultimo = inicio

    init {
        var s = 12345L
        for (i in 0 until nEstrelas) {
            s = (s * 1103515245L + 12345L) and 0x7fffffffL
            ex[i] = (s % 2000L) / 1000f - 1f
            s = (s * 1103515245L + 12345L) and 0x7fffffffL
            ey[i] = (s % 2000L) / 1000f - 1f
            s = (s * 1103515245L + 12345L) and 0x7fffffffL
            ez[i] = (s % 1000L) / 1000f
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
            else -> relogio(cv, w, h, t)
        }
        v.postInvalidateDelayed(45)
    }

    private fun rect(cv: Canvas, l: Float, t: Float, r: Float, b: Float, cor: Int) {
        tinta.color = cor
        cv.drawRect(l, t, r, b, tinta)
    }

    private fun mod(a: Float, m: Float): Float = ((a % m) + m) % m

    // ------------------------------------------------------------ 1. CAMPO

    private fun campo(cv: Canvas, w: Float, h: Float, t: Tema, ms: Long) {
        cv.drawColor(t.fundo)
        val px = max(3f, w / 160f)
        val chao = h * 0.74f

        // Estrelas piscando no ceu.
        for (i in 0 until 40) {
            val x = ((i * 977) % 1000) / 1000f * w
            val y = ((i * 613) % 1000) / 1000f * chao * 0.55f
            if ((ms / 450 + i) % 5L != 0L) rect(cv, x, y, x + px, y + px, t.apagado)
        }

        // Lua/sol quadrado.
        val lado = px * 9f
        rect(cv, w * 0.82f, h * 0.10f, w * 0.82f + lado, h * 0.10f + lado, t.destaque)

        // Nuvens de pixel deslizando devagar.
        for (i in 0 until 3) {
            val x = mod(w * (0.2f + 0.35f * i) - ms * (0.012f + 0.004f * i), w + px * 40f) - px * 20f
            val y = h * (0.16f + 0.09f * i)
            rect(cv, x, y, x + px * 14f, y + px * 3f, t.borda)
            rect(cv, x + px * 3f, y - px * 2f, x + px * 10f, y + px, t.borda)
        }

        // Chao: faixa de grama e terra, com tufos correndo para tras.
        rect(cv, 0f, chao, w, h, t.painel)
        rect(cv, 0f, chao, w, chao + px * 2f, t.texto)
        for (i in 0 until 14) {
            val x = mod(i * (w / 13f) - ms * 0.45f, w + px * 10f) - px * 5f
            rect(cv, x, chao + px * 5f, x + px, chao + px * 8f, t.borda)
            rect(cv, x + px * 2f, chao + px * 6f, x + px * 3f, chao + px * 9f, t.borda)
        }

        // O casal "correndo": pulinhos, balanco leve e a sombra no chao.
        val bmp = sprite
        if (bmp != null) {
            val sh = h * 0.32f
            val sw = bmp.width * sh / bmp.height
            val fase = ms / 170.0
            val pulo = (abs(sin(fase)) * sh * 0.10).toFloat()
            val cx = (w * 0.5 + sin(ms / 2200.0) * w * 0.10).toFloat()
            val encolhe = 1f - pulo / (sh * 0.5f)
            rect(cv, cx - sw * 0.30f * encolhe, chao - px, cx + sw * 0.30f * encolhe, chao + px, 0x55000000)
            cv.save()
            cv.rotate((sin(fase) * 3.0).toFloat(), cx, chao)
            dest.set(cx - sw / 2f, chao - sh - pulo, cx + sw / 2f, chao - pulo)
            cv.drawBitmap(bmp, null, dest, tinta)
            cv.restore()

            // Coracao que sobe de tempos em tempos.
            val ciclo = (ms % 2200L) / 2200f
            val c = max(2, (w * 0.004f).toInt())
            val alpha = (255 * (1f - ciclo)).toInt()
            val cor = (alpha shl 24) or (0xFF5A8A and 0xFFFFFF)
            PixelFont.desenhar(cv, "♥", cx - PixelFont.largura("♥", c) / 2f, chao - sh - h * 0.04f - ciclo * h * 0.12f, c, cor)
        }
    }

    // ------------------------------------------------------------ 2. ESTRELAS

    private fun estrelas(cv: Canvas, w: Float, h: Float, t: Tema, ms: Long) {
        cv.drawColor(t.fundo)
        val px = max(2f, w / 320f)
        val cx = w / 2f
        val cy = h / 2f
        for (i in 0 until nEstrelas) {
            // d vai de 1 (longe) ate 0 (perto) e recomeca.
            val d = mod(ez[i] - ms * 0.00022f, 1f)
            val k = 1f / (d * 2.6f + 0.12f)
            val x = cx + ex[i] * w * 0.09f * k
            val y = cy + ey[i] * h * 0.09f * k
            if (x < -px * 8f || x > w + px * 8f || y < -px * 8f || y > h + px * 8f) continue
            val tam = px * (1f + (1f - d) * 4f)
            val cor = if (d < 0.25f) t.destaque else if (d < 0.6f) t.texto else t.apagado
            rect(cv, x, y, x + tam, y + tam, cor)
        }
        // Nome discreto do modo no canto.
        val c = max(1, (w * 0.0028f).toInt())
        PixelFont.desenhar(cv, "VIAGEM ESPACIAL", w * 0.04f, h * 0.93f, c, t.apagado)
    }

    // ------------------------------------------------------------ 3. RELOGIO

    private fun relogio(cv: Canvas, w: Float, h: Float, t: Tema) {
        cv.drawColor(t.fundo)
        val agora = System.currentTimeMillis()
        val dt = ((agora - ultimo).coerceIn(0L, 200L)) / 1000f
        ultimo = agora

        val cal = Calendar.getInstance()
        val hora = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val c = max(4, (w * 0.011f).toInt())
        val dc = max(2, c / 2)
        val tw = PixelFont.largura(hora, c).toFloat()
        val th = PixelFont.altura(c).toFloat()
        val pad = c * 3f
        val bw = tw + pad * 2f
        val bh = th + dc * 7f + pad * 3f

        val cores = intArrayOf(t.texto, t.destaque, 0xFFFF5A8A.toInt(), 0xFF5AD1FF.toInt())
        // rx/ry sao fracoes (0..1) do espaco livre.
        val livreX = max(1f, w - bw)
        val livreY = max(1f, h - bh)
        rx += rvx * 0.11f * dt * (w / livreX)
        ry += rvy * 0.08f * dt * (h / livreY)
        if (rx <= 0f) { rx = 0f; rvx = 1f; rcor++ }
        if (rx >= 1f) { rx = 1f; rvx = -1f; rcor++ }
        if (ry <= 0f) { ry = 0f; rvy = 1f; rcor++ }
        if (ry >= 1f) { ry = 1f; rvy = -1f; rcor++ }

        val cor = cores[rcor % cores.size]
        val l = rx * livreX
        val tp = ry * livreY
        Desenho.caixa(cv, RectF(l, tp, l + bw, tp + bh), c * 1.2f, cor, t.painel, max(3f, c * 0.5f))
        PixelFont.desenhar(cv, hora, l + pad, tp + pad, c, cor, t.contorno)
        val dias = arrayOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SAB")
        val meses = arrayOf("JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ")
        val data = "${dias[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${"%02d".format(cal.get(Calendar.DAY_OF_MONTH))} ${meses[cal.get(Calendar.MONTH)]}"
        PixelFont.desenhar(cv, data, l + (bw - PixelFont.largura(data, dc)) / 2f, tp + pad * 2f + th, dc, t.texto)
    }
}
