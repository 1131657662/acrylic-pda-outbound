package com.acrylic.outbound.pda.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.rescue.RescuePackageDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One Activity instance owns the draft and pending file choice. No state is restored. */
internal class RescuePackageSetupFlow(private val activity: ComponentActivity,
    private val model: () -> RescueExportViewModel, private val active: (Boolean) -> Unit) {
    private var draft: RescuePackageDraft? = null
    private var view: RescuePackageSetupDialog? = null
    private var busy = false
    private var pending = ""
    private val save = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        result("SAVE", uri) { current, target ->
            current.write({ activity.contentResolver.openOutputStream(target, "wt") }, { activity.contentResolver.openInputStream(target) })
            view?.saved = true
            "解密材料写入与回读一致；尚未生成包。请独立保管后重新选择。"
        }
    }
    private val verify = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        result("VERIFY", uri) { current, target ->
            current.verify(checkNotNull(activity.contentResolver.openInputStream(target)))
            "本次解密材料逐字回读一致；请确认与包及救援凭据卡分开保管。"
        }
    }
    fun show() {
        if (draft != null || busy || pending.isNotEmpty()) return
        try {
            draft = model().packageDraft(); active(true)
            val dialog = RescuePackageSetupDialog(activity); view = dialog
            dialog.binding.savePackageSecret.setOnClickListener { choose("SAVE") }
            dialog.binding.verifyPackageSecret.setOnClickListener { choose("VERIFY") }
            dialog.show(::generate, ::clear)
        } catch (_: Exception) { clear() }
    }
    private fun choose(operation: String) {
        if (busy || pending.isNotEmpty()) return
        try { draft?.recheck() ?: return } catch (_: Exception) { close(); return }
        pending = operation; view?.resetCustody(); view?.render("正在选择文件；尚未生成包。", false, true)
        try {
            if (operation == "SAVE") save.launch("pda-package-secret-" + java.util.UUID.randomUUID() + ".json")
            else verify.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        } catch (_: Exception) { pending = ""; view?.render("文件选择未打开；尚未生成包。", false) }
    }
    private fun result(operation: String, uri: Uri?, action: (RescuePackageDraft, Uri) -> String) {
        if (pending != operation) return
        pending = ""
        if (uri == null) { view?.render("选择已取消；尚未生成新包，原资料保留。", false); return }
        perform { action(it, uri) }
    }
    private fun generate() {
        if (view?.binding?.packageCustody?.isChecked != true || pending.isNotEmpty()) return
        perform(generating = true) { model().generatePrepared(it) }
    }
    private fun perform(generating: Boolean = false, action: (RescuePackageDraft) -> String) {
        val current = draft ?: return
        if (busy) return
        busy = true; view?.render("正在核验，请勿拔出介质。", current.verified, true)
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { current.recheck(); action(current) } }
            busy = false
            if (draft !== current) return@launch
            result.fold({ message ->
                if (generating) view?.dismiss() else view?.render(message, current.verified)
            }, { view?.render("本次未完成；保留解密材料、旧包及原记录，不能使用半文件。请核对原范围后重试。", false) })
        }
    }
    private fun clear() { pending = ""; draft?.close(); draft = null; view = null; active(false) }
    fun close() { view?.dismiss(); clear() }
}
