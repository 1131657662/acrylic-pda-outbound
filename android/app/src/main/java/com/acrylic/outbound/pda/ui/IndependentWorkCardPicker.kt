package com.acrylic.outbound.pda.ui

import android.net.Uri
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.json.JSONObject

/** Selection is ephemeral; the original serial engine and grant enforce every capture. */
internal class IndependentWorkCardPicker(private val activity: ComponentActivity,
    private val binding: ActivityIndependentWorkBinding, private val scanner: ScannerReceiver) {
    private val app = activity.application as PdaApplication
    private val engine = IndependentWorkRuntime.shared(app)
    private val credentials = IndependentCredentials.shared(app, app.session, RecoveryProtection(app.database))
    private data class Selection(val task: IndependentTask, val identity: JSONObject)
    private var expected: Selection? = null
    private var returned = false
    private var result: Uri? = null
    private val controls = mutableMapOf<View, Boolean>()
    val pending get() = expected != null
    private val picker = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (expected != null) {
            result = uri; returned = true
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumed()
        }
    }
    fun choose() {
        if (pending) return
        val selection = runCatching {
            val state = engine.state.value
            check(state.queued == 0 && state.task?.state?.state in setOf("PLANNED", "SCANNING"))
            Selection(checkNotNull(state.task).task, credentials.cardSelection())
        }.getOrNull()
        if (selection == null) { engine.cardMessage("请先核对原任务、登录身份与可信时间；原件保留。"); return }
        if (!engine.prepareCardSelection()) return
        scanner.unregister(); expected = selection; returned = false; render()
        try { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        catch (_: Exception) { clear(); engine.cardMessage("文件选择器未打开，仍暂停采集；原件保留。") }
    }
    fun resumed(): Boolean {
        val original = expected ?: return false
        if (!returned) return true
        val uri = result; clear()
        if (uri == null) { engine.cardMessage("独立卡选择已取消，仍暂停采集；原件保留。"); return true }
        val current = runCatching { credentials.cardSelection() }.getOrNull()
        if (current == null || current.toString() != original.identity.toString() || engine.state.value.task?.task != original.task) {
            engine.cardMessage("原身份、可信时间或任务已变化，请重新核对并选择原卡。"); return true
        }
        engine.resume(original.task.id)
        engine.authorizeCard(original.task) {
            try {
                checkNotNull(app.contentResolver.openInputStream(uri)).use { credentials.unlockCard(it, original.identity) }
            } catch (_: Exception) { error("独立卡未通过原身份、签名、期限或尝试限制核验；原件保留") }
        }
        scanner.register(); return true
    }
    private fun clear() { expected = null; result = null; returned = false; restoreControls() }
    fun restoreControls() { controls.forEach { (view, enabled) -> view.isEnabled = enabled }; controls.clear() }
    fun render() {
        binding.workAuthorizeCard.isEnabled = engine.state.value.queued == 0 &&
            engine.state.value.task?.state?.state in setOf("PLANNED", "SCANNING")
        if (!pending) return
        listOf(binding.workAuthorizeCard, binding.workAuthorize, binding.workPassword, binding.workPause,
            binding.workManualToggle, binding.workManual, binding.workValidate, binding.workConfirm,
            binding.workAcknowledge, binding.workDiscard, binding.workVoid, binding.workFinish,
            binding.workLatest, binding.workOlder, binding.workLocate, binding.workBack).forEach {
            controls.putIfAbsent(it, it.isEnabled); it.isEnabled = false
        }
    }
}
