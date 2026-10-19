package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.*
import androidx.lifecycle.*
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import com.acrylic.outbound.pda.device.ScannerReceiver
import com.acrylic.outbound.pda.device.WorkKeys
import com.acrylic.outbound.pda.independent.IndependentWorkRuntime
import kotlinx.coroutines.launch

class IndependentWorkActivity : ComponentActivity() {
    private lateinit var binding: ActivityIndependentWorkBinding
    private val engine get() = IndependentWorkRuntime.shared(application as PdaApplication)
    private lateinit var scanner: ScannerReceiver
    private lateinit var display: IndependentWorkDisplay
    private lateinit var panels: IndependentWorkPanels
    private val keys = WorkKeys()
    private var id = ""
    private var imeOpen = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); id = intent.getStringExtra("taskId").orEmpty()
        if (id.isEmpty()) { finish(); return }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityIndependentWorkBinding.inflate(layoutInflater); setContentView(binding.root)
        display = IndependentWorkDisplay(binding)
        panels = IndependentWorkPanels(binding).also { it.bind() }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()); imeOpen = insets.isVisible(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            view.setPadding(bars.left, bars.top, bars.right, bottom); insets
        }
        scanner = ScannerReceiver(this, { engine.receive(it, "BROADCAST") }, engine::stop)
        actions(); corrections()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            engine.state.collect { display.render(it, engine.scanning, (application as PdaApplication).workSound.error) }
        } }
    }
    private fun actions() {
        binding.workValidate.setOnClickListener { manual() }
        binding.workConfirm.setOnClickListener { confirm() }
        binding.workAuthorize.setOnClickListener {
            val secret = binding.workPassword.text.toString().toCharArray(); binding.workPassword.text.clear()
            engine.resume(id); engine.authorize(id, secret); scanner.register()
        }
        binding.workPause.setOnClickListener {
            if (engine.scanning) { scanner.unregister(); engine.pause(); keys.reset(); display.render(engine.state.value, false) }
            else { engine.resume(id); scanner.register() }
        }
        binding.workLatest.setOnClickListener { engine.page(false) }
        binding.workOlder.setOnClickListener { engine.page(true) }
        binding.workBack.setOnClickListener { finish() }
    }
    private fun corrections() {
        binding.workAcknowledge.setOnClickListener { engine.state.value.candidate?.let { engine.acknowledge(it.capture.id) } }
        binding.workDiscard.setOnClickListener { discard() }
        binding.workLocate.setOnClickListener { engine.locate(binding.workLocateBarcode.text.toString()) }
        binding.workVoid.setOnClickListener {
            if (!binding.workUnloaded.isChecked) { binding.workSelected.text = "请先核实实物已卸下或未装车，并勾选确认。"; return@setOnClickListener }
            engine.state.value.selected?.let { engine.voidSelected(it.scan.captureId, binding.workReason.text.toString()) }
            binding.workUnloaded.isChecked = false
        }
        binding.workFinish.setOnClickListener {
            scanner.unregister()
            engine.finish(binding.workFinishReason.text.toString(), binding.workFinishConfirmed.isChecked)
            binding.workFinishConfirmed.isChecked = false
        }
    }
    private fun manual() {
        if (engine.receive(binding.workManual.text.toString(), "MANUAL")) panels.acceptedManual()
    }
    private fun confirm() { engine.state.value.candidate?.let { engine.confirm(it.capture.id) } }
    private fun discard() { engine.state.value.candidate?.let { engine.discard(it.capture.id, binding.workReason.text.toString()) } }
    // The public platform Activity callback is inherited through AndroidX core's restricted bridge.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val command = keys.command(event)
        if (command != null && !imeOpen) {
            if (keys.firstDown(event)) when (command) { "VALIDATE" -> manual(); "CONFIRM" -> confirm(); "DISCARD" -> discard() }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
    override fun onResume() { super.onResume(); if (::scanner.isInitialized) { engine.resume(id); scanner.register() } }
    override fun onPause() {
        if (::scanner.isInitialized) { scanner.unregister(); engine.pause(); keys.reset(); binding.workPassword.text.clear() }
        super.onPause()
    }
}
