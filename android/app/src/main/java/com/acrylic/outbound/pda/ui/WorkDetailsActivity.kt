package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityWorkDetailsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WorkDetailsActivity : ComponentActivity() {
    private lateinit var binding: ActivityWorkDetailsBinding
    private val app get() = application as PdaApplication
    private var assignment = ""
    private var packageRows = emptyList<com.acrylic.outbound.pda.work.WorkScan>()
    private var pendingRows = emptyList<com.acrylic.outbound.pda.work.WorkCapture>()
    private var packageCursor = 0L
    private var pendingCursor = 0L
    private var hasMore = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assignment = intent.getStringExtra("assignmentId").orEmpty()
        if (assignment.isBlank()) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityWorkDetailsBinding.inflate(layoutInflater); setContentView(binding.root)
        binding.back.setOnClickListener { finish() }
        binding.adjustPackages.setOnClickListener {
            startActivity(android.content.Intent(this, ScanAdjustmentActivity::class.java).putExtra("assignmentId", assignment))
        }
        binding.adjustPackages.isEnabled = false
        binding.loadMore.setOnClickListener { loadPage() }
        loadPage()
    }

    private fun loadPage() {
        if (!hasMore) return
        binding.loadMore.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                val work = app.database.work(); val captures = app.database.captures()
                Triple(work.order(assignment), work.lines(assignment),
                    captures.unresolvedPage(assignment, pendingCursor, 20) to app.database.scans().page(assignment, packageCursor, 20))
            }
            val order = result.first
            if (order == null) { finish(); return@launch }
            val block = withContext(Dispatchers.IO) { AdjustmentRecords(app, assignment, "").blockReason() }
            binding.adjustPackages.isEnabled = block.isBlank()
            binding.adjustPackages.text = if (block.isBlank()) "调整已扫码包" else "调整不可用 · $block"
            packageRows = packageRows + result.third.second
            pendingRows = pendingRows + result.third.first
            packageCursor = packageRows.maxOfOrNull { it.orderScanSeq } ?: packageCursor
            pendingCursor = pendingRows.maxOfOrNull { it.context.orderScanSeq } ?: pendingCursor
            hasMore = result.third.first.size == 20 || result.third.second.size == 20
            binding.loadMore.visibility = if (hasMore) android.view.View.VISIBLE else android.view.View.GONE
            binding.loadMore.isEnabled = hasMore
            binding.summary.text = "${order.vehicleNo}\n实装 ${order.actualCount} / ${order.plannedTotal} 包 · ${WorkRenderer.kg(order.weightGrams)} kg"
            binding.products.text = "产品汇总\n" + result.second.joinToString("\n") { "${it.deliveryCode}  ${it.actualCount}/${it.plannedCount} 包 · ${WorkRenderer.kg(it.weightGrams)} kg" }
            binding.packages.text = "包明细（保留原扫码序号）\n" + packageRows.joinToString("\n") { "${it.orderScanSeq}. ${it.barcode} · ${WorkRenderer.kg(it.weightGrams)} kg · ${if (it.status == "VOID") "已撤销" else "已保存"}" }
            binding.pending.text = "未处理输入：${pendingRows.size} 条" + if (pendingRows.isNotEmpty()) "\n" + pendingRows.joinToString("\n") { "${it.context.orderScanSeq}. ${it.context.barcode} · ${it.state}" } else ""
        }
    }
}
