package com.acrylic.outbound.pda.update

import com.acrylic.outbound.pda.network.ConnectionSettings
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

class UpdateHttp(server: String, private val channel: String, client: OkHttpClient = OkHttpClient()) {
    private val origin = origin(server)
    private val http = client.newBuilder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    @Volatile private var downloadCall: Call? = null

    fun manifest(): String = http.newCall(request("updates/$channel/manifest.json")).execute().use {
        check(it.code == 200) { if (it.code == 404) "本渠道尚无可用发布，请联系管理员" else "更新清单暂不可用，请稍后重试（HTTP ${it.code}）" }
        val bytes = checkNotNull(it.body).byteStream().use { input -> input.readBytesLimited(128 * 1024) }
        String(bytes, Charsets.UTF_8)
    }

    fun cancelDownload() { downloadCall?.cancel() }

    fun download(request: DownloadRequest, onBytes: (Long, Long) -> Unit, onHeaders: (String?) -> Unit): DownloadResult {
        require(request.path.matches(Regex("updates/$channel/[A-Za-z0-9._-]{1,120}\\.apk"))) { "更新包路径越界" }
        val offset = if (!request.etag.isNullOrBlank() && !request.etag.startsWith("W/")) request.offset else 0
        val builder = request(request.path).newBuilder()
        if (offset > 0) builder.header("Range", "bytes=$offset-").header("If-Range", request.etag!!)
        val call = http.newCall(builder.build()); downloadCall = call
        try {
            return call.execute().use { response ->
                val append = resumeAccepted(response, offset, request)
                val start = if (append) offset else 0L
                onHeaders(response.header("ETag"))
                onBytes(start, request.size)
                copy(response, request.copy(offset = start), append, onBytes)
                DownloadResult(request.target.length(), response.header("ETag"), append)
            }
        } finally { downloadCall = null }
    }

    private fun resumeAccepted(response: Response, offset: Long, request: DownloadRequest): Boolean {
        check(response.code in setOf(200, 206)) { "更新包下载失败（HTTP ${response.code}）" }
        if (response.code == 200) return false
        val range = Regex("bytes ([0-9]+)-([0-9]+)/([0-9]+)").matchEntire(response.header("Content-Range").orEmpty())
        check(range != null && range.groupValues[1].toLong() == offset &&
            range.groupValues[2].toLong() == request.size - 1 && range.groupValues[3].toLong() == request.size) {
            "服务器续传位置不匹配，请重新检查更新"
        }
        check(offset > 0 && response.header("ETag") == request.etag) { "更新文件标识已变化，请重新检查更新" }
        return true
    }

    private fun copy(response: Response, request: DownloadRequest, append: Boolean, progress: (Long, Long) -> Unit) {
        checkNotNull(response.body).byteStream().use { input ->
            FileOutputStream(request.target, append).use { output ->
                var written = request.offset; val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    written += count; check(written <= request.size) { "更新包超过清单大小" }
                    output.write(buffer, 0, count); progress(written, request.size)
                }
                output.fd.sync()
                check(written == request.size) { "安装包下载不完整，请重试" }
            }
        }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer); if (count < 0) break
            check(output.size() + count <= limit) { "更新清单过大" }; output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun request(path: String): Request = Request.Builder().url(origin.resolve(path).toString())
        .header("Accept", "application/json, application/octet-stream").header("Cache-Control", "no-cache").build()

    private fun origin(server: String): URI {
        val uri = URI(ConnectionSettings.validateServer(server))
        check(uri.scheme == "https") { "应用更新必须使用 HTTPS，请核对服务地址" }
        return URI(uri.scheme, uri.rawAuthority, "/", null, null)
    }

    data class DownloadRequest(val path: String, val target: File, val size: Long, val offset: Long, val etag: String?)
    data class DownloadResult(val written: Long, val etag: String?, val resumed: Boolean)
}
