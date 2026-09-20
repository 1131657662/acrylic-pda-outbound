package com.acrylic.outbound.pda

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

/** Register only the new synthetic renewal device through the real administrator API. */
internal class RenewalFixtureAdmin(private val fixture: JSONObject) : AutoCloseable {
    private val cookies = ArrayList<Cookie>()
    private val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, received: List<Cookie>) {
            cookies.removeAll { old -> received.any { it.name == old.name } }
            cookies.addAll(received)
        }
        override fun loadForRequest(url: HttpUrl) = cookies.filter { it.matches(url) }
    }).build()
    private var csrf = ""

    fun register(key: String): String {
        csrf = request("auth/csrf").getString("token")
        request("auth/web/login", FormBody.Builder().add("username", fixture.getString("username"))
            .add("password", fixture.getString("password")).build())
        csrf = request("auth/csrf").getString("token")
        val body = JSONObject().put("deviceCode", "RENEW-${UUID.randomUUID().toString().take(20)}")
            .put("name", "R05续期隔离模拟器").put("publicKey", key)
        return request("devices?warehouseId=${fixture.getString("warehouseId")}",
            body.toString().toRequestBody("application/json".toMediaType())).getString("deviceId")
    }

    private fun request(path: String, body: RequestBody? = null): JSONObject {
        val builder = Request.Builder().url(fixture.getString("baseUrl") + path)
        if (body != null) builder.post(body).header("X-CSRF-TOKEN", csrf)
            .header("X-Server-Recovery-Epoch", "1").header("Idempotency-Key", UUID.randomUUID().toString())
        return client.newCall(builder.build()).execute().use { response ->
            check(response.isSuccessful) { "Renewal fixture admin HTTP ${response.code}" }
            JSONObject(checkNotNull(response.body).string()).getJSONObject("data")
        }
    }

    override fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}
