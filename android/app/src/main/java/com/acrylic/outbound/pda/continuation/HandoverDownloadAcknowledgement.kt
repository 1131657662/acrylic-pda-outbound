package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.preparation.PackageVerifier
import com.acrylic.outbound.pda.preparation.VerifiedPackage
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Replays confirmation from durable imported material without enabling receiving work. */
internal class HandoverDownloadAcknowledgement(private val db: PdaDatabase,
    private val connection: HandoverDownloadConnection) {
    fun acknowledge(caseId: String): PreparedOrder {
        val id = UUID.fromString(caseId).toString()
        val expected = JSONObject(checkNotNull(connection.current()) { "请先登录" }.toString())
        val prepared = verified(id, expected)
        if (prepared.ackAt != null) return prepared
        val ack = connection.call(HandoverDownloadConnection.Call(
            "pda/assignments/${prepared.assignmentId}/download-ack", expected, command(prepared)))
        check(ack.getString("assignmentId") == prepared.assignmentId &&
            ack.getString("bundleId") == prepared.bundleId) { "下载确认与本机交接资料不一致" }
        val confirmedAt = ack.getString("downloadAckAt")
        Instant.parse(confirmedAt)
        db.runInTransaction {
            SessionIdentity.requireSame(expected, connection.current())
            check(verified(id, expected) == prepared) { "本机交接资料已变化，请保留资料" }
            db.preparations().acknowledged(prepared.assignmentId, confirmedAt)
            SessionIdentity.requireSame(expected, connection.current())
        }
        return checkNotNull(db.preparations().order(prepared.assignmentId))
    }

    private fun verified(caseId: String, expected: JSONObject): PreparedOrder {
        RecoveryProtection(db).requireNormal()
        SessionIdentity.requireSame(expected, connection.current())
        val current = JSONObject(checkNotNull(connection.current()).toString())
        val stage = checkNotNull(db.handoverStages().get(caseId)) { "请先暂存交接资料" }
        check(stage.state == "IMPORTED_FROZEN") { "原交接记录尚未导入，不能确认下载" }
        val target = JSONObject(stage.manifestJson).getJSONObject("activationReceipt")
            .getJSONObject("businessDecision").getString("assignmentId")
        val prepared = checkNotNull(db.preparations().order(target)) { "本机交接资料缺失" }
        val assignment = HandoverTargetAssignmentVerifier.verify(stage, JSONObject(prepared.assignmentJson), current, connection.now())
        HandoverRecoveryFacts.verify(stage)
        HandoverRecoveryAudit.verify(stage)
        val receipt = JSONObject().put("bundleId", prepared.bundleId).put("manifest", prepared.manifestJson)
            .put("manifestSha256", prepared.manifestSha256).put("signedManifest", prepared.signedManifest)
        val key = current.getJSONObject("signingKey")
        val manifest = PackageVerifier.manifest(receipt, assignment, key, connection.now())
        check(prepared.orderId == assignment.getString("orderId") && prepared.operatorId == stage.targetUserId &&
            prepared.deviceId == stage.targetDeviceId && db.preparations().count(target) == prepared.rowCount)
        val plan = prepared.planJson.toByteArray(Charsets.UTF_8)
        val parts = manifest.getJSONArray("parts")
        val descriptor = (0 until parts.length()).map { parts.getJSONObject(it) }.single { it.getString("name") == "plan" }
        check(JwtVerifier.sha(plan) == descriptor.getString("sha256") && plan.size == descriptor.getInt("bytes"))
        val input = VerifiedPackage(assignment, receipt, key, current, mapOf("plan" to plan), connection.now())
        val body = JSONObject(stage.recoveryBytes.toString(Charsets.UTF_8))
        val rows = HandoverRecoveryRows(body, target)
        HandoverImportIntegrity(db).verify(HandoverImportedWork(input, body, rows), rows)
        return prepared
    }

    private fun command(prepared: PreparedOrder): JSONObject {
        val assignment = JSONObject(prepared.assignmentJson)
        return JSONObject().put("bundleId", prepared.bundleId).put("manifestSha256", prepared.manifestSha256)
            .put("assignmentEpoch", assignment.getString("epoch"))
            .put("inventorySnapshotId", assignment.getString("inventorySnapshotId"))
            .put("activatedAt", prepared.activatedAt)
    }
}
