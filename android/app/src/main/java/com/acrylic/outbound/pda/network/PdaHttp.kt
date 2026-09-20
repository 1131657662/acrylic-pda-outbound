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
import java.util.concurrent.TimeUnit

interface PdaHttp {
    @GET @Streaming fun get(@Url path: String, @HeaderMap headers: Map<String, String>): Call<ResponseBody>
    @POST fun post(@Url path: String, @HeaderMap headers: Map<String, String>, @Body body: RequestBody): Call<ResponseBody>
}

class ApiFailure(val code: String, message: String, val retryAfterSeconds: Long? = null,
    val httpStatus: Int? = null) : Exception(message)

class PdaNetwork(baseUrl: String, private val observe: (JSONObject) -> Unit = {}) {
    private val http = Retrofit.Builder().baseUrl(baseUrl)
        .client(OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build())
        .build().create(PdaHttp::class.java)

    fun request(path: String, body: JSONObject? = null, headers: Map<String, String> = emptyMap(), raw: ByteArray? = null): JSONObject {
        require(!path.startsWith('/') && !path.contains(":") && !path.contains(".."))
        val call = if (raw != null) http.post(path, headers, raw.toRequestBody("application/octet-stream".toMediaType()))
            else if (body == null) http.get(path, headers) else http.post(path, headers, body.toString().toRequestBody("application/json".toMediaType()))
        val response = call.execute()
        val source = response.body() ?: response.errorBody() ?: throw ApiFailure("EMPTY_RESPONSE", "服务器未返回内容，请重试原操作", httpStatus = response.code())
        val envelope = source.use {
            try { JSONObject(String(readLimited(it.byteStream(), 2 * 1024 * 1024), Charsets.UTF_8)) }
            catch (error: org.json.JSONException) { throw ApiFailure("INVALID_RESPONSE", "服务器响应格式不正确，请重试原操作", httpStatus = response.code()) }
        }
        observe(envelope)
        if (!response.isSuccessful || envelope.optString("code") != "OK") throw ApiFailure(envelope.optString("code"), envelope.optString("message", "请求失败"), response.headers()["Retry-After"]?.toLongOrNull(), response.code())
        if (envelope.getString("serviceMode") != "NORMAL")
            throw ApiFailure("SERVER_RECOVERY_REQUIRED", "服务器正在恢复，保留本地资料", httpStatus = 503)
        return envelope
    }

    fun download(path: String, target: File, headers: Map<String, String>) {
        require(!path.startsWith('/') && !path.contains(":") && !path.contains(".."))
        val response = http.get(path, headers).execute()
        if (!response.isSuccessful) {
            val error = response.errorBody()?.use { JSONObject(it.string()) }
            error?.let(observe)
            throw ApiFailure(error?.optString("code") ?: "DOWNLOAD_FAILED", error?.optString("message") ?: "下载失败，占用已保留")
        }
        response.body()!!.use { body -> body.byteStream().use { input -> target.outputStream().use { output ->
            var size = 0L; val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break
                size += count; check(size <= 16 * 1024 * 1024) { "下载包过大" }; output.write(buffer, 0, count) }
        } } }
    }

    private fun readLimited(input: java.io.InputStream, maximum: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break
            check(output.size() + count <= maximum) { "响应超过支持大小" }; output.write(buffer, 0, count) }
        return output.toByteArray()
    }
}
