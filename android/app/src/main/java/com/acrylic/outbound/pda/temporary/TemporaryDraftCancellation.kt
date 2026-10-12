package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.submission.SubmissionJson
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject
import java.util.concurrent.Callable

data class TemporaryCancelInput(val revision: Long, val reason: String)

/** Local zero-work cancellation preserves facts; it never releases a guard or cancels a server batch. */
class TemporaryDraftCancellation(private val store: TemporaryWorkStore) {
    private val db get() = store.db
    fun read(id: String): TemporaryDraft {
        store.load(id)
        return db.runInTransaction(Callable {
            store.check(id, false).also { row ->
                if (row.state == "CANCELLED") {
                    val event = db.temporaryScans().events(id).single { it.type == "TEMP_DRAFT_CANCELLED" }
                    check(event.captureId == null && event.originDeviceId == row.creatorDeviceId && event.operatorId == row.operatorId)
                    check(event.detail == detail(row, JSONObject(event.detail).getString("reason"))) { "取消审计与原单不一致，请保全原件" }
                    check(db.temporaryScans().total(id).count == 0L && db.temporaryScans().guardCount(id) == 0L && db.temporarySubmissions().forOrder(id) == null)
                }
            }
        })
    }
    fun cancel(id: String, input: TemporaryCancelInput): TemporaryDraft = UpdateGate.business {
        val reason = input.reason.trim()
        require(reason.isNotBlank() && reason.length <= 500 && reason.none(Char::isISOControl)) { "取消须填写1～500字原因，不能包含控制字符" }
        store.load(id)
        db.runInTransaction(Callable {
            val draft = store.check(id, false)
            check(draft.revision == input.revision) { "计划修订已变化，请重新核对原草稿" }
            requireZeroWork(draft)
            val detail = detail(draft, reason)
            val old = db.temporaryScans().events(id).filter { it.type == "TEMP_DRAFT_CANCELLED" }
            if (draft.state == "CANCELLED") {
                check(old.size == 1 && old.single().detail == detail) { "取消已经记录，请恢复原取消原因与修订" }
                return@Callable draft
            }
            check(draft.state in setOf("DRAFT", "SCANNING") && old.isEmpty()) { "本单须走主管停止/实物处置，原记录保留" }
            val cancelled = draft.copy(state = "CANCELLED")
            db.temporaryDrafts().update(cancelled)
            store.event(cancelled, null, "TEMP_DRAFT_CANCELLED", detail)
            store.check(id, false)
        })
    }
    private fun requireZeroWork(draft: TemporaryDraft) {
        val id = draft.clientOrderId
        check(draft.workRound == 1L && draft.authorizationHistoryJson == "[]" && !store.isHandover(id)) { "续作/换机原件不能普通取消" }
        check(db.temporarySubmissions().forOrder(id) == null && db.localE2aStops().get(id) == null && db.localDraftStops().get(id) == null) { "已有冻结/上传/停止原件，须查询原批次并走主管处置" }
        check(db.temporaryScans().events(id).none { it.type in setOf("FROZEN", "STOP", "LOCAL_EVIDENCE_STOP") }) { "已有停止或冻结历史，须保全原件" }
        check(db.work().unresolvedCount() == 0 && db.temporaryScans().unresolved() == 0) { "请先处理全部已接收/待确认/失败输入，不能普通取消" }
        check(db.temporaryScans().total(id).count == 0L && db.temporaryScans().guardCount(id) == 0L) { "本单仍有有效包或占用，须走主管实物处置" }
    }
    private fun detail(draft: TemporaryDraft, reason: String): String = SubmissionJson.canonical(JSONObject()
        .put("policy", "LOCAL_ZERO_WORK_CANCEL_V1").put("clientOrderId", draft.clientOrderId)
        .put("revision", draft.revision.toString()).put("planSha256", draft.planSha256).put("reason", reason)
        .put("validCount", "0").put("guardCount", "0").put("serverCancellation", false))
}
