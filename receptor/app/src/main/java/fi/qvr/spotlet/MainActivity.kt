package fi.qvr.spotlet

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View

/** Tela unica: o painel pixelado do TV Spotify (SpotView). O receptor roda no servico, independente desta tela. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var tela: SpotView

    private val statusListener: () -> Unit = { tela.invalidate() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        tela = SpotView(this, prefs)
        tela.aoSair = { finish() }
        setContentView(tela)
        esconderBarras()

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        if (prefs.enabled) ReceiverService.start(this)
    }

    override fun onResume() {
        super.onResume()
        esconderBarras()
        ReceiverService.statusListeners += statusListener
        tela.invalidate()
    }

    override fun onPause() {
        ReceiverService.statusListeners -= statusListener
        super.onPause()
    }

    @Suppress("DEPRECATION")
    private fun esconderBarras() {
        tela.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (tela.tecla(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }
}