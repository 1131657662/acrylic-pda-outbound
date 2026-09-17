package com.acrylic.outbound.pda.submission

import com.acrylic.outbound.pda.network.PdaSession
import org.json.JSONObject

class SubmissionTransport(private val session: PdaSession) {
    fun send(local: LocalSubmission): JSONObject {
        val body = JSONObject(local.payload); val epoch = body.getString("sourceRecoveryEpoch")
        val bytes = local.payload.toByteArray(Charsets.UTF_8)
        if (bytes.size <= 1024 * 1024) return session.call("pda/orders/${body.getJSONObject("source").getString("sourceOrderId")}/submissions", body, epoch)
        val control = JSONObject().put("source", body.getJSONObject("source")).put("assignmentId", local.assignmentId)
            .put("freezeDeviceId", body.getString("freezeDeviceId")).put("workRound", body.getString("workRound"))
            .put("sourceRecoveryEpoch", epoch).put("payloadSha256", local.sha256).put("byteCount", bytes.size.toString())
            .put("chunkCount", ((bytes.size + 262143) / 262144).toString())
        val base = "pda/submissions/${local.id}"
        session.call("$base/upload", control, epoch)
        var offset = 0; var index = 0
        while (offset < bytes.size) {
            val end = minOf(offset + 262144, bytes.size)
            session.call("$base/chunks/$index", epoch = epoch, raw = bytes.copyOfRange(offset, end)); offset = end; index++
        }
        return session.call("$base/finalize", JSONObject(), epoch)
    }
}
