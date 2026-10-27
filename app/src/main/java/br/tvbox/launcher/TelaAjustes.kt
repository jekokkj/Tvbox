package br.tvbox.launcher

import android.content.Intent
import android.graphics.Canvas
import android.graphics.RectF
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

/**
 * Ajustes do launcher: tema, cor dos icones, descanso de tela, debloat e atalhos para as configuracoes do sistema.
 * Esquerda/direita mudam o valor; OK aciona os itens de acao.
 */
class TelaAjustes(private val v: LauncherView) : Painel {

    private class Item(
        val rotulo: String,
        val valor: (() -> String)? = null,
        val mudar: ((Int) -> Unit)? = null,
        val acao: (() -> Unit)? = null,
        val amostra: (() -> Int?)? = null
    )

    private val ctx = v.context
    private var sel = 0
    private val itens = ArrayList<Item>()

    init {
        montar()
    }

    private fun ciclar(atual: String?, d: Int): String? {
        val opcoes: List<String?> = listOf<String?>(null) + Config.CORES
        val i = max(0, opcoes.indexOf(atual))
        return opcoes[(i + d + opcoes.size) % opcoes.size]
    }

    private fun montar() {
        itens.add(Item(
            "TEMA",
            valor = { v.tema.nome },
            mudar = { d ->
                val i = TEMAS.indexOf(v.tema)
                val novo = TEMAS[(i + d + TEMAS.size) % TEMAS.size]
                v.tema = novo
                Config.setTema(ctx, novo.id)
            }
        ))
        itens.add(Item(
            "COR DOS ICONES",
            valor = { (v.corIcone ?: "AUTO (TEMA)").uppercase() },
            mudar = { d ->
                v.corIcone = ciclar(v.corIcone, d)
                Config.setCorIcone(ctx, v.corIcone)
                v.invalidate()
            },
            amostra = { v.corIcone?.let { Config.COR_RGB[it] } }
        ))
        itens.add(Item(
            "COR DO FOCO",
            valor = { (v.corFoco ?: "AUTO (TEMA)").uppercase() },
            mudar = { d ->
                v.corFoco = ciclar(v.corFoco, d)
                Config.setCorFoco(ctx, v.corFoco)
                v.invalidate()
            },
            amostra = { v.corFoco?.let { Config.COR_RGB[it] } }
        ))
        itens.add(Item(
            "DESCANSO DE TELA",
            valor = { Config.NOMES_DESCANSO[Config.descanso(ctx)] },
            mudar = { d ->
                val n = Config.NOMES_DESCANSO.size
                Config.setDescanso(ctx, (Config.descanso(ctx) + d + n) % n)
                v.aoMudarConfig?.invoke()
            }
        ))
        itens.add(Item(
            "ESPERA ATE O DESCANSO",
            valor = { "${Config.ESPERAS[Config.espera(ctx)]} MIN" },
            mudar = { d ->
                val n = Config.ESPERAS.size
                Config.setEspera(ctx, (Config.espera(ctx) + d + n) % n)
                v.aoMudarConfig?.invoke()
            }
        ))
        itens.add(Item(
            "TESTAR DESCANSO DE TELA",
            acao = { v.abrirPainel(Descanso(v, max(1, Config.descanso(ctx)))) }
        ))
        itens.add(Item(
            "CONTROLE PELO CELULAR",
            valor = { if (Servidor.rodando) "LIGADO" else "DESLIGADO" },
            acao = { v.abrirPainel(TelaControle(v)) }
        ))
        itens.add(Item(
            "VOLTAR PELO CELULAR EM OUTROS APPS",
            valor = { if (Acessibilidade.ativo != null) "ATIVO" else "ATIVAR (ACESSIBILIDADE)" },
            acao = { Acessibilidade.abrirConfiguracao(ctx) }
        ))
        itens.add(Item(
            "DEBLOAT: LIMPAR APPS INUTEIS",
            acao = { v.abrirPainel(TelaDebloat(v)) }
        ))
        itens.add(Item(
            "WI-FI E REDE",
            acao = { abrirSistema(Settings.ACTION_WIFI_SETTINGS) }
        ))
        itens.add(Item(
            "CONFIGURAÇÕES DO SISTEMA",
            acao = { abrirSistema(Settings.ACTION_SETTINGS) }
        ))
    }

    private fun abrirSistema(acao: String) {
        try {
            val i = Intent(acao)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "NAO FOI POSSIVEL ABRIR", Toast.LENGTH_SHORT).show()
        }
    }

    override fun desenhar(cv: Canvas, w: Float, h: Float, t: Tema) {
        val cc = Desenho.escala(w)
        val lc = max(2, (cc * 0.75f).toInt())
        Desenho.titulo(cv, w, h, t, "AJUSTES")

        val sx = w * 0.06f
        val sw = w * 0.88f
        val topo = h * 0.17f
        val linha = min(h * 0.078f, (h * 0.70f) / itens.size)
        val altCaixa = linha * 0.86f
        for (i in itens.indices) {
            val it = itens[i]
            val foco = i == sel
            val y = topo + linha * i
            val r = RectF(sx, y, sx + sw, y + altCaixa)
            Desenho.caixa(cv, r, altCaixa * 0.18f, if (foco) t.destaque else t.borda, if (foco) t.painelFoco else t.painel, if (foco) 4f else 2f)
            val ty = y + (altCaixa - 7 * lc) / 2f
            PixelFont.desenhar(cv, it.rotulo, sx + lc * 4f, ty, lc, if (foco) t.destaque else t.texto, t.contorno)

            val vf = it.valor
            if (vf != null) {
                val texto = if (it.mudar != null && foco) "◀ ${vf()} ▶" else vf()
                val larg = PixelFont.largura(texto, lc)
                val xv = sx + sw - lc * 4f - larg
                val cor = it.amostra?.invoke()
                if (cor != null) {
                    val q = 7f * lc
                    Desenho.retangulo(cv, xv - q - lc * 3f, ty, xv - lc * 3f, ty + q, cor)
                }
                PixelFont.desenhar(cv, texto, xv, ty, lc, if (foco) t.destaque else t.texto, t.contorno)
            } else if (foco) {
                val seta = "OK ▶"
                PixelFont.desenhar(cv, seta, sx + sw - lc * 4f - PixelFont.largura(seta, lc), ty, lc, t.destaque, t.contorno)
            }
        }
        Desenho.rodape(cv, w, h, t, "↑↓ ESCOLHER | ◀▶ MUDAR | OK ABRIR | VOLTAR SAIR")
    }

    private fun ehOk(k: Int) =
        k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER

    override fun tecla(k: Int): Boolean {
        val item = itens[sel]
        when {
            k == KeyEvent.KEYCODE_DPAD_UP -> sel = (sel + itens.size - 1) % itens.size
            k == KeyEvent.KEYCODE_DPAD_DOWN -> sel = (sel + 1) % itens.size
            k == KeyEvent.KEYCODE_DPAD_LEFT -> item.mudar?.invoke(-1)
            k == KeyEvent.KEYCODE_DPAD_RIGHT -> item.mudar?.invoke(1)
            ehOk(k) -> {
                val a = item.acao
                if (a != null) a() else item.mudar?.invoke(1)
            }
            else -> return false
        }
        v.invalidate()
        return true
    }
}
