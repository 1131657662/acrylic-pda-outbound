package com.acrylic.outbound.pda.ui

import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.acrylic.outbound.pda.R

/** Keeps a choice id only during system selection; never retains a card URI or password. */
internal class OwnRescueCardPicker(private val activity: ComponentActivity,
    private val model: () -> RescueExportViewModel, private val selection: () -> Int) {
    private var expected: String? = null
    val pending get() = expected != null
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = expected; expected = null
        if (id != null) {
            when {
                uri == null -> message("凭据卡选择已取消，尚未核验；原资料保留。")
                selected() != id -> message("所选只读身份或操作状态已变化，请重新选择本人凭据卡。")
                else -> model().unlockCard(id, uri)
            }
        }
    }
    fun choose() {
        if (pending) return
        val id = selected()?.takeIf { it.startsWith("own:") }
        if (id == null) { message("请先选取已预置的本人只读身份；主管授权使用原独立入口。"); return }
        expected = id
        try { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        catch (_: Exception) { expected = null; message("文件选择器未能打开，尚未核验；原资料保留。") }
    }
    private fun selected(): String? {
        val state = model().state.value
        if (state.busy || state.unlocked) return null
        return state.identities.getOrNull(selection())?.id
    }
    private fun message(text: String) {
        activity.findViewById<TextView>(R.id.status).text = text
        activity.findViewById<android.widget.Button>(R.id.ownCardUnlock).isEnabled = !model().state.value.busy
    }
}
