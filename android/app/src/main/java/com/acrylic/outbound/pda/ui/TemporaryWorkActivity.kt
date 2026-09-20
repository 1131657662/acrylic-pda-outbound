package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.*
import androidx.lifecycle.*
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityWorkBinding
import com.acrylic.outbound.pda.device.*
import kotlinx.coroutines.launch

class TemporaryWorkActivity : ComponentActivity() {
    private lateinit var binding: ActivityWorkBinding
    private lateinit var renderer: TemporaryWorkRenderer
    private lateinit var scanner: ScannerReceiver
    private val app get() = application as PdaApplication
    private val engine get() = app.temporaryWork
    private val keys = WorkKeys()
    private var id = ""
    private var dialogOpen = false
    private var imeOpen = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); id = intent.getStringExtra("clientOrderId").orEmpty()
        if (id.isEmpty()) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityWorkBinding.inflate(layoutInflater); setContentView(binding.root)
        renderer = TemporaryWorkRenderer(binding)
        scanner = ScannerReceiver(this, { engine.receive(it, "BROADCAST") }, engine::stop)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars()); imeOpen = insets.isVisible(WindowInsetsCompat.Type.ime())
            view.setPadding(system.left, system.top, system.right, system.bottom); insets
        }
        actions()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = leave() })
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { engine.state.collect { renderer.render(it, app.workSound.error) } } }
    }
    private fun actions() {
        binding.back.setOnClickListener { leave() }
        binding.closeOrder.setOnClickListener {
            suspendInput(); startActivity(Intent(this, TemporarySubmissionActivity::class.java).putExtra("clientOrderId", id)); finish()
        }
        binding.validate.setOnClickListener { manual() }; binding.confirm.setOnClickListener { confirm() }
        binding.discard.setOnClickListener { discard() }; binding.voidLast.setOnClickListener { voidLast() }
        binding.details.setOnClickListener { startActivity(Intent(this, TemporaryDetailsActivity::class.java).putExtra("clientOrderId", id)) }
        binding.sound.text = if (app.workSound.enabled) "声音开" else "声音关"
        binding.sound.setOnClickListener { app.workSound.enabled = !app.workSound.enabled; binding.sound.text = if (app.workSound.enabled) "声音开" else "声音关" }
        binding.manualToggle.setOnClickListener {
            val visible = binding.manualPanel.visibility != View.VISIBLE
            binding.manualPanel.visibility = if (visible) View.VISIBLE else View.GONE
            binding.products.visibility = if (visible) View.GONE else View.VISIBLE
            binding.recent.visibility = if (visible) View.GONE else View.VISIBLE; hideKeyboard()
        }
    }
    private fun manual() {
        val raw = binding.manual.text.toString(); if (raw.isEmpty()) return
        if (engine.receive(raw, "MANUAL")) { binding.manual.text.clear(); hideKeyboard() }
    }
    private fun confirm() {
        val state = renderer.displayed; val row = state.candidate
        when {
            row?.state in setOf("INVALID", "BUSINESS_REJECTED") -> engine.acknowledge(row!!.id)
            state.blocked.isNotBlank() -> engine.recover()
            row?.state == "WAITING_CONFIRM" -> engine.confirm(row.id)
        }
    }
    private fun discard() {
        val row = renderer.displayed.candidate ?: return
        if (row.inputSource == "MANUAL" && row.state in setOf("RECEIVED", "WAITING_CONFIRM"))
            reason("放弃手输 ${row.barcode}") { engine.discard(row.id, it) }
    }
    private fun voidLast() {
        val scan = renderer.displayed.recent.firstOrNull { it.status == "ACTIVE" } ?: return
        reason("撤销末包 ${scan.barcode}，请先核实实物已卸下或未装车") { engine.voidLast(scan.captureId, it) }
    }
    private fun reason(title: String, action: (String) -> Unit) {
        suspendInput(); val input = EditText(this).apply { hint = "实物核对及原因"; maxLines = 3 }
        val dialog = AlertDialog.Builder(this).setTitle(title).setMessage("原输入、扫码序号和审计保留").setView(input)
            .setNegativeButton("返回", null).setPositiveButton("确认", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val reason = input.text.toString().trim()
            if (reason.isEmpty() || reason.length > 500) input.error = "请输入1～500字原因" else { action(reason); dialog.dismiss() }
        } }
        dialog.setOnDismissListener { restoreInput() }; dialog.show()
    }
    private fun leave() {
        if (imeOpen) { hideKeyboard(); return }
        if (engine.state.value.candidate == null && engine.state.value.queued == 0) { finish(); return }
        suspendInput()
        AlertDialog.Builder(this).setTitle("保留未处理输入").setMessage("原输入保留，重新打开本单处理；处理完成前不能切到另一单扫码。")
            .setNegativeButton("继续处理", null).setPositiveButton("保留并返回") { _, _ -> finish() }.setOnDismissListener { restoreInput() }.show()
    }
    private fun hideKeyboard() { getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(binding.manual.windowToken, 0); binding.manual.clearFocus() }
    private fun suspendInput() { dialogOpen = true; scanner.unregister(); engine.pause(); keys.reset() }
    private fun restoreInput() { dialogOpen = false; if (!isFinishing && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { engine.resume(id); scanner.register() } }
    override fun onResume() { super.onResume(); if (::scanner.isInitialized && !dialogOpen) { engine.resume(id); scanner.register() } }
    override fun onPause() { if (::scanner.isInitialized) { scanner.unregister(); engine.pause(); keys.reset() }; super.onPause() }
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (dialogOpen || !::binding.isInitialized) return super.dispatchKeyEvent(event)
        val command = keys.command(event) ?: return super.dispatchKeyEvent(event)
        if (keys.firstDown(event)) when (command) { "VALIDATE" -> manual(); "CONFIRM" -> confirm(); "DISCARD" -> discard() }
        return true
    }
}
