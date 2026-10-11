package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.network.PdaNetwork
import okhttp3.*
import org.json.JSONObject
import java.util.UUID

/** Actual Web login/CSRF for one synthetic isolated warehouse; no production identity. */
internal class FaultRestartWebClient(private val base: String, private val input: JSONObject) {
    private val cookies = mutableListOf<Cookie>()
    private val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, values: List<Cookie>) {
            values.forEach { cookie -> cookies.removeAll { it.name == cookie.name }; cookies.add(cookie) }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies.filter { it.matches(url) }
    }).build()
    private val network = PdaNetwork(base, client)
    fun login() {
        val csrf = network.request("auth/csrf").getJSONObject("data")
        val form = FormBody.Builder().add("username", input.getString("username")).add("password", input.getString("password"))
            .add("_csrf", csrf.getString("token")).build()
        client.newCall(Request.Builder().url(base + "auth/web/login").header(csrf.getString("headerName"), csrf.getString("token"))
            .post(form).build()).execute().use { check(it.code == 200) { "Isolated Web login failed: ${it.code}" } }
    }
    fun detail(): JSONObject = network.request("orders/${input.getString("orderId")}?warehouseId=${input.getString("warehouseId")}").getJSONObject("data")
    fun get(path: String): JSONObject = network.request("$path?warehouseId=${input.getString("warehouseId")}").getJSONObject("data")
    fun uploadStock(bytes: ByteArray): String {
        val envelope = network.request("auth/csrf"); val csrf = envelope.getJSONObject("data")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "sku.txt", RequestBody.create(null, bytes)).build()
        val request = Request.Builder().url(base + "inventory/imports?warehouseId=" + input.getString("warehouseId"))
            .header(csrf.getString("headerName"), csrf.getString("token"))
            .header("X-Server-Recovery-Epoch", envelope.getString("serverRecoveryEpoch"))
            .header("Idempotency-Key", UUID.randomUUID().toString()).post(body).build()
        return client.newCall(request).execute().use { response ->
            check(response.code == 200) { "Isolated stock import failed: ${response.code}" }
            JSONObject(checkNotNull(response.body).string()).getJSONObject("data").getString("id")
        }
    }
    fun post(path: String, body: JSONObject): JSONObject {
        val envelope = network.request("auth/csrf"); val csrf = envelope.getJSONObject("data")
        val headers = mapOf(csrf.getString("headerName") to csrf.getString("token"),
            "X-Server-Recovery-Epoch" to envelope.getString("serverRecoveryEpoch"), "Idempotency-Key" to UUID.randomUUID().toString())
        return network.request("$path?warehouseId=${input.getString("warehouseId")}", body, headers).getJSONObject("data")
    }
    fun withdrawAndPublish() {
        val order = input.getString("orderId")
        val receipt = post("orders/$order/change-requests", JSONObject().put("type", "FAULT_WITHDRAWAL")
            .put("expectedVersion", detail().getJSONObject("order").getString("rowVersion")).put("reason", "隔离模拟器：坏机无STOP，原事实保留"))
        check(receipt.getString("status") == "WITHDRAWN" && !receipt.getBoolean("originalDeviceStopReceived"))
        post("orders/$order/publish", JSONObject().put("expectedVersion", detail().getJSONObject("order").getString("rowVersion"))
            .put("expectedPolicyVersion", "1").put("reason", "隔离模拟器：新设备独立从头作业"))
    }
}
