package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.ApiFailure
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.json.JSONObject

/** Retains each possible terminal response before parsing or trusting its product state. */
internal class TemporarySubmissionResponses(private val db: PdaDatabase, private val connection: TemporaryConnection,
    private val expected: JSONObject, private val local: TemporarySubmission) {
    data class Reply(val responseSha256: String, val data: JSONObject)

    fun receive(path: String, body: JSONObject? = null): Reply {
        val bytes = connection.original(TemporaryConnection.Original(path, expected,
            body?.let { SubmissionJson.canonical(it).toByteArray(Charsets.UTF_8) },
            JSONObject(local.payload).getString("sourceRecoveryEpoch")))
        val hash = TemporarySuccessRepository(db).retain(local.id, bytes)
        val envelope = try { TemporarySuccessJson.parse(bytes) }
            catch (error: java.io.IOException) { throw IllegalStateException("原响应格式无效，原件已保留", error) }
        RecoveryProtection(db).observe(envelope, expected.getString("serverRecoveryEpoch"))
        check(envelope.get("code") == "OK") { "服务器未返回有效原批次响应" }
        if (envelope.getString("serviceMode") != "NORMAL" ||
            envelope.getString("serverRecoveryEpoch") != expected.getString("serverRecoveryEpoch"))
            throw ApiFailure("SERVER_RECOVERY_REQUIRED", "服务器正在恢复对账，原响应已保留")
        RecoveryProtection(db).requireNormal()
        return Reply(hash, envelope.getJSONObject("data"))
    }
}
