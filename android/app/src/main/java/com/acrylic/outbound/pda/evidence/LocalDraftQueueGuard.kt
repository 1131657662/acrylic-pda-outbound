package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.PdaCapabilities
import org.json.JSONObject

/** Session changes durably revoke automatic transfer; only explicit preparation can enable it again. */
class LocalDraftQueueGuard(private val db: PdaDatabase) {
    fun sessionChanged(previous: JSONObject?, current: JSONObject?) {
        check(db.inTransaction())
        val fields = listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "localSessionId", "userAuthVersion", "deviceVersion")
        if (current == null || previous == null || fields.any { previous.optString(it) != current.optString(it) })
            db.localDraftQueue().pauseAll("登录身份或授权已变化，请在原身份下核对并明确选择补传原件")
    }

    fun reconcile(current: JSONObject?) = db.runInTransaction {
        val dao = db.localDraftQueue(); var after = ""
        while (true) {
            val rows = dao.enabled(after); if (rows.isEmpty()) break
            rows.filterNot { matches(it.clientOrderId, current) }.forEach {
                dao.update(it.copy(state = "ACTION_REQUIRED", autoRetry = false,
                    error = "原身份、仓库、设备或授权不可用，自动补传已暂停，请保留原资料"))
            }
            after = rows.last().clientOrderId
        }
    }

    private fun matches(id: String, current: JSONObject?): Boolean = runCatching {
        checkNotNull(current); check(!current.has("blockedReason"))
        check(PdaCapabilities.temporary(current) && PdaCapabilities.recovery(current))
        val draft = checkNotNull(db.temporaryDrafts().get(id))
        check(draft.state == "EVIDENCE_STOPPED")
        val preparation = checkNotNull(db.temporary().byAuthorization(draft.authorizationId))
        val request = checkNotNull(db.temporary().request(preparation.requestKey))
        val fields = mapOf("deviceId" to draft.creatorDeviceId, "userId" to draft.operatorId,
            "warehouseId" to draft.warehouseId, "serverRecoveryEpoch" to request.recoveryEpoch,
            "userAuthVersion" to request.userAuthVersion, "deviceVersion" to request.deviceVersion)
        check(fields.all { (field, value) -> current.optString(field) == value })
    }.isSuccess
}
