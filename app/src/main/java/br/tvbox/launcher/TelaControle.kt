package br.tvbox.launcher

import android.accessibilityservice.AccessibilityService
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.graphics.Canvas
import android.graphics.RectF
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File
import kotlin.math.max

/**
 * Cartao CONTROLE: mostra o endereco e o PIN do controle pelo celular e os arquivos recebidos.
 * OK abre o arquivo (APK instala) | segure OK apaga | MENU liga/desliga o servidor.
 */
class TelaControle(private val v: LauncherView) : Painel {

    private val ctx = v.context
    private var sel = 0
    private var arquivos: List<File> = emptyList()

    init {
        listar()
    }

    override fun aoRetomar() = listar()

    fun listar() {
        arquivos = Servidor.pastaRecebidos(ctx).listFiles()?.filter { !it.name.startsWith(".") }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
        sel = sel.coerceIn(0, max(0, arquivos.size - 1))
        v.invalidate()
    }

    private fun tamanho(b: Long): String = when {
        b >= 1L shl 30 -> "%.1f GB".format(b / (1024.0 * 1024 * 1024))
        b >= 1L shl 20 -> "%.1f MB".format(b / (1024.0 * 1024))
        else -> "${max(1, b / 1024)} KB"
    }.replace('.', ',')

    override fun desenhar(cv: Canvas, w: Float, h: Float, t: Tema) {
        val cc = Desenho.escala(w)
        val lc = max(2, (cc * 0.7f).toInt())
        Desenho.titulo(cv, w, h, t, "CONTROLE PELO CELULAR", if (Servidor.rodando) "LIGADO" else "DESLIGADO")
        val caixa = RectF(w * 0.06f, h * 0.15f, w * 0.94f, h * 0.44f)
        Desenho.caixa(cv, caixa, lc * 2f, t.destaque, t.painel, 3f)
        val ends = Servidor.enderecos()
        var y = caixa.top + lc * 4f
        if (!Servidor.rodando) {
            PixelFont.desenhar(cv, "SERVIDOR DESLIGADO. APERTE MENU PARA LIGAR", caixa.left + lc * 4f, y, lc, t.texto)
        } else if (ends.isEmpty()) {
            PixelFont.desenhar(cv, "SEM REDE: CONECTE A CAIXA AO WI-FI", caixa.left + lc * 4f, y, lc, t.texto)
        } else {
            PixelFont.desenhar(cv, "1. CELULAR NO MESMO WI-FI DA TV", caixa.left + lc * 4f, y, lc, t.texto)
            y += lc * 11f
            PixelFont.desenhar(cv, "2. ABRA NO NAVEGADOR:", caixa.left + lc * 4f, y, lc, t.texto)
            y += lc * 10f
            val ec = max(3, (cc * 1.1f).toInt())
            PixelFont.desenhar(cv, Desenho.limpar(ends[0], 40), caixa.left + lc * 8f, y, ec, t.destaque, t.contorno)
            y += 7 * ec + lc * 5f
            PixelFont.desenhar(cv, "3. DIGITE O PIN:", caixa.left + lc * 4f, y, lc, t.texto)
            PixelFont.desenhar(cv, Servidor.pin, caixa.left + lc * 4f + PixelFont.largura("3. DIGITE O PIN: ", lc), y - ec + lc, ec, t.destaque, t.contorno)
        }
        // Progresso de um envio em andamento.
        val total = Servidor.envioTotal
        if (Servidor.envioNome.isNotEmpty() && total > 0 && Servidor.envioRecebido < total) {
            val f = Servidor.envioRecebido.toFloat() / total
            val yb = caixa.bottom - lc * 9f
            PixelFont.desenhar(cv, "RECEBENDO " + Desenho.limpar(Servidor.envioNome, 30) + " ${(f * 100).toInt()}%", caixa.left + lc * 4f, yb - lc * 9f, lc, t.texto)
            Desenho.retangulo(cv, caixa.left + lc * 4f, yb, caixa.right - lc * 4f, yb + lc * 3f, t.borda)
            Desenho.retangulo(cv, caixa.left + lc * 4f, yb, caixa.left + lc * 4f + (caixa.width() - lc * 8f) * f, yb + lc * 3f, t.destaque)
            v.postInvalidateDelayed(300)
        }

        PixelFont.desenhar(cv, "ARQUIVOS RECEBIDOS (${arquivos.size})", w * 0.06f, h * 0.47f, lc, t.destaque, t.contorno)
        if (arquivos.isEmpty()) {
            PixelFont.desenhar(cv, "NENHUM AINDA. ENVIE PELO CELULAR (ENVIAR ARQUIVO)", w * 0.06f, h * 0.55f, max(1, lc - 1), t.apagado)
        } else {
            Lista.desenhar(cv, w, t, arquivos.map { it.name }, arquivos.map { tamanho(it.length()) }, sel, h * 0.52f, h * 0.88f)
        }
        Desenho.rodape(cv, w, h, t, "OK ABRIR/INSTALAR | SEGURE OK APAGAR | MENU LIGA/DESLIGA | VOLTAR SAIR")
        if (Servidor.rodando) v.postInvalidateDelayed(1000)
    }

