package br.tvbox.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import android.view.KeyEvent
import android.widget.Toast
import kotlin.math.max
import kotlin.math.min

/**
 * Todos os aplicativos instalados numa grade simples.
 * OK abre o app; OK segurado abre o menu (abrir / informacoes e parar / desinstalar).
 */
class TelaTodos(private val v: LauncherView) : Painel {

    private class App(val pacote: String, val nome: String, val info: ResolveInfo)

    private val ctx = v.context
    private val pm: PackageManager = ctx.packageManager
    private var apps: List<App> = emptyList()
    private var sel = 0
    private var topo = 0
    private var menu = false
    private var menuSel = 0
    private val cache = LruCache<String, Bitmap>(60)
    private val tinta = Paint().apply { isAntiAlias = false; isFilterBitmap = false; style = Paint.Style.FILL }
    private val colunas = 6
    private val opcoesMenu = listOf("ABRIR", "INFORMAÇÕES E PARAR", "DESINSTALAR", "VOLTAR")

    init {
        carregar()
    }

    private fun carregar() {
        val vistos = HashSet<String>()
        val lista = ArrayList<App>()
        val categorias = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (cat in categorias) {
            val intencao = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (ri in pm.queryIntentActivities(intencao, 0)) {
                val pkg = ri.activityInfo.packageName
                if (pkg == ctx.packageName || !vistos.add(pkg)) continue
                lista.add(App(pkg, ri.loadLabel(pm).toString(), ri))
            }
        }
        lista.sortBy { it.nome.lowercase() }
        apps = lista
        sel = min(sel, max(0, apps.size - 1))
        cache.evictAll()
    }

    private fun icone(a: App): Bitmap? {
        val guardado = cache.get(a.pacote)
        if (guardado != null) return guardado
        return try {
            val d = a.info.loadIcon(pm)
            val bmp = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            d.setBounds(0, 0, 72, 72)
            d.draw(c)
            cache.put(a.pacote, bmp)
            bmp
        } catch (e: Exception) {
            null
        }
    }

    override fun desenhar(cv: Canvas, w: Float, h: Float, t: Tema) {
        val cc = Desenho.escala(w)
        val tc = max(2, (cc * 0.75f).toInt())
        Desenho.titulo(cv, w, h, t, "TODOS OS APPS", "${apps.size} APPS")

        val sx = w * 0.06f
        val sw = w * 0.88f
        val topoGrade = h * 0.17f
        val altGrade = h * 0.66f
        val cw = sw / colunas
        val ch = cw * 1.12f
        val linhasVis = max(1, (altGrade / ch).toInt())
        val total = (apps.size + colunas - 1) / colunas

        // Mantem o selecionado visivel.
        val linhaSel = sel / colunas
        if (linhaSel < topo) topo = linhaSel
        if (linhaSel >= topo + linhasVis) topo = linhaSel - linhasVis + 1
        topo = max(0, min(topo, max(0, total - linhasVis)))

        val lc = max(1, (cw * 0.86f / PixelFont.largura("XXXXXXXXXX", 1)).toInt())
        for (r in 0 until linhasVis) {
            for (col in 0 until colunas) {
                val i = (topo + r) * colunas + col
                if (i >= apps.size) break
                val cx = sx + cw * (col + 0.5f)
                val cy = topoGrade + ch * r + cw * 0.40f
                desenharApp(cv, apps[i], i == sel, cx, cy, cw, lc, t)
            }
        }

        if (apps.isEmpty()) {
            Desenho.centro(cv, "NENHUM APP ENCONTRADO", w / 2f, h * 0.45f, tc, t.texto, t.contorno)
        } else {
            Desenho.centro(cv, Desenho.limpar(apps[sel].nome, 40), w / 2f, h * 0.855f, tc, t.texto, t.contorno)
        }
        Desenho.rodape(cv, w, h, t, "OK ABRIR | OK SEGURADO MENU | VOLTAR SAIR")
        if (menu) desenharMenu(cv, w, h, t, tc)
    }

    private fun desenharApp(cv: Canvas, a: App, foco: Boolean, cx: Float, cy: Float, cw: Float, lc: Int, t: Tema) {
        val meio = cw * 0.37f
        val r = RectF(cx - meio, cy - meio, cx + meio, cy + meio)
        val raio = cw * 0.07f
        val b = if (foco) max(3f, cw * 0.04f) else max(2f, cw * 0.025f)
        if (foco) {
            val anel = max(3f, cw * 0.04f)
            tinta.color = t.anel
            cv.drawRoundRect(RectF(r.left - anel, r.top - anel, r.right + anel, r.bottom + anel), raio + anel, raio + anel, tinta)
        }
        Desenho.caixa(cv, r, raio, if (foco) t.destaque else t.borda, if (foco) t.painelFoco else t.painel, b)

        val bmp = icone(a)
        if (bmp != null) {
            val lado = meio * 2f * 0.78f
            cv.drawBitmap(bmp, null, RectF(cx - lado / 2f, cy - lado / 2f, cx + lado / 2f, cy + lado / 2f), tinta)
        }

        val nome = Desenho.limpar(a.nome, 10)
        val lw = PixelFont.largura(nome, lc)
        val ly = cy + meio + lc * 3f
        if (foco) {
            tinta.color = t.destaque
            cv.drawRect(cx - lw / 2f - lc * 2, ly - lc * 1.5f, cx + lw / 2f + lc * 2, ly + 7 * lc + lc * 1.5f, tinta)
            PixelFont.desenhar(cv, nome, cx - lw / 2f, ly, lc, t.contorno)
        } else {
            PixelFont.desenhar(cv, nome, cx - lw / 2f, ly, lc, t.texto, t.contorno)
        }
    }

