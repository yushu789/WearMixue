package site.unclefish.wearmixue.phone.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import site.unclefish.wearmixue.core.AuthSession
import java.util.concurrent.TimeUnit

/**
 * POSTs the captured [AuthSession] to the watch's HTTP token endpoint.
 * The watch URL (e.g. `http://192.168.1.5:38291/token`) is obtained by scanning the
 * QR code displayed on the watch.
 */
class WatchTokenClient(
    private val client: OkHttpClient = defaultClient()
) {
    suspend fun sendToken(watchBaseUrl: String, session: AuthSession): Boolean = withContext(Dispatchers.IO) {
        val url = watchBaseUrl.trimEnd('/') + if (watchBaseUrl.endsWith("/token")) "" else "/token"
        val body = session.toJson().toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Connection", "close")
            .post(body)
            .build()
        runCatching {
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private fun defaultClient() = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
