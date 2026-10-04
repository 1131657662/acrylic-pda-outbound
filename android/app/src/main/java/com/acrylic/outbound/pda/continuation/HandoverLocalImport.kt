package com.acrylic.outbound.pda.continuation

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.preparation.LocalActivation
import com.acrylic.outbound.pda.preparation.VerifiedPackage
import com.acrylic.outbound.pda.work.*
import org.json.JSONObject

/** Imports source evidence atomically; the receiving order remains unable to scan or submit. */
internal class HandoverLocalImport(private val db: PdaDatabase) {
    fun install(stage: LocalHandoverStage, input: VerifiedPackage): WorkOrder {
        HandoverTargetAssignmentVerifier.verify(stage, input.assignment, input.session, input.now)
        HandoverRecoveryFacts.verify(stage)
        HandoverRecoveryAudit.verify(stage)
        val target = input.assignment.getString("id")
        val body = JSONObject(stage.recoveryBytes.toString(Charsets.UTF_8))
        val rows = HandoverRecoveryRows(body, target)
        val expected = HandoverImportedWork(input, body, rows)
        db.runInTransaction {
            val saved = checkNotNull(db.handoverStages().get(stage.caseId)) { "请先暂存交接资料" }
            check(sameHandoverEvidence(saved.copy(state = "STAGED"), stage.copy(state = "STAGED"))) {
                "交接暂存已变化，请保留资料"
            }
            check(saved.state in setOf("STAGED", "IMPORTED_FROZEN")) { "交接导入状态不受支持" }
            LocalActivation(db).activate(input)
            HandoverPreparationIntegrity(db).verify(input)
            if (saved.state == "STAGED") {
                persist(expected, rows)
                db.handoverStages().update(saved.copy(state = "IMPORTED_FROZEN"))
            }
            HandoverImportIntegrity(db).verify(expected, rows)
        }
        return checkNotNull(db.work().order(target))
    }

    private fun persist(work: HandoverImportedWork, rows: HandoverRecoveryRows) {
        check(db.work().order(work.order.assignmentId) == null) { "目标已有作业，禁止覆盖" }
        check(db.work().unresolvedCount() == 0 && db.temporaryScans().unresolved() == 0) { "本机仍有未处理输入" }
        db.work().insertOrder(work.order)
        db.work().insertLines(work.lines)
        rows.captures.forEach(db.captures()::insert)
        rows.scans.forEach(db.scans()::insert)
        rows.events.forEach(db.work()::insertEvent)
        rows.dispositions.forEach(db.dispositions()::insert)
        work.guards.forEach { guard ->
            check(db.temporaryScans().guard(guard.barcode) == null) { "原包与本机临时作业防重冲突" }
            db.scans().insertGuard(guard)
        }
        db.work().insertDevice(WorkDevice(work.targetDevice))
    }
}
