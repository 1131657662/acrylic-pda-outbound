package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.network.SessionIdentity
import org.json.JSONObject
import java.util.UUID

class HandoverImportRepository(private val db: PdaDatabase, private val session: PdaSession) {
    fun stage(caseId: String): LocalHandoverStage {
        val id = UUID.fromString(caseId).toString()
        val expected = JSONObject(session.requireSession().toString())
        val epoch = expected.getString("serverRecoveryEpoch")
        val path = "pda/handovers/$id"
        val manifestBytes = session.original(PdaSession.Original("$path/manifest", null, epoch,
            maximumBytes = 2 * 1024 * 1024), expected)
        val original = session.original(PdaSession.Original("$path/recovery-package", null, epoch,
            maximumBytes = 32 * 1024 * 1024), expected)
        val row = HandoverPackageVerifier.verify(JSONObject(manifestBytes.toString(Charsets.UTF_8)), original, expected)
        check(row.caseId == id)
        SessionIdentity.requireSame(expected, session.saved())
        saveVerifiedHandoverStage(db, row, expected)
        SessionIdentity.requireSame(expected, session.saved())
        val saved = checkNotNull(db.handoverStages().get(id))
        check(sameHandoverEvidence(saved, HandoverPackageVerifier.verify(
            JSONObject(saved.manifestJson), saved.recoveryBytes, expected))) {
            "已暂存交接资料损坏，请保留资料"
        }
        return saved
    }
}

internal fun saveVerifiedHandoverStage(db: PdaDatabase, row: LocalHandoverStage, expected: JSONObject) {
    db.runInTransaction {
        val prior = db.handoverStages().get(row.caseId)
        if (prior == null) {
            db.handoverStages().insert(row)
        } else {
            check(sameHandoverEvidence(prior, HandoverPackageVerifier.verify(
                JSONObject(prior.manifestJson), prior.recoveryBytes, expected))) {
                "已暂存交接资料损坏，请保留资料"
            }
            if (!sameHandoverEvidence(prior, row)) {
                val oldStatus = JSONObject(prior.manifestJson).getString("status")
                val newStatus = JSONObject(row.manifestJson).getString("status")
                check(oldStatus == "TARGET_PREPARED" && newStatus == "ACTIVATED" &&
                    sameHandoverEvidence(prior.copy(manifestJson = row.manifestJson), row)) {
                    "原交接暂存与重试正文不同，请保留资料"
                }
                db.handoverStages().update(row)
            }
        }
    }
}

private fun sameHandoverEvidence(left: LocalHandoverStage, right: LocalHandoverStage): Boolean =
    left.copy(recoveryBytes = right.recoveryBytes) == right &&
        left.recoveryBytes.contentEquals(right.recoveryBytes)
