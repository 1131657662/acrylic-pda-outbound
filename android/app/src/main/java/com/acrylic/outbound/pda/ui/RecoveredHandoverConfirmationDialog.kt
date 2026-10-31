package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Context
import android.view.WindowManager
import android.widget.*

/** The checklist and both decisions remain reachable with large accessibility fonts. */
internal object RecoveredHandoverConfirmationDialog {
    fun show(context: Context, originalChecklist: String, confirm: () -> Unit) {
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; setPadding(padding, padding, padding, padding)
        }
        content.addView(TextView(context).apply { text = originalChecklist; textSize = 18f })
        val scroll = ScrollView(context).apply { addView(content) }
        val dialog = AlertDialog.Builder(context).setTitle("核对恢复交接实物与资料").setView(scroll).create()
        dialog.window!!.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        content.addView(Button(context).apply {
            text = "已核对，确认接收"; setOnClickListener { dialog.dismiss(); confirm() }
        })
        content.addView(Button(context).apply {
            text = "返回核对"; setOnClickListener { dialog.dismiss() }
        })
        dialog.show()
    }
}
