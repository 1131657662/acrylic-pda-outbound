package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.update.UpdateGate
import org.json.JSONObject

/** Queues the receiving round's original technical-stop request after complete own-device evidence. */
class TemporaryHandoverStopRepository(private val db: PdaDatabase, private val connection: TemporaryConnection,
    private val enabled: Boolean = com.acrylic.outbound.pda.BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT) {
    @Synchronized fun stop(id: String, reason: String): TemporarySubmission = UpdateGate.business {
        check(enabled) { "临时交接停止开发入口未启用" }
        val local = checkNotNull(db.temporarySubmissions().get(id))
        check(JSONObject(local.payload).has("temporaryHandover")) { "此入口仅适用于独立交接新轮" }
        val uploads = TemporarySubmissionRepository(db, connection)
        if (local.state == "SYNCED" || db.temporarySuccess().evidence(id) != null) return@business uploads.upload(id)
        val value = reason.trim(); check(value.isNotEmpty() && value.length <= 500) { "停止须填写1～500字原因" }
        db.runInTransaction {
            val current = checkNotNull(db.temporarySubmissions().get(id))
            check(current.abortReason == null || current.abortReason == value) { "须恢复原交接停止请求" }
            TemporaryHandoverUploadValidation.verify(db, connection, current, JSONObject(current.payload))
            val request = checkNotNull(db.temporary().request(checkNotNull(db.temporary().byAuthorization(
                JSONObject(current.payload).getString("authorizationId"))).requestKey))
            TemporaryHandoverStopRequest(db).body(current, value, JSONObject(request.signingKeyJson))
            db.temporarySubmissions().abortIntent(id, value); db.temporarySubmissions().queue(id)
        }
        uploads.upload(id)
    }
}
