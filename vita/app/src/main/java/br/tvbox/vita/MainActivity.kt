package br.tvbox.vita

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.herohan.uvcapp.CameraException
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.Size

/**
 * Vita Link: mostra a tela do PS Vita (plugin UDCD UVC, que faz o console aparecer como webcam USB).
 * O video vai para um SurfaceView; toda a interface pixelada fica na VitaView por cima.
 */
class MainActivity : Activity() {

    private lateinit var raiz: FrameLayout
    private lateinit var superficie: SurfaceView
    private lateinit var tela: VitaView
    private var helper: ICameraHelper? = null
    private val handler = Handler(Looper.getMainLooper())

    private var videoLarg = 960
    private var videoAlt = 544
    private var tamanhoPendente: Size? = null
    private var indiceTamanho = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        raiz = FrameLayout(this)
        raiz.setBackgroundColor(0xFF000000.toInt())

        superficie = SurfaceView(this)
        raiz.addView(superficie, FrameLayout.LayoutParams(1, 1, Gravity.CENTER))
        superficie.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                helper?.addSurface(holder.surface, false)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                helper?.removeSurface(holder.surface)
            }
        })

        tela = VitaView(this)
        raiz.addView(
            tela,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        tela.aoAcao = { acao -> tratarAcao(acao) }
        setContentView(raiz)
        esconderBarras()
        pedirPermissaoCamera()
    }

    override fun onStart() {
        super.onStart()
        iniciarCamera()
    }

    override fun onStop() {
        pararCamera()
        super.onStop()
    }

    @Suppress("DEPRECATION")
    private fun esconderBarras() {
        raiz.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onResume() {
        super.onResume()
        esconderBarras()
    }

    // ------------------------------------------------------------------ permissao

    private fun pedirPermissaoCamera() {
        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (!ok) aviso("SEM PERMISSÃO DE CÂMERA O ANDROID PODE BLOQUEAR O VITA")
            pararCamera()
            iniciarCamera()
        }
    }

    // ------------------------------------------------------------------ camera

    private fun iniciarCamera() {
        if (helper != null) return
        val h = CameraHelper()
        h.setStateCallback(callback)
        helper = h
        // Se o Vita ja estava ligado ao abrir o app, nao chega aviso de "conectado": procura na lista.
        handler.postDelayed({ procurarDispositivo(false) }, 600L)
    }

    private fun pararCamera() {
        handler.removeCallbacksAndMessages(null)
        tamanhoPendente = null
        helper?.release()
        helper = null
        tela.estado = Estado.AGUARDANDO
    }

    private fun procurarDispositivo(avisar: Boolean) {
        val h = helper ?: return
        val lista = h.deviceList
        if (lista != null && lista.isNotEmpty()) {
            tela.estado = Estado.CONECTANDO
            h.selectDevice(lista[0])
        } else if (avisar) {
            aviso("NENHUM VITA ENCONTRADO NO USB")
        }
    }

    private val callback = object : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) {
            runOnUiThread { tela.estado = Estado.CONECTANDO }
            helper?.selectDevice(device)
        }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            val s = tamanhoPendente
            tamanhoPendente = null
            if (s != null) helper?.openCamera(s) else helper?.openCamera()
        }

        override fun onCameraOpen(device: UsbDevice) {
            val h = helper ?: return
            h.startPreview()
            val tamanho = h.previewSize
            if (tamanho != null) {
                videoLarg = tamanho.width
                videoAlt = tamanho.height
            }
            h.addSurface(superficie.holder.surface, false)
            runOnUiThread {
                tela.resolucao = "${videoLarg}X$videoAlt"
                ajustarSuperficie()
                tela.estado = Estado.TRANSMITINDO
            }
        }

        override fun onCameraClose(device: UsbDevice) {
            helper?.removeSurface(superficie.holder.surface)
            if (tamanhoPendente != null) {
                // Troca de resolucao: reabre o dispositivo e abre a camera ja no tamanho novo.
                helper?.selectDevice(device)
            } else {
                runOnUiThread { tela.estado = Estado.CONECTANDO }
            }
        }

        override fun onDeviceClose(device: UsbDevice) {}

        override fun onDetach(device: UsbDevice) {
            runOnUiThread {
                tela.estado = Estado.AGUARDANDO
                ajustarSuperficie()
            }
        }

        override fun onCancel(device: UsbDevice) {
            runOnUiThread {
                tela.erro = "PERMISSÃO USB NEGADA. DESCONECTE E CONECTE O CABO"
                tela.estado = Estado.ERRO
            }
        }

        override fun onError(device: UsbDevice, e: CameraException) {
            runOnUiThread {
                tela.erro = "FALHA NA CÂMERA (CÓDIGO ${e.code}). DESCONECTE E CONECTE O CABO"
                tela.estado = Estado.ERRO
            }
        }
    }

    // ------------------------------------------------------------------ acoes do menu

    private fun tratarAcao(acao: String) {
        when (acao) {
            "retry" -> {
                tela.estado = Estado.AGUARDANDO
                pararCamera()
                iniciarCamera()
                handler.postDelayed({ procurarDispositivo(true) }, 900L)
            }
            "sair" -> finish()
            "res+" -> trocarResolucao(1)
            "res-" -> trocarResolucao(-1)
            "proporcao" -> {
                tela.esticar = !tela.esticar
                ajustarSuperficie()
                tela.invalidate()
            }
            "desconectar" -> {
                helper?.closeCamera()
                tela.estado = Estado.AGUARDANDO
            }
        }
    }

    private fun trocarResolucao(direcao: Int) {
        val h = helper ?: return
        val lista = h.supportedSizeList
        if (lista == null || lista.isEmpty()) {
            aviso("RESOLUÇÕES INDISPONÍVEIS")
            return
        }
        var atual = lista.indexOfFirst { it.width == videoLarg && it.height == videoAlt }
        if (atual < 0) atual = indiceTamanho
        indiceTamanho = (atual + direcao + lista.size) % lista.size
        tamanhoPendente = lista[indiceTamanho]
        h.closeCamera()
    }

    /** Encaixa o video na tela (AJUSTAR mantem a proporcao; ESTICAR preenche tudo). */
    private fun ajustarSuperficie() {
        val larguraTela = raiz.width
        val alturaTela = raiz.height
        val lp = superficie.layoutParams as FrameLayout.LayoutParams
        if (tela.estado != Estado.TRANSMITINDO || larguraTela == 0 || alturaTela == 0) {
            lp.width = 1
            lp.height = 1
        } else if (tela.esticar) {
            lp.width = larguraTela
            lp.height = alturaTela
        } else {
            val proporcaoVideo = videoLarg.toFloat() / videoAlt.toFloat()
            val proporcaoTela = larguraTela.toFloat() / alturaTela.toFloat()
            if (proporcaoVideo > proporcaoTela) {
                lp.width = larguraTela
                lp.height = (larguraTela / proporcaoVideo).toInt()
            } else {
                lp.height = alturaTela
                lp.width = (alturaTela * proporcaoVideo).toInt()
            }
        }
        lp.gravity = Gravity.CENTER
        superficie.layoutParams = lp
    }

    private fun aviso(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    // ------------------------------------------------------------------ teclas

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (tela.tecla(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }
}
