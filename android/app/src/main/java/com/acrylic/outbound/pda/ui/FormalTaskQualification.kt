package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.sync.SyncWorkGuard
import com.acrylic.outbound.pda.work.*
import org.json.JSONObject

/** Reads existing checks without opening work or freezing a batch. */
class FormalTaskQualification(private val app: PdaApplication) {
    private val db get() = app.database
    private val store = WorkStore(app.database, SessionWorkAccess(app, app.session))

    fun read(id: String): TaskQualificationReport {
        val prepared = store.prepared(id)
        val assignment = identity(prepared)
        val work = db.work().order(id)
        val materials = QualificationCheck.read("计划摘要及本机库存条数已核对") {
            check(db.preparations().count(id) == prepared.rowCount) { "本机资料不完整，请恢复原准备请求" }
            check(JwtVerifier.sha(prepared.planJson.toByteArray(Charsets.UTF_8)) == assignment.getString("planSha256")) {
                "本地计划摘要不一致，请保留原资料" }
        }
        return TaskQualificationReport("正式单 · ${work?.vehicleNo ?: "本机原资料"}",
            "本单扫码授权截止：${assignment.getString("expiresAt")}\n下载确认：${if (prepared.ackAt == null) "待重试原确认" else "已保存确认"}",
            materials, scanning(prepared, materials), closing(prepared, materials), upload(id))
    }

    private fun scanning(prepared: PreparedOrder, materials: QualificationCheck) =
        QualificationCheck.read("本机条件已核对；回原作业页恢复，逐包仍须校验") {
            val id = prepared.assignmentId
            check(materials.passed) { materials.detail }
            store.access.authorize(prepared)
            check(db.work().order(id)?.state == "SCANNING") { "本单未在扫码状态；冻结或旧交接原件不能继续扫码" }
            check(db.recalls().header(id) == null) { "本单已有召回或撤回，须核对原记录" }
            noOtherInput(id); store.verifyTotals(id)
            check(app.work.state.value.queued == 0 && app.work.state.value.blocked.isBlank()) { "接收队列或保存故障未处理，请先恢复原作业" }
        }

    private fun closing(prepared: PreparedOrder, materials: QualificationCheck) =
        QualificationCheck.read("本机前置已核对；实物及短装原因须在结单页确认") {
            val id = prepared.assignmentId; val work = db.work().order(id)
            check(materials.passed) { materials.detail }
            SyncWorkGuard(db).requireAllowed(prepared); app.session.now()
            check(db.recalls().header(id) == null) { "本单已有召回或撤回，请核对原记录" }
            check(db.submissions().forOrder(id) == null && work?.state == "SCANNING" && work.actualCount > 0) {
                "须有有效装车记录且尚未冻结；已冻结只可核对原批次" }
            check(db.captures().pendingCount(id) == 0 && db.captures().failure(id) == null && app.work.state.value.queued == 0) {
                "请先处理待确认、失败和接收队列输入" }
            store.verifyTotals(id)
        }

    private fun identity(prepared: PreparedOrder): JSONObject {
        store.access.identity(prepared)
        val assignment = JSONObject(prepared.assignmentJson)
        val claims = JwtVerifier.verify(assignment.getString("offlineToken"), JSONObject(prepared.signingKeyJson), "acrylic-pda-offline")
        val auth = app.session.requireSession()
        check(listOf("sub" to "userId", "deviceId" to "deviceId", "warehouseId" to "warehouseId")
            .all { claims.getString(it.first) == auth.getString(it.second) }) { "须原人员在原设备、原仓库查看" }
        check(claims.getString("assignmentId") == assignment.getString("id") && claims.getString("orderId") == prepared.orderId &&
            claims.getString("planSha256") == assignment.getString("planSha256") &&
            claims.getLong("exp") == java.time.Instant.parse(assignment.getString("expiresAt")).epochSecond) { "原签名资格与本单不一致" }
        return assignment
    }

    private fun noOtherInput(id: String) {
        check(db.captures().unresolvedOther(id) == null && db.temporaryScans().unresolved() == 0 && db.independentScans().unresolved() == 0) {
            "另一来源仍有未处理输入，请先恢复原作业" }
    }

    private fun upload(id: String): String {
        val local = db.submissions().forOrder(id) ?: return "尚未冻结，无原批次可上传"
        if (local.state in setOf("SYNCED", "ABORTED", "REJECTED")) return "原批次已终结，保留原回执，不重复上传"
        if (db.recalls().header(id) != null) return "召回或撤回待核对，原批次保留"
        if (JwtVerifier.sha(local.payload.toByteArray(Charsets.UTF_8)) != local.sha256) return "原冻结正文摘要未通过，停止并保留原件"
        return "原正文摘要已核对；须联网先查原ID，恢复原上传或查询回执。后台权限及结果尚未核对"
    }
}
