package br.tvbox.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Um tocador so para radio e podcasts (MediaPlayer: MP3, AAC e HLS no Android 7).
 * O servico em primeiro plano so existe para o Android nao matar o som quando outro app abre.
 * Todo o estado fica aqui no companion e e mexido apenas na thread principal.
 */
class Tocador : Service() {

    companion object {
        const val PARADO = 0
        const val CARREGANDO = 1
        const val TOCANDO = 2
        const val PAUSADO = 3
        const val ERRO = 4

        var estado = PARADO
            private set
        var titulo = ""
            private set
        var subtitulo = ""
            private set
        /** "radio" ou "podcast". */
        var tipo = ""
            private set
        /** Identificador do que esta tocando (url da radio ou do episodio). */
        var chave: String? = null
            private set
        var erro = ""
            private set
        var velocidade = 1f
            private set

        /** Telas abertas se inscrevem aqui para redesenhar. */
        val ouvintes = LinkedHashSet<() -> Unit>()

        private var player: MediaPlayer? = null
        private var app: Context? = null
        private var urlAtual: String? = null
        private var tentouHttp = false
        private var inicioMs = 0
        private val h = Handler(Looper.getMainLooper())

        // Podcasts: grava a posicao a cada 10 s para retomar depois.
        private val salvarPosicao = object : Runnable {
            override fun run() {
                gravarPosicao()
                h.postDelayed(this, 10_000)
            }
        }

        private fun avisar() {
            for (o in ouvintes.toList()) o()
            app?.let { atualizarNotificacao(it) }
        }

        fun tocar(ctx: Context, url: String, titulo: String, subtitulo: String, tipo: String, chave: String, inicio: Int = 0) {
            gravarPosicao()
            liberar()
            app = ctx.applicationContext
            this.titulo = titulo
            this.subtitulo = subtitulo
            this.tipo = tipo
            this.chave = chave
            urlAtual = url
            tentouHttp = false
            inicioMs = inicio
            erro = ""
            iniciarServico(ctx)
            abrirUrl(url)
        }

        private fun abrirUrl(url: String) {
            val ctx = app ?: return
            estado = CARREGANDO
            avisar()
            val mp = MediaPlayer()
            player = mp
            try {
                if (Build.VERSION.SDK_INT >= 21) {
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                    )
                }
                mp.setWakeMode(ctx, android.os.PowerManager.PARTIAL_WAKE_LOCK)
                mp.setDataSource(url)
                mp.setOnPreparedListener {
                    if (player !== it) return@setOnPreparedListener
                    if (inicioMs > 0) it.seekTo(inicioMs)
                    aplicarVelocidade(it)
                    it.start()
                    estado = TOCANDO
                    if (tipo == "podcast") {
                        h.removeCallbacks(salvarPosicao)
                        h.postDelayed(salvarPosicao, 10_000)
                    }
                    avisar()
                }
                mp.setOnErrorListener { p, _, _ ->
                    if (player !== p) return@setOnErrorListener true
                    falhou()
                    true
                }
                mp.setOnCompletionListener { p ->
                    if (player !== p) return@setOnCompletionListener
                    if (tipo == "podcast") {
                        chave?.let { Config.setPosicao(ctx, it, -1) } // -1 = ouvido ate o fim
                        estado = PARADO
                        avisar()
                    } else {
                        // Radio que caiu: tenta reconectar.
                        h.postDelayed({ urlAtual?.let { u -> liberar(); abrirUrl(u) } }, 2000)
                    }
                }
                mp.prepareAsync()
            } catch (e: Exception) {
                falhou()
            }
        }

        /** Muitas radios tem certificado que o Android 7 recusa: tenta a mesma url em http antes de desistir. */
        private fun falhou() {
            val u = urlAtual
            liberar()
            if (u != null && u.startsWith("https://") && !tentouHttp) {
                tentouHttp = true
                urlAtual = "http://" + u.removePrefix("https://")
                abrirUrl(urlAtual!!)
                return
            }
            estado = ERRO
            erro = if (tipo == "radio") "RADIO FORA DO AR" else "NAO FOI POSSIVEL TOCAR"
            avisar()
        }

        private fun aplicarVelocidade(mp: MediaPlayer) {
            if (Build.VERSION.SDK_INT >= 23 && velocidade != 1f) {
                try {
                    mp.playbackParams = mp.playbackParams.setSpeed(velocidade)
                } catch (e: Exception) {
                }
            }
        }

