package com.acrylic.outbound.pda

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal object IndependentSkuDeviceEvidence {
    fun capture(activity: Activity, directory: File, name: String) {
        val root = activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val rows = JSONArray()
        inspect(root, root.width, rows)
        File(directory, "$name.json").writeText(JSONObject().put("width", root.width).put("height", root.height)
            .put("fontScale", activity.resources.configuration.fontScale).put("density", activity.resources.displayMetrics.density)
            .put("views", rows).toString())
        for (i in 0 until rows.length()) check(!rows.getJSONObject(i).optBoolean("overflow")) {
            "Text line exceeds view width; exact line metrics saved in $name.json"
        }
    }
    private fun inspect(view: View, width: Int, rows: JSONArray) {
        check(view.width <= width) { "View exceeds actual screen width" }
        if (view is TextView && view.visibility == View.VISIBLE && view.layout != null) {
            val available = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
            val lines = JSONArray(); var overflow = false
            for (i in 0 until view.layout.lineCount) {
                val visibleWidth = view.layout.getLineMax(i)
                if (visibleWidth > available + 1) overflow = true
                lines.put(JSONObject().put("width", view.layout.getLineWidth(i)).put("visibleWidth", visibleWidth)
                    .put("text", view.text.substring(view.layout.getLineStart(i), view.layout.getLineEnd(i))))
            }
            rows.put(JSONObject().put("text", view.text.toString()).put("width", view.width).put("height", view.height)
                .put("available", available).put("overflow", overflow).put("lines", lines))
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) inspect(view.getChildAt(i), width, rows)
    }
}
