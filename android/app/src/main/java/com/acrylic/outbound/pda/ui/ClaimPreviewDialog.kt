package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import kotlinx.coroutines.*
import org.json.JSONObject

/** Cancellation is read-only; only an explicit enabled confirmation invokes claim. */
internal class ClaimPreviewDialog(private val context: Context,
    private val load: suspend () -> JSONObject, private val confirm: (JSONObject, String) -> Unit) {
    fun show(): AlertDialog {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val input = EditText(context).apply { hint = "货堆/区域备注（可留空）"; maxLines = 3 }
        var checked: JSONObject? = null
        val dialog = AlertDialog.Builder(context).setTitle("领取前核对")
            .setMessage("正在读取服务器原订单；尚未领取").setView(input)
            .setNegativeButton("返回", null).setPositiveButton("确认领取", null).create()
        dialog.setOnDismissListener { scope.cancel() }
        dialog.show()
        val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        button.isEnabled = false
        button.setOnClickListener {
            val preview = checked ?: return@setOnClickListener
            checked = null; button.isEnabled = false
            confirm(preview, input.text.toString())
            dialog.dismiss()
        }
        scope.launch {
            try {
                val preview = load()
                dialog.setMessage(ClaimPreviewText.render(preview))
                checked = preview; button.isEnabled = true
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                dialog.setMessage("领取前核对未完成，尚未领取。${error.message.orEmpty()}\n请返回刷新或联系主管核对。")
            }
        }
        return dialog
    }
}
