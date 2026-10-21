package com.acrylic.outbound.pda.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.rescue.RescueBundle
import com.acrylic.outbound.pda.rescue.RescueCredentials
import com.acrylic.outbound.pda.rescue.SupervisorRescueSetupDraft
import com.acrylic.outbound.pda.security.DeviceKeys
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One original authorization and one pending file result, never an offline reissue. */
internal class SupervisorRescueSetupFlow(private val activity: ComponentActivity, private val finished: (String) -> Unit,
    private val active: (Boolean) -> Unit) {
    private val app get() = activity.application as PdaApplication
    private var draft: SupervisorRescueSetupDraft? = null
    private var view: SupervisorRescueSetupDialog? = null
    private var busy = false
    private var pending = ""
    @Volatile private var abandoned = false
    private val save = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        result("SAVE", uri) { current, target ->
            current.write({ activity.contentResolver.openOutputStream(target, "wt") }, { activity.contentResolver.openInputStream(target) })
            view?.saved = true
            "主管凭据卡目标写入及回读一致；尚未预置。请移至设备外，再重新选择。"
        }
    }
    private val verify = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        result("VERIFY", uri) { current, target ->
            current.verify(checkNotNull(activity.contentResolver.openInputStream(target)))
            "本次主管凭据卡逐字回读一致；设备外独立保管仍须本人确认。"
        }
    }
    fun show(material: String) {
        if (draft != null || busy || pending.isNotEmpty()) return
        try {
            abandoned = false
            val current = SupervisorRescueSetupDraft.create(app.session.requireSession(), material, binding())
            draft = current; active(true)
            val dialog = SupervisorRescueSetupDialog(activity); view = dialog
            dialog.binding.authorityScope.text = current.summary()
            dialog.binding.saveCard.setOnClickListener { choose("SAVE") }
            dialog.binding.verifyCard.setOnClickListener { choose("VERIFY") }
            dialog.show(::provision, ::clear)
        } catch (_: Exception) { clear(); finished("尚未生成主管解锁卡。请核对原签名、指定救援人、原设备与仓库；撤销原件沿原导入入口处理。") }
    }
    private fun choose(operation: String) {
        if (busy || pending.isNotEmpty()) return
        pending = operation; view?.resetCustody(); view?.render("正在选择文件；尚未预置。", false, true)
        try {
            if (operation == "SAVE") save.launch("pda-supervisor-rescue-card-" + java.util.UUID.randomUUID() + ".json")
            else verify.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        } catch (_: Exception) { pending = ""; view?.render("文件选择器未能打开；尚未预置。", false) }
    }
    private fun result(operation: String, uri: Uri?, action: (SupervisorRescueSetupDraft, Uri) -> String) {
        if (pending != operation) return
        pending = ""
        if (uri == null) { view?.render("文件选择已取消；尚未预置，原授权与资料保留。", false); return }
        perform { action(it, uri) }
    }
    private fun provision() {
        val dialog = view ?: return
        if (busy || pending.isNotEmpty() || !dialog.binding.custodyConfirmed.isChecked) return
        perform(provisioning = true) { current ->
            check(!abandoned)
            RescueCredentials.shared(app, app.session).importSupervisorSetup(current)
            "主管只读解锁已按原签名与在线原件预置；本授权旧口令已替换，设备外保管由本人确认，原资料保留。"
        }
    }
    private fun perform(provisioning: Boolean = false, action: (SupervisorRescueSetupDraft) -> String) {
        val current = draft ?: return
        if (busy) return
        busy = true; view?.render("正在核验；完成前不要拔出介质。", current.verified, true)
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { action(current) } }; busy = false
            if (draft !== current) return@launch
            result.fold({ message ->
                if (provisioning) { view?.dismiss(); finished(message) } else view?.render(message, current.verified)
            }, { view?.render("本次设置未确认完成。请保留新旧卡、原授权和资料，重新联网核对；不要推定旧方式仍有效。", false) })
        }
    }
    private fun binding(): Pair<String, String> {
        val public = DeviceKeys().publicKey(false).also(app.session.settings::verifyKey)
        return app.session.settings.device to RescueBundle.sha(Base64.getDecoder().decode(public))
    }
    private fun clear() { abandoned = true; draft?.close(); draft = null; view = null; active(false) }
    fun close() { view?.dismiss(); clear() }
}
