package com.acrylic.outbound.pda

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.acrylic.outbound.pda.rescue.RescueFileTransfer
import org.json.JSONObject
import java.io.File

/** Debug-only activity for a real DocumentsUI target; production export uses the same transfer primitive. */
class PhysicalSafCopyActivity : Activity() {
    private val requestCode = 73

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (state != null) return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "pda-physical-saf-test.pdarescue"
        val chooser = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, title)
        }
        startActivityForResult(chooser, requestCode)
    }

    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if (request != requestCode) return
        val evidence = File(checkNotNull(intent.getStringExtra(EXTRA_RESULT)))
        val source = File(checkNotNull(intent.getStringExtra(EXTRA_SOURCE)))
        val body = if (result != RESULT_OK || data?.data == null) {
            JSONObject().put("cancelled", true)
        } else {
            copy(source, checkNotNull(data.data))
        }
        evidence.parentFile?.mkdirs()
        evidence.writeText(body.toString())
        finish()
    }

    private fun copy(source: File, target: Uri): JSONObject = try {
        val digest = RescueFileTransfer.copy(source,
            { contentResolver.openOutputStream(target, "wt") } to
                { contentResolver.openInputStream(target) })
        JSONObject().put("copied", true).put("uri", target.toString()).put("sha256", digest)
    } catch (error: Exception) {
        JSONObject().put("copied", false).put("error", error.message ?: error.javaClass.name)
    }

    companion object {
        const val EXTRA_SOURCE = "source"
        const val EXTRA_RESULT = "result"
        const val EXTRA_TITLE = "title"
    }
}
