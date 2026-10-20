package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.core.view.*
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityScanAdjustmentBinding
import com.acrylic.outbound.pda.databinding.DialogScanAdjustmentBinding
import com.acrylic.outbound.pda.device.ScannerReceiver
import kotlinx.coroutines.*

/** This scanner only locates an existing row. It never feeds the capture queue. */
class ScanAdjustmentActivity : ComponentActivity() {
    private lateinit var binding: ActivityScanAdjustmentBinding
    private lateinit var records: AdjustmentRecords
    private lateinit var scanner: ScannerReceiver
    private val history = arrayListOf(0L)
    private var next: Long? = null
    private var busy = false
    private var dialogOpen = false
    private var blockReason = ""
    private var filter = AdjustmentFilter()
    private var filterError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PdaApplication
        val assignment = intent.getStringExtra("assignmentId").orEmpty()
        val temporary = intent.getStringExtra("clientOrderId").orEmpty()
        if (assignment.isBlank() == temporary.isBlank()) { finish(); return }
        app.work.pause(); app.temporaryWork.pause()
        records = AdjustmentRecords(app, assignment, temporary)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityScanAdjustmentBinding.inflate(layoutInflater); setContentView(binding.root)
        insets(); configureStatusFilter(); actions()
        scanner = ScannerReceiver(this, { locate(it) }, { binding.status.text = it })
        savedInstanceState?.getLongArray("history")?.let { history.clear(); history.addAll(it.toList()) }
        filter = runCatching {
            AdjustmentFilter.parse(
                savedInstanceState?.getString("sequence").orEmpty(),
                savedInstanceState?.getString("product").orEmpty(),
                AdjustmentStatusFilter.fromCode(savedInstanceState?.getString("status"))
            )
        }.getOrDefault(AdjustmentFilter())
        binding.statusFilter.setSelection(AdjustmentStatusFilter.values().indexOf(filter.status))
        filterError = savedInstanceState?.getString("filterError")
        load()
    }
    private fun insets() {
        val horizontal = binding.root.paddingLeft
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom = maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
            view.setPadding(bars.left + horizontal, bars.top, bars.right + horizontal, bottom); insets
        }
    }
    private fun actions() {
        binding.back.setOnClickListener { if (!busy) finish() }
        binding.locate.setOnClickListener { locate(binding.barcode.text.toString()) }
        binding.previous.setOnClickListener { history.removeAt(history.lastIndex); load() }
        binding.next.setOnClickListener { next?.let { history.add(it); load() } }
        binding.filterRecords.setOnClickListener { filterRecords() }
        binding.clearFilter.setOnClickListener {
            binding.sequence.text.clear(); binding.productFilter.text.clear()
            binding.statusFilter.setSelection(0); filterRecords()
        }
    }
    private fun configureStatusFilter() {
        val labels = AdjustmentStatusFilter.values().map { it.label }
        binding.statusFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }
    private fun filterRecords() {
        if (busy || dialogOpen) return
        val parsed = runCatching {
            AdjustmentFilter.parse(
                binding.sequence.text.toString(), binding.productFilter.text.toString(),
                AdjustmentStatusFilter.fromPosition(binding.statusFilter.selectedItemPosition)
            )
        }
        if (parsed.isFailure) {
            history.clear(); history.add(0L); next = null
            filterError = parsed.exceptionOrNull()?.message
            show(emptyList()); binding.status.text = filterError; controls(); return
        }
        filter = parsed.getOrThrow(); filterError = null; history.clear(); history.add(0L)
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(binding.sequence.windowToken, 0)
        binding.sequence.clearFocus(); binding.productFilter.clearFocus(); load()
    }
    private fun load() = task {
        val page = withContext(Dispatchers.IO) {
            if (filterError == null) records.page(history.last(), filter) else AdjustmentPage(emptyList(), false)
        }
        next = page.rows.lastOrNull()?.sequence?.takeIf { page.hasMore }
        binding.title.text = withContext(Dispatchers.IO) { records.title() }
        blockReason = withContext(Dispatchers.IO) { records.blockReason() }
        show(page.rows); binding.status.text = blockReason.ifBlank { filterError ?: run {
            if (page.rows.isEmpty()) "没有匹配的原记录（${filter.status.label}），未新增扫码"
            else "${filter.status.label} · 每页最多20条 · 原序号保留 · 点击已保存包核对"
        } }
    }
    private fun locate(raw: String) {
        if (dialogOpen) return
        getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(binding.barcode.windowToken, 0)
        binding.barcode.clearFocus()
        task {
            val row = withContext(Dispatchers.IO) { records.locate(raw) }
            blockReason = withContext(Dispatchers.IO) { records.blockReason() }
            show(listOfNotNull(row)); next = null
            binding.status.text = (if (row == null) "本单没有此条码的原记录，未新增扫码" else "已定位原记录，未新增扫码") +
                blockReason.takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
        }
    }
    private fun show(rows: List<AdjustmentRow>) {
        binding.rows.removeAllViews()
        rows.forEach { row -> binding.rows.addView(Button(this).apply {
            text = row.label; textSize = 16f; isAllCaps = false
            isEnabled = row.status == "ACTIVE" && blockReason.isBlank()
            setOnClickListener { if (row.status == "ACTIVE" && blockReason.isBlank() && !busy) confirm(row) }
        }) }
    }
    private fun confirm(row: AdjustmentRow) {
        dialogOpen = true; scanner.unregister()
        val content = DialogScanAdjustmentBinding.inflate(layoutInflater)
        val reason = content.reason
        content.details.text = "${row.label}\n请核实本包已卸下或未装车。原输入、明细及序号保留。"
        val dialog = AlertDialog.Builder(this).setTitle("核对原包 ${row.sequence}")
            .setView(content.root).setNegativeButton("返回核对", null).setPositiveButton("确认调整", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = reason.text.toString().trim()
            if (value.isBlank() || value.length > 500) reason.error = "请填写1～500字实物核对原因"
            else { apply(row, value); dialog.dismiss() }
        } }
        dialog.setOnDismissListener {
            dialogOpen = false
            if (!isFinishing && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) scanner.register()
        }; dialog.show()
    }
    private fun apply(row: AdjustmentRow, reason: String) = task {
        records.adjust(row, reason)
        val updated = withContext(Dispatchers.IO) { records.locate(row.barcode) }
        show(listOfNotNull(updated)); next = null
        binding.status.text = "调整已保存到本机，原记录保留；不代表后台出库或车辆放行"
    }
    private fun task(action: suspend () -> Unit) {
        if (busy) return
        busy = true; controls()
        lifecycleScope.launch {
            try { action() }
            catch (error: Exception) { binding.status.text = "${error.message ?: "读取或调整失败"}；原记录保留，请核对后重试" }
            finally { busy = false; controls() }
        }
    }
    private fun controls() {
        binding.locate.isEnabled = !busy; binding.back.isEnabled = !busy
        binding.previous.isEnabled = !busy && history.size > 1; binding.next.isEnabled = !busy && next != null
        binding.statusFilter.isEnabled = !busy
        listOf(binding.sequence, binding.productFilter, binding.filterRecords, binding.clearFilter).forEach { it.isEnabled = !busy }
    }
    override fun onResume() { super.onResume(); if (::scanner.isInitialized && !dialogOpen) scanner.register() }
    override fun onPause() { if (::scanner.isInitialized) scanner.unregister(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLongArray("history", history.toLongArray())
        outState.putString("sequence", filter.sequence?.toString().orEmpty()); outState.putString("product", filter.product)
        outState.putString("status", filter.status.code)
        outState.putString("filterError", filterError)
        super.onSaveInstanceState(outState)
    }
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (::binding.isInitialized && !dialogOpen && event.keyCode == KeyEvent.KEYCODE_ENTER && binding.barcode.hasFocus()) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) locate(binding.barcode.text.toString())
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
