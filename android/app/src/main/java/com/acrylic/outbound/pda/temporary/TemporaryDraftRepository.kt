package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.update.UpdateGate
import com.acrylic.outbound.pda.work.WorkDevice
import java.util.concurrent.Callable

data class TemporaryPlanInput(val revision: Long, val vehicle: String, val container: String, val reason: String,
    val lines: List<Pair<String, String>>)

class TemporaryDraftRepository(private val store: TemporaryWorkStore) {
    private val db get() = store.db
    fun create(id: String): TemporaryDraft = UpdateGate.business {
        TemporaryVerifier.uuid(id)
        db.temporaryDrafts().get(id)?.let { store.load(id); return@business it }
        val auth = TemporaryAccess(db, store.connection).snapshot()
        val request = checkNotNull(db.temporary().current(auth.getString("userId"), auth.getString("deviceId"), auth.getString("warehouseId"))) { "请先联网准备临时资料" }
        val source = TemporaryCatalog.load(db, request)
        db.runInTransaction(Callable {
            TemporaryAccess(db, store.connection).request(request, auth)
            TemporaryVerifier.identity(source.manifest, auth); TemporaryVerifier.time(source.manifest, store.connection.now())
            store.requireNoOtherInput(id)
            check(db.temporary().current(request.userId, request.deviceId, request.warehouseId) == request) { "当前准备已变化，请重新新建草稿" }
            db.work().insertDevice(WorkDevice(request.deviceId))
            val number = checkNotNull(db.work().device(request.deviceId)).nextEventSeq.toString().padStart(6, '0')
            val label = "TMP-${request.deviceId.takeLast(4).uppercase()}-$number"
            db.temporaryDrafts().get(id) ?: TemporaryDraft(id, request.deviceId, request.userId, request.warehouseId,
                source.preparation.authorizationId, label, store.connection.now().toString()).also {
                db.temporaryDrafts().insert(it); store.event(it, null, "TEMP_DRAFT_CREATED")
                TemporaryAccess(db, store.connection).request(request, auth)
                TemporaryVerifier.time(source.manifest, store.connection.now())
            }
        }).also { store.load(id) }
    }
    fun save(id: String, input: TemporaryPlanInput): TemporaryDraft = UpdateGate.business {
        db.runInTransaction(Callable {
            val draft = store.check(id); store.requireNoOtherInput(id)
            check(draft.state == "DRAFT" && draft.revision == input.revision) { "草稿已变化或已经开工，请重新打开原单" }
            val vehicle = text(input.vehicle, 50, true); val container = text(input.container, 50, false)
            val reason = text(input.reason, 500, true); val lines = lines(id, input.lines)
            lines.fold(0L) { sum, line -> Math.addExact(sum, line.plannedCount) }
            val changed = draft.copy(vehicleNo = vehicle, containerNo = container, reason = reason, revision = Math.addExact(draft.revision, 1))
            val next = changed.copy(planSha256 = TemporaryPlanDigest.digest(changed, lines))
            db.temporaryDrafts().deleteLines(id); db.temporaryDrafts().insertLines(lines); db.temporaryDrafts().update(next)
            store.event(next, null, "TEMP_PLAN_SAVED", "revision=${next.revision}")
            store.check(id); next
        })
    }
    fun start(id: String, revision: Long): TemporaryDraft = UpdateGate.business {
        db.runInTransaction(Callable {
            val draft = store.check(id); store.requireNoOtherInput(id)
            check(draft.revision == revision) { "计划版本变化，请重新核对" }
            if (draft.state == "SCANNING") return@Callable draft
            check(draft.state == "DRAFT" && draft.vehicleNo.isNotBlank() && draft.reason.isNotBlank() && db.temporaryDrafts().lines(id).isNotEmpty()) { "请先保存完整车号、原因和产品计划" }
            draft.copy(state = "SCANNING").also {
                db.temporaryDrafts().update(it); store.event(it, null, "TEMP_WORK_STARTED"); store.check(id)
            }
        })
    }
    private fun lines(id: String, entries: List<Pair<String, String>>): List<TemporaryLine> {
        require(entries.isNotEmpty() && entries.size <= 500) { "请选择1～500个产品" }
        require(entries.map { it.first }.toSet().size == entries.size) { "同一产品代码不能重复" }
        return entries.map { (code, count) ->
            val product = checkNotNull(store.source(id).products[code]) { "产品不在原有效映射中" }
            require(count.matches(Regex("[1-9][0-9]{0,8}"))) { "每个产品计划须为1～999999999的正整数" }
            TemporaryLine(id, code, product.productId, product.model, product.name, count.toLong())
        }
    }
    private fun text(raw: String, maximum: Int, required: Boolean): String = raw.trim().also {
        require(it.length <= maximum && (!required || it.isNotBlank()) && it.none { c -> c.isISOControl() }) { "车号/柜号最长50字，原因1～500字，不能包含控制字符" }
    }
}
