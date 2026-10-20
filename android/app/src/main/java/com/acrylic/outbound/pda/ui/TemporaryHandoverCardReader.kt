package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.temporary.*
import org.json.JSONObject

/** Projects the separate TEMP receiving milestones without treating them as a FORMAL assignment. */
internal class TemporaryHandoverCardReader(private val app: PdaApplication) {
    private val actionsEnabled get() = BuildConfig.DEBUG && BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT
    fun read(id: String): HandoverCard = runCatching {
        val row = TemporaryHandoverRepository(app.database, TemporarySession(app.session)).read(id)
        val pending = app.temporaryHandoverConfirmations.read(id)
        val plan = JSONObject(row.payloadBytes.toString(Charsets.UTF_8)).getJSONObject("temporaryOrder")
        val details = "车号 ${plan.getString("vehicleNo")} · 柜号 ${plan.optString("containerNo")}\n原记录 ${row.inputHighWatermark} 条"
        val installed = app.database.temporaryHandoverActivations().get(id)
        val (action, label, status) = state(pending, installed)
        HandoverCard(id, installed?.newClientOrderId.orEmpty(), "临时作业交接", "$details\n$status", action, label,
            "$details\n交接编号 $id\n请逐包核对下方主管批准的保留清单。确认只记录接收事实，激活完成前保持停止。\n" +
                TemporaryHandoverConfirmationDisplay.lines(row), actionAvailable = action != "TEMP_DISABLED")
    }.getOrElse { HandoverCard(id, "", "临时作业交接", "${it.message ?: "临时交接需核对"}；原资料保留",
        if (actionsEnabled) "TEMP_INSTALL" else "TEMP_DISABLED", "核对接收资格与原资料",
        actionAvailable = actionsEnabled) }

    private fun state(pending: TemporaryHandoverConfirmation?, installed: TemporaryHandoverActivation?): Triple<String, String, String> {
        val enabled = actionsEnabled
        return when {
            !enabled -> Triple("TEMP_DISABLED", "临时交接操作已关闭", "原临时记录已保存；仅可只读核对，保持停止")
            installed != null -> {
                val store = app.temporaryStore(); val draft = store.load(installed.newClientOrderId)
                store.check(draft.clientOrderId, false)
                when (draft.state) {
                    "SCANNING" -> Triple("TEMP_WORK", "继续临时作业", "新轮已核验开启；原记录与保留占用继续保留")
                    "FROZEN" -> Triple("TEMP_SUBMISSION", "查看临时提交与回执", "新轮已冻结，请核对提交结果")
                    "HANDOVER_BLOCKED" -> Triple("TEMP_START", "确认新资料并开启本轮", "激活原件与新资格已保存；资料下载确认未完成，保持停止")
                    else -> Triple("TEMP_INSTALL", "核对原激活结果", "本单已经停止，请保留原资料核对")
                }
            }
            pending?.state == "CONFIRMED" -> Triple("TEMP_INSTALL", "核对并保存激活结果", "接收确认已保存；等待主管独立激活，保持停止")
            pending != null -> Triple("TEMP_CONFIRM", "恢复原接收确认", "${pending.error.ifBlank { "接收确认结果待核对" }}；原请求保留")
            else -> Triple("TEMP_CONFIRM", "核对实物并确认接收", "原临时记录已保存，尚未确认实物和资料")
        }
    }
}
