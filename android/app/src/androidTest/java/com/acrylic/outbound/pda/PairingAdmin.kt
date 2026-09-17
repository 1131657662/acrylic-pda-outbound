package com.acrylic.outbound.pda

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class PairingAdmin(private val fixture: JSONObject) {
    private val cookies = ArrayList<Cookie>()
    private val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this@PairingAdmin.cookies.removeAll { old -> cookies.any { it.name == old.name } }; this@PairingAdmin.cookies.addAll(cookies)
        }
        override fun loadForRequest(url: HttpUrl) = cookies.filter { it.matches(url) }
    }).build()
    private var csrf = ""
    init {
        csrf = request("auth/csrf").getString("token")
        request("auth/web/login", FormBody.Builder().add("username", fixture.getString("username")).add("password", fixture.getString("password")).build())
        csrf = request("auth/csrf").getString("token")
    }
    fun approve(code: String): JSONObject {
        val query = "?warehouseId=${fixture.getString("warehouseId")}"; val application = request("device-pairings$query&code=$code")
        return request("device-pairings/${application.getString("applicationId")}/approval$query", JSONObject()
            .put("deviceCode", "PAIR-${UUID.randomUUID().toString().take(20)}").put("name", "短码登记模拟器")
            .toString().toRequestBody("application/json".toMediaType()))
    }
    private fun request(path: String, body: RequestBody? = null): JSONObject {
        val builder = Request.Builder().url(fixture.getString("baseUrl") + path)
        if (body != null) builder.post(body).header("X-CSRF-TOKEN", csrf).header("X-Server-Recovery-Epoch", "1").header("Idempotency-Key", UUID.randomUUID().toString())
        return client.newCall(builder.build()).execute().use { response ->
            val result = JSONObject(response.body!!.string()); check(response.isSuccessful) { "Pairing admin request failed: ${result.optString("code")}" }
            result.getJSONObject("data")
        }
    }
}
