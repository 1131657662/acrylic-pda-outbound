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
    private fun render(state: TemporaryDraftUi) {
        binding.status.text = listOf(state.draft?.temporaryNo.orEmpty(), state.message).filter { it.isNotBlank() }.joinToString("\n")
        if (!initialized && state.draft != null) {
            initialized = true
            binding.vehicle.setText(restored?.getString("vehicle") ?: state.draft.vehicleNo)
            binding.container.setText(restored?.getString("container") ?: state.draft.containerNo)
            binding.reason.setText(restored?.getString("reason") ?: state.draft.reason)
            binding.product.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, state.products.map { "${it.code} · ${it.model} · ${it.name}" })
            state.lines.forEach { selected[it.deliveryCode] = it.plannedCount.toString() }
            restored?.getString("lines")?.let { json -> selected.clear(); JSONObject(json).let { obj -> obj.keys().forEach { selected[it] = obj.getString(it) } } }
            renderLines()
        }
        val enabled = !state.busy && state.draft?.state == "DRAFT"
        listOf(binding.vehicle, binding.container, binding.reason, binding.product, binding.count, binding.add, binding.start).forEach { it.isEnabled = enabled }
        binding.save.isEnabled = !state.busy; binding.back.isEnabled = !state.busy
        binding.save.text = if (state.draft == null) "恢复原草稿" else "保存草稿"
        if (!state.busy && state.draft?.state in setOf("SCANNING", "FROZEN", "EVIDENCE_STOPPED") && !opened) {
            val target = when (state.draft?.state) { "FROZEN" -> TemporarySubmissionActivity::class.java; "EVIDENCE_STOPPED" -> LocalDraftEvidenceActivity::class.java; else -> TemporaryWorkActivity::class.java }
            opened = true; startActivity(Intent(this, target).putExtra("clientOrderId", model.id)); finish()
        }
    }
    private fun add() {
        val product = model.state.value.products.getOrNull(binding.product.selectedItemPosition) ?: return
        val count = binding.count.text.toString()
        if (!count.matches(Regex("[1-9][0-9]{0,8}"))) { binding.count.error = "请输入正整数计划包数"; return }
        selected[product.code] = count; binding.count.text.clear(); renderLines()
    }
    private fun renderLines() {
        binding.lines.removeAllViews()
        selected.forEach { (code, count) -> binding.lines.addView(Button(this).apply {
            val product = model.state.value.products.firstOrNull { it.code == code }
            text = "$code · ${product?.model.orEmpty()} · $count 包（点击移除）"
            setOnClickListener { if (!model.state.value.busy) { selected.remove(code); renderLines() } }
        }) }
    }
    private fun save(start: Boolean) {
        val draft = model.state.value.draft ?: run { model.retry(); return }
        model.save(TemporaryPlanInput(draft.revision, binding.vehicle.text.toString(), binding.container.text.toString(),
            binding.reason.text.toString(), selected.toList()), start)
    }
    private fun leave() {
        if (model.state.value.busy) return
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
