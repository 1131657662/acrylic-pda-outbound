package com.acrylic.outbound.pda.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityWorkDetailsBinding
import com.acrylic.outbound.pda.work.*
import kotlinx.coroutines.*

class WorkDetailsActivity : ComponentActivity() {
    private lateinit var binding: ActivityWorkDetailsBinding
    private val app get() = application as PdaApplication
    private var assignment = ""
    private var filter = WorkDetailFilter.ALL
    private var page: WorkDetailsPage? = null
    private var anchor = 0L
    private var busy = false
    private var adjustmentBlock = "正在核对原单资格"
    private val reader by lazy {
        val store = WorkStore(app.database, SessionWorkAccess(app, app.session))
        WorkDetailsReader(app.database, assignment) { store.access.identity(store.prepared(assignment)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assignment = intent.getStringExtra("assignmentId").orEmpty()
        if (assignment.isBlank()) { finish(); return }
        anchor = savedInstanceState?.getLong("anchor") ?: 0L
        filter = WorkDetailFilter.entries.firstOrNull { it.name == savedInstanceState?.getString("filter") } ?: WorkDetailFilter.ALL
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityWorkDetailsBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom); insets
        }
        bindActions(); bindFilters()
    }

    override fun onResume() { super.onResume(); if (::binding.isInitialized) load(anchor) }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("anchor", anchor); outState.putString("filter", filter.name)
        super.onSaveInstanceState(outState)
    }

    private fun bindActions() {
        binding.back.setOnClickListener { finish() }
        binding.rescueExport.setOnClickListener {
            startActivity(Intent(this, RescueExportActivity::class.java).putExtra("source", "FORMAL").putExtra("localOrderId", assignment))
        }
        binding.adjustPackages.setOnClickListener {
            startActivity(Intent(this, ScanAdjustmentActivity::class.java).putExtra("assignmentId", assignment))
        }
        binding.previous.setOnClickListener { page?.rows?.firstOrNull()?.let { load(it.sequence, backwards = true) } }
        binding.next.setOnClickListener { page?.rows?.lastOrNull()?.let { load(it.sequence) } }
        binding.first.setOnClickListener { load() }
        binding.retry.setOnClickListener { load(anchor) }
        binding.locate.setOnClickListener { load(locate = true) }
        binding.qualificationDetails.setOnClickListener {
            android.app.AlertDialog.Builder(this).setTitle("资格核对原始原因")
                .setMessage(adjustmentBlock).setPositiveButton("返回", null).show()
        }
        controls(false)
    }

    private fun bindFilters() {
        binding.filter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, WorkDetailFilter.entries.map { it.label })
        binding.filter.setSelection(filter.ordinal)
        binding.locateMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("原序号", "完整条码"))
        binding.filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = WorkDetailFilter.entries[position]
                if (selected != filter) { filter = selected; load() }
            }
        }
    }

    private fun load(cursor: Long = 0, backwards: Boolean = false, locate: Boolean = false) {
        if (busy) return
        busy = true; controls(false)
        val raw = binding.locateInput.text.toString()
        val byBarcode = binding.locateMode.selectedItemPosition == 1
        binding.status.text = "正在读取本机原记录"
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val start = if (locate) checkNotNull(reader.locate(raw, byBarcode)) { "没有匹配原记录，请核对原序号或条码" }.sequence - 1 else cursor
                    reader.page(if (locate) WorkDetailFilter.ALL else filter, start, backwards).also {
                        adjustmentBlock = AdjustmentRecords(app, assignment, "").blockReason()
                    }
                }
                if (locate) { filter = WorkDetailFilter.ALL; binding.filter.setSelection(filter.ordinal) }
                render(result)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                page = null; binding.rows.removeAllViews(); binding.summary.text = ""; binding.products.text = ""
                adjustmentBlock = "请先重新核对原单"
                binding.status.text = "读取未完成，原记录保留。${error.message.orEmpty()}"
            } finally { busy = false; controls(true) }
        }
    }

    private fun render(result: WorkDetailsPage) {
        page = result; anchor = result.rows.firstOrNull()?.sequence?.minus(1) ?: 0L
        binding.summary.text = WorkDetailsText.summary(result)
        binding.products.text = "本单产品汇总\n" + result.products.joinToString("\n") {
            "${it.deliveryCode} · ${it.model} · ${it.name}\n${it.actualCount}/${it.plannedCount} 包 · ${WorkRenderer.kg(it.weightGrams)} kg"
        }
        binding.status.text = "只读原记录 · 每页最多20条\n${filter.label} · 本页 ${result.rows.size} 条" +
            (result.rows.firstOrNull()?.let { " · 原序号 ${it.sequence}–${result.rows.last().sequence}" } ?: " · 暂无匹配记录")
        binding.rows.removeAllViews()
        result.rows.forEach { row -> binding.rows.addView(TextView(this).apply {
            text = WorkDetailsText.row(row); textSize = 18f; setTextIsSelectable(true)
            setPadding(0, (12 * resources.displayMetrics.density).toInt(), 0, 0)
        }) }
    }

    private fun controls(enabled: Boolean) {
        binding.filter.isEnabled = enabled; binding.locateMode.isEnabled = enabled
        binding.locate.isEnabled = enabled; binding.retry.isEnabled = enabled; binding.first.isEnabled = enabled
        binding.previous.isEnabled = enabled && page?.previous == true
        binding.next.isEnabled = enabled && page?.next == true
        binding.adjustPackages.isEnabled = enabled && adjustmentBlock.isBlank()
        binding.adjustPackages.text = WorkDetailsText.adjustment(adjustmentBlock)
        binding.qualificationDetails.visibility = if (adjustmentBlock.isBlank()) View.GONE else View.VISIBLE
        binding.qualificationDetails.isEnabled = enabled
    }
}
