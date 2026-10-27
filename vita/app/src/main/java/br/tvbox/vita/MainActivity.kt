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
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import java.util.concurrent.atomic.AtomicLong

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
    private var tamanhoAtual: Size? = null
    private var dispositivo: UsbDevice? = null
    private var cameraAberta = false

    // Resolucao e FPS escolhidos (lembrados entre aberturas). O Vita manda NV12 sem compressao por USB bulk:
    // 960x544 a 30 FPS sao ~23 MB/s, o que uma TV box aguenta; 60 FPS dobra isso.
    private var resPreferida = "960x544"
    private var fps = 30

    // Superficie do video: so e entregue a biblioteca depois de ter o tamanho final (senao ela desenha em 1x1 e fica preto).
    private var superficieAnexada = false
    private var superficieLarg = 0
    private var superficieAlt = 0

    // Diagnostico.
    private val frames = AtomicLong(0)
    private var framesAntes = 0L
    private var fpsDiag = 0L
    private var callbackFrames: IFrameCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val cfg = getSharedPreferences("cfg", MODE_PRIVATE)
        resPreferida = cfg.getString("res", "960x544") ?: "960x544"
        fps = cfg.getInt("fps", 30)

        raiz = FrameLayout(this)
        raiz.setBackgroundColor(0xFF000000.toInt())

        // A superficie nasce em tela cheia (a VitaView cobre tudo enquanto nao ha video).
        superficie = SurfaceView(this)
        raiz.addView(
            superficie,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER)
        )
        superficie.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {}

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                superficieLarg = width
                superficieAlt = height
                // O tamanho mudou (ajuste de proporcao ou troca de resolucao): entrega de novo com o tamanho certo.
                if (cameraAberta) reanexar()
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                if (superficieAnexada) {
                    helper?.removeSurface(holder.surface)
                    superficieAnexada = false
                }
            }
        })

        tela = VitaView(this)
        raiz.addView(
            tela,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        tela.fps = fps
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
        cameraAberta = false
        superficieAnexada = false
        callbackFrames = null
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

    /**
     * Escolhe a resolucao (a preferida, senao a tela nativa do Vita, senao a primeira) com o FPS pedido.
     * So funciona com a camera ja aberta: antes disso a biblioteca devolve a lista vazia
     * (era por isso que o Vita abria sempre em 1280x720).
     */
    private fun escolherTamanho(lista: List<Size>?): Size? {
        if (lista == null || lista.isEmpty()) return null
        val ordem = listOf(resPreferida, "960x544", "864x488", "896x504", "480x272", "1280x720")
        var achado: Size? = null
        for (r in ordem) {
            achado = lista.firstOrNull { "${it.width}x${it.height}" == r }
            if (achado != null) break
        }
        val s = (achado ?: lista[0]).clone()
        val opcoes = s.fpsList ?: emptyList<Int>()
        if (opcoes.contains(fps)) s.fps = fps else if (opcoes.isNotEmpty()) s.fps = opcoes.minOrNull() ?: s.fps
        return s
    }

    private fun mesmoTamanho(a: Size?, b: Size?) =
        a != null && b != null && a.width == b.width && a.height == b.height && a.fps == b.fps && a.type == b.type

    private val callback = object : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) {
            dispositivo = device
            runOnUiThread { tela.estado = Estado.CONECTANDO }
            helper?.selectDevice(device)
        }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            dispositivo = device
            // A lista de resolucoes so existe depois de abrir: abre no padrao e troca em onCameraOpen.
            helper?.openCamera()
        }

        override fun onCameraOpen(device: UsbDevice) {
            val h = helper ?: return
            var tamanho = h.previewSize
            val desejado = escolherTamanho(h.supportedSizeList)
            if (desejado != null && !mesmoTamanho(desejado, tamanho)) {
                // Fila do CameraHelper: troca o tamanho e so depois comeca o video.
                h.setPreviewSize(desejado)
                tamanho = desejado
            }
            h.startPreview()
            if (tamanho != null) {
                tamanhoAtual = tamanho
                videoLarg = tamanho.width
                videoAlt = tamanho.height
            }
            cameraAberta = true
            runOnUiThread {
                tela.resolucao = "${videoLarg}X$videoAlt"
                tela.estado = Estado.TRANSMITINDO
                ajustarSuperficie()
                // Se o tamanho da superficie nao mudou, nao chega surfaceChanged: garante a entrega.
                handler.postDelayed({ if (cameraAberta && !superficieAnexada) reanexar() }, 400L)
            }
            if (callbackFrames != null) registrarContadorDeFrames()
        }

        override fun onCameraClose(device: UsbDevice) {
            cameraAberta = false
            if (superficieAnexada) {
                helper?.removeSurface(superficie.holder.surface)
                superficieAnexada = false
            }
            runOnUiThread { tela.estado = Estado.CONECTANDO }
        }

        override fun onDeviceClose(device: UsbDevice) {}

        override fun onDetach(device: UsbDevice) {
            cameraAberta = false
            superficieAnexada = false
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

    /** Entrega (ou entrega de novo) a superficie do video a biblioteca, ja com o tamanho final. */
    private fun reanexar() {
        val h = helper ?: return
        val s = superficie.holder.surface
        if (superficieAnexada) {
            h.removeSurface(s)
            superficieAnexada = false
        }
        if (cameraAberta && s != null && s.isValid && superficieLarg > 1 && superficieAlt > 1) {
            h.addSurface(s, false)
            superficieAnexada = true
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
            "fps" -> alternarFps()
            "diag" -> abrirDiagnostico()
            "diag-fechar" -> handler.removeCallbacks(atualizarDiag)
        }
    }

    /** Alterna 30/60 FPS e aplica na hora (para o video, troca o tamanho e recomeca). */
    private fun alternarFps() {
        fps = if (fps == 30) 60 else 30
        tela.fps = fps
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putInt("fps", fps).apply()
        tela.invalidate()
        aplicarTamanho()
    }

    private fun trocarResolucao(direcao: Int) {
        val h = helper ?: return
        val lista = h.supportedSizeList
        if (lista == null || lista.isEmpty()) {
            aviso("RESOLUÇÕES INDISPONÍVEIS")
            return
        }
        var atual = lista.indexOfFirst { it.width == videoLarg && it.height == videoAlt }
        if (atual < 0) atual = 0
        val novo = lista[(atual + direcao + lista.size) % lista.size]
        resPreferida = "${novo.width}x${novo.height}"
        getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("res", resPreferida).apply()
        aplicarTamanho()
    }

    /** Aplica [resPreferida] e [fps] com o video rodando. */
    private fun aplicarTamanho() {
        val h = helper ?: return
        if (!cameraAberta) return
        val desejado = escolherTamanho(h.supportedSizeList) ?: return
        h.stopPreview()
        h.setPreviewSize(desejado)
        h.startPreview()
        tamanhoAtual = desejado
        videoLarg = desejado.width
        videoAlt = desejado.height
        tela.resolucao = "${videoLarg}X$videoAlt"
        tela.estado = Estado.TRANSMITINDO
        ajustarSuperficie()
    }

    /** Encaixa o video na tela (AJUSTAR mantem a proporcao; ESTICAR preenche tudo). */
    private fun ajustarSuperficie() {
        val larguraTela = raiz.width
        val alturaTela = raiz.height
        val lp = superficie.layoutParams as FrameLayout.LayoutParams
        if (tela.estado != Estado.TRANSMITINDO || larguraTela == 0 || alturaTela == 0 || tela.esticar) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
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
        if (cameraAberta) handler.postDelayed({ if (cameraAberta) reanexar() }, 250L)
    }

    // ------------------------------------------------------------------ diagnostico

    private fun registrarContadorDeFrames() {
        val h = helper ?: return
        val cb = callbackFrames ?: IFrameCallback { frames.incrementAndGet() }.also { callbackFrames = it }
        try {
            h.setFrameCallback(cb, UVCCamera.PIXEL_FORMAT_NV21)
        } catch (e: Exception) {
            // sem contador de frames: o diagnostico mostra o restante
        }
    }

    private val atualizarDiag = object : Runnable {
        override fun run() {
            val agora = frames.get()
            fpsDiag = (agora - framesAntes) * 2L
            framesAntes = agora
            tela.diagLinhas = montarDiagnostico(agora)
            tela.invalidate()
            handler.postDelayed(this, 500L)
        }
    }

    private fun abrirDiagnostico() {
        framesAntes = frames.get()
        if (cameraAberta) registrarContadorDeFrames() else if (callbackFrames == null) callbackFrames = IFrameCallback { frames.incrementAndGet() }
        handler.removeCallbacks(atualizarDiag)
        atualizarDiag.run()
    }

    private fun montarDiagnostico(totalFrames: Long): List<String> {
        val l = ArrayList<String>()
        val d = dispositivo
        l.add("FRAMES RECEBIDOS: $totalFrames (${fpsDiag} POR SEGUNDO)")
        l.add("CAMERA ABERTA: " + (if (cameraAberta) "SIM" else "NAO") + "  ESTADO: " + tela.estado.name)
        if (d != null) {
            l.add("DISPOSITIVO: ${d.productName ?: "?"}  ID ${java.lang.Integer.toHexString(d.vendorId)}:${java.lang.Integer.toHexString(d.productId)}")
        } else {
            l.add("DISPOSITIVO: NENHUM")
        }
        val t = tamanhoAtual
        l.add("TAMANHO ATUAL: " + (if (t != null) "${t.width}X${t.height} A ${t.fps} FPS TIPO ${t.type}" else "NENHUM"))
        l.add("SUPERFICIE: ${superficieLarg}X$superficieAlt ENTREGUE: " + (if (superficieAnexada) "SIM" else "NAO"))
        // Contadores da biblioteca nativa corrigida (ver vita/tools/tvbox_uvc.h): mostram em que etapa o video para.
        try {
            val n = UVCCamera.nativeTvboxStats()
            if (n != null && n.size >= 15) {
                l.add("USB: ${n[0]} LEITURAS DE ${n[7]} BYTES  ERROS ${n[4]}  INICIOS ${n[14]}")
                l.add("PAYLOADS: ${n[1]} (ULTIMO ${n[2]} BYTES, FLAGS ${java.lang.Long.toHexString(n[6])})  DESCARTADOS ${n[3]}")
                l.add("QUADROS MONTADOS: ${n[5]}  CONVERTIDOS: ${n[8]}  RECUSADOS: ${n[9]}")
                l.add("ULTIMO QUADRO: FORMATO ${n[10]} ${n[12]}X${n[13]} ${n[11]} BYTES")
            }
        } catch (e: Throwable) {
            l.add("CONTADORES NATIVOS INDISPONIVEIS")
        }
        val h = helper
        try {
            val formatos = h?.supportedFormatList
            if (formatos != null) {
                for (f in formatos) l.add("FORMATO ${f.index} TIPO ${f.type}: ${f.frameDescriptors?.size ?: 0} TAMANHOS")
            }
            val tamanhos = h?.supportedSizeList
            if (tamanhos != null) {
                for (s in tamanhos.take(8)) l.add("OFERECE ${s.width}X${s.height} A ${s.fps} FPS TIPO ${s.type}")
            }
        } catch (e: Exception) {
            l.add("LISTA DE FORMATOS INDISPONIVEL")
        }
        return l
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