    private fun abrirArquivo(f: File) {
        val uri = Uri.parse("content://${ctx.packageName}.arquivos/" + Uri.encode(f.name))
        val ext = f.extension.lowercase()
        val tipo = if (ext == "apk") "application/vnd.android.package-archive"
            else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val i = Intent(if (ext == "apk") Intent.ACTION_INSTALL_PACKAGE else Intent.ACTION_VIEW)
            .setDataAndType(uri, tipo)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "NENHUM APP ABRE ESSE ARQUIVO", Toast.LENGTH_SHORT).show()
        }
    }

    override fun tecla(k: Int): Boolean {
        when (k) {
            KeyEvent.KEYCODE_DPAD_UP -> if (sel > 0) sel--
            KeyEvent.KEYCODE_DPAD_DOWN -> if (sel < arquivos.size - 1) sel++
            KeyEvent.KEYCODE_MENU -> {
                val ligar = !Servidor.rodando
                Config.setControle(ctx, ligar)
                if (ligar) Servidor.iniciar(ctx) else Servidor.parar()
                v.postInvalidateDelayed(400)
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> arquivos.getOrNull(sel)?.let { abrirArquivo(it) }
            else -> return false
        }
        v.invalidate()
        return true
    }

    override fun teclaLonga(): Boolean {
        arquivos.getOrNull(sel)?.let {
            it.delete()
            Toast.makeText(ctx, "APAGADO: " + it.name, Toast.LENGTH_SHORT).show()
            listar()
        }
        return true
    }
}

/** Entrega os arquivos recebidos para outros apps (instalador de APK, galeria, player) sem expor o caminho. */
class Arquivos : ContentProvider() {
    override fun onCreate() = true

    private fun arquivo(uri: Uri): File? {
        val c = context ?: return null
        val nome = uri.lastPathSegment ?: return null
        val f = File(Servidor.pastaRecebidos(c), nome)
        // So arquivos de dentro da pasta (nada de ../).
        return if (f.parentFile?.canonicalPath == Servidor.pastaRecebidos(c).canonicalPath && f.exists()) f else null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = arquivo(uri) ?: return null
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? {
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: return null
        return if (ext == "apk") "application/vnd.android.package-archive" else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? {
        val f = arquivo(uri) ?: return null
        val c = android.database.MatrixCursor(arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE))
        c.addRow(arrayOf<Any>(f.name, f.length()))
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?) = 0
}

/**
 * Servico de acessibilidade OPCIONAL: so serve para o VOLTAR e o INICIO do celular funcionarem
 * quando outro app (Kodi, YouTube...) esta na frente. Ativar em Configuracoes > Acessibilidade > TV Launcher.
 */
class Acessibilidade : AccessibilityService() {
    companion object {
        var ativo: Acessibilidade? = null

        fun abrirConfiguracao(ctx: android.content.Context) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
            }
        }
    }

    override fun onServiceConnected() {
        ativo = this
    }

    override fun onDestroy() {
        if (ativo === this) ativo = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun voltar() = performGlobalAction(GLOBAL_ACTION_BACK)
}
