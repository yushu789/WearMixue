package site.unclefish.wearmixue.network

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import site.unclefish.wearmixue.auth.SessionStore
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MixueApiException(
    override val message: String,
    val code: Int? = null,
    val path: String = ""
) : Exception(message)

class MixueApiClient(
    private val sessionStore: SessionStore,
    private val signer: RequestSigner,
    private val httpClient: OkHttpClient = defaultHttpClient(),
    private val baseUrl: String = BASE_URL
) {
    private var timeOffsetMillis: Long? = null

    val isSignerConfigured: Boolean get() = signer.isConfigured

    suspend fun get(path: String, params: Map<String, Any?> = emptyMap()): JSONObject =
        request("GET", path, params)

    suspend fun post(path: String, params: Map<String, Any?> = emptyMap()): JSONObject =
        request("POST", path, params)

    private suspend fun request(method: String, path: String, params: Map<String, Any?>): JSONObject {
        if (path != CONFIG_PATH) {
            ensureConfig()
        }
        val url = if (path.startsWith("http")) path else "$baseUrl/activity$path"
        val signedParams = try {
            signedParams(params)
        } catch (error: Exception) {
            val requestPath = "${method.uppercase()} ${url.toHttpUrl().encodedPath}"
            throw MixueApiException(
                "Signing failed at $requestPath: ${error.javaClass.simpleName} ${error.message.orEmpty()}",
                path = requestPath
            )
        }
        val request = when (method.uppercase()) {
            "GET" -> buildGet(url, signedParams)
            else -> buildPost(url, signedParams)
        }
        return execute(request)
    }

    private suspend fun ensureConfig() {
        if (timeOffsetMillis != null) return
        val request = Request.Builder()
            .url("$baseUrl/activity$CONFIG_PATH")
            .headers(includeAuth = false)
            .get()
            .build()
        val json = execute(request, allowMissingCode = true)
        val timestamp = json.optJSONObject("data")?.optLong("timestamp", 0L)
            ?: json.optLong("timestamp", 0L)
        timeOffsetMillis = if (timestamp > 0L) System.currentTimeMillis() - timestamp else 0L
    }

    private fun signedParams(params: Map<String, Any?>): LinkedHashMap<String, Any?> {
        val withMeta = linkedMapOf<String, Any?>()
        params.forEach { (key, value) ->
            if (value != null) withMeta[key] = value
        }
        withMeta["appId"] = APP_ID
        withMeta["t"] = System.currentTimeMillis() - (timeOffsetMillis ?: 0L)
        val signed = linkedMapOf<String, Any?>("sign" to signer.sign(withMeta))
        signed.putAll(withMeta)
        return signed
    }

    private fun buildGet(url: String, params: Map<String, Any?>): Request {
        val builder = url.toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            if (value != null) builder.addQueryParameter(key, value.toString())
        }
        return Request.Builder()
            .url(builder.build())
            .headers()
            .get()
            .build()
    }

    private fun buildPost(url: String, params: Map<String, Any?>): Request {
        val body = JsonCodec.stringify(params).toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder()
            .url(url)
            .headers()
            .post(body)
            .build()
    }

    private suspend fun execute(request: Request, allowMissingCode: Boolean = false): JSONObject {
        val response = httpClient.newCall(request).await()
        response.use {
            val text = withContext(Dispatchers.IO) { it.body?.string().orEmpty() }
            val requestPath = "${request.method} ${request.url.encodedPath}"
            if (!it.isSuccessful) {
                throw MixueApiException(
                    "HTTP ${it.code} at $requestPath: ${text.safeSnippet()}",
                    it.code,
                    requestPath
                )
            }
            val json = try {
                JSONObject(text)
            } catch (error: Exception) {
                throw MixueApiException("Response parse failed at $requestPath", path = requestPath)
            }
            if (allowMissingCode && !json.has("code")) {
                return json
            }
            val code = json.optInt("code", 0)
            if (code != 0) {
                val msg = json.optString("msg", "Service unavailable").ifBlank { "Service unavailable" }
                throw MixueApiException("$requestPath code=$code msg=$msg", code, requestPath)
            }
            return json
        }
    }

    private fun Request.Builder.headers(includeAuth: Boolean = true): Request.Builder {
        val auth = sessionStore.read()
        header("Accept", "*/*")
        header("content-type", "application/json; charset=utf-8")
        header("version", VERSION)
        header("Referer", MINIAPP_REFERER)
        header("User-Agent", MINIAPP_USER_AGENT)
        header(
            "X-Client-Info",
            "model=${Build.MODEL};os=Android;network=UNKNOWN;signal-strength=-1;"
        )
        if (includeAuth && auth.accessToken.isNotBlank()) {
            header("access-token", auth.accessToken)
        }
        return this
    }

    companion object {
        const val BASE_URL = "https://third-activity.mxbc.net"
        const val APP_ID = "e08880ca53a947209c72ec774afc21bf"
        const val VERSION = "1.1.5.3"
        private const val MINIAPP_REFERER = "https://miniapi.ksapisrv.com/ks692709921858700801/89028/page-frame.html"
        private const val CONFIG_PATH = "/v1/app/config"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val MINIAPP_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; ${Build.MODEL} Build/BP2A.250605.031.A3; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/121.0.6167.212 KsWebView/1.8.121.942 (rel) Mobile Safari/537.36 NetType/wifi Language/zh miniProgram/1.124.0 NEBULA/14.4.10.11443 aegon/4.46.0"

        private fun defaultHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .cookieJar(MemoryCookieJar())
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
    }
}

private fun String.safeSnippet(max: Int = 160): String {
    return replace(Regex("\"sign\"\\s*:\\s*\"[^\"]+\""), "\"sign\":\"<redacted>\"")
        .replace(Regex("\"accessToken\"\\s*:\\s*\"[^\"]+\""), "\"accessToken\":\"<redacted>\"")
        .replace(Regex("eyJ[\\w.-]+"), "<jwt>")
        .take(max)
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response)
        }
    })
}

private class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val bucket = store.getOrPut(url.host) { mutableListOf() }
        bucket.removeAll { old -> cookies.any { it.name == old.name } }
        bucket.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        return store[url.host].orEmpty().filter { it.expiresAt > now && it.matches(url) }
    }
}
