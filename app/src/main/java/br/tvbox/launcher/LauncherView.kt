package br.tvbox.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.view.View
import android.widget.Toast
import java.util.Calendar
import kotlin.math.max

/** Tela inteira desenhada numa unica View (sem layouts, sem foco do Android): leve e previsivel. */
class LauncherView(ctx: Context) : View(ctx) {

    var tema: Tema = TEMAS[0]
        set(v) {
            field = v
            if (!egg) carregarWallpaper()
            invalidate()
        }

    /** true enquanto a tela do easter egg esta aberta. */
    var egg = false
        private set

    private var selecionado = 0

    /** Cor dos icones escolhida nos Ajustes (null = a cor do tema). */
    var corIcone: String? = null
    var corFoco: String? = null

    /** Tela aberta por cima do inicio (todos os apps, ajustes, debloat, descanso). null = inicio. */
    var painel: Painel? = null
        private set
    private val pilha = ArrayList<Painel>()

    /** A MainActivity usa isso para reagendar o descanso de tela quando a configuracao muda. */
    var aoMudarConfig: (() -> Unit)? = null

    private val tinta = Paint().apply { isAntiAlias = false; isFilterBitmap = false; style = Paint.Style.FILL }
    private val traco = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE }
    private val pIcone = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val icones = HashMap<String, Bitmap?>()
    private var wallpaper: Bitmap? = null
    private var wallpaperId = ""
    private var sprite: Bitmap? = null
    private var foto: Bitmap? = null
    private var instalados = BooleanArray(CARTOES.size)
    private var hora = ""
    private var data = ""

    // Codigo secreto: cima cima baixo baixo esquerda direita esquerda direita (0=cima 1=baixo 2=esq 3=dir).
    private val codigo = intArrayOf(0, 0, 1, 1, 2, 3, 2, 3)
    private val setas = "↑↑↓↓←→←→"
    private var progresso = 0
    private val zerar = Runnable { progresso = 0; invalidate() }

    private class Parte(val txt: String, val caixa: Boolean, val cor: Int)

    init {
        atualizarHora()
        atualizarInstalados()
    }

    // O easter egg usa o tema escolhido pelo usuario.
    private val ativo: Tema
        get() = tema

    fun decodificar(nome: String, cfg: Bitmap.Config): Bitmap? {
        val id = resources.getIdentifier(nome, "drawable", context.packageName)
        if (id == 0) return null
        val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = cfg }
        return BitmapFactory.decodeResource(resources, id, o)
    }

    private fun carregarWallpaper() {
        val id = ativo.wallpaper
        if (id == wallpaperId && wallpaper != null) return
        wallpaper?.recycle()
        // RGB_565 usa metade da memoria; o fundo nao tem transparencia.
        wallpaper = decodificar("wp_$id", Bitmap.Config.RGB_565)
        wallpaperId = id
    }

    private fun icone(app: String, cor: String): Bitmap? =
        icones.getOrPut("${app}_$cor") { decodificar("icon_${app}_$cor", Bitmap.Config.ARGB_8888) }

    fun atualizarHora() {
        val c = Calendar.getInstance()
        val dias = arrayOf("DOM", "SEG", "TER", "QUA", "QUI", "SEX", "SAB")
        val meses = arrayOf("JAN", "FEV", "MAR", "ABR", "MAI", "JUN", "JUL", "AGO", "SET", "OUT", "NOV", "DEZ")
        val h = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        val d = "${dias[c.get(Calendar.DAY_OF_WEEK) - 1]} ${"%02d".format(c.get(Calendar.DAY_OF_MONTH))} " +
            meses[c.get(Calendar.MONTH)]
        if (h != hora || d != data) {
            hora = h
            data = d
            invalidate()
        }
    }

    fun atualizarInstalados() {
        val pm = context.packageManager
        instalados = BooleanArray(CARTOES.size) { i ->
            val c = CARTOES[i]
            c.interno != null ||
            c.pacotes.any { pm.getLaunchIntentForPackage(it) != null || pm.getLeanbackLaunchIntentForPackage(it) != null } ||
                (c.acao != null && Intent(c.acao).resolveActivity(pm) != null)
        }
        invalidate()
    }

    fun mover(d: Int) {
        val n = (selecionado + d).coerceIn(0, CARTOES.size - 1)
        if (n == selecionado) return
        selecionado = n
        invalidate()
    }

    /** Registra uma direcao para o codigo secreto (0=cima 1=baixo 2=esquerda 3=direita). */
    fun sequencia(d: Int) {
        progresso = when {
            d == codigo[progresso] -> progresso + 1
            d == 0 -> if (progresso == 2) 2 else 1
            else -> 0
        }
        removeCallbacks(zerar)
        if (progresso >= codigo.size) {
            progresso = 0
            abrirEgg()
            return
        }
        if (progresso > 0) postDelayed(zerar, 5000)
        invalidate()
    }

    private fun abrirEgg() {
        egg = true
        carregarWallpaper()
        sprite = decodificar("sprite_casal", Bitmap.Config.ARGB_8888)
        foto = decodificar("foto_pixel", Bitmap.Config.ARGB_8888)
        invalidate()
    }

    fun fecharEgg() {
        egg = false
        sprite?.recycle(); sprite = null
        foto?.recycle(); foto = null
        carregarWallpaper()
        invalidate()
    }

    fun abrirPainel(p: Painel) {
        painel?.let { pilha.add(it) }
        painel = p
        invalidate()
    }

    fun fecharPainel() {
        painel?.aoFechar()
        painel = if (pilha.isEmpty()) null else pilha.removeAt(pilha.size - 1)
        painel?.aoRetomar()
        invalidate()
    }

    fun abrir() {
        val c = CARTOES[selecionado]
        val interno = c.interno
        if (interno != null) {
            when (interno) {
                "todos" -> abrirPainel(TelaTodos(this))
                "ajustes" -> abrirPainel(TelaAjustes(this))
                "radio" -> abrirPainel(TelaRadio(this))
                "podcasts" -> abrirPainel(TelaPodcasts(this))
                "painel" -> abrirPainel(TelaPainel(this))
                "controle" -> abrirPainel(TelaControle(this))
            }
            return
        }
        val pm = context.packageManager
        for (p in c.pacotes) {
            val i = pm.getLeanbackLaunchIntentForPackage(p) ?: pm.getLaunchIntentForPackage(p)
            if (i != null && lancar(i)) return
        }
        c.acao?.let {
            val i = Intent(it)
            if (i.resolveActivity(pm) != null && lancar(i)) return
        }
        c.loja?.let { if (lancar(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$it")))) return }
        c.web?.let { if (lancar(Intent(Intent.ACTION_VIEW, Uri.parse(it)))) return }
        Toast.makeText(context, "NAO FOI POSSIVEL ABRIR", Toast.LENGTH_SHORT).show()
    }

    /** Abre um cartao pelo nome do icone (usado pelo controle do celular). Fecha as telas abertas antes. */
    fun abrirCartao(nome: String): Boolean {
        val i = CARTOES.indexOfFirst { it.icone == nome || it.interno == nome }
        if (i < 0) return false
        while (painel != null) fecharPainel()
        if (egg) fecharEgg()
        selecionado = i
        abrir()
        invalidate()
        return true
    }

    private fun lancar(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    override fun onDraw(cv: Canvas) {
        if (width == 0 || height == 0) return
        val t = ativo
        val p = painel
        if (p != null && !p.usaFundo) {
            p.desenhar(cv, width.toFloat(), height.toFloat(), t)
            return
        }
        carregarWallpaper()
        cv.drawColor(t.fundo)
        wallpaper?.let { cv.drawBitmap(it, null, Rect(0, 0, width, height), tinta) }
        if (p != null) {
            p.desenhar(cv, width.toFloat(), height.toFloat(), t)
            return
        }
        if (egg) desenharEgg(cv, t) else desenharInicio(cv, t)
    }

    // ---------------------------------------------------------------- tela inicial

    private fun desenharInicio(cv: Canvas, t: Tema) {
        val w = width.toFloat()
        val h = height.toFloat()
        // Area segura: 8% de cada borda.
        val sx = w * 0.08f
        val sy = h * 0.08f
        val sw = w * 0.84f

        // Topo: titulo a esquerda, relogio e data a direita.
        val cc = max(2, (w * 0.0055f).toInt())
        val altRel = PixelFont.altura(cc).toFloat()
        PixelFont.desenhar(cv, hora, sx + sw - PixelFont.largura(hora, cc), sy, cc, t.destaque, t.contorno)
        val dc = max(1, cc / 2)
        PixelFont.desenhar(cv, data, sx + sw - PixelFont.largura(data, dc), sy + altRel + dc * 3, dc, t.texto)
        val tc = max(2, (cc * 0.75f).toInt())
        PixelFont.desenhar(cv, "▶ INÍCIO", sx, sy + (altRel - PixelFont.altura(tc)) / 2f, tc, t.destaque, t.contorno)

        // Fileira de cartoes: 6 por vez, rolando para acompanhar o foco.
        val visiveis = minOf(6, CARTOES.size)
        val slot = sw / visiveis
        val cw = slot * 0.83f
        val cy = h * 0.42f
        val lc = max(1, (slot * 0.95f / PixelFont.largura("X".repeat(CARTOES.maxOf { it.rotulo.length }), 1)).toInt())
        val primeiro = (selecionado - 2).coerceIn(0, CARTOES.size - visiveis)
        for (k in 0 until visiveis) {
            val i = primeiro + k
            if (i != selecionado) desenharCartao(cv, i, sx + slot * (k + 0.5f), cy, cw, lc, t)
        }
        desenharCartao(cv, selecionado, sx + slot * (selecionado - primeiro + 0.5f), cy, cw, lc, t) // foco por cima
        // Setas indicando que ha mais cartoes para os lados.
        val sc = max(2, cc / 2)
        if (primeiro > 0) PixelFont.desenhar(cv, "◀", sx - sc * 8f, cy - sc * 3.5f, sc, t.destaque, t.contorno)
        if (primeiro + visiveis < CARTOES.size) PixelFont.desenhar(cv, "▶", sx + sw + sc * 3f, cy - sc * 3.5f, sc, t.destaque, t.contorno)
        // Pontinhos de posicao.
        val dot = max(2f, sc * 0.8f)
        val totalDots = CARTOES.size * dot * 2.5f
        for (i in CARTOES.indices) {
            val x = w / 2f - totalDots / 2f + i * dot * 2.5f
            Desenho.retangulo(cv, x, h * 0.605f, x + dot, h * 0.605f + dot, if (i == selecionado) t.destaque else t.borda)
        }

        // Descricao do cartao em foco.
        val c = CARTOES[selecionado]
        PixelFont.desenhar(cv, c.descricao, w / 2f - PixelFont.largura(c.descricao, lc) / 2f, h * 0.645f, lc, t.texto, t.contorno)
        if (!instalados[selecionado]) {
            val aviso = "NAO INSTALADO: OK ABRE A LOJA"
            val ac = max(1, lc / 2)
            PixelFont.desenhar(cv, aviso, w / 2f - PixelFont.largura(aviso, ac) / 2f, h * 0.695f, ac, t.apagado)
        }

        // Indicador do codigo secreto: aparece so enquanto voce esta digitando.
        if (progresso > 0) desenharSequencia(cv, t, w / 2f, h * 0.79f, max(1, cc / 3))

        rodape(
            cv, h * 0.86f, lc, listOf(
                Parte("OK", true, t.texto), Parte("ABRIR", false, t.texto), Parte("|", false, t.borda),
                Parte("VOLTAR", true, t.texto), Parte("SAIR", false, t.texto), Parte("|", false, t.borda),
                Parte("◀▶", true, t.texto), Parte("MOVER", false, t.texto)
            )
        )
    }

    private fun desenharCartao(cv: Canvas, i: Int, cx: Float, cy: Float, cw: Float, lc: Int, t: Tema) {
        val c = CARTOES[i]
        val foco = i == selecionado
        val meio = cw * (if (foco) 1.12f else 1f) / 2f
        val r = RectF(cx - meio, cy - meio, cx + meio, cy + meio)
        val raio = cw * 0.08f
        val anel = max(4f, cw * 0.05f)
        val b = if (foco) max(3f, cw * 0.035f) else max(2f, cw * 0.02f)

        if (foco) {
            tinta.color = t.anel
            cv.drawRoundRect(RectF(r.left - anel, r.top - anel, r.right + anel, r.bottom + anel), raio + anel, raio + anel, tinta)
            tinta.color = t.destaque
        } else {
            tinta.color = t.borda
        }
        cv.drawRoundRect(r, raio, raio, tinta)
        tinta.color = if (foco) t.painelFoco else t.painel
        cv.drawRoundRect(RectF(r.left + b, r.top + b, r.right - b, r.bottom - b), raio * 0.7f, raio * 0.7f, tinta)

        // Icone em escala inteira (pixel nitido); mais apagado se o app nao estiver instalado.
        val nomeCor = if (foco) (corFoco ?: t.corIconeFoco) else (corIcone ?: t.corIcone)
        val pixel = ICONES_PIXEL[c.icone]
        if (pixel != null) {
            // Icone desenhado em codigo (16x16), em escala inteira.
            val u = max(1f, Math.floor((meio * 2f * 0.62f / 16f).toDouble()).toFloat())
            val x0 = cx - 8 * u
            val y0 = cy - 8 * u
            tinta.color = Config.COR_RGB[nomeCor] ?: t.texto
            if (!instalados[i]) tinta.alpha = 110
            for (r in 0 until 16) for (col in 0 until 16) {
                if (pixel[r][col] == '1') cv.drawRect(x0 + col * u, y0 + r * u, x0 + (col + 1) * u, y0 + (r + 1) * u, tinta)
            }
            tinta.alpha = 255
        } else if (c.icone == "todos") {
            // Grade 3x3 de quadrados, desenhada em codigo (sem arquivo de icone).
            val lado = meio * 2f * 0.60f
            val q = lado / 3.4f
            val gap = (lado - q * 3f) / 2f
            tinta.color = Config.COR_RGB[nomeCor] ?: t.texto
            val x0 = cx - lado / 2f
            val y0 = cy - lado / 2f
            for (gx in 0 until 3) {
                for (gy in 0 until 3) {
                    val l = x0 + gx * (q + gap)
                    val tp = y0 + gy * (q + gap)
                    cv.drawRect(l, tp, l + q, tp + q, tinta)
                }
            }
        } else {
            val bmp = icone(c.icone, nomeCor)
            if (bmp != null) {
                val k = max(1, Math.round(meio * 2f * 0.72f / bmp.width))
                val tw = bmp.width * k
                val th = bmp.height * k
                pIcone.alpha = if (instalados[i]) 255 else 110
                cv.drawBitmap(bmp, null, RectF(cx - tw / 2f, cy - th / 2f, cx + tw / 2f, cy + th / 2f), pIcone)
            }
        }
        // Nome: etiqueta cheia de destaque quando em foco.
        val lw = PixelFont.largura(c.rotulo, lc)
        val ly = cy + cw * 0.61f + lc * 3f
        if (foco) {
            tinta.color = t.destaque
            cv.drawRect(cx - lw / 2f - lc * 2, ly - lc * 1.5f, cx + lw / 2f + lc * 2, ly + 7 * lc + lc * 1.5f, tinta)
            PixelFont.desenhar(cv, c.rotulo, cx - lw / 2f, ly, lc, t.contorno)
        } else {
            PixelFont.desenhar(cv, c.rotulo, cx - lw / 2f, ly, lc, t.texto, t.contorno)
        }
    }

    private fun desenharSequencia(cv: Canvas, t: Tema, cx: Float, y: Float, ic: Int) {
        val sw = 9f * ic
        val sh = 11f * ic
        val gap = 2f * ic
        val total = setas.length * sw + (setas.length - 1) * gap
        var x = cx - total / 2f
        traco.strokeWidth = max(1f, ic * 0.7f)
        for (i in setas.indices) {
            val aceso = i < progresso
            val r = RectF(x, y, x + sw, y + sh)
            if (aceso) {
                tinta.color = t.destaque
                cv.drawRect(r, tinta)
            } else {
                traco.color = t.borda
                cv.drawRect(r, traco)
            }
            PixelFont.desenhar(cv, setas[i].toString(), x + 2f * ic, y + 2f * ic, ic, if (aceso) t.contorno else t.borda)
            x += sw + gap
        }
    }

    private fun rodape(cv: Canvas, y: Float, fc: Int, partes: List<Parte>) {
        val pad = fc * 2f
        val gap = fc * 3f
        var total = 0f
        for ((i, p) in partes.withIndex()) {
            total += PixelFont.largura(p.txt, fc) + (if (p.caixa) 2 * pad else 0f)
            if (i < partes.size - 1) total += gap
        }
        var x = width / 2f - total / 2f
        traco.strokeWidth = max(1f, fc * 0.7f)
        for (p in partes) {
            val lw = PixelFont.largura(p.txt, fc)
            if (p.caixa) {
                traco.color = p.cor
                cv.drawRoundRect(RectF(x, y - pad, x + lw + 2 * pad, y + 7 * fc + pad), fc.toFloat(), fc.toFloat(), traco)
                PixelFont.desenhar(cv, p.txt, x + pad, y, fc, p.cor)
                x += lw + 2 * pad
            } else {
                PixelFont.desenhar(cv, p.txt, x, y, fc, p.cor)
                x += lw
            }
            x += gap
        }
    }

    // ---------------------------------------------------------------- easter egg

    private fun desenharEgg(cv: Canvas, t: Tema) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cc = max(2, (w * 0.0055f).toInt())
        val tc = max(2, (cc * 0.8f).toInt())

        val titulo = "★ EASTER EGG ★"
        PixelFont.desenhar(cv, titulo, w / 2f - PixelFont.largura(titulo, tc) / 2f, h * 0.10f, tc, t.destaque, t.contorno)
        val sub = "TE AMO, CAROLINA"
        PixelFont.desenhar(cv, sub, w / 2f - PixelFont.largura(sub, tc) / 2f, h * 0.19f, tc, t.texto, t.contorno)

        // Foto estilo polaroid, levemente torta.
        foto?.let {
            val fh = h * 0.40f
            val fw = fh * it.width / it.height
            val pad = h * 0.018f
            val rot = h * 0.075f
            val fx = w * 0.34f - (fw + 2 * pad) / 2f
            val fy = h * 0.26f
            val fim = fy + pad + fh + rot
            cv.save()
            cv.rotate(-4f, fx + (fw + 2 * pad) / 2f, (fy + fim) / 2f)
            tinta.color = 0x66000000
            cv.drawRect(fx + pad * 0.6f, fy + pad * 0.6f, fx + fw + 2 * pad + pad * 0.6f, fim + pad * 0.6f, tinta)
            tinta.color = 0xFFEFE6CF.toInt()
            cv.drawRect(fx, fy, fx + fw + 2 * pad, fim, tinta)
            cv.drawBitmap(it, null, RectF(fx + pad, fy + pad, fx + pad + fw, fy + pad + fh), tinta)
            val pc = max(1, (rot * 0.5f / 7f).toInt())
            val tx = "PLAYER 2"
            PixelFont.desenhar(cv, tx, fx + pad + fw / 2f - PixelFont.largura(tx, pc) / 2f, fy + pad + fh + (rot - PixelFont.altura(pc)) / 2f, pc, 0xFF2A2230.toInt())
            cv.restore()
        }

        // O casal em pe sobre a linha, com um coracao que pulsa.
        val cxCasal = w * 0.64f
        sprite?.let {
            val sh = h * 0.41f
            val sw = it.width * sh / it.height
            val base = h * 0.74f
            cv.drawBitmap(it, null, RectF(cxCasal - sw / 2f, base - sh, cxCasal + sw / 2f, base), tinta)
            val pulso = (System.currentTimeMillis() / 500) % 2L == 0L
            val hc = max(2, (cc * 0.6f).toInt()) + (if (pulso) 1 else 0)
            val hx = cxCasal - PixelFont.largura("♥", hc) / 2f
            PixelFont.desenhar(cv, "♥", hx, base - sh - 7 * hc - h * 0.02f, hc, 0xFFFF5A8A.toInt())
            postInvalidateDelayed(500)
        }
        val nc = max(1, (tc * 0.8f).toInt())
        val larg = PixelFont.largura("DAVI ♥ CAROLINA", nc)
        val nx = cxCasal - larg / 2f
        val ny = h * 0.785f
        PixelFont.desenhar(cv, "DAVI ", nx, ny, nc, t.texto, t.contorno)
        PixelFont.desenhar(cv, "♥", nx + 5 * 6f * nc, ny, nc, 0xFFFF5A8A.toInt())
        PixelFont.desenhar(cv, " CAROLINA", nx + 6 * 6f * nc, ny, nc, t.texto, t.contorno)

        rodape(
            cv, h * 0.86f, max(1, nc), listOf(
                Parte("OK", true, t.texto), Parte("CONTINUAR", false, t.texto), Parte("|", false, t.borda),
                Parte("VOLTAR", true, t.texto), Parte("SAIR", false, t.texto)
            )
        )
    }
}
