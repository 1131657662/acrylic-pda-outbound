package com.acrylic.outbound.pda.ui

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import org.json.JSONObject

/** Only selection metadata survives until this page receives its explicit file result. */
internal class IndependentCardPicker(activity: ComponentActivity, private val model: () -> IndependentCredentialViewModel,
    private val changed: () -> Unit) {
    private var expected: JSONObject? = null
    val pending get() = expected != null
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val original = expected; expected = null; changed()
        if (original != null) {
            when {
                uri == null -> model().cardMessage("凭据卡选择已取消，尚未核验；原资料保留。")
                original.toString() != model().cardSelection()?.toString() ->
                    model().cardMessage("独立身份或操作状态已变化，请重新选择原凭据卡。")
                else -> model().verifyCard(uri, original)
            }
        }
    }
    fun choose() {
        if (pending) return
        val selection = model().cardSelection()
        if (selection == null) { model().cardMessage("请先联网核对原人员和设备，已有原件保留。"); return }
        expected = selection; changed()
        try { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        catch (_: Exception) {
            expected = null; changed(); model().cardMessage("文件选择器未能打开，尚未核验；原资料保留。")
        }
    }
}
