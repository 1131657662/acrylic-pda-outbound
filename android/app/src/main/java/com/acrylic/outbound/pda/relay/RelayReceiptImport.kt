package com.acrylic.outbound.pda.relay

import android.util.AtomicFile
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.submission.ReceiptVerifier
import com.acrylic.outbound.pda.temporary.*
import org.json.JSONObject
import java.io.File
import java.util.Base64

/** Applies historical signed facts only; grants no scanning, release, archive or deletion capability. */
class RelayReceiptImport(private val app: PdaApplication) {
    fun apply(bytes: ByteArray, target: RescueTarget, grant: RescueReadGrant): String {
        check(bytes.size in 1..262144) { "中转回执文件长度无效" }
        grant.recheck(); retain(bytes)
        RescueExportFiles(app).requireRelayOriginal(TemporarySuccessJson.parse(bytes), grant)
        val public = DeviceKeys().publicKey(false).also(app.session.settings::verifyKey)
        var state = ""
        app.database.runInTransaction {
            check(app.database.sync().recovery()?.mode != "RECONCILING") { "恢复待对账中；只保留原文件，不改变本机业务显示" }
            state = when (target.source) {
                "FORMAL" -> formal(bytes, target, grant, Base64.getDecoder().decode(public))
                "TEMP" -> temporary(bytes, target, grant, Base64.getDecoder().decode(public))
                else -> error("独立来源只接收证据，不能消费正式提交回执")
            }
            grant.recheck()
        }
        return when (state) {
            "SYNCED" -> "原成功回执已验签并保存；实物放行仍按原流程核对。"
            "REVIEW_REQUIRED" -> "原批次已接收，待主管独立核对；不可放行。"
            else -> "原批次回执已核验并保存，状态 $state；原记录和实物保护保留。"
        }
    }
    private fun formal(bytes: ByteArray, target: RescueTarget, grant: RescueReadGrant, key: ByteArray): String {
        val db = app.database; val local = checkNotNull(db.submissions().forOrder(target.localOrderId)) { "本单没有原冻结批次" }
        check(local.currentMarker == 1)
        val prepared = checkNotNull(db.preparations().order(local.assignmentId))
        val identity = RescueFormalSnapshot(db, RescueRows(db.openHelper.readableDatabase)).identity(prepared); grant.check(identity)
        val receipt = RelayReceiptVerifier.verify(bytes, identity, local.payload, key, JSONObject(prepared.signingKeyJson))
        val state = ReceiptVerifier.verify(local, receipt, JSONObject(prepared.signingKeyJson))
        RelayReceiptVerifier.requireForward(local.state, local.receipt, state, receipt)
        if (local.state in setOf("SYNCED", "ABORTED", "REJECTED")) return state
        db.submissions().progress(local.id, state, receipt.toString(), "", false); return state
    }
    private fun temporary(bytes: ByteArray, target: RescueTarget, grant: RescueReadGrant, key: ByteArray): String {
        val db = app.database; val local = checkNotNull(db.temporarySubmissions().forOrder(target.localOrderId)) { "本临时单没有原冻结批次" }
        val draft = checkNotNull(db.temporaryDrafts().get(local.clientOrderId)); val body = JSONObject(local.payload)
        val preparation = checkNotNull(db.temporary().byAuthorization(body.getString("authorizationId")))
        val request = checkNotNull(db.temporary().request(preparation.requestKey)); TemporaryAckVerifier.declaration(preparation, request)
        val identity = JSONObject().put("source", "TEMP").put("localOrderId", local.clientOrderId).put("deviceId", draft.creatorDeviceId)
            .put("operatorId", draft.operatorId).put("warehouseId", draft.warehouseId); grant.check(identity)
        val trustedKey = JSONObject(request.signingKeyJson)
        val receipt = RelayReceiptVerifier.verify(bytes, identity, local.payload, key, trustedKey)
        val state = if (receipt.getString("status") == "SUCCEEDED") success(local, receipt, trustedKey)
            else TemporarySubmissionReceipt.verify(local, receipt, trustedKey)
        RelayReceiptVerifier.requireForward(local.state, local.receipt, state, receipt)
        db.temporarySubmissions().progress(local.id, state, receipt.toString(), "", false); return state
    }
    private fun success(local: TemporarySubmission, receipt: JSONObject, key: JSONObject): String {
        val original = JSONObject().put("code", "OK").put("data", receipt).toString().toByteArray(Charsets.UTF_8)
        val repository = TemporarySuccessRepository(app.database); val hash = repository.retain(local.id, original)
        repository.verify(local.id, hash, key); return "SYNCED"
    }
    private fun retain(bytes: ByteArray) {
        val root = File(app.noBackupFilesDir, "original-relay-receipts").apply { check(isDirectory || mkdirs()) }
        val file = AtomicFile(File(root, "${RescueBundle.sha(bytes)}.json"))
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) }
        catch (error: Exception) { file.failWrite(output); throw error }
        check(file.openRead().use { it.readBytes() }.contentEquals(bytes)) { "中转原回执落盘回读不一致" }
    }
}
