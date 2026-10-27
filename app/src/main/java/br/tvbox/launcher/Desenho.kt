package br.tvbox.launcher

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.max

/** Pequenas ferramentas de desenho compartilhadas pelas telas (todas no estilo pixelado). */
object Desenho {
    // Letras que a PixelFont sabe desenhar (as acentuadas estao em \u para nao depender de codificacao).
    private const val PERMITIDOS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:-./!?<>,|() ÁÉÍÓÚÂÊÔÃÕÇ"
    private val SEM_ACENTO: Map<Char, Char> = mapOf(
        'À' to 'A', 'È' to 'E', 'Ì' to 'I', 'Ò' to 'O', 'Ù' to 'U',
        'Ä' to 'A', 'Ë' to 'E', 'Ï' to 'I', 'Ö' to 'O', 'Ü' to 'U',
        'Ñ' to 'N'
    )

    private val tinta = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }

    /** Deixa [s] so com letras que a fonte pixelada desenha, em maiusculas, com no maximo [limite] caracteres. */
    fun limpar(s: String, limite: Int): String {
        val sb = StringBuilder()
        for (c0 in s.uppercase()) {
            val c = SEM_ACENTO[c0] ?: c0
            sb.append(if (PERMITIDOS.indexOf(c) >= 0) c else ' ')
        }
        var r = sb.toString().trim().replace(Regex(" +"), " ")
        if (r.length > limite) r = r.substring(0, max(1, limite - 1)).trimEnd() + "."
        return r
    }

    /** Tamanho base da celula de pixel conforme a largura da tela. */
    fun escala(w: Float): Int = max(2, (w * 0.0055f).toInt())

    fun centro(cv: Canvas, s: String, cx: Float, y: Float, c: Int, cor: Int, sombra: Int = 0) {
        PixelFont.desenhar(cv, s, cx - PixelFont.largura(s, c) / 2f, y, c, cor, sombra)
    }

    fun retangulo(cv: Canvas, l: Float, t: Float, r: Float, b: Float, cor: Int) {
        tinta.color = cor
        cv.drawRect(l, t, r, b, tinta)
    }

    /** Caixa com borda e fundo (mesmo desenho dos cartoes do inicio). */
    fun caixa(cv: Canvas, r: RectF, raio: Float, borda: Int, fundo: Int, espessura: Float) {
        tinta.color = borda
        cv.drawRoundRect(r, raio, raio, tinta)
        tinta.color = fundo
        cv.drawRoundRect(
            RectF(r.left + espessura, r.top + espessura, r.right - espessura, r.bottom - espessura),
            raio * 0.7f, raio * 0.7f, tinta
        )
    }

    fun titulo(cv: Canvas, w: Float, h: Float, t: Tema, texto: String, direita: String = "") {
        val cc = escala(w)
        val tc = max(2, (cc * 0.75f).toInt())
        val sx = w * 0.06f
        val sy = h * 0.07f
        PixelFont.desenhar(cv, "▶ $texto", sx, sy, tc, t.destaque, t.contorno)
        if (direita.isNotEmpty()) {
            PixelFont.desenhar(cv, direita, w - sx - PixelFont.largura(direita, tc), sy, tc, t.texto, t.contorno)
        }
    }

    /** Linha de dicas no pe da tela, ex.: "OK ESCOLHER | VOLTAR SAIR". */
    fun rodape(cv: Canvas, w: Float, h: Float, t: Tema, texto: String) {
        val c = max(1, (escala(w) * 0.5f).toInt())
        centro(cv, texto, w / 2f, h * 0.915f, c, t.apagado)
    }
}
