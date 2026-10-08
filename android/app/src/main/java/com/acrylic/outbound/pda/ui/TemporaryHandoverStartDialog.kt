package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The long acknowledgement action remains fully reachable at large accessibility font sizes. */
internal object TemporaryHandoverStartDialog {
    fun show(context: Context, confirm: () -> Unit) {
        val padding = (16 * context.resources.displayMetrics.density).toInt()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        content.addView(TextView(context).apply {
            text = "请核对原计划、保留实物和本人的新资格。联网确认资料后开启本轮，原记录与占用继续保留。"
            textSize = 18f
        })
        val scroll = ScrollView(context).apply { addView(content) }
        val dialog = AlertDialog.Builder(context).setTitle("核对临时交接新轮").setView(scroll).create()
        content.addView(Button(context).apply {
            text = "确认资料并开启"
            setOnClickListener { dialog.dismiss(); confirm() }
        })
        content.addView(Button(context).apply {
            text = "返回核对"
            setOnClickListener { dialog.dismiss() }
        })
        dialog.show()
    }
}