        fun pausarOuContinuar() {
            val mp = player
            when {
                mp != null && estado == TOCANDO -> {
                    mp.pause()
                    estado = PAUSADO
                    gravarPosicao()
                }
                mp != null && estado == PAUSADO -> {
                    if (tipo == "radio") {
                        // radio ao vivo: volta para o "agora"
                        val u = urlAtual
                        liberar()
                        if (u != null) abrirUrl(u)
                        return
                    }
                    mp.start()
                    estado = TOCANDO
                }
                estado == ERRO || estado == PARADO -> {
                    val u = urlAtual ?: return
                    tentouHttp = false
                    inicioMs = chave?.let { c -> app?.let { Config.posicao(it, c) } }?.coerceAtLeast(0) ?: 0
                    liberar()
                    abrirUrl(u)
                    return
                }
            }
            avisar()
        }

        fun pular(ms: Int) {
            val mp = player ?: return
            if (tipo != "podcast" || (estado != TOCANDO && estado != PAUSADO)) return
            try {
                val alvo = (mp.currentPosition + ms).coerceIn(0, maxOf(0, mp.duration - 1000))
                mp.seekTo(alvo)
            } catch (e: Exception) {
            }
            avisar()
        }

        fun trocarVelocidade() {
            val opcoes = floatArrayOf(1f, 1.25f, 1.5f, 2f)
            val i = opcoes.indexOfFirst { it == velocidade }
            velocidade = opcoes[(i + 1) % opcoes.size]
            player?.let { if (estado == TOCANDO) aplicarVelocidade(it) }
            avisar()
        }

        fun posicaoMs(): Int = try {
            if (estado == TOCANDO || estado == PAUSADO) player?.currentPosition ?: 0 else 0
        } catch (e: Exception) {
            0
        }

        fun duracaoMs(): Int = try {
            if (estado == TOCANDO || estado == PAUSADO) maxOf(0, player?.duration ?: 0) else 0
        } catch (e: Exception) {
            0
        }

        private fun gravarPosicao() {
            val ctx = app ?: return
            val c = chave ?: return
            if (tipo != "podcast") return
            val p = posicaoMs()
            if (p > 5000) Config.setPosicao(ctx, c, p)
        }

        fun parar(ctx: Context) {
            gravarPosicao()
            liberar()
            estado = PARADO
            urlAtual = null
            chave = null
            titulo = ""
            avisar()
            ctx.stopService(Intent(ctx, Tocador::class.java))
        }

        private fun liberar() {
            h.removeCallbacks(salvarPosicao)
            player?.let {
                try {
                    it.reset()
                    it.release()
                } catch (e: Exception) {
                }
            }
            player = null
        }

        private fun iniciarServico(ctx: Context) {
            val i = Intent(ctx, Tocador::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (e: Exception) {
            }
        }

        private const val CANAL = "tocador"
        private const val ID = 7

        private fun notificacao(ctx: Context): Notification {
            val abrir = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
            )
            val b = if (Build.VERSION.SDK_INT >= 26) {
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(CANAL) == null) {
                    nm.createNotificationChannel(NotificationChannel(CANAL, "Radio e podcasts", NotificationManager.IMPORTANCE_LOW))
                }
                Notification.Builder(ctx, CANAL)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(ctx)
            }
            val txt = when (estado) {
                CARREGANDO -> "Carregando..."
                PAUSADO -> "Pausado"
                ERRO -> erro
                else -> subtitulo
            }
            return b.setSmallIcon(R.drawable.icon_jogos_verde)
                .setContentTitle(titulo.ifEmpty { "TV Launcher" })
                .setContentText(txt)
                .setContentIntent(abrir)
                .setOngoing(estado == TOCANDO || estado == CARREGANDO)
                .build()
        }

        private fun atualizarNotificacao(ctx: Context) {
            if (estado == PARADO) return
            try {
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(ID, notificacao(ctx))
            } catch (e: Exception) {
            }
        }
    }

    private var foco: AudioManager.OnAudioFocusChangeListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = notificacao(this)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(ID, n)
        }
        if (foco == null) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val f = AudioManager.OnAudioFocusChangeListener { mudanca ->
                // Outro app tocando (YouTube, Spotify...): pausa a radio/podcast.
                if (mudanca == AudioManager.AUDIOFOCUS_LOSS && estado == TOCANDO) pausarOuContinuar()
            }
            foco = f
            @Suppress("DEPRECATION")
            am.requestAudioFocus(f, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        foco?.let {
            @Suppress("DEPRECATION")
            (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocus(it)
        }
        foco = null
        super.onDestroy()
    }
}
