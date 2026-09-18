package com.acrylic.outbound.pda.update

import com.acrylic.outbound.pda.network.ConnectionSettings
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit

class UpdateHttp(server: String, private val channel: String) {
    private val origin = origin(server)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    fun manifest(): String {
        val response = http.newCall(request("updates/$channel/manifest.json")).execute()
        response.use {
            check(it.isSuccessful) { "更新清单暂不可用，请稍后重试（HTTP ${it.code}）" }
            return it.body!!.use { body -> body.string().also { text -> check(text.length <= 128 * 1024) { "更新清单过大" } } }
        }
    }

    fun download(path: String, target: File, offset: Long, etag: String?, onBytes: (Long, Long) -> Unit,
        onHeaders: (String?) -> Unit = {}) : DownloadResult {
        require(path.startsWith("updates/$channel/") && !path.contains("..")) { "更新包路径越界" }
        val builder = request(path).newBuilder()
        if (offset > 0) {
            builder.header("Range", "bytes=$offset-")
            if (!etag.isNullOrBlank()) builder.header("If-Range", etag)
        }
        val response = http.newCall(builder.build()).execute()
        response.use {
            check(it.isSuccessful || it.code == 206) { "更新包下载失败（HTTP ${it.code}）" }
            onHeaders(it.header("ETag"))
            val contentRange = it.header("Content-Range")
            val rangeStart = contentRange?.substringAfter("bytes ")?.substringBefore("-")?.toLongOrNull()
            if (offset > 0 && it.code == 206) check(rangeStart == offset) { "服务器续传位置不匹配，请重新下载" }
            val append = it.code == 206 && offset > 0
            val start = if (append) offset else 0
            val total = it.header("Content-Range")?.substringAfterLast("/")?.toLongOrNull()
                ?: it.body!!.contentLength().takeIf { size -> size >= 0 }?.let { size -> size + start } ?: -1
            it.body!!.byteStream().use { input ->
                FileOutputStream(target, append).use { output ->
                    var written = start; val buffer = ByteArray(64 * 1024)
                    while (true) { val count = input.read(buffer); if (count < 0) break
                        written += count; check(written <= 64L * 1024 * 1024) { "更新包超过大小限制" }
                        output.write(buffer, 0, count); onBytes(written, total)
                    }
                }
            }
            return DownloadResult(written = target.length(), etag = it.header("ETag"), resumed = append)
        }
    }

    private fun request(path: String): Request = Request.Builder().url(origin.resolve(path).toString())
        .header("Accept", "application/json, application/octet-stream").build()

    private fun origin(server: String): URI {
        val validated = ConnectionSettings.validateServer(server)
        val uri = URI(validated)
        return URI(uri.scheme, uri.rawAuthority, "/", null, null)
    }

    data class DownloadResult(val written: Long, val etag: String?, val resumed: Boolean)
}
