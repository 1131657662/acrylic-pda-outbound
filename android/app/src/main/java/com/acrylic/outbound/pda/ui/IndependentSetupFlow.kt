package com.acrylic.outbound.pda.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.independent.IndependentSetupDraft
import com.acrylic.outbound.pda.rescue.RescueBundle
import com.acrylic.outbound.pda.independent.IndependentCredentials
import com.acrylic.outbound.pda.sync.RecoveryProtection
import com.acrylic.outbound.pda.security.DeviceKeys
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One ephemeral draft and one outstanding file operation; closing never provisions. */
internal class IndependentSetupFlow(private val activity: ComponentActivity, private val finished: (String) -> Unit,
    private val active: (Boolean) -> Unit) {
    private val app get() = activity.application as PdaApplication
    private var draft: IndependentSetupDraft? = null
    private var view: IndependentSetupDialog? = null
    private var busy = false
    private var pending = ""
    @Volatile private var abandoned = false
    private val save = activity.registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        result("SAVE", uri) { current, target ->
            current.write({ activity.contentResolver.openOutputStream(target, "wt") }, { activity.contentResolver.openInputStream(target) })
            view?.saved = true
            "凭据卡目标写入及回读一致；尚未预置。请移至设备外，再重新选择回读。"
        }
    }
    private val verify = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        result("VERIFY", uri) { current, target ->
            current.verify(checkNotNull(activity.contentResolver.openInputStream(target)))
            "本次凭据卡逐字回读一致；请确认设备外独立保管，再联网预置。"
        }
    }

    fun show() {
        if (draft != null || busy || pending.isNotEmpty()) return
        try {
            abandoned = false
            draft = IndependentSetupDraft.create(app.session.requireSession(), keyDigest())
            val dialog = IndependentSetupDialog(activity); view = dialog; active(true)
            dialog.binding.saveCard.setOnClickListener { choose("SAVE") }
            dialog.binding.verifyCard.setOnClickListener { choose("VERIFY") }
            dialog.show(::provision, ::clear)
        } catch (_: Exception) { clear(); finished("尚未开始设置。请先联网登录原人员并核对原设备，已有凭据和资料保留。") }
    }

    private fun choose(operation: String) {
        if (busy || pending.isNotEmpty()) return
        pending = operation; view?.resetCustody()
        view?.render("正在选择文件；尚未预置。", false, true)
        try {
            if (operation == "SAVE") save.launch("pda-independent-capture-card-" + java.util.UUID.randomUUID() + ".json")
            else verify.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        } catch (_: Exception) { pending = ""; view?.render("未能打开文件选择器；尚未预置。", false) }
    }

    private fun result(operation: String, uri: Uri?, action: (IndependentSetupDraft, Uri) -> String) {
        if (pending != operation) return
        pending = ""
        if (uri == null) { view?.render("文件选择已取消；尚未预置，原凭据保留。", false); return }
        perform { action(it, uri) }
    }

    private fun provision() {
        val dialog = view ?: return
        if (busy || pending.isNotEmpty() || !dialog.binding.custodyConfirmed.isChecked) return
        perform(provisioning = true) { current ->
            val password = current.credential(app.session.requireSession(), keyDigest())
            try {
                check(!abandoned) { "设置已关闭" }
                IndependentCredentials.shared(app, app.session, RecoveryProtection(app.database))
                    .provision(password, current.expectedSession())
            } finally { password.fill('\u0000') }
            "独立采集身份已按原签名核验预置，仍受原可信期限约束；设备外保管由本人确认，独立记录始终待对账。"
        }
    }

    private fun perform(provisioning: Boolean = false, action: (IndependentSetupDraft) -> String) {
        val current = draft ?: return
        if (busy) return
        busy = true; view?.render("正在核验，请稍候；不要拔出介质。", current.verified, true)
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { action(current) } }
            busy = false
            if (draft !== current) return@launch
            result.fold({ message ->
                if (provisioning) { view?.dismiss(); finished(message) }
                else view?.render(message, current.verified)
            }, { view?.render("本次设置未确认完成。请保留凭据卡、旧卡及原资料，重新联网核对；不要推定旧方式仍有效。", false) })
        }
    }

    private fun keyDigest(): String {
        val public = DeviceKeys().publicKey(false).also(app.session.settings::verifyKey)
        return RescueBundle.sha(Base64.getDecoder().decode(public))
    }
    private fun clear() { abandoned = true; draft?.close(); draft = null; view = null; active(false) }
    fun close() { view?.dismiss(); clear() }
}