    private fun desenharMenu(cv: Canvas, w: Float, h: Float, t: Tema, tc: Int) {
        // Escurece o fundo e abre uma caixa no meio.
        tinta.color = 0xAA000000.toInt()
        cv.drawRect(0f, 0f, w, h, tinta)
        val larg = w * 0.46f
        val linha = h * 0.085f
        val alt = linha * opcoesMenu.size + linha * 1.6f
        val l = (w - larg) / 2f
        val tp = (h - alt) / 2f
        Desenho.caixa(cv, RectF(l, tp, l + larg, tp + alt), 12f, t.destaque, t.painel, 4f)
        val nome = Desenho.limpar(apps[sel].nome, 22)
        Desenho.centro(cv, nome, w / 2f, tp + linha * 0.35f, tc, t.destaque, t.contorno)
        for (i in opcoesMenu.indices) {
            val y = tp + linha * (i + 1.3f)
            val foco = i == menuSel
            if (foco) Desenho.retangulo(cv, l + 10f, y - linha * 0.12f, l + larg - 10f, y + 7 * tc + linha * 0.12f, t.destaque)
            Desenho.centro(cv, opcoesMenu[i], w / 2f, y, tc, if (foco) t.contorno else t.texto)
        }
    }

    private fun ehOk(k: Int) =
        k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER

    override fun tecla(k: Int): Boolean {
        if (menu) return teclaMenu(k)
        if (apps.isEmpty()) return false
        when {
            k == KeyEvent.KEYCODE_DPAD_LEFT -> { if (sel > 0) sel-- }
            k == KeyEvent.KEYCODE_DPAD_RIGHT -> { if (sel < apps.size - 1) sel++ }
            k == KeyEvent.KEYCODE_DPAD_UP -> { if (sel - colunas >= 0) sel -= colunas }
            k == KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (sel + colunas < apps.size) sel += colunas else sel = apps.size - 1
            }
            ehOk(k) -> abrirApp()
            else -> return false
        }
        v.invalidate()
        return true
    }

    override fun teclaLonga(): Boolean {
        if (apps.isEmpty() || menu) return false
        menu = true
        menuSel = 0
        v.invalidate()
        return true
    }

    private fun teclaMenu(k: Int): Boolean {
        when {
            k == KeyEvent.KEYCODE_DPAD_UP -> menuSel = (menuSel + opcoesMenu.size - 1) % opcoesMenu.size
            k == KeyEvent.KEYCODE_DPAD_DOWN -> menuSel = (menuSel + 1) % opcoesMenu.size
            ehOk(k) -> {
                val escolhido = menuSel
                menu = false
                when (escolhido) {
                    0 -> abrirApp()
                    1 -> abrirInformacoes()
                    2 -> desinstalar()
                }
            }
            else -> return false
        }
        v.invalidate()
        return true
    }

    override fun voltar(): Boolean {
        if (menu) {
            menu = false
            v.invalidate()
            return false
        }
        return true
    }

    override fun aoRetomar() {
        carregar()
        v.invalidate()
    }

    private fun aviso(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    private fun lancar(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    private fun abrirApp() {
        val a = apps[sel]
        val i = pm.getLeanbackLaunchIntentForPackage(a.pacote) ?: pm.getLaunchIntentForPackage(a.pacote)
        if (i == null || !lancar(i)) aviso("NAO FOI POSSIVEL ABRIR")
    }

    /** Tela do sistema com Forcar parada, Limpar dados e Permissoes (um app comum nao pode parar outro sozinho). */
    private fun abrirInformacoes() {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + apps[sel].pacote))
        if (!lancar(i)) aviso("SEM TELA DE INFORMACOES NESTA TV BOX")
    }

    private fun desinstalar() {
        val a = apps[sel]
        val sistema = (a.info.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        if (sistema) {
            aviso("APP DE SISTEMA: USE O DEBLOAT")
            return
        }
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pacote))
        if (!lancar(i)) aviso("NAO FOI POSSIVEL DESINSTALAR")
    }
}
