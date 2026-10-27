package br.tvbox.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import android.view.KeyEvent
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

/** Lista de apps normalmente inuteis em TV box. A mesma lista esta em ferramentas/debloat.bat e debloat.sh. */
object Debloat {
    val PACOTES = listOf(
        "com.android.email",
        "com.android.exchange",
        "com.android.calendar",
        "com.android.deskclock",
        "com.android.calculator2",
        "com.android.music",
        "com.android.gallery3d",
        "com.android.soundrecorder",
        "com.android.quicksearchbox",
        "com.android.stk",
        "com.android.providers.partnerbookmarks",
        "com.android.bookmarkprovider",
        "com.android.wallpaper.livepicker",
        "com.android.musicvis",
        "com.android.dreams.basic",
        "com.android.dreams.phototable",
        "com.android.magicsmoke",
        "com.android.noisefield",
        "com.android.phasebeam",
        "com.android.galaxy4",
        "com.android.holospiral",
        "com.android.facelock",
        "com.google.android.videos",
        "com.google.android.music",
        "com.google.android.apps.magazines",
        "com.google.android.talk",
        "com.google.android.keep"
    )
}

/**
 * Mostra os apps da lista de debloat que existem nesta TV box.
 * Apps instalados pelo usuario saem aqui (o Android pede confirmacao); apps de SISTEMA so saem pelo ADB,
 * com o script ferramentas/debloat.bat (ou debloat.sh).
 */
class TelaDebloat(private val v: LauncherView) : Painel {

    private class Achado(val pacote: String, val nome: String, val sistema: Boolean)

    private val ctx = v.context
    private val pm: PackageManager = ctx.packageManager
    private var achados: List<Achado> = emptyList()
    private var sel = 0
    private var topo = 0

    init {
        carregar()
    }

    private fun carregar() {
        val lista = ArrayList<Achado>()
        for (pkg in Debloat.PACOTES) {
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                val sistema = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                lista.add(Achado(pkg, pm.getApplicationLabel(ai).toString(), sistema))
            } catch (e: Exception) {
                // nao existe nesta TV box
            }
        }
        achados = lista
        sel = min(sel, max(0, achados.size - 1))
    }

    override fun desenhar(cv: Canvas, w: Float, h: Float, t: Tema) {
        val cc = Desenho.escala(w)
        val lc = max(2, (cc * 0.7f).toInt())
        Desenho.titulo(cv, w, h, t, "DEBLOAT", "${achados.size} ENCONTRADOS")

        val sx = w * 0.06f
        val sw = w * 0.88f
        val topoLista = h * 0.17f
        val linha = h * 0.072f
        val altCaixa = linha * 0.86f
        val vis = max(1, ((h * 0.60f) / linha).toInt())

        if (achados.isEmpty()) {
            Desenho.centro(cv, "NENHUM APP INUTIL ENCONTRADO", w / 2f, h * 0.40f, lc, t.texto, t.contorno)
        } else {
            if (sel < topo) topo = sel
            if (sel >= topo + vis) topo = sel - vis + 1
            for (r in 0 until vis) {
                val i = topo + r
                if (i >= achados.size) break
                val a = achados[i]
                val foco = i == sel
                val y = topoLista + linha * r
                Desenho.caixa(
                    cv, RectF(sx, y, sx + sw, y + altCaixa), altCaixa * 0.18f,
                    if (foco) t.destaque else t.borda, if (foco) t.painelFoco else t.painel, if (foco) 4f else 2f
                )
                val ty = y + (altCaixa - 7 * lc) / 2f
                PixelFont.desenhar(cv, Desenho.limpar(a.nome, 34), sx + lc * 4f, ty, lc, if (foco) t.destaque else t.texto, t.contorno)
                val tag = if (a.sistema) "SISTEMA" else "INSTALADO"
                PixelFont.desenhar(
                    cv, tag, sx + sw - lc * 4f - PixelFont.largura(tag, lc), ty, lc,
                    if (a.sistema) t.apagado else t.destaque
                )
            }
        }

        val c2 = max(1, lc / 2)
        Desenho.centro(cv, "INSTALADO: OK DESINSTALA (O ANDROID PEDE CONFIRMACAO)", w / 2f, h * 0.805f, c2, t.texto)
        Desenho.centro(cv, "SISTEMA: SO SAI PELO ADB - USE FERRAMENTAS/DEBLOAT.BAT", w / 2f, h * 0.845f, c2, t.texto)
        Desenho.rodape(cv, w, h, t, "↑↓ ESCOLHER | OK DESINSTALAR | VOLTAR SAIR")
    }

    private fun ehOk(k: Int) =
        k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER

    override fun tecla(k: Int): Boolean {
        if (achados.isEmpty()) return false
        when {
            k == KeyEvent.KEYCODE_DPAD_UP -> sel = (sel + achados.size - 1) % achados.size
            k == KeyEvent.KEYCODE_DPAD_DOWN -> sel = (sel + 1) % achados.size
            ehOk(k) -> desinstalar(achados[sel])
            else -> return false
        }
        v.invalidate()
        return true
    }

    private fun desinstalar(a: Achado) {
        if (a.sistema) {
            Toast.makeText(ctx, "APP DE SISTEMA: USE O DEBLOAT.BAT", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pacote))
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "NAO FOI POSSIVEL DESINSTALAR", Toast.LENGTH_SHORT).show()
        }
    }

    override fun aoRetomar() {
        carregar()
        v.invalidate()
    }
}
