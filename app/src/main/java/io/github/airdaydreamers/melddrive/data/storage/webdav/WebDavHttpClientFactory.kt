package io.github.airdaydreamers.melddrive.data.storage.webdav

import android.annotation.SuppressLint
import io.github.airdaydreamers.melddrive.data.db.RemoteServer
import okhttp3.Authenticator
import okhttp3.Challenge
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import timber.log.Timber
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object WebDavHttpClientFactory {

    private const val TIMEOUT_SECONDS = 30L

    fun createClient(server: RemoteServer): OkHttpClient {
        Timber.d(
            "WebDavHttpClientFactory: createClient serverId=%d host='%s' port=%d isAnonymous=%b trustSelfSigned=%b username='%s'",
            server.id,
            server.host,
            server.port,
            server.isAnonymous,
            server.trustSelfSigned,
            server.username,
        )

        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)

        if (!server.isAnonymous && !server.username.isNullOrEmpty()) {
            val username = server.username
            val password = server.password ?: ""
            builder.authenticator(WebDavAuthenticator(username, password))
        }

        if (server.trustSelfSigned) {
            configureSelfSignedSsl(builder)
        }

        return builder.build()
    }

    private fun configureSelfSignedSsl(builder: OkHttpClient.Builder) {
        val trustAllCerts = arrayOf<TrustManager>(
            @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    // Trust all client certs for self-signed SSL
                }

                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    // Trust all server certs for self-signed SSL
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            },
        )

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, SecureRandom())

        val sslSocketFactory = sslContext.socketFactory
        val trustManager = trustAllCerts[0] as X509TrustManager

        builder.sslSocketFactory(sslSocketFactory, trustManager)
        builder.hostnameVerifier(HostnameVerifier { _, _ -> true })
    }
}

class WebDavAuthenticator(private val username: String, private val password: String) : Authenticator {

    private var ncCount = 0

    override fun authenticate(route: Route?, response: Response): Request? {
        val requestUrl = response.request.url
        Timber.d("WebDavAuthenticator: Received 401 for %s", requestUrl)

        if (responseCount(response) >= MAX_RETRY_COUNT) {
            Timber.w("WebDavAuthenticator: Max auth retries reached (%d) for %s", MAX_RETRY_COUNT, requestUrl)
            return null
        }

        val challenges = response.challenges()
        Timber.d("WebDavAuthenticator: Challenges received: %s", challenges)

        return tryDigestAuth(challenges, response.request)
            ?: tryBasicAuth(challenges, response.request)
    }

    private fun tryDigestAuth(challenges: List<Challenge>, request: Request): Request? {
        val digestChallenge = challenges.firstOrNull { it.scheme.equals("Digest", ignoreCase = true) }
        val authHeader = if (digestChallenge != null) buildDigestHeader(digestChallenge, request) else null
        return if (authHeader != null) {
            Timber.d("WebDavAuthenticator: Responding with Digest auth header for user '%s'", username)
            request.newBuilder()
                .header("Authorization", authHeader)
                .build()
        } else {
            null
        }
    }

    private fun tryBasicAuth(challenges: List<Challenge>, request: Request): Request? {
        val basicChallenge = challenges.firstOrNull { it.scheme.equals("Basic", ignoreCase = true) }
        if (basicChallenge == null) {
            Timber.w("WebDavAuthenticator: No supported auth scheme in challenges: %s", challenges)
            return null
        }

        val credential = Credentials.basic(username, password)
        Timber.d("WebDavAuthenticator: Responding with Basic auth header for user '%s'", username)
        return request.newBuilder()
            .header("Authorization", credential)
            .build()
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    private fun buildDigestHeader(challenge: Challenge, request: Request): String? {
        val realm = challenge.realm
        val nonce = challenge.authParams["nonce"]
        if (realm == null || nonce == null) return null

        val opaque = challenge.authParams["opaque"]
        val qop = challenge.authParams["qop"]
        val algorithm = challenge.authParams["algorithm"] ?: "MD5"

        val uri = extractRequestUri(request)
        val ha1 = md5("$username:$realm:$password")
        val ha2 = md5("${request.method}:$uri")
        val cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, CNONCE_LENGTH)

        val isAuthQop = qop != null && qop.split(",").map { it.trim() }.contains("auth")

        return computeDigestHeaderString(realm, nonce, uri, ha1, ha2, opaque, algorithm, cnonce, isAuthQop)
    }

    private fun computeDigestHeaderString(
        realm: String,
        nonce: String,
        uri: String,
        ha1: String,
        ha2: String,
        opaque: String?,
        algorithm: String,
        cnonce: String,
        isAuthQop: Boolean,
    ): String = if (isAuthQop) {
        ncCount++
        val nc = String.format(Locale.US, "%08x", ncCount)
        val responseVal = md5("$ha1:$nonce:$nc:$cnonce:auth:$ha2")
        buildAuthString(realm, nonce, uri, responseVal, opaque, algorithm, nc, cnonce)
    } else {
        val responseVal = md5("$ha1:$nonce:$ha2")
        buildAuthString(realm, nonce, uri, responseVal, opaque, algorithm, null, null)
    }

    private fun extractRequestUri(request: Request): String = try {
        val rawUri = request.url.toUri()
        val rawPath = if (rawUri.rawPath.isNullOrEmpty()) "/" else rawUri.rawPath
        rawPath + if (rawUri.rawQuery != null) "?${rawUri.rawQuery}" else ""
    } catch (_: IllegalArgumentException) {
        request.url.encodedPath
    }

    private fun buildAuthString(
        realm: String,
        nonce: String,
        uri: String,
        responseVal: String,
        opaque: String?,
        algorithm: String,
        nc: String?,
        cnonce: String?,
    ): String = buildString {
        append("Digest username=\"").append(username).append("\", ")
        append("realm=\"").append(realm).append("\", ")
        append("nonce=\"").append(nonce).append("\", ")
        append("uri=\"").append(uri).append("\", ")
        append("response=\"").append(responseVal).append("\"")
        if (nc != null && cnonce != null) {
            append(", qop=auth, ")
            append("nc=").append(nc).append(", ")
            append("cnonce=\"").append(cnonce).append("\"")
        }
        if (!opaque.isNullOrEmpty()) {
            append(", opaque=\"").append(opaque).append("\"")
        }
        if (algorithm.isNotEmpty()) {
            append(", algorithm=\"").append(algorithm).append("\"")
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_RETRY_COUNT = 3
        private const val CNONCE_LENGTH = 16
    }
}
