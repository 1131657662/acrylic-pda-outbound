package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.security.TimeSample
import java.util.UUID
import java.util.concurrent.Callable

data class IndependentSkuReview(val imported: IndependentSkuImport, val preview: IndependentSkuPreview?,
    val error: String?, val decision: IndependentSkuDecision?, val activeImportId: String?) {
    val canActivate: Boolean get() = decision == null && preview != null && preview.errorRows == 0
}

/** This catalog grants no identity, scan permission, stock availability or business completion. */
class IndependentSkuRepository(private val db: PdaDatabase, private val sample: () -> TimeSample) {
    private val dao get() = db.independentSku()
    fun stage(original: ByteArray, name: String): IndependentSkuReview {
        require(original.size <= IndependentSkuParser.MAX_BYTES) { "文件不得超过2MiB" }
        val bytes = original.copyOf(); val sha = IndependentSkuBytes.hash(bytes); val time = sample()
        val imported = IndependentSkuImport(UUID.randomUUID().toString(), sha, displayName(name), time.wall, time.elapsed, time.boot)
        db.runInTransaction {
            if (dao.original(sha) == null) {
                dao.insertOriginal(IndependentSkuOriginal(sha, bytes.size, IndependentSkuParser.VERSION))
                dao.insertChunks((bytes.indices step IndependentSkuBytes.CHUNK_SIZE).map { start ->
                    IndependentSkuChunk(sha, start / IndependentSkuBytes.CHUNK_SIZE,
                        bytes.copyOfRange(start, minOf(start + IndependentSkuBytes.CHUNK_SIZE, bytes.size)))
                })
            } else check(IndependentSkuBytes.original(dao, sha).contentEquals(bytes)) { "原资料摘要冲突" }
            dao.insertImport(imported)
        }
        return review(imported.id)
    }
    fun review(id: String): IndependentSkuReview = db.runInTransaction(Callable {
        val imported = checkNotNull(dao.imported(id)) { "请选择已保存的资料原件" }
        val bytes = IndependentSkuBytes.original(dao, imported.sha256)
        val decision = dao.decision(id); val active = dao.active()?.importId
        try { IndependentSkuReview(imported, IndependentSkuParser.parse(bytes), null, decision, active) }
        catch (error: IndependentSkuException) {
            IndependentSkuReview(imported, null, "${error.code}：${error.message}", decision, active)
        }
    })
    fun latest(): IndependentSkuReview? = dao.latest()?.let { review(it.id) }
    fun history(before: Long = Long.MAX_VALUE): List<IndependentSkuHistory> = dao.history(before)
    fun active(): IndependentSkuReview? = dao.active()?.let { review(it.importId) }
    fun original(id: String): ByteArray = db.runInTransaction(Callable {
        IndependentSkuBytes.original(dao, checkNotNull(dao.imported(id)).sha256)
    })
    fun activate(id: String, duplicatesConfirmed: Boolean, expectedActiveId: String?): IndependentSkuReview {
        db.runInTransaction {
            val review = review(id)
            if (review.decision != null) {
                check(review.decision.outcome == "ACTIVATED" && review.decision.duplicatesConfirmed == duplicatesConfirmed &&
                    review.decision.previousImportId == expectedActiveId) { "该预检已有其他决定，请重新读取" }
                return@runInTransaction
            }
            check(review.canActivate) { "原资料存在错误，不能启用" }
            check(checkNotNull(review.preview).duplicateRows == 0 || duplicatesConfirmed) { "请明确确认完全重复行去重" }
            check(review.activeImportId == expectedActiveId) { "使用中的资料已变化，请重新预检确认" }
            dao.insertDecision(decision(id, "ACTIVATED", expectedActiveId, duplicatesConfirmed))
            dao.setActive(IndependentSkuActive(importId = id))
        }
        return review(id)
    }
    fun cancel(id: String): IndependentSkuReview {
        db.runInTransaction {
            checkNotNull(dao.imported(id))
            val previous = dao.decision(id)
            if (previous == null) dao.insertDecision(decision(id, "CANCELLED", dao.active()?.importId, false))
            else check(previous.outcome == "CANCELLED") { "该资料已经启用，不能用取消预检覆盖原决定" }
        }
        return review(id)
    }
    private fun decision(id: String, outcome: String, previous: String?, confirmed: Boolean): IndependentSkuDecision {
        val time = sample()
        return IndependentSkuDecision(id, outcome, previous, confirmed, time.wall, time.elapsed, time.boot)
    }
    private fun displayName(name: String) = name.filterNot { Character.isISOControl(it) }.take(160).ifBlank { "sku.txt" }
}
