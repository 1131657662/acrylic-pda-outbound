package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.TrustedTime
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Fixed catalog and original authorization; independent sequences never touch work_device. */
internal class IndependentScanStore(val db: PdaDatabase, private val grant: IndependentCaptureGrant,
    val environment: IndependentScanEnvironment) {
    private var original: IndependentTask? = null
    private var rows: Map<String, IndependentSkuRow> = emptyMap()
    val dao get() = db.independentScans()
    fun open(id: String) {
        val task = owner(id); authorize(task)
        val review = IndependentSkuRepository(db, environment.sample).review(task.importId)
        check(review.imported.sha256 == task.skuSha256); IndependentTaskCatalog.products(review)
        rows = checkNotNull(review.preview).rows.filter { it.errorCode == null }.associateBy { checkNotNull(it.parsedFields).barcode }
        original = task; verifyTotals(id)
    }
    fun owner(id: String): IndependentTask = checkNotNull(db.independentTasks().task(id)).also { environment.actor().requireOwner(it) }
    fun check(id: String): IndependentTask {
        val task = owner(id)
        check(task == original) { "请先重新核验原独立任务和资料" }
        authorize(task)
        check(db.independentTasks().state(id)?.state in setOf("PLANNED", "SCANNING")) { "原独立任务已经封存，请保留原件" }
        return task
    }
    fun authorize(task: IndependentTask): Instant {
        val current = grant.snapshot()
        val fixed = IndependentCredentialProof.verify(task.signedCredential, JSONObject(task.trustedKeyJson))
        IndependentCredentialSnapshot(fixed, task.trustedKeyJson, task.timeAnchorJson, task.localSessionId).requireSame(current)
        check(current.proof.originalToken == task.signedCredential) { "原任务采集凭据已替换；不能续期或替换原授权" }
        return TrustedTime.now(JSONObject(task.timeAnchorJson), environment.sample()).also { fixed.at(it) }
    }
    fun row(barcode: String): IndependentSkuRow? = rows[barcode]
    fun requireNoOtherInput(id: String) {
        check(db.work().unresolvedCount() == 0 && db.temporaryScans().unresolved() == 0 && dao.unresolved(id) == 0) {
            "另一来源或独立任务仍有未处理输入，请先恢复原作业"
        }
    }
    fun verifyTotals(id: String) {
        val state = checkNotNull(db.independentTasks().state(id)); val total = dao.total(id)
        check(state.actualCount == total.count && state.weightGrams == total.grams && dao.missingGuards(id) == 0 && dao.invalidGuards(id) == 0) {
            "独立原明细、有效占用与汇总不一致，请停扫保全"
        }
        val plans = db.independentTasks().lines(id).associateBy { it.deliveryCode }
        check(dao.products(id).all { it.count <= (plans[it.deliveryCode]?.plannedCount ?: -1) })
    }
    fun event(id: String, type: String, detail: JSONObject = JSONObject()) {
        check(db.inTransaction())
        val state = checkNotNull(db.independentTasks().state(id)); val time = environment.sample()
        db.independentTasks().insertEvent(IndependentTaskEvent(UUID.randomUUID().toString(), id, state.nextEventSeq,
            type, detail.toString(), time.wall, time.elapsed, time.boot))
        db.independentTasks().updateState(state.copy(nextEventSeq = Math.addExact(state.nextEventSeq, 1)))
    }
}
