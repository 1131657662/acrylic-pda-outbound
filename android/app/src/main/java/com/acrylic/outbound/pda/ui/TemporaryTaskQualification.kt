package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.temporary.*

/** A newer preparation never lends this task a new expiry. */
class TemporaryTaskQualification(private val app: PdaApplication) {
    private val db get() = app.database

    fun read(id: String): TaskQualificationReport {
        val draft = checkNotNull(db.temporaryDrafts().get(id)) { "原临时单不存在" }
        identity(draft)
        val prepared = checkNotNull(db.temporary().byAuthorization(draft.authorizationId)) { "原临时资料尚未完整保存" }
        val request = checkNotNull(db.temporary().request(prepared.requestKey)) { "原准备请求缺失" }
        val catalog = TemporaryCatalog.load(db, request)
        val store = app.temporaryStore()
        val materials = QualificationCheck.read("原签名、资料文件及本单计划已核对") { store.load(id); store.check(id, false) }
        val report = TaskQualificationReport("临时单 · ${draft.vehicleNo.ifBlank { "未填车号" }} · ${draft.temporaryNo}",
            "本单扫码授权截止：${catalog.manifest.getString("expiresAt")}\n下载确认：${if (prepared.ackJson == null) "待重试原确认" else "原签名已核对"}",
            materials, scanning(store, draft, materials), closing(store, draft, materials), upload(id))
        identity(draft)
        return report
    }

    private fun scanning(store: TemporaryWorkStore, draft: TemporaryDraft, materials: QualificationCheck) =
        QualificationCheck.read("本机条件已核对；回原作业页恢复，逐包仍须校验") {
            val id = draft.clientOrderId
            check(materials.passed) { materials.detail }
            store.check(id); store.requireNoOtherInput(id)
            check(store.canScan(draft)) { "本单未在允许扫码状态；旧交接原件不能继续扫码" }
            check(app.temporaryWork.state.value.queued == 0 && app.temporaryWork.state.value.blocked.isBlank()) {
                "接收队列或保存故障未处理，请先恢复原作业" }
        }

    private fun closing(store: TemporaryWorkStore, draft: TemporaryDraft, materials: QualificationCheck) =
        QualificationCheck.read("本机前置已核对；实物及短装原因须在结单页确认") {
            val id = draft.clientOrderId
            check(materials.passed) { materials.detail }
            store.check(id, false); store.requireNoOtherInput(id); app.session.now()
            check(db.temporarySubmissions().forOrder(id) == null && store.canScan(draft) && store.total(id).count > 0) {
                "须有有效装车记录且尚未冻结；已冻结只可核对原批次" }
            check(db.temporaryScans().pending(id) == null && db.temporaryScans().failure(id) == null && app.temporaryWork.state.value.queued == 0) {
                "请先处理待确认、失败和接收队列输入" }
        }

    private fun identity(draft: TemporaryDraft) {
        val auth = app.session.requireSession()
        check(auth.getString("deviceId") == draft.creatorDeviceId && auth.getString("userId") == draft.operatorId &&
            auth.getString("warehouseId") == draft.warehouseId) { "须原人员在原设备、原仓库查看" }
    }

    private fun upload(id: String): String {
        val local = db.temporarySubmissions().forOrder(id) ?: return "尚未冻结，无原批次可上传"
        if (local.state in setOf("SYNCED", "ABORTED", "REJECTED")) return "原批次已终结，保留原回执，不重复上传"
        if (JwtVerifier.sha(local.payload.toByteArray(Charsets.UTF_8)) != local.sha256) return "原冻结正文摘要未通过，停止并保留原件"
        return "原正文摘要已核对；须联网先查原ID，恢复原上传或查询独立确认。后台权限及结果尚未核对"
    }
}
