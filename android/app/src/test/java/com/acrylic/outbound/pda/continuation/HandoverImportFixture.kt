package com.acrylic.outbound.pda.continuation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.preparation.VerifiedPackage
import com.acrylic.outbound.pda.recall.LocalRecall
import com.acrylic.outbound.pda.recall.RecoveryFreeze
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.work.WorkFixture
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.UUID

internal class HandoverImportFixture(directory: File, sourceWork: (WorkFixture) -> Unit = {
    it.scan(); it.scan("bad")
}) : AutoCloseable {
    val context: Context = ApplicationProvider.getApplicationContext()
    private val signing = HandoverImportSigning()
    val caseId = UUID.randomUUID().toString()
    val target = UUID.randomUUID().toString()
    private val device = UUID.randomUUID().toString()
    private val user = UUID.randomUUID().toString()
    val body: JSONObject
    val input: VerifiedPackage
    var db: PdaDatabase

    init {
        lateinit var original: VerifiedPackage
        lateinit var frozen: JSONObject
        WorkFixture(directory).use { source ->
            sourceWork(source)
            source.db.runInTransaction {
                source.db.recalls().insert(LocalRecall(UUID.randomUUID().toString(), source.assignment, "{}"))
                check(source.db.work().recall(source.assignment) == 1)
            }
            val freeze = RecoveryFreeze(source.store) { source.now }
            frozen = JSONObject(checkNotNull(freeze.freeze(source.assignment,
                freeze.fingerprint(freeze.unresolved(source.assignment))).payload))
            original = source.input
        }
        body = frozen
        input = targetInput(original)
        db = PdaDatabase.open(context)
        db.handoverStages().insert(stage())
    }

    fun stage(): LocalHandoverStage {
        val bytes = SubmissionJson.canonical(body).toByteArray()
        return HandoverPackageVerifier.verify(manifest(bytes), bytes, input.session)
    }

    fun install() = HandoverLocalImport(db).install(checkNotNull(db.handoverStages().get(caseId)), input)
    fun reopen() { db.close(); db = PdaDatabase.open(context) }
    override fun close() { db.close(); context.deleteDatabase("outbound-pda.db") }

    private fun targetInput(original: VerifiedPackage): VerifiedPackage {
        val session = JSONObject(original.session.toString()).put("deviceId", device).put("userId", user)
            .put("signingKey", signing.key)
        val assignment = JSONObject(original.assignment.toString()).put("id", target).put("deviceId", device)
            .put("operatorId", user).put("epoch", "2").put("startOrderScanSeq", next())
        val claims = JSONObject(String(Base64.getUrlDecoder().decode(original.assignment.getString("offlineToken").split('.')[1])))
            .put("assignmentId", target).put("deviceId", device).put("sub", user).put("assignmentEpoch", "2")
            .put("startOrderScanSeq", next())
        assignment.put("offlineToken", signing.jwt(claims))
        val receipt = JSONObject(original.receipt.toString())
        val manifest = JSONObject(receipt.getString("manifest")).put("assignmentId", target)
            .put("deviceId", device).put("operatorId", user).put("assignmentEpoch", "2")
        val text = manifest.toString()
        receipt.put("manifest", text).put("manifestSha256", JwtVerifier.sha(text.toByteArray()))
        receipt.put("signedManifest", signing.jwt(JSONObject().put("iss", "acrylic-outbound")
            .put("aud", "acrylic-pda-preparation").put("manifestSha256", receipt.getString("manifestSha256"))
            .put("assignmentId", target).put("exp", claims.getLong("exp"))))
        return original.copy(assignment = assignment, receipt = receipt, session = session, key = signing.key)
    }

    private fun manifest(bytes: ByteArray): JSONObject {
        val context = context()
        val ticket = JSONObject().put("caseId", caseId).put("targetDeviceId", device).put("targetUserId", user)
            .put("canonicalOrderId", body.getString("orderId")).put("inputHighWatermark", high()).put("issuedAt", input.now.toString())
        val digest = JwtVerifier.sha(SubmissionJson.canonical(ticket).toByteArray())
        val approve = decision("APPROVED").put("inputHighWatermark", high())
        val prepare = decision("TARGET_PREPARED").put("targetDeviceId", device).put("targetUserId", user)
            .put("preparedTicketSha256", digest)
        val activate = decision("ACTIVATED").put("assignmentId", target).put("assignmentEpoch", "2")
            .put("targetDeviceId", device).put("targetUserId", user).put("workRound", "2").put("startOrderScanSeq", next())
        return JSONObject().put("caseId", caseId).put("status", "ACTIVATED").put("recoveryPackageSha256", JwtVerifier.sha(bytes))
            .put("inputHighWatermark", high()).put("preparedTicket", ticket).put("preparedTicketSha256", digest)
            .put("stopReceipt", stop(context, bytes)).put("approvalReceipt", command(context, "APPROVE", approve))
            .put("preparationReceipt", command(context, "PREPARE", prepare))
            .put("activationReceipt", command(context, "ACTIVATE", activate))
    }

    private fun stop(context: JSONObject, bytes: ByteArray): JSONObject {
        val source = JSONObject(context.toString()).put("assignmentId", body.getString("assignmentId"))
            .put("orderId", body.getString("orderId")).put("deviceId", body.getString("freezeDeviceId"))
            .put("operatorId", body.getJSONArray("events").getJSONObject(0).getString("operatorId"))
            .put("sourceRecoveryEpoch", body.getString("sourceRecoveryEpoch")).put("workRound", body.getString("workRound"))
        val command = JSONObject().put("payloadSha256", JwtVerifier.sha(bytes))
            .put("recoveryPackageId", body.getString("recoveryPackageId"))
            .put("changeRequestId", body.getString("changeRequestId")).put("captureHighWatermark", high())
        return signing.receipt(JSONObject().put("status", "STOPPED").put("caseContext", context).put("source", source)
            .put("actor", JSONObject().put("deviceId", source.getString("deviceId")).put("principalId", source.getString("operatorId")))
            .put("originalCommand", JSONObject().put("caseId", caseId).put("command", command)), true)
    }

    private fun context() = JSONObject().put("caseId", caseId).put("warehouseId", input.session.getString("warehouseId"))
        .put("scopeId", UUID.randomUUID().toString()).put("submissionId", UUID.randomUUID().toString())
        .put("canonicalOrderId", body.getString("orderId")).put("sourceKind", "FORMAL")
        .put("fromWorkRound", "1").put("toWorkRound", "2")

    private fun command(context: JSONObject, type: String, decision: JSONObject) = signing.receipt(
        JSONObject().put("status", "RECORDED").put("commandType", type).put("caseContext", context)
            .put("businessDecision", decision).put("recordedAt", input.now.toString()))

    private fun decision(status: String) = JSONObject().put("caseId", caseId).put("status", status)
    private fun high() = body.getString("captureHighWatermark")
    private fun next() = Math.addExact(high().toLong(), 1).toString()
}
