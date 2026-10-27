package br.tvbox.vita

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

enum class Estado { AGUARDANDO, CONECTANDO, TRANSMITINDO, ERRO }

/**
 * Toda a interface do Vita Link numa unica View pixelada:
 * tela de espera (logo do PS Vita, console animado, cabo USB, menu), ajuda, e o menu que abre por cima do video.
 * Quando esta transmitindo, a View fica transparente e o video (SurfaceView) aparece por tras.
 */
class VitaView(ctx: Context) : View(ctx) {

    var estado: Estado = Estado.AGUARDANDO
        set(v) {
            if (field != v) {
                field = v
                menuAberto = false
                sel = 0
                if (v == Estado.TRANSMITINDO) hudAte = System.currentTimeMillis() + 5000L
            }
            invalidate()
        }

    var erro: String = ""
    var resolucao: String = "--"
    var esticar: Boolean = false

    /** Tela de diagnostico (o que a caixa recebeu do Vita): linhas de texto preenchidas pela MainActivity. */
    var diagAberto: Boolean = false
    var diagLinhas: List<String> = emptyList()

    /** Acoes: "retry", "sair", "res-", "res+", "proporcao", "desconectar". */
    var aoAcao: ((String) -> Unit)? = null

    private var menuAberto = false
    private var ajuda = false
    private var sel = 0
    private var hudAte = 0L
    private val t0 = System.currentTimeMillis()
    private val tinta = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }

    // Paleta (azul PlayStation + cores dos simbolos).
    private val corTopo = 0xFF040A1C.toInt()
    private val corBase = 0xFF0B2A66.toInt()
    private val azul = 0xFF2F7BFF.toInt()
    private val ciano = 0xFF5AD1FF.toInt()
    private val branco = 0xFFEAF2FF.toInt()
    private val apagado = 0xFF6E86B8.toInt()
    private val verde = 0xFF34D07A.toInt()
    private val vermelho = 0xFFFF4D5E.toInt()
    private val rosa = 0xFFFF7FBF.toInt()
    private val sombra = 0xFF030814.toInt()

    // ------------------------------------------------------------------ itens de menu

    private fun itens(): List<String> = when {
        estado == Estado.TRANSMITINDO -> listOf(
            "RESOLUÇÃO: $resolucao",
            "PROPORÇÃO: " + (if (esticar) "ESTICAR" else "AJUSTAR"),
            "DIAGNOSTICO",
            "VOLTAR"
        )
        else -> listOf("TENTAR DE NOVO", "COMO CONECTAR", "DIAGNOSTICO", "SAIR")
    }

    private fun ehOk(k: Int) =
        k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER

    /** Devolve true se a tecla foi tratada aqui. */
    fun tecla(k: Int): Boolean {
        if (diagAberto) {
            diagAberto = false
            aoAcao?.invoke("diag-fechar")
            invalidate()
            return true
        }
        if (ajuda) {
            ajuda = false
            invalidate()
            return true
        }
        if (estado == Estado.TRANSMITINDO && !menuAberto) {
            if (ehOk(k) || k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_BACK) {
                menuAberto = true
                sel = 0
                invalidate()
                return true
            }
            return false
        }
        val lista = itens()
        val vertical = estado == Estado.TRANSMITINDO
        val ant = if (vertical) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_LEFT
        val prox = if (vertical) KeyEvent.KEYCODE_DPAD_DOWN else KeyEvent.KEYCODE_DPAD_RIGHT
        when {
            k == ant -> sel = (sel + lista.size - 1) % lista.size
            k == prox -> sel = (sel + 1) % lista.size
            vertical && k == KeyEvent.KEYCODE_DPAD_LEFT && sel == 0 -> aoAcao?.invoke("res-")
            vertical && k == KeyEvent.KEYCODE_DPAD_RIGHT && sel == 0 -> aoAcao?.invoke("res+")
            vertical && k == KeyEvent.KEYCODE_BACK -> menuAberto = false
            vertical && k == KeyEvent.KEYCODE_MENU -> menuAberto = false
            ehOk(k) -> executar(lista[sel])
            else -> return false
        }
        invalidate()
        return true
    }

    private fun executar(item: String) {
        when {
            item.startsWith("RESOLU") -> aoAcao?.invoke("res+")
            item.startsWith("PROPOR") -> aoAcao?.invoke("proporcao")
            item == "DESCONECTAR" -> { menuAberto = false; aoAcao?.invoke("desconectar") }
            item == "VOLTAR" -> menuAberto = false
            item == "TENTAR DE NOVO" -> aoAcao?.invoke("retry")
            item == "COMO CONECTAR" -> ajuda = true
            item == "DIAGNOSTICO" -> { menuAberto = false; diagAberto = true; aoAcao?.invoke("diag") }
            item == "SAIR" -> aoAcao?.invoke("sair")
        }
    }

    // ------------------------------------------------------------------ desenho

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return
        val agora = System.currentTimeMillis()

        if (diagAberto) {
            desenharFundo(cv, w, h, agora - t0)
            desenharDiag(cv, w, h)
            postInvalidateDelayed(250)
            return
        }

        if (estado == Estado.TRANSMITINDO) {
            // Transparente: o video aparece por tras. So o aviso de resolucao e o menu ficam por cima.
            val hud = agora < hudAte
            if (hud) desenharHud(cv, w, h)
            if (menuAberto) desenharMenuVideo(cv, w, h)
            if (hud || menuAberto) postInvalidateDelayed(80)
            return
        }

        val ms = agora - t0
        desenharFundo(cv, w, h, ms)
        if (ajuda) desenharAjuda(cv, w, h) else desenharEspera(cv, w, h, ms)
        postInvalidateDelayed(60)
    }

    private fun rect(cv: Canvas, x: Float, y: Float, largura: Float, altura: Float, cor: Int) {
        tinta.color = cor
        cv.drawRect(x, y, x + largura, y + altura, tinta)
    }

    private fun mistura(a: Int, b: Int, f: Float): Int {
        val ff = f.coerceIn(0f, 1f)
        val ra = (a shr 16) and 0xFF
        val ga = (a shr 8) and 0xFF
        val ba = a and 0xFF
        val rb = (b shr 16) and 0xFF
        val gb = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val r = (ra + (rb - ra) * ff).toInt()
        val g = (ga + (gb - ga) * ff).toInt()
        val bl = (ba + (bb - ba) * ff).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private fun desenharFundo(cv: Canvas, w: Float, h: Float, ms: Long) {
        // Faixas horizontais (degrade pixelado) do azul escuro para o azul do Vita.
        val faixas = 16
        val fh = h / faixas
        for (i in 0 until faixas) {
            rect(cv, 0f, i * fh, w, fh + 1f, mistura(corTopo, corBase, i / (faixas - 1f)))
        }
        // Bolhas subindo, como no menu do console.
        val px = max(3f, w / 200f)
        for (i in 0 until 26) {
            val vel = 0.018f + (i % 5) * 0.008f
            val x = ((i * 379) % 1000) / 1000f * w
            val y = h - ((ms * vel + i * 137f) % (h + 60f)) + 20f
            val lado = px * (2 + i % 4)
            val a = (40 + (i % 4) * 14) shl 24
            rect(cv, x, y, lado, lado, a or (ciano and 0xFFFFFF))
        }
    }

    // ---------------- tela de espera

    private fun desenharEspera(cv: Canvas, w: Float, h: Float, ms: Long) {
        val cc = max(2, (w * 0.0055f).toInt())
        val tc = max(2, (cc * 0.75f).toInt())

        // Topo: nome do app e situacao.
        PixelFont.desenhar(cv, "▶ VITA LINK", w * 0.06f, h * 0.06f, tc, ciano, sombra)
        val situacao = when (estado) {
            Estado.CONECTANDO -> "CONECTANDO"
            Estado.ERRO -> "ERRO"
            else -> "SEM SINAL"
        }
        val corSit = when (estado) {
            Estado.CONECTANDO -> ciano
            Estado.ERRO -> vermelho
            else -> apagado
        }
        PixelFont.desenhar(cv, situacao, w * 0.94f - PixelFont.largura(situacao, tc), h * 0.06f, tc, corSit, sombra)

        // Logo "PS VITA" com os quatro simbolos embaixo.
        val lc = max(4, (w * 0.0105f).toInt())
        val larg = PixelFont.largura("PS VITA", lc)
        val lx = w / 2f - larg / 2f
        val ly = h * 0.13f
        PixelFont.desenhar(cv, "PS", lx, ly, lc, azul, sombra)
        PixelFont.desenhar(cv, " VITA", lx + 2 * 6f * lc, ly, lc, branco, sombra)
        simbolos(cv, w / 2f, ly + 7 * lc + lc * 1.6f, max(2f, lc / 2.4f))

        // Console, cabo e TV box.
        val u = w * 0.0082f
        val cx = w * 0.14f
        val cy = h * 0.34f
        desenharConsole(cv, cx, cy, u, estado == Estado.CONECTANDO)
        val xCabo0 = cx + 40 * u
        val xTv = w * 0.66f
        desenharCabo(cv, xCabo0, cy + 9 * u, xTv, ms, estado == Estado.CONECTANDO)
        desenharTv(cv, xTv, cy + 2.5f * u, u, estado == Estado.CONECTANDO)

        // Mensagem principal.
        val mc = max(3, (cc * 1.0f).toInt())
        val principal: String
        val corPrincipal: Int
        when (estado) {
            Estado.CONECTANDO -> { principal = "ABRINDO A CÂMERA"; corPrincipal = ciano }
            Estado.ERRO -> { principal = "ALGO DEU ERRADO"; corPrincipal = vermelho }
            else -> { principal = "AGUARDANDO CONEXÃO"; corPrincipal = branco }
        }
        val pontos = ".".repeat(((ms / 450L) % 4L).toInt())
        val larguraBase = PixelFont.largura(principal + "...", mc)
        val mx = w / 2f - larguraBase / 2f
        PixelFont.desenhar(cv, principal + pontos, mx, h * 0.64f, mc, corPrincipal, sombra)

        val sub = when (estado) {
            Estado.ERRO -> Desenho.limpar(erro.ifEmpty { "FALHA AO ABRIR O VITA" }, 52)
            Estado.CONECTANDO -> "VITA DETECTADO - ACEITE A PERMISSÃO USB NA TELA"
            else -> "LIGUE O VITA COM O PLUGIN UDCD UVC E CONECTE O CABO USB"
        }
        Desenho.centro(cv, sub, w / 2f, h * 0.725f, max(1, (cc * 0.55f).toInt()), apagado)

        // Menu horizontal.
        desenharMenuHorizontal(cv, w, h, tc)
        Desenho.centro(cv, "◀▶ ESCOLHER | OK CONFIRMAR | VOLTAR SAIR", w / 2f, h * 0.945f, max(1, (cc * 0.5f).toInt()), apagado)
    }

    private fun desenharMenuHorizontal(cv: Canvas, w: Float, h: Float, tcBase: Int) {
        val lista = itens()
        // Diminui a letra ate o menu inteiro caber na largura da tela.
        var tc = tcBase
        var gap = w * 0.02f
        var total = 0f
        while (true) {
            total = gap * (lista.size - 1)
            for (s in lista) total += PixelFont.largura(s, tc) + tc * 8f
            if (total <= w * 0.92f || tc <= 1) break
            tc -= 1
            gap = w * 0.015f
        }
        val altura = 7 * tc + tc * 4f
        val y = h * 0.80f
        var x = w / 2f - total / 2f
        for ((i, s) in lista.withIndex()) {
            val larg = PixelFont.largura(s, tc) + tc * 8f
            val foco = i == sel
            val r = RectF(x, y, x + larg, y + altura)
            tinta.color = if (foco) ciano else apagado
            cv.drawRoundRect(r, tc * 1.5f, tc * 1.5f, tinta)
            tinta.color = if (foco) azul else 0xFF0A1633.toInt()
            cv.drawRoundRect(RectF(r.left + tc, r.top + tc, r.right - tc, r.bottom - tc), tc * 1.2f, tc * 1.2f, tinta)
            PixelFont.desenhar(cv, s, x + tc * 4f, y + tc * 2f, tc, if (foco) branco else apagado, if (foco) sombra else 0)
            x += larg + gap
        }
    }

    // ---------------- diagnostico

    private fun desenharDiag(cv: Canvas, w: Float, h: Float) {
        val cc = max(2, (w * 0.0055f).toInt())
        val tc = max(2, (cc * 0.75f).toInt())
        PixelFont.desenhar(cv, "\u25B6 DIAGN\u00D3STICO", w * 0.06f, h * 0.05f, tc, ciano, sombra)
        val linhas = diagLinhas.map { Desenho.limpar(it, 60) }
        val maior = max(20, linhas.maxOfOrNull { it.length } ?: 20)
        val c = max(1, min((w * 0.88f / (maior * 6f)).toInt(), (h * 0.80f / (max(1, linhas.size) * 11f)).toInt()))
        var y = h * 0.14f
        for (l in linhas) {
            PixelFont.desenhar(cv, l, w * 0.06f, y, c, if (l.startsWith("FRAMES")) branco else apagado, sombra)
            y += 7 * c + c * 4f
        }
        Desenho.centro(cv, "QUALQUER TECLA VOLTA", w / 2f, h * 0.95f, max(1, (cc * 0.5f).toInt()), apagado)
    }
    // ---------------- ajuda

    private fun desenharAjuda(cv: Canvas, w: Float, h: Float) {
        val cc = max(2, (w * 0.0055f).toInt())
        val tc = max(2, (cc * 0.75f).toInt())
        PixelFont.desenhar(cv, "▶ COMO CONECTAR", w * 0.06f, h * 0.06f, tc, ciano, sombra)
        val linhas = listOf(
            "1  NO VITA INSTALE O PLUGIN UDCD UVC (TAIHEN)",
            "2  REINICIE O VITA E ABRA O JOGO OU O MENU",
            "3  LIGUE O CABO USB DO VITA NA TV BOX",
            "4  ACEITE O PEDIDO DE PERMISSÃO USB NA TELA",
            "5  O VIDEO APARECE SOZINHO",
            "",
            "SEM ÁUDIO: O PLUGIN SÓ ENVIA IMAGEM",
            "USE UM CABO P2 NA SAÍDA DE FONE DO VITA",
            "COM CHIADO: DESLIGUE USB POWER SUPPLY NO VITA"
        )
        val maior = linhas.maxOf { it.length }
        val c = max(1, (w * 0.84f / (maior * 6f)).toInt())
        val altLinha = 7 * c + c * 4f
        var y = h * 0.20f
        for (l in linhas) {
            if (l.isNotEmpty()) PixelFont.desenhar(cv, l, w * 0.08f, y, c, if (l.startsWith("SEM") || l.startsWith("USE") || l.startsWith("COM")) apagado else branco, sombra)
            y += altLinha
        }
        Desenho.centro(cv, "QUALQUER TECLA VOLTA", w / 2f, h * 0.92f, max(1, (cc * 0.5f).toInt()), apagado)
    }

    // ---------------- por cima do video

    private fun desenharHud(cv: Canvas, w: Float, h: Float) {
        val c = max(2, (w * 0.0035f).toInt())
        val texto = "AO VIVO $resolucao"
        val larg = PixelFont.largura(texto, c) + c * 10f
        tinta.color = 0xAA000000.toInt()
        cv.drawRect(w * 0.02f, h * 0.03f, w * 0.02f + larg, h * 0.03f + 7 * c + c * 4f, tinta)
        rect(cv, w * 0.02f + c * 2f, h * 0.03f + c * 2.5f, c * 2f, c * 2f, vermelho)
        PixelFont.desenhar(cv, texto, w * 0.02f + c * 6f, h * 0.03f + c * 2f, c, branco)
        val dica = "OK MENU"
        val d = max(1, c / 2)
        PixelFont.desenhar(cv, dica, w * 0.98f - PixelFont.largura(dica, d), h * 0.04f, d, 0xCCFFFFFF.toInt())
    }

    private fun desenharMenuVideo(cv: Canvas, w: Float, h: Float) {
        val lista = itens()
        val c = max(2, (w * 0.0042f).toInt())
        val linha = 7 * c + c * 5f
        val larg = w * 0.46f
        val alt = linha * lista.size + c * 12f
        val l = w / 2f - larg / 2f
        val t = h / 2f - alt / 2f
        tinta.color = 0xCC000000.toInt()
        cv.drawRect(0f, 0f, w, h, tinta)
        tinta.color = ciano
        cv.drawRoundRect(RectF(l, t, l + larg, t + alt), c * 2f, c * 2f, tinta)
        tinta.color = 0xFF0A1633.toInt()
        cv.drawRoundRect(RectF(l + c, t + c, l + larg - c, t + alt - c), c * 1.5f, c * 1.5f, tinta)
        Desenho.centro(cv, "VITA LINK", w / 2f, t + c * 2.5f, c, azul, sombra)
        for ((i, s) in lista.withIndex()) {
            val y = t + c * 8f + linha * i
            val foco = i == sel
            if (foco) rect(cv, l + c * 2f, y - c, larg - c * 4f, 7 * c + c * 2f, azul)
            val texto = if (foco && i == 0) "◀ $s ▶" else s
            Desenho.centro(cv, texto, w / 2f, y, c, if (foco) branco else apagado)
        }
        Desenho.centro(cv, "OK ESCOLHER | MENU OU VOLTAR FECHA", w / 2f, t + alt + c * 3f, max(1, c / 2), apagado)
    }

    // ---------------- arte pixelada

    /** Console estilo PS Vita, em unidades [u]: 40 x 18. */
    private fun desenharConsole(cv: Canvas, x: Float, y: Float, u: Float, ligado: Boolean) {
        tinta.color = 0xFF3A4F7A.toInt()
        cv.drawRoundRect(RectF(x, y, x + 40 * u, y + 18 * u), 3 * u, 3 * u, tinta)
        tinta.color = 0xFF141B2E.toInt()
        cv.drawRoundRect(RectF(x + u, y + u, x + 39 * u, y + 17 * u), 2.5f * u, 2.5f * u, tinta)
        rect(cv, x + 4 * u, y + u, 32 * u, 0.5f * u, 0xFF5B74A8.toInt())
        // Tela.
        rect(cv, x + 8 * u, y + 2.5f * u, 24 * u, 13 * u, 0xFF02060F.toInt())
        rect(cv, x + 8.5f * u, y + 3 * u, 23 * u, 12 * u, if (ligado) 0xFF0B3C8C.toInt() else 0xFF0A1530.toInt())
        var i = 0
        while (i < 12) {
            rect(cv, x + 8.5f * u, y + (3 + i) * u, 23 * u, 0.5f * u, 0x22FFFFFF)
            i += 2
        }
        val c = max(1, (u / 3.2f).toInt())
        val txt = if (ligado) "VITA" else "ZZZ"
        PixelFont.desenhar(cv, txt, x + 20 * u - PixelFont.largura(txt, c) / 2f, y + 9 * u - 3.5f * c, c, if (ligado) branco else apagado)
        // Analogico esquerdo e direcional.
        rect(cv, x + 2 * u, y + 3 * u, 4.5f * u, 4.5f * u, 0xFF2A3656.toInt())
        rect(cv, x + 3 * u, y + 4 * u, 2.5f * u, 2.5f * u, 0xFF8FA0C8.toInt())
        rect(cv, x + 3 * u, y + 9.6f * u, 4 * u, 1.4f * u, 0xFFC6D0E8.toInt())
        rect(cv, x + 4.3f * u, y + 8.3f * u, 1.4f * u, 4 * u, 0xFFC6D0E8.toInt())
        // Botoes do lado direito (cores dos simbolos) e analogico direito.
        rect(cv, x + 34.6f * u, y + 8.3f * u, 1.5f * u, 1.5f * u, verde)
        rect(cv, x + 36.4f * u, y + 10.1f * u, 1.5f * u, 1.5f * u, vermelho)
        rect(cv, x + 34.6f * u, y + 11.9f * u, 1.5f * u, 1.5f * u, azul)
        rect(cv, x + 32.8f * u, y + 10.1f * u, 1.5f * u, 1.5f * u, rosa)
        rect(cv, x + 33.5f * u, y + 3 * u, 4.5f * u, 4.5f * u, 0xFF2A3656.toInt())
        rect(cv, x + 34.5f * u, y + 4 * u, 2.5f * u, 2.5f * u, 0xFF8FA0C8.toInt())
        // Botoes PS / START / SELECT pequenos.
        rect(cv, x + 3.5f * u, y + 14.2f * u, 1.3f * u, 1.3f * u, 0xFF8FA0C8.toInt())
        rect(cv, x + 34.5f * u, y + 14.2f * u, 1.3f * u, 1.3f * u, 0xFF8FA0C8.toInt())
        // Ombros.
        rect(cv, x + 3 * u, y - 0.6f * u, 7 * u, 0.8f * u, 0xFF5B74A8.toInt())
        rect(cv, x + 30 * u, y - 0.6f * u, 7 * u, 0.8f * u, 0xFF5B74A8.toInt())
    }

    private fun desenharCabo(cv: Canvas, x0: Float, y: Float, x1: Float, ms: Long, ativo: Boolean) {
        val px = max(3f, width / 200f)
        val n = 14
        val passo = (x1 - x0) / n
        for (i in 0 until n) {
            val fase = ((ms / 140L) + i) % 5L
            val cor = when {
                ativo && fase == 0L -> branco
                ativo -> ciano
                fase == 0L -> apagado
                else -> 0xFF233A6B.toInt()
            }
            rect(cv, x0 + passo * i, y, px * 2f, px * 1.2f, cor)
        }
    }

    private fun desenharTv(cv: Canvas, x: Float, y: Float, u: Float, ativo: Boolean) {
        tinta.color = 0xFF3A4F7A.toInt()
        cv.drawRoundRect(RectF(x, y, x + 24 * u, y + 13 * u), 1.5f * u, 1.5f * u, tinta)
        tinta.color = 0xFF141B2E.toInt()
        cv.drawRoundRect(RectF(x + 0.8f * u, y + 0.8f * u, x + 23.2f * u, y + 12.2f * u), 1.2f * u, 1.2f * u, tinta)
        val c = max(1, (u / 3.2f).toInt())
        PixelFont.desenhar(cv, "TV BOX", x + 12 * u - PixelFont.largura("TV BOX", c) / 2f, y + 3.5f * u, c, if (ativo) ciano else apagado)
        rect(cv, x + 3 * u, y + 8.3f * u, 1.2f * u, 1.2f * u, if (ativo) verde else apagado)
        rect(cv, x + 5.2f * u, y + 8.3f * u, 1.2f * u, 1.2f * u, 0xFF233A6B.toInt())
        rect(cv, x + 5 * u, y + 13 * u, 14 * u, 0.8f * u, 0xFF3A4F7A.toInt())
    }

    /** Os quatro simbolos (triangulo, circulo, cruz, quadrado) em pixel, centrados em [cx]. */
    private fun simbolos(cv: Canvas, cx: Float, y: Float, u: Float) {
        val padroes = listOf(
            listOf("0001000", "0001000", "0010100", "0010100", "0100010", "0100010", "1111111"),
            listOf("0011100", "0100010", "1000001", "1000001", "1000001", "0100010", "0011100"),
            listOf("1000001", "0100010", "0010100", "0001000", "0010100", "0100010", "1000001"),
            listOf("1111111", "1000001", "1000001", "1000001", "1000001", "1000001", "1111111")
        )
        val cores = intArrayOf(verde, vermelho, azul, rosa)
        val gap = u * 3f
        val total = 4 * 7 * u + 3 * gap
        var x = cx - total / 2f
        for (k in 0 until 4) {
            val p = padroes[k]
            for (r in 0 until 7) {
                for (c in 0 until 7) {
                    if (p[r][c] == '1') rect(cv, x + c * u, y + r * u, u, u, cores[k])
                }
            }
            x += 7 * u + gap
        }
    }
}

/** Texto: filtra o que a fonte pixelada sabe desenhar. */
object Desenho {
    private const val PERMITIDOS =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789:-./!?<>,|() ÁÉÍÓÚÂÊÔÃÕÇ"

    fun limpar(s: String, limite: Int): String {
        val sb = StringBuilder()
        for (c in s.uppercase()) sb.append(if (PERMITIDOS.indexOf(c) >= 0) c else ' ')
        var r = sb.toString().trim().replace(Regex(" +"), " ")
        if (r.length > limite) r = r.substring(0, max(1, limite - 1)).trimEnd() + "."
        return r
    }

    fun centro(cv: Canvas, s: String, cx: Float, y: Float, c: Int, cor: Int, sombra: Int = 0) {
        PixelFont.desenhar(cv, s, cx - PixelFont.largura(s, c) / 2f, y, c, cor, sombra)
    }
}
