package it.mithril.app.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Server HTTP minimo che offre UN file del telefono alla TV durante la trasmissione.
 * Risponde solo al percorso con il token casuale e supporta le richieste Range (servono per i salti).
 */
class LocalMediaServer(private val context: Context, private val uri: Uri, private val mime: String) {
    private val token = ByteArray(12).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val server = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(0)) }
    @Volatile private var running = true

    init {
        thread(name = "mithril-cast-server", isDaemon = true) {
            while (running) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { serve(socket) }; runCatching { socket.close() } }
            }
        }
    }

    /** URL raggiungibile dalla TV sulla stessa rete Wi-Fi, o null se il telefono non ha un IPv4 locale. */
    fun url(): String? {
        val ip = localIpv4(context) ?: return null
        return "http://$ip:${server.localPort}/$token/video"
    }

    fun stop() { running = false; runCatching { server.close() } }

    private fun serve(socket: Socket) {
        socket.soTimeout = 30_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
        val request = reader.readLine() ?: return
        var range: String? = null
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
        }
        val parts = request.split(' ')
        val out = socket.getOutputStream()
        if (parts.size < 2 || !parts[1].startsWith("/$token/")) return respond(out, "404 Not Found", emptyMap())
        val head = parts[0] == "HEAD"
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val size = pfd.statSize
            var start = 0L
            var end = size - 1
            val r = range?.removePrefix("bytes=")
            if (r != null && size > 0) {
                val (a, b) = r.split('-', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                if (a.isNotEmpty()) { start = a.toLong(); if (b.isNotEmpty()) end = minOf(b.toLong(), size - 1) }
                else if (b.isNotEmpty()) start = (size - b.toLong()).coerceAtLeast(0)
            }
            if (start > end) return respond(out, "416 Range Not Satisfiable", mapOf("Content-Range" to "bytes */$size"))
            val length = end - start + 1
            val headers = mutableMapOf(
                "Content-Type" to mime,
                "Accept-Ranges" to "bytes",
                "Content-Length" to length.toString(),
                "Access-Control-Allow-Origin" to "*",
            )
            if (r != null) headers["Content-Range"] = "bytes $start-$end/$size"
            respond(out, if (r != null) "206 Partial Content" else "200 OK", headers)
            if (head) return
            FileInputStream(pfd.fileDescriptor).use { input ->
                input.channel.position(start)
                val buf = ByteArray(256 * 1024)
                var left = length
                while (left > 0 && running) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n); left -= n
                }
                out.flush()
            }
        } ?: respond(out, "404 Not Found", emptyMap())
    }

    private fun respond(out: OutputStream, status: String, headers: Map<String, String>) {
        val sb = StringBuilder("HTTP/1.1 $status\r\nConnection: close\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        if ("Content-Length" !in headers) sb.append("Content-Length: 0\r\n")
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1)); out.flush()
    }

    companion object {
        fun localIpv4(context: Context): String? {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
            val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
            return props.linkAddresses.map { it.address }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.isSiteLocalAddress }?.hostAddress
        }
    }
}
