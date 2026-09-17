package com.acrylic.outbound.pda

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class RecallAdmin(private val fixture: JSONObject) {
    private val cookies = ArrayList<Cookie>()
    private val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { this@RecallAdmin.cookies.removeAll { c -> cookies.any { it.name == c.name } }; this@RecallAdmin.cookies.addAll(cookies) }
        override fun loadForRequest(url: HttpUrl) = cookies.filter { it.matches(url) }
    }).build()
    private var csrf = ""
    init {
        csrf = request("auth/csrf").getString("token")
        request("auth/web/login", FormBody.Builder().add("username", fixture.getString("username")).add("password", fixture.getString("password")).build())
        csrf = request("auth/csrf").getString("token")
    }
    fun register(publicKey: String): String = request("devices?warehouseId=${fixture.getString("warehouseId")}", JSONObject()
        .put("deviceCode", "recall-${UUID.randomUUID().toString().take(20)}").put("name", "召回模拟器").put("publicKey", publicKey)
        .toString().toRequestBody("application/json".toMediaType())).getString("deviceId")
    fun recall(order: String): JSONObject {
        val path = "orders/$order"; val query = "?warehouseId=${fixture.getString("warehouseId")}"
        val version = request(path + query).getJSONObject("order").getString("rowVersion")
        return request("$path/change-requests$query", JSONObject().put("type", "RECALL").put("expectedVersion", version)
            .put("reason", "核对车辆，原机停扫并保留已装包").toString().toRequestBody("application/json".toMediaType()))
    }
    fun status(order: String) = request("orders/$order?warehouseId=${fixture.getString("warehouseId")}").getJSONObject("order").getString("status")
    fun review(submission: com.acrylic.outbound.pda.submission.LocalSubmission, decision: String) = request("submissions/${submission.id}/review", JSONObject()
        .put("decision", decision).put("payloadSha256", submission.sha256).put("reason", "模拟器实物核验：批准短装或退回补扫")
        .toString().toRequestBody("application/json".toMediaType()))
    fun continueWork(order: String, previous: String, disposition: String? = null): JSONObject {
        val query = "?warehouseId=${fixture.getString("warehouseId")}"
        val recall = request("orders/$order/change-requests$query").getJSONObject("request")
        val recovery = recall.getJSONObject("recovery")
        val detail = request("recovery-packages/${recovery.getString("recoveryPackageId")}")
        val actions = org.json.JSONArray(); val scans = detail.getJSONArray("scans")
        for (i in 0 until scans.length()) if (scans.getJSONObject(i).getString("status") == "ACTIVE") actions.put(JSONObject()
            .put("barcode", scans.getJSONObject(i).getString("barcode")).put("action", "RETAIN").put("expectedDispatchCycle", scans.getJSONObject(i).getString("expectedDispatchCycle")))
        if (disposition != null) {
            val scan = scans.getJSONObject(0)
            val approved = request("recovery-packages/${recovery.getString("recoveryPackageId")}/disposition-evidence$query", JSONObject()
                .put("expectedVersion", recall.getString("rowVersion")).put("payloadSha256", recovery.getString("payloadSha256"))
                .put("dispositions", org.json.JSONArray().put(JSONObject().put("captureId", scan.getString("captureId"))
                    .put("expectedDispatchCycle", scan.getString("expectedDispatchCycle")).put("action", disposition).put("physicalEvidence", "模拟器核验卸至 D-12，凭据 PDA-D-13")))
                .toString().toRequestBody("application/json".toMediaType()))
            actions.getJSONObject(0).put("action", disposition).put("approvedEvidenceId", approved.getJSONArray("evidence").getJSONObject(0).getString("approvedEvidenceId"))
        }
        val ticket = request("orders/$order/change-requests$query", JSONObject().put("type", "REOPEN_WORK").put("previousSubmissionId", previous)
            .put("expectedWorkRound", "1").put("expectedRevision", recall.getInt("revision")).put("recoveryPackageId", recovery.getString("recoveryPackageId"))
            .put("requestedDeviceId", recall.getString("deviceId")).put("reason", "原车全部保留并补扫").put("dispositions", actions)
            .toString().toRequestBody("application/json".toMediaType()))
        return request("change-requests/${ticket.getString("continuationId")}/resolve$query", JSONObject().put("action", "REOPEN_WORK")
            .put("expectedVersion", recall.getString("rowVersion")).put("retainedManifestSha256", ticket.getString("retainedManifestSha256"))
            .put("physicalEvidence", "主管已逐包核对原车货物，全部原装包保留").toString().toRequestBody("application/json".toMediaType()))
    }
    private fun request(path: String, body: RequestBody? = null): JSONObject {
        val builder = Request.Builder().url(fixture.getString("baseUrl") + path)
        if (body != null) builder.post(body).header("X-CSRF-TOKEN", csrf).header("X-Server-Recovery-Epoch", "1").header("Idempotency-Key", UUID.randomUUID().toString())
        return client.newCall(builder.build()).execute().use {
            val result = JSONObject(it.body!!.string()); check(it.isSuccessful) { "Recall fixture request failed: ${result.optString("code")}" }
            result.getJSONObject("data")
        }
    }
}
