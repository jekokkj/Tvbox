package br.tvbox.launcher

import android.graphics.Canvas
import android.graphics.Paint

/** Fonte bitmap 5x7 desenhada em codigo (sem arquivo de fonte). So maiusculas, numeros e poucos sinais. */
object PixelFont {
    private val glifos = HashMap<Char, Array<String>>()
    private val tinta = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }

    private fun def(c: Char, linhas: String) {
        glifos[c] = linhas.split(' ').toTypedArray()
    }

    init {
        def('A', "01110 10001 10001 11111 10001 10001 10001")
        def('B', "11110 10001 10001 11110 10001 10001 11110")
        def('C', "01110 10001 10000 10000 10000 10001 01110")
        def('D', "11110 10001 10001 10001 10001 10001 11110")
        def('E', "11111 10000 10000 11110 10000 10000 11111")
        def('F', "11111 10000 10000 11110 10000 10000 10000")
        def('G', "01110 10001 10000 10111 10001 10001 01111")
        def('H', "10001 10001 10001 11111 10001 10001 10001")
        def('I', "01110 00100 00100 00100 00100 00100 01110")
        def('J', "00111 00010 00010 00010 00010 10010 01100")
        def('K', "10001 10010 10100 11000 10100 10010 10001")
        def('L', "10000 10000 10000 10000 10000 10000 11111")
        def('M', "10001 11011 10101 10101 10001 10001 10001")
        def('N', "10001 11001 10101 10011 10001 10001 10001")
        def('O', "01110 10001 10001 10001 10001 10001 01110")
        def('P', "11110 10001 10001 11110 10000 10000 10000")
        def('Q', "01110 10001 10001 10001 10101 10010 01101")
        def('R', "11110 10001 10001 11110 10100 10010 10001")
        def('S', "01111 10000 10000 01110 00001 00001 11110")
        def('T', "11111 00100 00100 00100 00100 00100 00100")
        def('U', "10001 10001 10001 10001 10001 10001 01110")
        def('V', "10001 10001 10001 10001 10001 01010 00100")
        def('W', "10001 10001 10001 10101 10101 11011 10001")
        def('X', "10001 10001 01010 00100 01010 10001 10001")
        def('Y', "10001 10001 01010 00100 00100 00100 00100")
        def('Z', "11111 00001 00010 00100 01000 10000 11111")
        def('0', "01110 10001 10011 10101 11001 10001 01110")
        def('1', "00100 01100 00100 00100 00100 00100 01110")
        def('2', "01110 10001 00001 00010 00100 01000 11111")
        def('3', "11110 00001 00001 01110 00001 00001 11110")
        def('4', "00010 00110 01010 10010 11111 00010 00010")
        def('5', "11111 10000 11110 00001 00001 10001 01110")
        def('6', "00110 01000 10000 11110 10001 10001 01110")
        def('7', "11111 00001 00010 00100 01000 01000 01000")
        def('8', "01110 10001 10001 01110 10001 10001 01110")
        def('9', "01110 10001 10001 01111 00001 00010 01100")
        def(':', "00000 00100 00100 00000 00100 00100 00000")
        def('-', "00000 00000 00000 11111 00000 00000 00000")
        def('.', "00000 00000 00000 00000 00000 01100 01100")
        def('/', "00001 00001 00010 00100 01000 10000 10000")
        def('!', "00100 00100 00100 00100 00100 00000 00100")
        def('?', "01110 10001 00001 00010 00100 00000 00100")
        def('<', "00010 00100 01000 10000 01000 00100 00010")
        def('>', "01000 00100 00010 00001 00010 00100 01000")
        def(',', "00000 00000 00000 00000 00110 00100 01000")
        def('|', "00100 00100 00100 00100 00100 00100 00100")
        def('(', "00010 00100 01000 01000 01000 00100 00010")
        def(')', "01000 00100 00010 00010 00010 00100 01000")
        def('▶', "01000 01100 01110 01111 01110 01100 01000")
        def('◀', "00010 00110 01110 11110 01110 00110 00010")
        def('♥', "00000 01010 11111 11111 01110 00100 00000")
        def('★', "00100 00100 11111 01110 01110 11011 10001")
        def('↑', "00100 01110 10101 00100 00100 00100 00000")
        def('↓', "00000 00100 00100 00100 10101 01110 00100")
        def('←', "00000 00100 01000 11111 01000 00100 00000")
        def('→', "00000 00100 00010 11111 00010 00100 00000")
        def(' ', "00000 00000 00000 00000 00000 00000 00000")
    }

    // Letras acentuadas: letra base + marca (a = agudo, c = circunflexo, t = til, z = cedilha).
    private val acentuadas = mapOf(
        'Á' to Pair('A', 'a'), 'É' to Pair('E', 'a'), 'Í' to Pair('I', 'a'),
        'Ó' to Pair('O', 'a'), 'Ú' to Pair('U', 'a'),
        'Â' to Pair('A', 'c'), 'Ê' to Pair('E', 'c'), 'Ô' to Pair('O', 'c'),
        'Ã' to Pair('A', 't'), 'Õ' to Pair('O', 't'), 'Ç' to Pair('C', 'z')
    )

    private fun marca(cv: Canvas, tipo: Char, x: Float, y: Float, c: Int) {
        val linhas: Array<String>
        val inicio: Int
        when (tipo) {
            'a' -> { linhas = arrayOf("00010", "00100"); inicio = -2 }
            'c' -> { linhas = arrayOf("00100", "01010"); inicio = -2 }
            't' -> { linhas = arrayOf("01101", "10010"); inicio = -2 }
            else -> { linhas = arrayOf("00100"); inicio = 7 }
        }
        for (r in linhas.indices) {
            for (col in 0 until 5) {
                if (linhas[r][col] == '1') {
                    val l = x + col * c
                    val tp = y + (inicio + r) * c
                    cv.drawRect(l, tp, l + c, tp + c, tinta)
                }
            }
        }
    }

    /** Largura em pixels de [texto] com celulas de [c] pixels. */
    fun largura(texto: String, c: Int): Int = if (texto.isEmpty()) 0 else (texto.length * 6 - 1) * c

    fun altura(c: Int): Int = 7 * c

    fun desenhar(cv: Canvas, texto: String, x: Float, y: Float, c: Int, cor: Int, sombra: Int = 0) {
        if (sombra != 0) {
            val d = maxOf(1, c / 2).toFloat()
            passada(cv, texto, x + d, y + d, c, sombra)
        }
        passada(cv, texto, x, y, c, cor)
    }

    private fun passada(cv: Canvas, texto: String, x: Float, y: Float, c: Int, cor: Int) {
        tinta.color = cor
        var px = x
        val t = texto.uppercase()
        for (ch0 in t) {
            val par = acentuadas[ch0]
            val ch = par?.first ?: ch0
            val g = glifos[ch] ?: glifos['?']!!
            if (par != null) marca(cv, par.second, px, y, c)
            for (r in 0 until 7) {
                val linha = g[r]
                for (col in 0 until 5) {
                    if (linha[col] == '1') {
                        val l = px + col * c
                        val tp = y + r * c
                        cv.drawRect(l, tp, l + c, tp + c, tinta)
                    }
                }
            }
            px += 6f * c
        }
    }
}
