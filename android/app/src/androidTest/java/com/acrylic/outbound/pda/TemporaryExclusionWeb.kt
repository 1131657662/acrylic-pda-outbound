package com.acrylic.outbound.pda

import com.acrylic.outbound.pda.network.PdaNetwork
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Actual isolated supervisor Session/CSRF; command originals retained before the Android result query. */
internal class TemporaryExclusionWeb(private val input: JSONObject, private val directory: File) {
    private val cookies = mutableListOf<Cookie>()
    private val client = OkHttpClient.Builder().cookieJar(object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, values: List<Cookie>) {
            values.forEach { cookie -> cookies.removeAll { it.name == cookie.name }; cookies.add(cookie) }
        }
        override fun loadForRequest(url: HttpUrl) = cookies.filter { it.matches(url) }
    }).build()
    private val base = input.getString("baseUrl")
    private val network = PdaNetwork(base, client)
    private val warehouse = input.getString("warehouseId")

    fun login() {
        val csrf = network.request("auth/csrf").getJSONObject("data")
        val actor = input.getJSONObject("supervisor")
        val form = FormBody.Builder().add("username", actor.getString("username"))
            .add("password", actor.getString("password")).build()
        client.newCall(Request.Builder().url(base + "auth/web/login")
            .header(csrf.getString("headerName"), csrf.getString("token")).post(form).build()).execute().use {
            check(it.code == 200) { "Supervisor login HTTP ${it.code}" }
        }
    }

    fun confirm(submission: String) {
        login()
        command(submission, "DUPLICATE_EXCLUDE", "exclude")
        command(submission, "LINK", "link")
        command(submission, "DUPLICATE_EXCLUDE", "recheck")
        command(submission, "NORMAL_CONFIRM", "confirm")
    }

    private fun command(submission: String, type: String, label: String) {
        val facts = get("temporary-commands/facts", submission)
        val page = get("temporary-reviews/$submission/duplicate-candidates", null)
        val csrfEnvelope = network.request("auth/csrf"); val csrf = csrfEnvelope.getJSONObject("data")
        val body = JSONObject(facts.toString()).put("commandVersion", 1).put("commandType", type)
            .put("commandId", UUID.randomUUID().toString()).put("transportRecoveryEpoch", csrfEnvelope.getString("serverRecoveryEpoch"))
            .put("reason", if (type == "NORMAL_CONFIRM") "" else "隔离主管逐项核验原候选及完整计划")
        body.getJSONObject("expected").put("candidateContextSha256", page.getString("commandContextSha256"))
        if (type == "DUPLICATE_EXCLUDE") body.put("exclusion", exclusion(page))
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        File(directory, "$label-command.json").writeBytes(bytes)
        val request = Request.Builder().url(base + "temporary-commands")
            .header(csrf.getString("headerName"), csrf.getString("token"))
            .post(bytes.toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val original = checkNotNull(response.body).bytes()
            File(directory, "$label-response.json").writeBytes(original)
            check(response.code == 200) { "$label HTTP ${response.code}: ${original.toString(Charsets.UTF_8)}" }
            val receipt = JSONObject(original.toString(Charsets.UTF_8))
            check(receipt.getString("commandId") == body.getString("commandId"))
            check(!receipt.getJSONObject("capabilities").getBoolean("localDeletionAllowed"))
        }
    }

    private fun exclusion(page: JSONObject): JSONObject {
        check(page.isNull("nextCursor")) { "Fixture requires the complete candidate page" }
        val items = page.getJSONArray("items")
        val order = (0 until items.length()).map { items.getJSONObject(it).getJSONObject("order") }
            .single { it.getString("id") == input.getString("excludedId") }
        check(order.getString("status") == "CANCELLED")
        return JSONObject().put("orderId", order.getString("id")).put("revision", order.getInt("revision").toString())
            .put("rowVersion", order.getString("rowVersion")).put("basis", "CANCELLED")
            .put("evidence", "隔离原取消命令已生效，无领取、实装或占用；逐项核对后排除")
            .put("withdrawalRequestId", JSONObject.NULL)
    }

    private fun get(path: String, submission: String?): JSONObject {
        val query = "?warehouseId=$warehouse" + if (submission == null) "&limit=200" else "&submissionId=$submission"
        return network.request(path + query).getJSONObject("data")
    }
}
