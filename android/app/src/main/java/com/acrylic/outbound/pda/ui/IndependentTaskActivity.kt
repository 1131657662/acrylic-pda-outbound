package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.view.*
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.*
import com.acrylic.outbound.pda.databinding.ActivityIndependentTaskBinding
import kotlinx.coroutines.launch

class IndependentTaskActivity : ComponentActivity() {
    private lateinit var binding: ActivityIndependentTaskBinding
    private val model: IndependentTaskViewModel by viewModels()
    private var renderedId: String? = null
    private var productIds = emptyList<String>()
    private var historyIds = emptyList<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityIndependentTaskBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        actions()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) } }
    }
    private fun actions() {
        binding.vehicle.doAfterTextChanged { model.fields(it.toString(), binding.reason.text.toString()) }
        binding.reason.doAfterTextChanged { model.fields(binding.vehicle.text.toString(), it.toString()) }
        binding.add.setOnClickListener { model.add(binding.product.selectedItemPosition, binding.quantity.text.toString()) }
        binding.remove.setOnClickListener { model.remove(binding.product.selectedItemPosition) }
        binding.create.setOnClickListener {
            model.fields(binding.vehicle.text.toString(), binding.reason.text.toString())
            val secret = binding.password.text.toString().toCharArray(); binding.password.text.clear(); model.create(secret)
        }
        binding.newTask.setOnClickListener { model.newDraft() }
        binding.reload.setOnClickListener { model.reload() }
        binding.historyLatest.setOnClickListener { model.history(false) }
        binding.historyOlder.setOnClickListener { model.history(true) }
        binding.openHistory.setOnClickListener { model.open(binding.history.selectedItemPosition) }
        binding.back.setOnClickListener { finish() }
    }
    private fun render(state: IndependentTaskUi) {
        binding.status.text = state.message
        if (renderedId != state.draft?.id) {
            renderedId = state.draft?.id
            binding.vehicle.setText(state.draft?.vehicle.orEmpty()); binding.reason.setText(state.draft?.reason.orEmpty())
            binding.quantity.text.clear(); binding.password.text.clear()
        }
        binding.summary.text = IndependentTaskDisplay.summary(state)
        binding.lines.text = IndependentTaskDisplay.lines(state)
        binding.original.text = IndependentTaskDisplay.original(state)
        val editable = !state.busy && !state.locked && state.draft != null
        listOf(binding.vehicle, binding.reason, binding.quantity, binding.product, binding.add, binding.remove).forEach { it.isEnabled = editable }
        binding.password.isEnabled = !state.busy && state.task == null
        binding.create.isEnabled = !state.busy && state.task == null && state.draft != null
        binding.create.text = if (state.locked) "核验口令，按原请求重试创建" else "核验口令并保存独立计划"
        listOf(binding.newTask, binding.reload, binding.historyLatest, binding.back).forEach { it.isEnabled = !state.busy }
        renderSelections(state)
    }
    private fun renderSelections(state: IndependentTaskUi) {
        val codes = state.products.map { it.deliveryCode }
        if (codes != productIds) {
            productIds = codes
            binding.product.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                state.products.map { "${it.deliveryCode} · ${it.model} · ${it.name}" })
        }
        val ids = state.history.map { it.request.id }
        if (ids != historyIds) {
            historyIds = ids
            binding.history.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                state.history.map { IndependentTaskDisplay.request(it.request) })
        }
        binding.history.isEnabled = !state.busy
        binding.historyOlder.isEnabled = !state.busy && ids.size == 25
        binding.openHistory.isEnabled = !state.busy && ids.isNotEmpty()
    }
}
