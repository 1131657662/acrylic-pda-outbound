package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityTemporaryCancelBinding
import com.acrylic.outbound.pda.temporary.*
import kotlinx.coroutines.*
import org.json.JSONObject

/** An explicit local cancellation; a lost UI result is recovered by rereading the same retained order. */
class TemporaryCancellationActivity : ComponentActivity() {
    private lateinit var binding: ActivityTemporaryCancelBinding
    private val app get() = application as PdaApplication
    private var id = ""
    private var revision: Long? = null
    private var busy = false
    private var eligible = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getStringExtra("clientOrderId").orEmpty()
        if (id.isBlank()) { finish(); return }
        app.temporaryWork.pause()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityTemporaryCancelBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.layoutParams = (view.layoutParams as android.view.ViewGroup.MarginLayoutParams).apply {
                setMargins(bars.left, bars.top, bars.right, bars.bottom)
            }
            insets
        }
        binding.back.setOnClickListener { if (!busy) finish() }
        binding.retry.setOnClickListener { load() }
        binding.details.setOnClickListener { startActivity(Intent(this, TemporaryDetailsActivity::class.java).putExtra("clientOrderId", id)) }
        binding.cancelDraft.setOnClickListener { confirm() }
        load()
    }
    private fun load() = task {
        val store = app.temporaryStore()
        val row = withContext(Dispatchers.IO) {
            TemporaryDraftCancellation(store).read(id)
        }
        revision = row.revision
        val facts = withContext(Dispatchers.IO) {
            Triple(store.total(id).count, store.db.temporaryScans().guardCount(id), store.db.temporaryScans().events(id).filter { it.type == "TEMP_DRAFT_CANCELLED" })
        }
        binding.summary.text = "${row.temporaryNo}\n车号 ${row.vehicleNo.ifBlank { "未填" }}\n有效 ${facts.first} 包 · 本单占用 ${facts.second}\n原计划修订 ${row.revision}"
        eligible = row.state in setOf("DRAFT", "SCANNING") && row.workRound == 1L && facts.first == 0L && facts.second == 0L
        binding.status.text = when {
            row.state == "CANCELLED" && facts.third.size == 1 -> "已取消本机草稿；原记录保留，未作服务端取消\n原因：${JSONObject(facts.third.single().detail).getString("reason")}"
            row.state == "CANCELLED" -> "取消审计不完整，请保留原机核对"
            eligible -> "确认后先排空接收队列，再核对原身份、占用和全部未决输入。"
            else -> "本单不符合普通取消条件；请核对原批次并走主管处置，原记录保留。"
        }
    }
    private fun confirm() {
        if (busy || !eligible) return
        val reason = binding.reason.text.toString().trim()
        if (reason.isBlank() || reason.length > 500) { binding.reason.error = "请填写1～500字取消原因"; return }
        val input = TemporaryCancelInput(checkNotNull(revision), reason)
        AlertDialog.Builder(this).setTitle("取消本机零作业草稿？").setMessage("原计划、输入与审计保留；不会取消服务端批次、释放占用或确认出库。")
            .setNegativeButton("继续保留", null).setPositiveButton("确认本机取消") { _, _ ->
                task {
                    app.temporaryWork.cancelDraft(id, input).await()
                    eligible = false; binding.status.text = "已取消本机草稿；原记录保留，未作服务端取消\n原因：$reason"
                }
            }.show()
    }
    private fun task(action: suspend () -> Unit) {
        if (busy) return
        busy = true; controls()
        lifecycleScope.launch {
            try { action() }
            catch (error: Exception) { eligible = false; binding.status.text = "${error.message ?: "结果未取得"}；原记录保留，请重读原单核对。" }
            finally { busy = false; controls() }
        }
    }
    private fun controls() {
        binding.cancelDraft.isEnabled = !busy && eligible
        listOf(binding.back, binding.retry, binding.details, binding.reason).forEach { it.isEnabled = !busy }
    }
}
