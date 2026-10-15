package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.lifecycle.*
import androidx.core.widget.doAfterTextChanged
import com.acrylic.outbound.pda.databinding.ActivityTemporaryDraftBinding
import com.acrylic.outbound.pda.temporary.TemporaryPlanInput
import kotlinx.coroutines.launch
import org.json.JSONObject

class TemporaryDraftActivity : ComponentActivity() {
    private lateinit var binding: ActivityTemporaryDraftBinding
    private val model: TemporaryDraftViewModel by viewModels()
    private val selected = linkedMapOf<String, String>()
    private var initialized = false
    private var opened = false
    private var restored: Bundle? = null
    private var visibleProducts = emptyList<com.acrylic.outbound.pda.temporary.TemporaryProduct>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); restored = savedInstanceState
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityTemporaryDraftBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding.back.setOnClickListener { leave() }
        (binding.back.parent as LinearLayout).addView(Button(this).apply {
            text = "取消本机零作业草稿"
            setOnClickListener { startActivity(Intent(this@TemporaryDraftActivity, TemporaryCancellationActivity::class.java).putExtra("clientOrderId", model.id)) }
        }, 3)
        binding.add.setOnClickListener { add() }
        binding.productSearch.doAfterTextChanged { binding.productSearch.error = null; filterProducts() }
        binding.count.doAfterTextChanged { binding.count.error = null }
        editorActions()
        listOf(binding.vehicle, binding.container, binding.reason).forEach { field -> field.doAfterTextChanged { showDirty() } }
        binding.save.setOnClickListener { save(false) }; binding.start.setOnClickListener { save(true) }
        (binding.start.parent as LinearLayout).addView(Button(this).apply {
            text = "停止并保全已保存草稿"
            setOnClickListener {
                if (!model.state.value.busy && model.state.value.draft != null)
                    startActivity(Intent(this@TemporaryDraftActivity, LocalDraftEvidenceActivity::class.java).putExtra("clientOrderId", model.id))
            }
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) { override fun handleOnBackPressed() = leave() })
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) } }
    }
    private fun editorActions() {
        listOf(binding.productSearch, binding.count).forEach { field -> field.setOnEditorActionListener { _, action, _ ->
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) false else {
                field.clearFocus()
                getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
                true
            }
        } }
    }
    private fun render(state: TemporaryDraftUi) {
        binding.status.text = listOf(state.draft?.temporaryNo.orEmpty(), state.message).filter { it.isNotBlank() }.joinToString("\n")
        if (!initialized && state.draft != null) {
            initialized = true
            binding.vehicle.setText(restored?.getString("vehicle") ?: state.draft.vehicleNo)
            binding.container.setText(restored?.getString("container") ?: state.draft.containerNo)
            binding.reason.setText(restored?.getString("reason") ?: state.draft.reason)
            filterProducts()
            state.lines.forEach { selected[it.deliveryCode] = it.plannedCount.toString() }
            restored?.getString("lines")?.let { json -> selected.clear(); JSONObject(json).let { obj -> obj.keys().forEach { selected[it] = obj.getString(it) } } }
            renderLines()
        }
        val enabled = !state.busy && state.draft?.state == "DRAFT"
        listOf(binding.vehicle, binding.container, binding.reason, binding.productSearch, binding.product, binding.count, binding.add, binding.start).forEach { it.isEnabled = enabled }
        binding.save.isEnabled = !state.busy; binding.back.isEnabled = !state.busy
        binding.save.text = if (state.draft == null) "恢复原草稿" else "保存草稿"
        showDirty()
        if (!state.busy && state.draft?.state in setOf("SCANNING", "FROZEN", "EVIDENCE_STOPPED") && !opened) {
            val target = when (state.draft?.state) { "FROZEN" -> TemporarySubmissionActivity::class.java; "EVIDENCE_STOPPED" -> LocalDraftEvidenceActivity::class.java; else -> TemporaryWorkActivity::class.java }
            opened = true; startActivity(Intent(this, target).putExtra("clientOrderId", model.id)); finish()
        }
    }
    private fun add() {
        val product = visibleProducts.getOrNull(binding.product.selectedItemPosition) ?: run {
            binding.productSearch.error = "请选择匹配产品后添加"; return
        }
        val count = binding.count.text.toString()
        if (!count.matches(Regex("[1-9][0-9]{0,8}"))) { binding.count.error = "请输入正整数计划包数"; return }
        selected[product.code] = count; binding.count.text.clear(); renderLines(); showDirty()
    }
    private fun filterProducts() {
        visibleProducts = TemporaryProductSearch.filter(model.state.value.products, binding.productSearch.text.toString())
        binding.product.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            visibleProducts.map { "${it.code} · ${it.model} · ${it.name}" }.ifEmpty { listOf("没有匹配产品，请修改搜索") })
    }
    private fun renderLines() {
        binding.lines.removeAllViews()
        selected.forEach { (code, count) -> binding.lines.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val product = model.state.value.products.firstOrNull { it.code == code }
            addView(TextView(context).apply { text = "$code · ${product?.model.orEmpty()} · ${product?.name.orEmpty()} · $count 包"; textSize = 16f })
            addView(Button(context).apply { text = "修改 $code 数量"; setOnClickListener { editCount(code, count) } })
            addView(Button(context).apply { text = "移除产品 $code"; setOnClickListener { remove(code) } })
        }) }
    }
    private fun editCount(code: String, count: String) {
        if (model.state.value.busy || model.state.value.draft?.state != "DRAFT") return
        val input = EditText(this).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(count) }
        val dialog = AlertDialog.Builder(this).setTitle("修改 $code 计划包数").setView(input)
            .setNegativeButton("返回", null).setPositiveButton("保存页面修改", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = input.text.toString()
            if (!value.matches(Regex("[1-9][0-9]{0,8}"))) input.error = "请输入正整数计划包数"
            else { selected[code] = value; renderLines(); showDirty(); dialog.dismiss() }
        } }; dialog.show()
    }
    private fun remove(code: String) {
        if (model.state.value.busy || model.state.value.draft?.state != "DRAFT") return
        AlertDialog.Builder(this).setTitle("移除产品 $code？").setMessage("只修改尚未开始扫码的计划，保存草稿后生效。")
            .setNegativeButton("保留", null).setPositiveButton("确认移除") { _, _ -> selected.remove(code); renderLines(); showDirty() }.show()
    }
    private fun dirty(): Boolean {
        val row = model.state.value.draft ?: return false
        return initialized && (binding.vehicle.text.toString() != row.vehicleNo || binding.container.text.toString() != row.containerNo ||
            binding.reason.text.toString() != row.reason || selected != model.state.value.lines.associate { it.deliveryCode to it.plannedCount.toString() })
    }
    private fun showDirty() { binding.dirty.visibility = if (dirty()) android.view.View.VISIBLE else android.view.View.GONE }
    private fun save(start: Boolean) {
        val draft = model.state.value.draft ?: run { model.retry(); return }
        model.save(TemporaryPlanInput(draft.revision, binding.vehicle.text.toString(), binding.container.text.toString(),
            binding.reason.text.toString(), selected.toList()), start)
    }
    private fun leave() {
        if (model.state.value.busy) return
        if (!dirty()) { finish(); return }
        AlertDialog.Builder(this).setTitle("返回临时订单")
            .setMessage("已保存的草稿和身份会保留；页面上尚未保存的修改会丢失。")
            .setNegativeButton("继续编辑", null).setPositiveButton("保留已存草稿并返回") { _, _ -> finish() }.show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("vehicle", binding.vehicle.text.toString()); outState.putString("container", binding.container.text.toString())
        outState.putString("reason", binding.reason.text.toString()); outState.putString("lines", JSONObject(selected.toMap()).toString())
        super.onSaveInstanceState(outState)
    }
}
