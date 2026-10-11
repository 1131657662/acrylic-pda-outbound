package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.update.UpdateGate
import com.acrylic.outbound.pda.security.JwtVerifier
import org.json.JSONObject
import java.util.concurrent.Callable

/** Durable evidence only: no ordinary preparation, confirmation, activation, guard or work is created. */
class TemporaryRecoveredHandoverRepository(private val db: PdaDatabase, private val connection: TemporaryConnection,
    development: Boolean = BuildConfig.RECOVERED_HANDOVER_DEVELOPMENT) {
    private val access = TemporaryRecoveredHandoverAccess(db, connection, development)
    fun stage(caseId: String): TemporaryRecoveredHandoverStage = UpdateGate.business {
        val id = TemporaryVerifier.uuid(caseId); val expected = access.snapshot(); val path = "pda/handovers/$id"
        val manifest = connection.original(TemporaryConnection.Original("$path/manifest", expected, maximumBytes = 2 * 1024 * 1024))
        val payload = connection.original(TemporaryConnection.Original("$path/recovery-package", expected, maximumBytes = 32 * 1024 * 1024))
        val original = TemporaryRecoveredHandoverVerifier.Originals(manifest.clone(), payload.clone())
        val row = verified(original, expected); check(row.caseId == id) { "接收案件与原回执不一致" }
        db.runInTransaction {
            access.check(expected); check(verified(original, expected) == row)
            val previous = db.temporaryRecoveredHandoverStages().get(id)
            if (previous == null) {
                db.temporaryRecoveredHandoverStages().insert(row)
                db.temporaryRecoveredHandoverChunks().save(id, "MANIFEST", original.manifest)
                db.temporaryRecoveredHandoverChunks().save(id, "PAYLOAD", original.payload)
            } else check(previous == row) { "原暂存案件、资格或会话变化，请保留资料" }
            checkSaved(row, expected); access.check(expected)
        }
        read(id)
    }
    fun read(caseId: String): TemporaryRecoveredHandoverStage {
        val expected = access.snapshot(); val id = TemporaryVerifier.uuid(caseId)
        return db.runInTransaction(Callable {
            val row = checkNotNull(db.temporaryRecoveredHandoverStages().get(id)) { "请先保存恢复交接资料" }
            checkSaved(row, expected); access.check(expected); row
        })
    }
    private fun checkSaved(row: TemporaryRecoveredHandoverStage, expected: JSONObject) {
        check(row.manifestLength in 1..2 * 1024 * 1024 && row.payloadLength in 1..32 * 1024 * 1024)
        val original = TemporaryRecoveredHandoverVerifier.Originals(
            db.temporaryRecoveredHandoverChunks().read(row.caseId, "MANIFEST", row.manifestLength),
            db.temporaryRecoveredHandoverChunks().read(row.caseId, "PAYLOAD", row.payloadLength))
        check(verified(original, expected) == row) { "恢复交接原件或身份绑定变化，请保留资料" }
    }
    private fun verified(original: TemporaryRecoveredHandoverVerifier.Originals, expected: JSONObject): TemporaryRecoveredHandoverStage {
        access.check(expected)
        val qualification = JSONObject(original.manifest.toString(Charsets.UTF_8)).getJSONObject("preparedTicket")
            .getJSONObject("temporaryQualification")
        check(qualification.getJSONObject("recipient").getString("keySha256") ==
            JwtVerifier.sha(connection.localDraftStopKey().toByteArray(Charsets.UTF_8))) { "接收设备持钥绑定不一致，请保留原资料" }
        return TemporaryRecoveredHandoverVerifier.verify(original, expected, connection.now())
    }
}
