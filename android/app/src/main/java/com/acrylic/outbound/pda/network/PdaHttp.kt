package com.acrylic.outbound.pda.network

import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.*
import java.io.File
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

interface PdaHttp {
    @GET @Streaming fun get(@Url path: String, @HeaderMap headers: Map<String, String>): Call<ResponseBody>
    @POST @Streaming fun post(@Url path: String, @HeaderMap headers: Map<String, String>, @Body body: RequestBody): Call<ResponseBody>
}

class ApiFailure(val code: String, message: String, val retryAfterSeconds: Long? = null,
    val httpStatus: Int? = null) : Exception(message)

class PdaTransportFailure(message: String, cause: IOException) : IOException(message, cause)

class PdaNetwork(baseUrl: String,
    client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build(),
    private val observe: (JSONObject) -> Unit = {}) {
    private val http = Retrofit.Builder().baseUrl(baseUrl)
        .client(client)
        .build().create(PdaHttp::class.java)

    /** The caller verifies the signature before these untouched bytes become trusted evidence. */
    fun original(path: String, body: ByteArray?, headers: Map<String, String>, raw: ByteArray? = null): ByteArray {
        require(!path.startsWith('/') && !path.contains(":") && !path.contains(".."))
        require(body == null || raw == null)
        val call = if (raw != null) http.post(path, headers, raw.toRequestBody("application/octet-stream".toMediaType()))
            else if (body == null) http.get(path, headers)
            else http.post(path, headers, body.toRequestBody("application/json".toMediaType()))
        val response = try { call.execute() } catch (error: IOException) { throw transportFailure(error) }
        val source = response.body() ?: response.errorBody()
            ?: throw ApiFailure("EMPTY_RESPONSE", "原回执为空，请保留原请求", httpStatus = response.code())
        val bytes = try { source.use { readLimited(it.byteStream(), 262144) } }
            catch (error: IOException) { throw transportFailure(error) }
        if (!response.isSuccessful) {
            val error = runCatching { JSONObject(bytes.toString(Charsets.UTF_8)) }.getOrNull()
            error?.let(observe)
            throw ApiFailure(error?.optString("code") ?: "INVALID_RESPONSE",
                error?.optString("message") ?: "无法核对原回执，请保留原请求",
                response.headers()["Retry-After"]?.toLongOrNull(), response.code())
        }
        check(response.code() == 200 && source.contentType()?.type == "application" &&
            source.contentType()?.subtype == "json" && response.headers().values("Cache-Control")
                .flatMap { it.split(',') }.any { it.trim().equals("no-store", ignoreCase = true) }) {
            "原回执响应类型不正确，请保留原请求"
        }
        return bytes
    }

    fun request(path: String, body: JSONObject? = null, headers: Map<String, String> = emptyMap(), raw: ByteArray? = null): JSONObject {
        require(!path.startsWith('/') && !path.contains(":") && !path.contains(".."))
        val started = android.os.SystemClock.elapsedRealtime()
        val call = if (raw != null) http.post(path, headers, raw.toRequestBody("application/octet-stream".toMediaType()))
            else if (body == null) http.get(path, headers) else http.post(path, headers, body.toString().toRequestBody("application/json".toMediaType()))
        val response = try { call.execute() } catch (error: IOException) { throw transportFailure(error) }
        val source = response.body() ?: response.errorBody() ?: throw ApiFailure("EMPTY_RESPONSE", "服务器未返回内容，请重试原操作", httpStatus = response.code())
        val envelope = try { source.use {
            try { JSONObject(String(readLimited(it.byteStream(), 2 * 1024 * 1024), Charsets.UTF_8)) }
            catch (error: org.json.JSONException) { throw ApiFailure("INVALID_RESPONSE", "服务器响应格式不正确，请重试原操作", httpStatus = response.code()) }
        } } catch (error: IOException) { throw transportFailure(error) }
        android.util.Log.i("PdaTransport", "${if (body == null && raw == null) "GET" else "POST"} status=${response.code()} elapsedMs=${android.os.SystemClock.elapsedRealtime() - started}")
        observe(envelope)
        if (!response.isSuccessful || envelope.optString("code") != "OK") throw ApiFailure(envelope.optString("code"), envelope.optString("message", "请求失败"), response.headers()["Retry-After"]?.toLongOrNull(), response.code())
        if (envelope.getString("serviceMode") != "NORMAL")
            throw ApiFailure("SERVER_RECOVERY_REQUIRED", "服务器正在恢复，保留本地资料", httpStatus = 503)
        return envelope
    }

    fun download(path: String, target: File, headers: Map<String, String>) {
        require(!path.startsWith('/') && !path.contains(":") && !path.contains(".."))
        val started = android.os.SystemClock.elapsedRealtime()
        val response = try { http.get(path, headers).execute() } catch (error: IOException) { throw transportFailure(error) }
        if (!response.isSuccessful) {
            val error = try { response.errorBody()?.use { JSONObject(it.string()) } }
                catch (failure: IOException) { throw transportFailure(failure) }
            error?.let(observe)
            throw ApiFailure(error?.optString("code") ?: "DOWNLOAD_FAILED", error?.optString("message") ?: "下载失败，占用已保留")
        }
        try { (response.body() ?: throw ApiFailure("EMPTY_RESPONSE", "下载包为空，占用已保留")).use { body -> body.byteStream().use { input -> target.outputStream().use { output ->
            var size = 0L; val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break
                size += count; check(size <= 16 * 1024 * 1024) { "下载包过大" }; output.write(buffer, 0, count) }
        } } } } catch (error: IOException) { throw transportFailure(error) }
        android.util.Log.i("PdaTransport", "DOWNLOAD status=${response.code()} bytes=${target.length()} elapsedMs=${android.os.SystemClock.elapsedRealtime() - started}")
    }

    private fun readLimited(input: java.io.InputStream, maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break
            check(output.size() + count <= maximum) { "响应超过支持大小" }; output.write(buffer, 0, count) }
        return output.toByteArray()
    }

    private fun transportFailure(error: IOException): IOException = if (error is PdaTransportFailure) error else PdaTransportFailure(
        if (error is UnknownHostException) "无法解析服务器地址，请检查 Wi-Fi、DNS 或网络后重试；原请求记录已保留"
        else "网络连接或传输失败，请检查网络后重试；结果未知时请核对原请求", error)
}
