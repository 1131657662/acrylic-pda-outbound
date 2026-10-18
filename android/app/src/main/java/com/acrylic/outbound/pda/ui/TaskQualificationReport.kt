package com.acrylic.outbound.pda.ui

import kotlinx.coroutines.CancellationException

/** A local observation, never an authorization or a server result. */
data class QualificationCheck(val summary: String, val passed: Boolean, val detail: String = "") {
    companion object {
        fun read(success: String, action: () -> Unit): QualificationCheck = try {
            action(); QualificationCheck(success, true)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            QualificationCheck(blockedSummary(error.message.orEmpty()), false, error.message ?: "原因尚待核对，请保留原资料")
        }

        private fun blockedSummary(reason: String): String = "未通过 · " + when {
            reason.contains("重启") || reason.contains("校时") || reason.contains("时间发生变化") -> "须联网核对时间，原记录可保全"
            reason.contains("到期") || reason.contains("未生效") -> "原扫码授权未生效或已到期，请联网核对原资格"
            reason.contains("恢复对账") || reason.contains("恢复代次") -> "恢复待核对，请保持停止并联系主管"
            reason.contains("输入") || reason.contains("队列") || reason.contains("保存故障") -> "请先恢复原作业，处理全部输入和保存故障"
            reason.contains("冻结") || reason.contains("装车记录") || reason.contains("扫码状态") -> "本单状态或有效数量不满足，请核对原记录"
            reason.contains("召回") || reason.contains("撤回") -> "原资格已停止，请核对后台处理结果"
            else -> "原因尚待核对，请展开原始原因；原记录保留"
        }
    }
}

data class TaskQualificationReport(val title: String, val expiry: String, val materials: QualificationCheck,
    val scanning: QualificationCheck, val closing: QualificationCheck, val uploading: String) {
    fun summary(): String = listOf(title, expiry,
        "可看：原人原机已核对，可只读查看原记录",
        "资料：${materials.summary}", "可扫：${scanning.summary}", "可结：${closing.summary}",
        "可传：$uploading",
        "可导出：须在只读应急导出页解锁预置凭据，并核验本单范围；本页未解锁",
        "到期、重启或退出不会删除原记录。无法正常查看时请从数据页使用预置救援凭据。",
        "仅为本次本机核对。离线无法获知后台新撤权；实际操作仍重新核验，未获最终确认不得放行。"
    ).joinToString("\n\n")

    fun details(): String = listOf("资料" to materials, "扫码" to scanning, "结单" to closing)
        .filter { it.second.detail.isNotBlank() }.joinToString("\n\n") { "${it.first}：${it.second.detail}" }
}
