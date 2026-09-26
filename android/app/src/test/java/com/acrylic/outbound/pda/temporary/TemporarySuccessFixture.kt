package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.io.File

/** Imports server synthetic frozen originals for consumer tests, not an Android-created business fixture. */
class TemporarySuccessFixture(root: File, val vector: JSONObject = vector()) : AutoCloseable {
    val work = TemporaryWorkFixture(root)
    val db get() = work.db
    val body = JSONObject(vector.getString("payloadUtf8"))
    val local = local(vector)
    val key = vector.getJSONObject("signingKey")
    val bytes = vector.getString("responseUtf8").toByteArray(Charsets.UTF_8)
    val repository get() = TemporarySuccessRepository(db)
    init {
        work.start(); work.scan(); TemporaryFreeze(work.store).freeze(work.id, "保留本地原件")
        val draft = checkNotNull(db.temporaryDrafts().get(work.id))
        db.temporaryDrafts().insert(draft.copy(clientOrderId = local.clientOrderId,
            creatorDeviceId = body.getString("freezeDeviceId"), state = "FROZEN"))
        db.temporarySubmissions().insert(local)
    }
    fun reopen() = work.reopen()
    override fun close() = work.close()
    companion object {
        fun vector(name: String = "normal"): JSONObject = JSONObject(File(
            checkNotNull(System.getProperty("pda.temporarySuccessFixture"))).readText()).getJSONObject(name)
        fun local(vector: JSONObject): TemporarySubmission {
            val payload = vector.getString("payloadUtf8"); val body = JSONObject(payload)
            return TemporarySubmission(body.getString("submissionId"), body.getJSONObject("source").getString("clientOrderId"),
                payload, JwtVerifier.sha(payload.toByteArray(Charsets.UTF_8)), "REVIEW_REQUIRED",
                vector.getJSONObject("pendingReceipt").toString(), abortReason = "迟到取消意图仍保留")
        }
    }
}
