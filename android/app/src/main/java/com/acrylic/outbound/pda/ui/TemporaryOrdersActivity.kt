package com.acrylic.outbound.pda.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityTemporaryOrdersBinding
import kotlinx.coroutines.*

class TemporaryOrdersActivity : ComponentActivity() {
    private lateinit var binding: ActivityTemporaryOrdersBinding
    private val app get() = application as PdaApplication
    private val history = arrayListOf("")
    private var after = ""
    private var next = ""
    private var busy = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityTemporaryOrdersBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        savedInstanceState?.getStringArrayList("history")?.let { history.clear(); history.addAll(it) }
        after = history.last()
        binding.back.setOnClickListener { finish() }
        binding.create.setOnClickListener { binding.create.isEnabled = false; startActivity(Intent(this, TemporaryDraftActivity::class.java)) }
        binding.retry.setOnClickListener { load() }
        binding.next.setOnClickListener { history.add(next); after = next; load() }
        binding.previous.setOnClickListener { history.removeAt(history.lastIndex); after = history.last(); load() }
    }
    override fun onResume() { super.onResume(); load() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putStringArrayList("history", history); super.onSaveInstanceState(outState) }
    private fun load() {
        if (busy) return
        busy = true; controls(false); binding.orders.removeAllViews(); binding.status.text = "正在读取本机临时单"
        lifecycleScope.launch {
            try {
                val rows = withContext(Dispatchers.IO) {
                    val auth = app.session.requireSession()
                    app.database.temporaryDrafts().page(auth.getString("deviceId"), auth.getString("userId"), auth.getString("warehouseId"), after)
                }
                next = if (rows.size > 20) rows[19].clientOrderId else ""
                rows.take(20).forEach { row -> binding.orders.addView(Button(this@TemporaryOrdersActivity).apply {
                    text = "${row.vehicleNo.ifBlank { "未填车号" }} · ${when (row.state) { "DRAFT" -> "草稿"; "FROZEN" -> "已冻结 · 核对原提交"; "CONTINUATION_BLOCKED" -> "续作待确认"; "HANDOVER_BLOCKED" -> "交接新轮待核验"; "EVIDENCE_STOPPED" -> "已停止 · 原件保留待核对"; else -> "本机作业" }}\n${row.temporaryNo}"
                    setOnClickListener { startActivity(Intent(this@TemporaryOrdersActivity,
                        when (row.state) { "DRAFT" -> TemporaryDraftActivity::class.java; "FROZEN" -> TemporarySubmissionActivity::class.java; "CONTINUATION_BLOCKED" -> TemporaryContinuationActivity::class.java; "HANDOVER_BLOCKED" -> HandoverActivity::class.java; "EVIDENCE_STOPPED" -> LocalDraftEvidenceActivity::class.java; else -> TemporaryWorkActivity::class.java }).putExtra("clientOrderId", row.clientOrderId)) }
                }) }
                binding.status.text = if (rows.isEmpty()) "当前账号没有本机临时单" else "本页 ${minOf(rows.size, 20)} 单 · 原记录持续保留"
            } catch (error: Exception) { binding.status.text = error.message ?: "读取失败，请重试"; next = "" }
            finally { busy = false; controls(true) }
        }
    }
    private fun controls(enabled: Boolean) {
        binding.create.isEnabled = enabled; binding.retry.isEnabled = enabled
        binding.next.isEnabled = enabled && next.isNotEmpty(); binding.previous.isEnabled = enabled && history.size > 1
    }
}
