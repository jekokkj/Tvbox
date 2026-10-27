package br.tvbox.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Acesso a internet para radio, podcasts e painel.
 * O Android 7.0 nao conhece a raiz ISRG do Let's Encrypt (usada por boa parte dos sites hoje):
 * aqui o HTTPS confia nas raizes do sistema MAIS as duas ISRG embutidas em res/raw.
 * Tudo roda numa thread de fundo e a resposta volta na thread principal.
 */
object Rede {
    private val fila = Executors.newFixedThreadPool(3)
    private val principal = Handler(Looper.getMainLooper())
    private var fabrica: SSLSocketFactory? = null

    fun iniciar(ctx: Context) {
        if (fabrica != null) return
        try {
            val cf = CertificateFactory.getInstance("X.509")
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            ks.load(null, null)
            for ((i, id) in listOf(R.raw.isrg_root_x1, R.raw.isrg_root_x2).withIndex()) {
                ctx.resources.openRawResource(id).use { ks.setCertificateEntry("isrg$i", cf.generateCertificate(it)) }
            }
            val tmfExtra = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
            val tmfSistema = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                .apply { init(null as KeyStore?) }
            val extra = tmfExtra.trustManagers.filterIsInstance<X509TrustManager>().first()
            val sistema = tmfSistema.trustManagers.filterIsInstance<X509TrustManager>().first()
            val juntos = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    sistema.checkClientTrusted(chain, authType)

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    try {
                        sistema.checkServerTrusted(chain, authType)
                    } catch (e: Exception) {
                        extra.checkServerTrusted(chain, authType)
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = sistema.acceptedIssuers + extra.acceptedIssuers
            }
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf(juntos), null)
            fabrica = ssl.socketFactory
        } catch (e: Exception) {
            fabrica = null
        }
    }

    /** Abre a conexao (segue redirecionamentos, inclusive de http para https). */
    fun abrir(endereco: String, timeoutMs: Int = 12000): HttpURLConnection {
        var url = URL(endereco)
        repeat(6) {
            val c = url.openConnection() as HttpURLConnection
            if (c is HttpsURLConnection) fabrica?.let { c.sslSocketFactory = it }
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "TVBoxLauncher/2.0 (Android)")
            val cod = c.responseCode
            if (cod in 300..399) {
                val destino = c.getHeaderField("Location") ?: return c
                c.disconnect()
                url = URL(url, destino)
            } else {
                return c
            }
        }
        throw java.io.IOException("redirecionamentos demais")
    }

    /** Baixa um texto (limite de [maxBytes] para nao estourar a memoria da caixa). */
    fun texto(endereco: String, maxBytes: Int = 4 shl 20): String {
        val c = abrir(endereco)
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return ler(c.inputStream, maxBytes)
        } finally {
            c.disconnect()
        }
    }

    private fun ler(ent: InputStream, maxBytes: Int): String {
        val buf = java.io.ByteArrayOutputStream()
        val b = ByteArray(16384)
        ent.use {
            while (true) {
                val n = it.read(b)
                if (n < 0) break
                buf.write(b, 0, n)
                if (buf.size() > maxBytes) break
            }
        }
        return String(buf.toByteArray(), Charsets.UTF_8)
    }

    /** Roda [trabalho] em segundo plano e entrega o resultado (ou o erro) na thread principal. */
    fun <T> fundo(trabalho: () -> T, pronto: (T?, Exception?) -> Unit) {
        fila.execute {
            try {
                val r = trabalho()
                principal.post { pronto(r, null) }
            } catch (e: Exception) {
                principal.post { pronto(null, e) }
            }
        }
    }

    fun naPrincipal(bloco: () -> Unit) {
        principal.post(bloco)
    }
}
