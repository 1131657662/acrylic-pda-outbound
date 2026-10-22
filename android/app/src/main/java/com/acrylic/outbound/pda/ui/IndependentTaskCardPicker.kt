package com.acrylic.outbound.pda.ui

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Keeps selection metadata only; never persists the card URI, password or grant. */
internal class IndependentTaskCardPicker(activity: ComponentActivity, private val model: () -> IndependentTaskViewModel,
    private val changed: () -> Unit) {
    private var expected: IndependentTaskCardSelection? = null
    val pending get() = expected != null
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val original = expected; expected = null; changed()
        if (original != null) {
            val current = model().cardSelection()
            when {
                uri == null -> model().cardMessage("独立卡选择已取消，未创建请求；原资料保留。")
                current == null || original.commandSha256 != current.commandSha256 ||
                    original.identity.toString() != current.identity.toString() ->
                    model().cardMessage("原身份或计划已变化，请重新核对并选择原卡。")
                else -> model().createCard(uri, original)
            }
        }
    }
    fun choose() {
        if (pending) return
        val selection = model().cardSelection()
        if (selection == null) { model().cardMessage("请先核对原登录身份、有效独立凭据与完整计划；原资料保留。"); return }
        expected = selection; changed()
        try { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        catch (_: Exception) {
            expected = null; changed(); model().cardMessage("文件选择器未打开，未创建请求；原资料保留。")
        }
    }
}
