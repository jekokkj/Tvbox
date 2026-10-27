package br.tvbox.launcher

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject

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
        Rede.iniciar(this)
        Servidor.executor = { cmd, args -> executar(cmd, args) }
        if (Config.controle(this)) Servidor.iniciar(this)
    }

    private var naFrente = false

    override fun onResume() {
        super.onResume()
        naFrente = true
        esconderBarras()
        tela.atualizarInstalados()
        tela.painel?.aoRetomar()
        relogio.run()
        reagendarOcioso()
    }

    override fun onPause() {
        naFrente = false
        // Regrava as escolhas ao sair (gravacao sincrona): TV box desligada da tomada nao pode perder o tema.
        Config.setTema(this, tela.tema.id)
        Config.setCorIcone(this, tela.corIcone)
        Config.setCorFoco(this, tela.corFoco)
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

    // ---------------------------------------------------------------- controle pelo celular

    private fun trazerParaFrente() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    /** Simula o aperto de uma tecla do controle remoto dentro do launcher. */
    private fun injetar(codigo: Int) {
        val agora = android.os.SystemClock.uptimeMillis()
        dispatchKeyEvent(KeyEvent(agora, agora, KeyEvent.ACTION_DOWN, codigo, 0))
        dispatchKeyEvent(KeyEvent(agora, agora + 60, KeyEvent.ACTION_UP, codigo, 0))
    }

    /** Comandos vindos do Servidor (sempre na thread principal). Devolve um texto curto para o celular. */
    private fun executar(cmd: String, args: Map<String, String>): String {
        reagendarOcioso()
        when (cmd) {
            "estado" -> return JSONObject()
                .put("tela", tela.painel?.javaClass?.simpleName ?: "INICIO")
                .put("naFrente", naFrente)
                .put("tocando", Tocador.titulo)
                .toString()
            "redesenhar" -> { tela.invalidate(); return "OK" }
            "recebido" -> {
                Toast.makeText(this, "ARQUIVO RECEBIDO: " + (args["nome"] ?: ""), Toast.LENGTH_LONG).show()
                (tela.painel as? TelaControle)?.listar()
                return "OK"
            }
            "painel-atualizar" -> { (tela.painel as? TelaPainel)?.atualizar(); return "OK" }
            "volume" -> {
                val am = getSystemService(AUDIO_SERVICE) as AudioManager
                val d = when (args["d"]) {
                    "mais" -> AudioManager.ADJUST_RAISE
                    "menos" -> AudioManager.ADJUST_LOWER
                    else -> AudioManager.ADJUST_TOGGLE_MUTE
                }
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
                return "VOLUME " + am.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }
            "tema" -> { proximoTema(); return "TEMA " + tela.tema.nome }
            "abrir" -> {
                if (!naFrente) trazerParaFrente()
                return if (tela.abrirCartao(args["c"] ?: "")) "ABRINDO" else "NAO ACHEI"
            }
            "radio" -> {
                val b = args["busca"]?.trim().orEmpty()
                val p = tela.painel
                if (p is TelaRadio && b.isNotEmpty()) p.buscar(b) else {
                    TelaRadio.buscaPendente = b.ifEmpty { null }
                    if (!naFrente) trazerParaFrente()
                    tela.abrirCartao("radio")
                }
                return "BUSCANDO NA TV"
            }
            "texto" -> {
                val s = args["t"]?.trim().orEmpty()
                if (s.isEmpty()) return "VAZIO"
                if (s.startsWith("http://") || s.startsWith("https://")) {
                    return try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(s)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); "ABRINDO O LINK"
                    } catch (e: Exception) { "NENHUM APP ABRE ESSE LINK" }
                }
                val p = tela.painel
                if (p is TelaRadio) { p.buscar(s); return "BUSCANDO RADIO" }
                Toast.makeText(this, s, Toast.LENGTH_LONG).show()
                return "MOSTRADO NA TV"
            }
            "tecla" -> {
                val k = args["k"] ?: return "?"
                if (k == "play") { Tocador.pausarOuContinuar(); return "PLAY/PAUSE" }
                if (k == "home") {
                    if (!naFrente) trazerParaFrente() else { while (tela.painel != null) tela.fecharPainel(); if (tela.egg) tela.fecharEgg() }
                    return "INICIO"
                }
                if (!naFrente) {
                    if (k == "back") {
                        val a = Acessibilidade.ativo ?: return "ATIVE O SERVICO DE ACESSIBILIDADE (AJUSTES)"
                        a.voltar(); return "VOLTAR"
                    }
                    return "OUTRO APP NA FRENTE: USE O CONTROLE DA TV"
                }
                if (k == "oklongo") {
                    val p = tela.painel
                    if (p != null && p !is Descanso) p.teclaLonga() else if (p == null && !tela.egg) proximoTema()
                    tela.invalidate()
                    return "OK SEGURADO"
                }
                val codigo = when (k) {
                    "up" -> KeyEvent.KEYCODE_DPAD_UP
                    "down" -> KeyEvent.KEYCODE_DPAD_DOWN
                    "left" -> KeyEvent.KEYCODE_DPAD_LEFT
                    "right" -> KeyEvent.KEYCODE_DPAD_RIGHT
                    "ok" -> KeyEvent.KEYCODE_DPAD_CENTER
                    "back" -> KeyEvent.KEYCODE_BACK
                    "menu" -> KeyEvent.KEYCODE_MENU
                    else -> return "?"
                }
                injetar(codigo)
                return "OK"
            }
        }
        return "?"
    }
}