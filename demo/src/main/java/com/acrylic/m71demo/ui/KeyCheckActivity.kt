package com.acrylic.m71demo.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.acrylic.m71demo.databinding.ActivityKeyCheckBinding
import com.acrylic.m71demo.device.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/** This screen never creates ScanEngine or sends an input to the outbound workflow. */
@AndroidEntryPoint
class KeyCheckActivity : ComponentActivity() {
    @Inject lateinit var telemetry: Telemetry
    private lateinit var view: ActivityKeyCheckBinding
    private lateinit var renderer: KeyWizardRenderer
    private lateinit var store: KeyCalibrationStore
    private lateinit var scanner: ScannerReceiver
    private var session = KeyWizardSession()
    private var armed = false
    private var busy = false
    private var applied = false
    private var error = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        view = ActivityKeyCheckBinding.inflate(layoutInflater)
        setContentView(view.root)
        renderer = KeyWizardRenderer(view)
        store = KeyCalibrationStore(this)
        configureInsets()
        scanner = ScannerReceiver(this, telemetry) { raw, source, _ ->
            telemetry.event("KEY_WIZARD_BARCODE_IGNORED", mapOf("runId" to session.id, "rawInput" to raw.take(128), "source" to source))
        }
        view.primary.setOnClickListener { primary() }
        view.previous.setOnClickListener { changeStep { session.previous() } }
        view.retry.setOnClickListener { changeStep { session.retryCurrent() } }
        view.leave.setOnClickListener { if (!busy) finish() }
        perform({ session = store.loadDraft() ?: KeyWizardSession()
            applied = store.activeProfile()?.id == session.id }) { render() }
    }

    private fun configureInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view.root) { root, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val gap = (12 * resources.displayMetrics.density).toInt()
            root.setPadding(bars.left + gap, bars.top + gap, bars.right + gap, bars.bottom)
            insets
        }
    }

    private fun primary() {
        if (busy || error.isNotBlank()) return
        if (applied) {
            applied = false; armed = false; session = KeyWizardSession()
            perform({ store.saveDraft(session) }) { render() }; return
        }
        if (session.complete) { applyProfile(); return }
        session.pause()
        perform({ store.saveDraft(session) }) {
            armed = hasWindowFocus()
            telemetry.event("KEY_WIZARD_STARTED", mapOf("runId" to session.id, "samples" to session.samples.size))
            render()
        }
    }

    private fun changeStep(change: () -> Unit) {
        if (busy || applied || error.isNotBlank()) return
        telemetry.event("KEY_WIZARD_RETEST", mapOf("runId" to session.id, "previousSamples" to session.samples.size))
        change()
        perform({ store.saveDraft(session) }) { render() }
    }

    private fun applyProfile() {
        val completed = KeyWizardSession(session.id, session.samples.toMutableList())
        perform({ store.apply(completed) }) {
        applied = true; armed = false
        telemetry.event("KEY_PROFILE_APPLIED", mapOf("runId" to completed.id))
        // The private active file is authoritative; failure to copy a report must not undo activation.
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { store.archive(completed, "APPLIED") }.onFailure {
                telemetry.event("KEY_REPORT_COPY_FAILED", mapOf("runId" to completed.id))
            }
        }
        render()
        }
    }

    private fun perform(work: () -> Unit, done: () -> Unit = {}) {
        busy = true; render()
        lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { work() }; busy = false; done(); render() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                busy = false; armed = false; error = "进度或配置保存失败，保留原数据，请退出后重试"
                telemetry.event("KEY_WIZARD_STORAGE_ERROR", mapOf("error" to e.javaClass.simpleName)); render()
            }
        }
    }

    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::renderer.isInitialized) return super.dispatchKeyEvent(event)
        val stroke = KeyStroke(event.keyCode, event.scanCode, event.deviceId, event.source, event.action,
            event.repeatCount, event.eventTime, event.downTime, event.device?.isVirtual == false)
        telemetry.event("KEY_WIZARD_EVENT", mapOf("runId" to session.id, "expected" to session.role?.name,
            "armed" to armed, "busy" to busy, "event" to KeyWizardJson.stroke(stroke).toString()))
        if (armed && !busy && error.isBlank()) {
            if (session.accept(stroke)) perform({ store.saveDraft(session) }) {
                if (session.complete) armed = false
                render()
            } else render()
        }
        return true
    }

    private fun render() {
        if (!::renderer.isInitialized) return
        if (armed) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        renderer.render(session, armed, busy, applied, error)
    }

    override fun onResume() { super.onResume(); if (::scanner.isInitialized) scanner.register() }
    override fun onPause() {
        armed = false; session.pause()
        if (::scanner.isInitialized) scanner.unregister()
        telemetry.event("KEY_WIZARD_PAUSED", mapOf("runId" to session.id, "samples" to session.samples.size))
        render(); super.onPause()
    }
}
