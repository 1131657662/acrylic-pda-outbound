package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.PdaSession
import com.acrylic.outbound.pda.network.SessionIdentity
import com.acrylic.outbound.pda.preparation.VerifiedPackage
import com.acrylic.outbound.pda.work.WorkOrder
import org.json.JSONObject
import java.io.File
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
        check(sameHandoverEvidence(saved.copy(state = "STAGED"), HandoverPackageVerifier.verify(
            JSONObject(saved.manifestJson), saved.recoveryBytes, expected))) {
            "已暂存交接资料损坏，请保留资料"
        }
        return saved
    }

    fun assignment(caseId: String): JSONObject {
        val id = UUID.fromString(caseId).toString()
        val expected = JSONObject(session.requireSession().toString())
        val saved = checkNotNull(db.handoverStages().get(id)) { "请先暂存交接资料" }
        val result = session.call("pda/handovers/$id/assignment",
            epoch = expected.getString("serverRecoveryEpoch"), expected = expected)
        SessionIdentity.requireSame(expected, session.saved())
        val verified = HandoverTargetAssignmentVerifier.verify(saved, result, expected, session.now())
        HandoverRecoveryFacts.verify(saved)
        HandoverRecoveryAudit.verify(saved)
        SessionIdentity.requireSame(expected, session.saved())
        return verified
    }

    fun downloadAndImport(caseId: String, cacheDirectory: File, progress: (String) -> Unit): WorkOrder =
        HandoverPreparationDownload(db, HandoverDownloadSession(session)).import(caseId, cacheDirectory, progress)

    fun acknowledgeDownload(caseId: String) =
        HandoverDownloadAcknowledgement(db, HandoverDownloadSession(session)).acknowledge(caseId)

    fun importFrozen(caseId: String, preparation: VerifiedPackage): WorkOrder {
        val id = UUID.fromString(caseId).toString()
        val expected = JSONObject(session.requireSession().toString())
        SessionIdentity.requireSame(preparation.session, expected)
        lateinit var imported: WorkOrder
        db.runInTransaction {
            val saved = checkNotNull(db.handoverStages().get(id)) { "请先暂存交接资料" }
            SessionIdentity.requireSame(expected, session.saved())
            val input = preparation.copy(session = expected, key = expected.getJSONObject("signingKey"), now = session.now())
            imported = HandoverLocalImport(db).install(saved, input)
            SessionIdentity.requireSame(expected, session.saved())
        }
        return imported
    }
}

internal fun saveVerifiedHandoverStage(db: PdaDatabase, row: LocalHandoverStage, expected: JSONObject) {
    db.runInTransaction {
        val prior = db.handoverStages().get(row.caseId)
        if (prior == null) {
            db.handoverStages().insert(row)
        } else {
            val evidence = prior.copy(state = "STAGED")
            check(sameHandoverEvidence(evidence, HandoverPackageVerifier.verify(
                JSONObject(prior.manifestJson), prior.recoveryBytes, expected))) {
                "已暂存交接资料损坏，请保留资料"
            }
            if (!sameHandoverEvidence(evidence, row)) {
                val oldStatus = JSONObject(prior.manifestJson).getString("status")
                val newStatus = JSONObject(row.manifestJson).getString("status")
                check(prior.state == "STAGED" && oldStatus == "TARGET_PREPARED" && newStatus == "ACTIVATED" &&
                    sameHandoverEvidence(evidence.copy(manifestJson = row.manifestJson), row)) {
                    "原交接暂存与重试正文不同，请保留资料"
                }
                db.handoverStages().update(row)
            }
        }
    }
}

internal fun sameHandoverEvidence(left: LocalHandoverStage, right: LocalHandoverStage): Boolean =
    left.copy(recoveryBytes = right.recoveryBytes) == right &&
        left.recoveryBytes.contentEquals(right.recoveryBytes)
