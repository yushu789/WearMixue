package site.unclefish.wearmixue.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import site.unclefish.wearmixue.core.AuthSession

class TokenHttpServerTest {
    private lateinit var scope: CoroutineScope
    private lateinit var server: TokenHttpServer
    private val client = OkHttpClient()
    private val json = "application/json; charset=utf-8".toMediaType()

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server = TokenHttpServer(scope)
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private fun url(): String = "http://127.0.0.1:${server.port}/token"

    @Test
    fun postTokenEmitsSession() = runBlocking {
        val session = AuthSession(
            accessToken = "token-xyz",
            customerId = "customer-1",
            seqNum = "seq-1",
            mobilePhone = "phone-test-1"
        )
        val body = session.toJson().toRequestBody(json)
        val request = Request.Builder().url(url()).post(body).build()

        val deferred = async {
            withTimeout(5000) { server.incomingTokens.first() }
        }
        // Give the collector a tick to subscribe before we POST.
        delay(100)
        val code = client.newCall(request).execute().use { it.code }
        val received = deferred.await()

        assertEquals(200, code)
        assertEquals(session, received)
    }

    @Test
    fun badBodyReturns400() = runBlocking {
        val body = "not-json".toRequestBody(json)
        val request = Request.Builder().url(url()).post(body).build()
        val code = client.newCall(request).execute().use { it.code }
        assertEquals(400, code)
    }

    @Test
    fun tokenWithoutOrderingIdentityReturns400() = runBlocking {
        val body = AuthSession(accessToken = "token-only").toJson().toRequestBody(json)
        val request = Request.Builder().url(url()).post(body).build()
        val code = client.newCall(request).execute().use { it.code }
        assertEquals(400, code)
    }

    @Test
    fun unknownPathReturns404() = runBlocking {
        val body = "{}".toRequestBody(json)
        val request = Request.Builder()
            .url("http://127.0.0.1:${server.port}/nope")
            .post(body)
            .build()
        val code = client.newCall(request).execute().use { it.code }
        assertEquals(404, code)
    }
}
