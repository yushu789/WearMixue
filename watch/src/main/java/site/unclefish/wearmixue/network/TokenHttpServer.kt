package site.unclefish.wearmixue.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import site.unclefish.wearmixue.core.AuthSession
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Minimal HTTP server that receives the auth token POSTed by the phone login app.
 *
 * Exposes a single endpoint `POST /token` whose body is [AuthSession.toJson]. The
 * client (phone) is under our control, so the protocol is constrained to keep the
 * parser tiny: `Content-Length` + `Connection: close`, no chunked/keep-alive.
 */
class TokenHttpServer(
    private val scope: CoroutineScope
) {
    private val _incomingTokens = MutableSharedFlow<AuthSession>(extraBufferCapacity = 4)
    val incomingTokens: SharedFlow<AuthSession> = _incomingTokens.asSharedFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    /** The port the OS assigned. Only valid after [start]. */
    val port: Int get() = serverSocket?.localPort ?: -1

    fun start() {
        if (acceptJob?.isActive == true) return
        serverSocket = ServerSocket().apply { bind(InetSocketAddress(0)) }
        acceptJob = scope.launch(Dispatchers.IO) {
            val server = serverSocket ?: return@launch
            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (e: java.io.IOException) {
                    if (server.isClosed) break
                    continue
                }
                launch(Dispatchers.IO) { handle(socket) }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
    }

    fun scope(): CoroutineScope = scope

    private fun handle(socket: Socket) {
        socket.use { s ->
            val input = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val output = s.getOutputStream()
            val requestLine = input.readLine() ?: run { writeResponse(output, 400, BAD_BODY); return }
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0)
            val path = parts.getOrNull(1)
            if (method == null || path == null) {
                writeResponse(output, 400, BAD_BODY); return
            }

            var contentLength = 0
            while (true) {
                val header = input.readLine() ?: break
                if (header.isEmpty()) break
                val colon = header.indexOf(':')
                if (colon < 0) continue
                val name = header.substring(0, colon).trim().lowercase()
                val value = header.substring(colon + 1).trim()
                if (name == "content-length") {
                    contentLength = value.toIntOrNull() ?: 0
                }
            }

            if (method.equals("POST", true) && path == "/token") {
                if (contentLength <= 0) {
                    writeResponse(output, 400, BAD_BODY); return
                }
                val body = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = input.read(body, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                val json = String(body, 0, read)
                val session = runCatching { AuthSession.fromJson(json) }.getOrNull()
                if (session == null || !session.isUsableForOrdering) {
                    writeResponse(output, 400, BAD_BODY); return
                }
                _incomingTokens.tryEmit(session)
                writeResponse(output, 200, OK_BODY)
            } else {
                writeResponse(output, 404, NOT_FOUND_BODY)
            }
        }
    }

    private fun writeResponse(output: OutputStream, code: Int, body: String) {
        val reason = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; else -> "OK" }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = StringBuilder()
            .append("HTTP/1.1 ").append(code).append(' ').append(reason).append("\r\n")
            .append("Content-Type: application/json; charset=utf-8\r\n")
            .append("Content-Length: ").append(bytes.size).append("\r\n")
            .append("Connection: close\r\n")
            .append("\r\n")
            .toString()
        output.write(header.toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private companion object {
        const val OK_BODY = "{\"ok\":true}"
        const val BAD_BODY = "{\"ok\":false,\"error\":\"bad request\"}"
        const val NOT_FOUND_BODY = "{\"ok\":false,\"error\":\"not found\"}"
    }
}
