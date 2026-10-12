package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityTemporaryOrdersBinding
import kotlinx.coroutines.*

/** Read-only keyset paging, including VOID evidence; never changes original capture order. */
class TemporaryDetailsActivity : ComponentActivity() {
    private lateinit var binding: ActivityTemporaryOrdersBinding
    private val history = arrayListOf(0L)
    private var next: Long? = null
    private var busy = false
    private var id = ""
    private var adjustmentBlock = "正在核对原单资格"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); id = intent.getStringExtra("clientOrderId").orEmpty()
        if (id.isEmpty()) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityTemporaryOrdersBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding.create.text = "调整已扫码包"
        (binding.create.parent as android.widget.LinearLayout).addView(android.widget.Button(this).apply {
            text = "取消本机零作业草稿"
            setOnClickListener { startActivity(android.content.Intent(this@TemporaryDetailsActivity, TemporaryCancellationActivity::class.java).putExtra("clientOrderId", id)) }
        }, 5)
        binding.rescueExport.visibility = android.view.View.VISIBLE
        binding.rescueExport.setOnClickListener {
            startActivity(android.content.Intent(this, RescueExportActivity::class.java).putExtra("source", "TEMP").putExtra("localOrderId", id))
        }
        binding.create.setOnClickListener { startActivity(android.content.Intent(this, ScanAdjustmentActivity::class.java).putExtra("clientOrderId", id)) }
        binding.back.setOnClickListener { finish() }; binding.retry.setOnClickListener { load() }
        binding.previous.setOnClickListener { history.removeAt(history.lastIndex); load() }
        binding.next.setOnClickListener { next?.let { history.add(it); load() } }
        savedInstanceState?.getLongArray("history")?.let { history.clear(); history.addAll(it.toList()) }
        load()
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putLongArray("history", history.toLongArray()); super.onSaveInstanceState(outState) }
    private fun load() {
        if (busy) return
        busy = true; controls(false); binding.orders.removeAllViews()
        lifecycleScope.launch {
            try {
                val text = withContext(Dispatchers.IO) { details() }
                binding.status.text = "本机原记录 · 每页最多50条 · 撤销保留序号"
                binding.orders.addView(TextView(this@TemporaryDetailsActivity).apply { this.text = text; textSize = 18f; setTextIsSelectable(true) })
            } catch (error: Exception) { binding.status.text = error.message ?: "读取失败，请重试" }
            finally { busy = false; controls(true) }
        }
    }
    private fun details(): String {
        val store = (application as PdaApplication).temporaryStore(); val row = store.load(id)
        adjustmentBlock = AdjustmentRecords(application as PdaApplication, "", id).blockReason()
        val lines = store.db.temporaryDrafts().lines(id); val scans = store.db.temporaryScans()
        val counts = store.products(id).associateBy { it.deliveryCode }; val page = store.page(id, history.last())
        next = page.lastOrNull()?.orderScanSeq?.takeIf { store.page(id, it).isNotEmpty() }
        return "${row.temporaryNo}\n车号 ${row.vehicleNo} · 柜号 ${row.containerNo.ifBlank { "未填" }}\n原因 ${row.reason}\n" +
            lines.joinToString("\n") { "${it.deliveryCode} · ${it.model} · ${it.name}：${counts[it.deliveryCode]?.count ?: 0}/${it.plannedCount} 包" } +
            "\n\n原扫码明细\n" + page.joinToString("\n") { "${it.orderScanSeq}. ${it.barcode} · ${WorkRenderer.kg(it.weightGrams)} kg${if (it.status == "VOID") " · 已撤销" else ""}" }
    }
    private fun controls(enabled: Boolean) {
        binding.create.isEnabled = enabled && adjustmentBlock.isBlank()
        binding.create.text = if (adjustmentBlock.isBlank()) "调整已扫码包" else "调整不可用 · $adjustmentBlock"
        binding.retry.isEnabled = enabled; binding.previous.isEnabled = enabled && history.size > 1
        binding.next.isEnabled = enabled && next != null
    }
}
