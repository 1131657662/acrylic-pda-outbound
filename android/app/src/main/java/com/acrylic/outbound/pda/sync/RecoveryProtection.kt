package com.acrylic.outbound.pda.sync

import com.acrylic.outbound.pda.data.*
import org.json.JSONObject

/** A new login, a NORMAL response or a downloaded baseline must never clear reconciliation. */
class RecoveryProtection(private val db: PdaDatabase) {
    /** Shares Room's short commit boundary with sync; never wraps a network request. */
    internal fun <T> transaction(action: () -> T): T = db.runInTransaction(java.util.concurrent.Callable(action))
    fun observe(envelope: JSONObject, previousEpoch: String? = null) {
        val epoch = envelope.optString("serverRecoveryEpoch").takeIf { it.matches(Regex("[1-9][0-9]*")) } ?: return
        val serviceMode = envelope.optString("serviceMode")
        if (serviceMode.isBlank()) return
        db.runInTransaction {
            val old = db.sync().recovery()
            val changed = (old?.observedServerEpoch ?: previousEpoch)?.let { it != epoch } ?: false
            val historical = if (old == null) db.preparations().orders().any {
                JSONObject(it.assignmentJson).getString("serverRecoveryEpoch") != epoch
            } else false
            val blocked = old?.mode == "RECONCILING" || changed || historical || serviceMode != "NORMAL"
            db.sync().saveRecovery(LocalRecoveryControl(observedServerEpoch = epoch,
                mode = if (blocked) "RECONCILING" else "NORMAL", recoverySessionId = old?.recoverySessionId))
        }
    }
    fun requireNormal() {
        check(db.sync().recovery()?.mode != "RECONCILING") { "服务器正在恢复对账，暂停新增扫码和结单；原记录已保留，请联系主管" }
    }
}
