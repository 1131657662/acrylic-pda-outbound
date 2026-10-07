package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityTemporaryContinuationBinding
import com.acrylic.outbound.pda.temporary.*
import kotlinx.coroutines.*

class TemporaryContinuationActivity : ComponentActivity() {
    private lateinit var binding: ActivityTemporaryContinuationBinding
    private val app get() = application as PdaApplication
    private var id = ""
    private var ticket: String? = null
    private var draft: TemporaryDraft? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); id = intent.getStringExtra("clientOrderId").orEmpty()
        if (id.isEmpty()) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityTemporaryContinuationBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding.back.setOnClickListener { finish() }
        binding.confirm.setOnClickListener { confirm() }
        binding.refresh.setOnClickListener { run { ticket?.let { app.temporaryContinuationCoordinator.resume(it) } } }
        binding.open.setOnClickListener { startActivity(Intent(this, if (draft?.state == "FROZEN")
            TemporarySubmissionActivity::class.java else TemporaryWorkActivity::class.java).putExtra("clientOrderId", id)) }
    }
    override fun onResume() { super.onResume(); run {} }

    private fun confirm() {
        if (busy || ticket == null) return
        AlertDialog.Builder(this).setTitle("恢复原计划装车").setMessage("请核对车号和原计划，确认保留包仍在本车。确认请求将保存，网络恢复后只继续这个原请求。")
            .setNegativeButton("返回核对", null).setPositiveButton("确认恢复") { _, _ -> run {
                val key = checkNotNull(ticket)
                app.temporaryContinuationCoordinator.queue(key)
                TemporaryContinuationWorker.schedule(app, key)
                app.temporaryContinuationCoordinator.resume(key)
            } }.show()
    }

    private fun run(action: () -> Unit) {
        if (busy) return
        busy = true; controls(); binding.error.text = ""
        lifecycleScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    val store = app.temporaryStore(); draft = store.load(id); store.check(id, false)
                    ticket = checkNotNull(app.database.temporaryContinuations().forTarget(id)).ticketId
                    action(); draft = store.load(id)
                    val lines = app.database.temporaryDrafts().lines(id)
                    "${draft!!.vehicleNo} · ${draft!!.temporaryNo}\n" + lines.joinToString("\n") { "${it.deliveryCode} · ${it.name}：计划 ${it.plannedCount} 包" }
                }
                binding.summary.text = summary
                val queued = withContext(Dispatchers.IO) { ticket?.let { app.database.temporaryContinuationQueue().get(it) } }
                binding.error.text = queued?.error.orEmpty()
                binding.status.text = when (draft?.state) {
                    "SCANNING" -> "续作资料已核验 · 可继续本机装车"
                    "FROZEN" -> "本轮已冻结 · 核对原上传批次"
                    else -> if (!BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT) "恢复装车入口尚未启用 · 原记录保留"
                        else if (queued?.autoRetry == true) "确认请求已保存 · 网络恢复后核对原请求" else "续作待确认 · 原记录保留"
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                draft = null; ticket = null
                binding.status.text = "原请求尚未确认 · 原记录保留"
                binding.error.text = error.message ?: "请返回后重新核对原请求"
            }
            finally { busy = false; controls() }
        }
    }

    private fun controls() {
        binding.confirm.isEnabled = !busy && ticket != null && draft?.state == "CONTINUATION_BLOCKED" && BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT
        binding.refresh.isEnabled = !busy && ticket != null && BuildConfig.TEMPORARY_CONTINUATION_DEVELOPMENT
        binding.open.isEnabled = !busy && (draft?.canScan() == true || draft?.state == "FROZEN")
    }
}
