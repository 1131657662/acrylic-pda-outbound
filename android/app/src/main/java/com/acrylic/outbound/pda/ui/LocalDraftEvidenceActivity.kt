package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivitySubmissionBinding
import com.acrylic.outbound.pda.evidence.*
import kotlinx.coroutines.*

/** Preserves stopped originals; this screen has no business confirmation, release or delete action. */
class LocalDraftEvidenceActivity : ComponentActivity() {
    private lateinit var binding: ActivitySubmissionBinding
    private val app get() = application as PdaApplication
    private var id = ""
    private var busy = false
    private var stopped = false
    private var verified = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); id = intent.getStringExtra("clientOrderId").orEmpty()
        if (id.isEmpty()) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivitySubmissionBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        app.temporaryWork.pause()
        binding.back.text = "返回临时订单"; binding.back.setOnClickListener { if (!busy) finish() }
        binding.reason.visibility = View.GONE
        binding.freeze.text = "停止本单并保全原件"; binding.freeze.setOnClickListener { preserve() }
        binding.upload.text = "补传已停止的原件"; binding.upload.setOnClickListener { transfer() }
        binding.refresh.text = "重新核对本机资料"; binding.refresh.setOnClickListener { run { } }
        run { }
    }
    private fun preserve() {
        if (busy || !verified || stopped) return
        AlertDialog.Builder(this).setTitle("停止本单并保全原件")
            .setMessage("停止后本单不能继续扫码、修改计划或结单。已接收输入先落盘，未处理记录和原件保留；这不会取消业务、卸货或解除占用。")
            .setNegativeButton("返回核对", null).setPositiveButton("确认停止并保全") { _, _ ->
                run { app.temporaryWork.preserve(id).await() }
            }.show()
    }
    private fun transfer() {
        if (busy || !verified || !stopped) return
        run {
            app.localDraftRecovery.prepare(id)
            LocalDraftRecoveryWorker.schedule(app, id)
            app.localDraftRecovery.resume(id)
        }
    }
    private fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; verified = false; controls(); binding.error.text = ""
        lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { binding.error.text = error.message ?: "操作未确认，请保留原机和原件" }
            finally {
                try { render(withContext(Dispatchers.IO) { snapshot() }) }
                catch (error: Exception) {
                    verified = false; binding.status.text = "资料核验未完成 · 保留原机和原件 · 不得放行"
                    binding.error.text = error.message ?: "资料暂不可核验，请保留原件"
                }
                busy = false; controls()
            }
        }
    }
    private fun snapshot(): Snapshot {
        val store = app.temporaryStore(); val draft = store.load(id); store.check(id, false)
        val stop = store.db.localDraftStops().get(id)
        if (stop != null || draft.state == "EVIDENCE_STOPPED") LocalDraftStopRepository(store).read(id)
        if (store.db.localDraftTransfers().get(id) != null) app.localDraftRecovery.checked(id)
        return Snapshot(draft.vehicleNo, draft.temporaryNo, draft.state, store.db.localDraftQueue().get(id))
    }
    private fun render(value: Snapshot) {
        verified = true
        stopped = value.state == "EVIDENCE_STOPPED"
        binding.summary.text = "${value.vehicle.ifBlank { "未填车号" }} · ${value.number}"
        binding.status.text = if (stopped) "本单已停止 · 原件保留 · 不得放行" else "本机临时草稿 · 尚未停止"
        binding.notice.text = LocalDraftEvidenceNotice.text(stopped, value.queue)
    }
    private fun controls() {
        binding.back.isEnabled = !busy; binding.refresh.isEnabled = !busy
        binding.freeze.visibility = if (stopped) View.GONE else View.VISIBLE; binding.freeze.isEnabled = !busy && verified
        binding.upload.isEnabled = !busy && verified && stopped
    }
    private data class Snapshot(val vehicle: String, val number: String, val state: String, val queue: LocalDraftQueue?)
}
