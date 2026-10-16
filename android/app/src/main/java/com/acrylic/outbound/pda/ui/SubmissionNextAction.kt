package com.acrylic.outbound.pda.ui

/** Presentation only: repositories still query the immutable original batch before sending. */
object SubmissionNextAction {
    fun render(binding: com.acrylic.outbound.pda.databinding.ActivitySubmissionBinding, state: String?) {
        val open = state == null
        val terminal = state in setOf("SYNCED", "ABORTED", "REJECTED")
        val send = state in setOf("FROZEN", "ACTION_REQUIRED")
        binding.freeze.visibility = if (open) android.view.View.VISIBLE else android.view.View.GONE
        binding.reason.visibility = binding.freeze.visibility
        binding.upload.visibility = if (send) android.view.View.VISIBLE else android.view.View.GONE
        binding.refresh.visibility = if (!open && !terminal && !send) android.view.View.VISIBLE else android.view.View.GONE
        binding.upload.text = "核对并上传原批次"
        binding.refresh.text = if (state in setOf("RESULT_UNKNOWN", "REGISTERED", "RECEIVING")) "先查询原批次结果" else "查询原确认回执"
        binding.nextStep.text = when {
            open -> "请核对实物、车号及数量；冻结后不能继续扫码或本地调整。"
            terminal -> "原记录和回执保留。受控拒绝或停止请联系主管办理实物处置。"
            send -> "冻结原件保留；先查原ID，尚未受理时只恢复原上传。"
            else -> "先查原ID；结果未知不等于失败，不新建批次。未获有效最终确认不得放行。"
        }
    }
}
