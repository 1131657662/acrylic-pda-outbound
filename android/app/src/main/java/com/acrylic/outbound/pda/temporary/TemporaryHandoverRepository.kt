package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.concurrent.Callable

class TemporaryHandoverRepository(private val db: PdaDatabase, private val connection: TemporaryConnection) {
    private val access = TemporaryAccess(db, connection)

    fun stage(caseId: String): TemporaryHandoverStage = UpdateGate.business {
        val id = TemporaryVerifier.uuid(caseId)
        val expected = access.snapshot()
        val path = "pda/handovers/$id"
        val manifest = connection.original(TemporaryConnection.Original("$path/manifest", expected, maximumBytes = 2 * 1024 * 1024))
        check(manifest.isNotEmpty() && manifest.size <= 2 * 1024 * 1024)
        val bytes = connection.original(TemporaryConnection.Original("$path/recovery-package", expected, maximumBytes = 32 * 1024 * 1024))
        val row = verified(JSONObject(manifest.toString(Charsets.UTF_8)), bytes, expected)
        check(row.caseId == id) { "交接编号与原回执不一致" }
        db.runInTransaction {
            access.check(expected)
            val current = verified(JSONObject(row.manifestJson), row.payloadBytes, expected)
            val previous = db.temporaryHandoverStages().get(id)
            if (previous == null) db.temporaryHandoverStages().insert(current) else {
                checkSame(previous, current)
                verifySaved(previous, expected)
            }
            access.check(expected)
        }
        read(id)
    }

    fun read(caseId: String): TemporaryHandoverStage {
        val expected = access.snapshot()
        val id = TemporaryVerifier.uuid(caseId)
        return db.runInTransaction(Callable {
            val saved = checkNotNull(db.temporaryHandoverStages().get(id)) { "请先暂存临时交接资料" }
            verifySaved(saved, expected)
            access.check(expected)
            saved
        })
    }

    private fun verifySaved(row: TemporaryHandoverStage, expected: JSONObject) {
        val evidenceSession = JSONObject(expected.toString()).put("signingKey", JSONObject(row.signingKeyJson))
        checkSame(row, verified(JSONObject(row.manifestJson), row.payloadBytes, evidenceSession))
    }

    private fun verified(manifest: JSONObject, bytes: ByteArray, expected: JSONObject): TemporaryHandoverStage {
        access.check(expected)
        val receipts = TemporaryHandoverReceipts.verify(manifest, expected)
        val body = TemporaryHandoverPayload.verify(manifest, bytes, receipts)
        TemporaryHandoverQualification(db, connection).verify(receipts.qualification, body, expected)
        val context = receipts.stop.getJSONObject("caseContext")
        return TemporaryHandoverStage(manifest.getString("caseId"), context.getString("scopeId"),
            context.getString("submissionId"), context.getString("canonicalOrderId"),
            receipts.qualification.getString("authorizationId"), expected.getString("userId"),
            expected.getString("deviceId"), expected.getString("warehouseId"),
            context.getString("fromWorkRound").toLong(), context.getString("toWorkRound").toLong(),
            manifest.getString("inputHighWatermark").toLong(), receipts.qualification.getString("payloadSha256"),
            SubmissionJson.canonical(manifest), SubmissionJson.canonical(expected.getJSONObject("signingKey")), bytes.clone())
    }

    private fun checkSame(left: TemporaryHandoverStage, right: TemporaryHandoverStage) {
        check(left.copy(payloadBytes = right.payloadBytes) == right && left.payloadBytes.contentEquals(right.payloadBytes)) {
            "临时交接原文或暂存事实变化，请保留资料"
        }
    }
}
