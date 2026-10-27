package br.tvbox.launcher

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View

class MainActivity : Activity() {

    private lateinit var tela: LauncherView
    private val handler = Handler(Looper.getMainLooper())
    private var longoDisparado = false

    private val relogio = object : Runnable {
        override fun run() {
            tela.atualizarHora()
            handler.postDelayed(this, 15_000)
        }
    }

    // Descanso de tela: comeca depois de [Config.ESPERAS] minutos sem apertar nada.
    private val ocioso = Runnable {
        val atual = tela.painel
        if (atual !is Descanso && !tela.egg) {
            val modo = Config.descanso(this)
            if (modo > 0) tela.abrirPainel(Descanso(tela, modo))
        }
    }

    private fun reagendarOcioso() {
        handler.removeCallbacks(ocioso)
        if (Config.descanso(this) > 0) {
            handler.postDelayed(ocioso, Config.ESPERAS[Config.espera(this)] * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tela = LauncherView(this)
        val salvo = Config.tema(this)
        tela.tema = TEMAS.firstOrNull { it.id == salvo } ?: TEMAS[0]
        tela.corIcone = Config.corIcone(this)
        tela.corFoco = Config.corFoco(this)
        tela.aoMudarConfig = { reagendarOcioso() }
        setContentView(tela)
        esconderBarras()
    }

    override fun onResume() {
        super.onResume()
        esconderBarras()
        tela.atualizarInstalados()
        tela.painel?.aoRetomar()
        relogio.run()
        reagendarOcioso()
    }

    override fun onPause() {
        handler.removeCallbacks(relogio)
        handler.removeCallbacks(ocioso)
        super.onPause()
    }

    @Suppress("DEPRECATION")
    private fun esconderBarras() {
        tela.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    private fun proximoTema() {
        val i = TEMAS.indexOf(tela.tema)
        val novo = TEMAS[(i + 1) % TEMAS.size]
        tela.tema = novo
        Config.setTema(this, novo.id)
    }

    private fun ehOk(k: Int) =
        k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_NUMPAD_ENTER

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        reagendarOcioso()
        if (ehOk(keyCode) && event.repeatCount == 0) longoDisparado = false

        // Descanso de tela aberto: qualquer tecla so fecha o descanso.
        val painel = tela.painel
        if (painel is Descanso) {
            tela.fecharPainel()
            longoDisparado = true // evita que o soltar do OK abra algo logo em seguida
            return true
        }

        // Alguma tela interna aberta (apps, ajustes, debloat).
        if (painel != null) {
            if (ehOk(keyCode)) {
                event.startTracking()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) return super.onKeyDown(keyCode, event)
            return painel.tecla(keyCode) || super.onKeyDown(keyCode, event)
        }

        // Easter egg aberto: OK ou VOLTAR fecham; o resto e ignorado.
        if (tela.egg) {
            if (ehOk(keyCode) || keyCode == KeyEvent.KEYCODE_BACK) tela.fecharEgg()
            return true
        }
        val primeira = event.repeatCount == 0
        return when {
            keyCode == KeyEvent.KEYCODE_DPAD_UP -> { if (primeira) tela.sequencia(0); true }
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> { if (primeira) tela.sequencia(1); true }
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> { if (primeira) tela.sequencia(2); tela.mover(-1); true }
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> { if (primeira) tela.sequencia(3); tela.mover(1); true }
            ehOk(keyCode) -> { event.startTracking(); true }
            keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS -> {
                if (primeira) proximoTema()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        val painel = tela.painel
        if (ehOk(keyCode) && painel != null && painel !is Descanso) {
            longoDisparado = true
            painel.teclaLonga()
            return true
        }
        if (ehOk(keyCode) && painel == null && !tela.egg) {
            longoDisparado = true
            proximoTema()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (ehOk(keyCode) && event.isTracking && !event.isCanceled && !longoDisparado) {
            val painel = tela.painel
            if (painel != null) {
                if (painel !is Descanso) painel.tecla(keyCode)
                return true
            }
            if (!tela.egg) {
                tela.abrir()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    // Launcher nao deve fechar com o botao voltar: so volta uma tela, ou fecha o easter egg.
    @Deprecated("Launcher fica sempre aberto")
    override fun onBackPressed() {
        val painel = tela.painel
        if (painel != null) {
            if (painel is Descanso || painel.voltar()) tela.fecharPainel()
            reagendarOcioso()
            return
        }
        if (tela.egg) tela.fecharEgg()
    }
}
