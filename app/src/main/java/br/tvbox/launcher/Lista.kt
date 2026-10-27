package br.tvbox.launcher

import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Pecas de tela compartilhadas: lista com rolagem, abas e a barra "tocando agora". */
object Lista {

    /** Lista vertical com rolagem. [subs] (opcional) aparece apagado a direita de cada linha. */
    fun desenhar(
        cv: Canvas, w: Float, t: Tema, itens: List<String>, subs: List<String>?, sel: Int,
        topo: Float, base: Float, ativo: Boolean = true, marcas: List<Boolean>? = null
    ) {
        val cc = Desenho.escala(w)
        val lc = max(2, (cc * 0.7f).toInt())
        val sx = w * 0.06f
        val sw = w * 0.88f
        val linha = 7f * lc + lc * 6f
        val cabem = max(1, ((base - topo) / linha).toInt())
        if (itens.isEmpty()) return
        val inicio = (sel - cabem / 2).coerceIn(0, max(0, itens.size - cabem))
        for (k in 0 until min(cabem, itens.size - inicio)) {
            val i = inicio + k
            val y = topo + k * linha
            val foco = i == sel && ativo
            val r = RectF(sx, y, sx + sw, y + linha - lc * 1.5f)
            Desenho.caixa(cv, r, lc * 1.5f, if (foco) t.destaque else t.borda, if (foco) t.painelFoco else t.painel, if (foco) 3f else 1.5f)
            val ty = y + (linha - lc * 1.5f - 7 * lc) / 2f
            val marca = marcas?.getOrNull(i) == true
            val sub = subs?.getOrNull(i) ?: ""
            val larguraSub = if (sub.isEmpty()) 0f else PixelFont.largura(sub, lc).toFloat() + lc * 6f
            val maxChars = max(4, ((sw - lc * 8f - larguraSub) / (6f * lc)).toInt())
            val texto = (if (marca) "★ " else "") + Desenho.limpar(itens[i], maxChars - (if (marca) 2 else 0))
            PixelFont.desenhar(cv, texto, sx + lc * 4f, ty, lc, if (foco) t.destaque else t.texto, t.contorno)
            if (sub.isNotEmpty()) PixelFont.desenhar(cv, sub, sx + sw - lc * 4f - PixelFont.largura(sub, lc), ty, lc, t.apagado)
        }
        // Barra de rolagem.
        if (itens.size > cabem) {
            val alt = base - topo
            val bh = max(lc * 4f, alt * cabem / itens.size)
            val by = topo + (alt - bh) * inicio / max(1, itens.size - cabem)
            Desenho.retangulo(cv, sx + sw + lc * 2f, topo, sx + sw + lc * 3f, base, t.borda)
            Desenho.retangulo(cv, sx + sw + lc * 1.5f, by, sx + sw + lc * 3.5f, by + bh, t.destaque)
        }
    }

    /** Abas horizontais (categorias). */
    fun abas(cv: Canvas, w: Float, t: Tema, nomes: List<String>, sel: Int, y: Float, ativo: Boolean) {
        val cc = Desenho.escala(w)
        var lc = max(2, (cc * 0.6f).toInt())
        var gap = lc * 3f
        var total: Float
        while (true) {
            total = nomes.sumOf { PixelFont.largura(it, lc) + lc * 6.0 }.toFloat() + gap * (nomes.size - 1)
            if (total <= w * 0.88f || lc <= 1) break
            lc--
            gap = lc * 2f
        }
        // Se ainda nao cabe, desliza para manter a aba escolhida visivel.
        var x = w * 0.06f
        if (total > w * 0.88f) {
            var antes = 0f
            for (i in 0 until sel) antes += PixelFont.largura(nomes[i], lc) + lc * 6f + gap
            x -= max(0f, antes - w * 0.3f)
        }
        for ((i, n) in nomes.withIndex()) {
            val larg = PixelFont.largura(n, lc) + lc * 6f
            val foco = i == sel
            if (x + larg > 0 && x < w) {
                val r = RectF(x, y, x + larg, y + 7 * lc + lc * 4f)
                Desenho.caixa(cv, r, lc.toFloat(), if (foco) (if (ativo) t.destaque else t.texto) else t.borda,
                    if (foco) t.painelFoco else t.painel, if (foco) 3f else 1.5f)
                PixelFont.desenhar(cv, n, x + lc * 3f, y + lc * 2f, lc, if (foco) t.destaque else t.apagado)
            }
            x += larg + gap
        }
    }

    fun tempo(ms: Int): String {
        val s = max(0, ms / 1000)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
    }

    /** Barra "tocando agora" no pe da tela. Devolve true se desenhou (ha algo tocando). */
    fun barraTocando(cv: Canvas, w: Float, h: Float, t: Tema, y: Float): Boolean {
        if (Tocador.estado == Tocador.PARADO) return false
        val cc = Desenho.escala(w)
        val lc = max(2, (cc * 0.6f).toInt())
        val sx = w * 0.06f
        val sw = w * 0.88f
        val alt = 7f * lc * 2 + lc * 9f
        Desenho.caixa(cv, RectF(sx, y, sx + sw, y + alt), lc * 2f, t.destaque, t.painel, 3f)
        val ms = System.currentTimeMillis()
        // Equalizador.
        val tocando = Tocador.estado == Tocador.TOCANDO
        val bw = lc * 1.5f
        for (i in 0 until 6) {
            val f = if (tocando) 0.25f + 0.75f * abs(sin(ms / (140.0 + i * 37) + i)).toFloat() else 0.15f
            val bh = (alt - lc * 6f) * f
            Desenho.retangulo(cv, sx + lc * 4f + i * (bw + lc), y + alt - lc * 3f - bh, sx + lc * 4f + i * (bw + lc) + bw, y + alt - lc * 3f, t.destaque)
        }
        val tx = sx + lc * 4f + 6 * (bw + lc) + lc * 4f
        val estado = when (Tocador.estado) {
            Tocador.CARREGANDO -> "CARREGANDO" + ".".repeat(((ms / 400) % 4).toInt())
            Tocador.PAUSADO -> "PAUSADO"
            Tocador.ERRO -> Tocador.erro
            else -> if (Tocador.tipo == "radio") "AO VIVO" else
                Lista.tempo(Tocador.posicaoMs()) + " / " + Lista.tempo(Tocador.duracaoMs()) +
                    (if (Tocador.velocidade != 1f) "  ${Tocador.velocidade}X" else "")
        }
        val maxC = max(4, ((sx + sw - tx - lc * 4f) / (6f * lc)).toInt())
        PixelFont.desenhar(cv, Desenho.limpar(Tocador.titulo, maxC), tx, y + lc * 3f, lc, t.texto, t.contorno)
        PixelFont.desenhar(cv, Desenho.limpar(estado, maxC), tx, y + lc * 3f + 7 * lc + lc * 2f, lc,
            if (Tocador.estado == Tocador.ERRO) 0xFFFF5A5A.toInt() else t.apagado)
        return true
    }
}
