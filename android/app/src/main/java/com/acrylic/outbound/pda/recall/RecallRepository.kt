package com.acrylic.outbound.pda.recall

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject

class RecallRepository(private val db: PdaDatabase, private val session: PdaSession, private val pause: (String) -> Unit) {
    @Synchronized fun check(assignment: String): LocalRecall? {
        val prepared = checkNotNull(db.preparations().order(assignment)); identity(prepared.operatorId, prepared.deviceId)
        val authority = db.continuations().current(assignment)?.authorizationJson ?: prepared.assignmentJson
        val authorityId = JSONObject(authority).getString("id")
        val response = session.call("pda/assignments/$authorityId/change-request")
        if (response.isNull("request")) return db.recalls().header(assignment)
        val request = response.getJSONObject("request")
        check(request.getString("assignmentId") == authorityId && request.getString("orderId") == prepared.orderId)
        pause(assignment)
        db.runInTransaction {
            if (db.recalls().header(assignment) == null) db.recalls().insert(LocalRecall(request.getString("changeRequestId"), assignment, request.toString()))
            db.recalls().request(request.getString("changeRequestId"), request.toString())
            db.work().recall(assignment)
            db.submissions().forOrder(assignment)?.let { db.submissions().progress(it.id, it.state, it.receipt, "已收到召回，请办理停止恢复", false) }
        }
        return db.recalls().header(assignment)
    }
    @Synchronized fun upload(assignment: String): LocalRecall {
        val local = checkNotNull(db.recalls().get(assignment)); if (local.state == "RECALLED") return local
        val prepared = checkNotNull(db.preparations().order(assignment)); identity(prepared.operatorId, prepared.deviceId)
        val payload = checkNotNull(local.payload) { "须先核对未保存记录并停止" }; val bytes = payload.toByteArray(Charsets.UTF_8)
        check(JwtVerifier.sha(bytes) == local.sha256) { "恢复正文损坏，请保留数据" }
        val body = JSONObject(payload); val epoch = body.getString("sourceRecoveryEpoch")
        val received = session.call("pda/recovery-packages", epoch = epoch, raw = bytes)
        check(received.getString("recoveryPackageId") == local.packageId && received.getString("payloadSha256") == local.sha256)
        val ack = JSONObject().put("recoveryPackageId", local.packageId).put("payloadSha256", local.sha256)
            .put("captureHighWatermark", body.getString("captureHighWatermark"))
            .put("auditThroughSeq", body.getJSONArray("auditCoverage").getJSONObject(0).getString("throughSeq"))
        val proof = session.call("pda/change-requests/${local.id}/stop-ack", ack, epoch)
        RecallVerifier.verify(local, proof, JSONObject(prepared.signingKeyJson))
        val stopped = local.copy(state = "RECALLED", proof = proof.toString())
        db.runInTransaction { db.recalls().update(stopped); db.work().recalled(assignment) }
        return stopped
    }
    private fun identity(user: String, device: String) {
        val current = session.requireSession()
        check(current.getString("userId") == user && current.getString("deviceId") == device) { "须由原人员在原设备办理停止恢复" }
    }
}
