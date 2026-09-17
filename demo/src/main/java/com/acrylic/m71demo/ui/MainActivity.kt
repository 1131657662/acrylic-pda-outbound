package com.acrylic.m71demo.ui

import android.os.Bundle
import android.view.*
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.*
import com.acrylic.m71demo.databinding.ActivityMainBinding
import com.acrylic.m71demo.device.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    val model: DemoViewModel by viewModels()
    lateinit var binding: ActivityMainBinding
        private set
    lateinit var keys: KeyBinding
        private set
    private lateinit var renderer: ScreenRenderer
    private lateinit var receiver: ScannerReceiver
    private lateinit var dialogs: DemoDialogs
    private lateinit var keyboardGuard: KeyboardVisibilityGuard
    private var manual = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val gap = (12 * resources.displayMetrics.density).toInt()
            view.setPadding(bars.left + gap, bars.top + gap, bars.right + gap, bars.bottom)
            insets
        }
        renderer = ScreenRenderer(binding)
        keys = KeyBinding(this, model.engine.hardware.telemetry)
        dialogs = DemoDialogs(this)
        keyboardGuard = KeyboardVisibilityGuard(this, binding.manualInput) { manual }.also { it.attach() }
        receiver = ScannerReceiver(this, model.engine.hardware.telemetry, ::onBarcode)
        binding.manualButton.setOnClickListener { manualAction() }
        binding.auxButton.setOnClickListener { dialogs.auxiliary() }
        binding.menuButton.setOnClickListener { dialogs.menu() }
        binding.confirmButton.setOnClickListener { model.confirm(renderer.displayedCapture) }
        observe()
    }

    private fun observe() = lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { model.state.collect { renderer.render(it) } }
            launch { while (isActive) {
                withContext(Dispatchers.IO) { model.engine.hardware.telemetry.memory() }
                delay(1000)
            } }
        }
    }

    private fun onBarcode(raw: String, source: String, captureId: String?) {
        if (manual || !hasWindowFocus()) {
            model.engine.hardware.telemetry.event("INPUT_REFUSED", mapOf("reason" to "MANUAL_OR_DIALOG"))
            model.engine.hardware.sound.play("ERROR")
            return
        }
        model.receive(raw, source, captureId)
    }

    fun manualAction() {
        if (!manual) {
            if (model.state.value.pending > 0) { notify("先处理当前候选"); return }
            manual = true
            binding.manualInput.visibility = View.VISIBLE
            binding.manualInput.requestFocus()
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .showSoftInput(binding.manualInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            return
        }
        val text = binding.manualInput.text.toString()
        if (text.isBlank()) return
        model.receive(text, "MANUAL")
        manual = false
        binding.manualInput.text.clear()
        getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(binding.manualInput.windowToken, 0)
        binding.manualInput.visibility = View.GONE
    }

    // Public Android Activity callback; the inherited AndroidX forwarding method is annotated restricted.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::keys.isInitialized) return super.dispatchKeyEvent(event)
        val action = runCatching { keys.action(event) }.getOrElse { notify(it.message.orEmpty()); return true }
        when (action) {
            "CONFIRM" -> if (!manual) model.confirm(renderer.displayedCapture) else notify("手输请先按K1/F1校验")
            "VALIDATE" -> manualAction()
            "AUXILIARY" -> dialogs.auxiliary()
            "BACK" -> onBackPressedDispatcher.onBackPressed()
            "UP", "REPEAT" -> if (keys.isCommand(event)) return true else return super.dispatchKeyEvent(event)
            else -> return super.dispatchKeyEvent(event)
        }
        return true
    }

    fun notify(text: String) = runOnUiThread { android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_LONG).show() }
    override fun onResume() {
        super.onResume()
        if (::keys.isInitialized) keys.reload()
        if (::receiver.isInitialized) receiver.register()
    }

    fun openKeyWizard() = model.engine.enqueue {
        check(!model.tools.benchmark.running && model.state.value.pending == 0 && model.state.value.candidate == null) {
            "请先结束压测并处理当前条码"
        }
        runOnUiThread { startActivity(android.content.Intent(this, KeyCheckActivity::class.java)) }
    }
    override fun onPause() {
        if (::receiver.isInitialized) receiver.unregister()
        if (::keys.isInitialized) keys.reset()
        model.tools.benchmark.stop()
        super.onPause()
    }

    override fun onDestroy() {
        if (::keyboardGuard.isInitialized) keyboardGuard.detach()
        super.onDestroy()
    }
}
