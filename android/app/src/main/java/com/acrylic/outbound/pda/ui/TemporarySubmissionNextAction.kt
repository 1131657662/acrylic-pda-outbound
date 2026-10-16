package com.acrylic.outbound.pda.ui

/** TEMP presentation keeps independent-confirmation and retention semantics. */
object TemporarySubmissionNextAction {
    fun render(binding: com.acrylic.outbound.pda.databinding.ActivitySubmissionBinding, state: String?) {
        val open = state == null
        val terminal = state in setOf("SYNCED", "ABORTED", "REJECTED")
        val send = state in setOf("FROZEN", "ACTION_REQUIRED")
        binding.freeze.visibility = if (open) android.view.View.VISIBLE else android.view.View.GONE
        binding.reason.visibility = binding.freeze.visibility
        binding.upload.visibility = if (send) android.view.View.VISIBLE else android.view.View.GONE
        binding.refresh.visibility = if (!open && !terminal && !send) android.view.View.VISIBLE else android.view.View.GONE
        binding.upload.text = "核对并上传原临时批次"
        binding.refresh.text = if (state in setOf("RESULT_UNKNOWN", "REGISTERED", "RECEIVING")) "先查询原临时批次结果" else "查询独立确认回执"
        binding.nextStep.text = when {
            open -> "请核对实物、车号及数量；冻结后不能继续扫码或本地调整。"
            terminal -> "原回执、实物及占用保留；归档入口尚未开放，请联系主管核对。"
            send -> "原临时单及资格保留；先查原ID，尚未受理时只恢复原上传。"
            else -> "先查原ID；接收原件不代表独立确认。结果未知不新建批次，不得放行。"
        }
    }
}
