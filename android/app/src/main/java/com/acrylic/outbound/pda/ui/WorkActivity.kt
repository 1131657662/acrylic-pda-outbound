package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityWorkBinding
import com.acrylic.outbound.pda.device.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkActivity : ComponentActivity() {
    private lateinit var binding: ActivityWorkBinding
    private val app get() = application as PdaApplication
    private val engine get() = app.work
    private val keys = WorkKeys()
    private lateinit var scanner: ScannerReceiver
    private lateinit var renderer: WorkRenderer
    private var assignment = ""
    private var dialogOpen = false
    private var recallOpening = false
    private var imeOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assignment = intent.getStringExtra("assignmentId").orEmpty()
        if (assignment.isEmpty()) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityWorkBinding.inflate(layoutInflater); setContentView(binding.root)
        renderer = WorkRenderer(binding)
        binding.sound.text = if (app.workSound.enabled) "声音开" else "声音关"
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            imeOpen = insets.isVisible(WindowInsetsCompat.Type.ime())
            view.setPadding(system.left, system.top, system.right, system.bottom); insets
        }
        scanner = ScannerReceiver(this, { engine.receive(it, "BROADCAST") }, engine::stop)
        bindActions()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = leave() })
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            engine.state.collect { renderer.render(it, app.workSound.error) }
        } }
    }

    private fun bindActions() {
        binding.back.setOnClickListener { leave() }
        binding.recall.setOnClickListener {
            startActivity(android.content.Intent(this, RecallActivity::class.java).putExtra("assignmentId", assignment))
        }
        binding.closeOrder.setOnClickListener {
            startActivity(android.content.Intent(this, SubmissionActivity::class.java).putExtra("assignmentId", assignment))
        }
        binding.details.setOnClickListener {
            startActivity(android.content.Intent(this, WorkDetailsActivity::class.java).putExtra("assignmentId", assignment))
        }
        binding.confirm.setOnClickListener { confirmDisplayed() }
        binding.validate.setOnClickListener { manualInput() }
        binding.discard.setOnClickListener { discard() }
        binding.voidLast.setOnClickListener { voidLast() }
        binding.sound.setOnClickListener {
            app.workSound.enabled = !app.workSound.enabled
            binding.sound.text = if (app.workSound.enabled) "声音开" else "声音关"
        }
        binding.manualToggle.setOnClickListener {
            val visible = binding.manualPanel.visibility != View.VISIBLE
            binding.manualPanel.visibility = if (visible) View.VISIBLE else View.GONE
            binding.products.visibility = if (visible) View.GONE else View.VISIBLE
            binding.recent.visibility = if (visible) View.GONE else View.VISIBLE
            hideKeyboard()
        }
    }

    private fun manualInput() {
        val text = binding.manual.text.toString()
        if (text.isEmpty()) return
        if (engine.receive(text, "MANUAL")) { binding.manual.text.clear(); hideKeyboard() }
    }
    private fun confirmDisplayed() {
        val state = renderer.displayed; val candidate = state.candidate
        when {
            candidate?.state in setOf("INVALID", "BUSINESS_REJECTED") -> engine.acknowledge(candidate!!.id)
            state.blocked.isNotEmpty() -> engine.recover()
            candidate?.state == "WAITING_CONFIRM" -> engine.confirm(candidate.id)
        }
    }
    private fun discard() {
        val c = renderer.displayed.candidate
        if (c == null) { binding.manual.text.clear(); return }
        if (c.context.confirmationPolicy != "MANUAL" || c.state !in setOf("RECEIVED", "WAITING_CONFIRM")) return
        reasonDialog("放弃手输 ${c.context.barcode}", "原输入与序号保留，请填写原因") { engine.discard(c.id, it) }
    }
    private fun voidLast() {
        val scan = renderer.displayed.recent.firstOrNull { it.status == "ACTIVE" && it.captureId !in renderer.displayed.dispositions } ?: return
        reasonDialog("撤销末包 ${scan.barcode}", "请先核实实物已卸下或未装车；撤销会保留原明细与审计") { engine.voidLast(scan.captureId, it) }
    }
    private fun reasonDialog(title: String, message: String, action: (String) -> Unit) {
        suspendInput()
        val input = EditText(this).apply { hint = "实物核对及原因"; maxLines = 3 }
        val dialog = AlertDialog.Builder(this).setTitle(title).setMessage(message).setView(input)
            .setNegativeButton("返回", null).setPositiveButton("确认", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val reason = input.text.toString().trim()
            if (reason.isBlank() || reason.length > 500) input.error = "请输入1～500字原因"
            else { action(reason); dialog.dismiss() }
        } }
        dialog.setOnDismissListener { restoreInput() }; dialog.show()
    }
    private fun leave() {
        if (imeOpen) { hideKeyboard(); return }
        val state = engine.state.value
        if (state.pending == 0 && state.queued == 0 && state.candidate == null) { finish(); return }
        suspendInput()
        AlertDialog.Builder(this).setTitle("保留未处理输入")
            .setMessage("原输入与确认意图保留，恢复本单后继续处理；未处理完成不能切到另一单作业。")
            .setNegativeButton("继续处理", null).setPositiveButton("保留并返回") { _, _ -> finish() }
            .setOnDismissListener { restoreInput() }.show()
    }
    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(binding.manual.windowToken, 0)
        binding.manual.clearFocus()
    }
    private fun suspendInput() { dialogOpen = true; scanner.unregister(); engine.pause(); keys.reset() }
    private fun restoreInput() {
        dialogOpen = false
        if (!isFinishing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { engine.resume(assignment); scanner.register() }
    }
    override fun onResume() {
        super.onResume()
        if (::scanner.isInitialized && !dialogOpen) {
            lifecycleScope.launch(Dispatchers.IO) {
                val recall = runCatching { app.recalls.check(assignment) }.getOrElse { app.database.recalls().header(assignment) }
                if (recall != null && !recallOpening) {
                    recallOpening = true
                    runOnUiThread { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startActivity(android.content.Intent(this@WorkActivity, RecallActivity::class.java).putExtra("assignmentId", assignment)) }
                } else if (recall == null) {
                    runOnUiThread { if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { engine.resume(assignment); scanner.register() } }
                }
            }
        }
    }
    override fun onPause() {
        if (::scanner.isInitialized) { scanner.unregister(); engine.pause(); keys.reset() }
        super.onPause()
    }
    // Android's public Activity callback; AndroidX annotates its inherited forwarding implementation as restricted.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (dialogOpen || !::binding.isInitialized) return super.dispatchKeyEvent(event)
        val command = keys.command(event) ?: return super.dispatchKeyEvent(event)
        if (keys.firstDown(event)) when (command) {
            "CONFIRM" -> confirmDisplayed()
            "VALIDATE" -> manualInput()
            "DISCARD" -> discard()
        }
        return true
    }
}
