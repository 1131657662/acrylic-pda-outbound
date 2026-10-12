package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.*
import com.acrylic.outbound.pda.databinding.ActivityRescueRequestBinding
import kotlinx.coroutines.launch

class RescueRequestActivity : ComponentActivity() {
    private lateinit var binding: ActivityRescueRequestBinding
    private val model: RescueRequestViewModel by viewModels()
    private var orders = emptyList<String>()
    private var requests = emptyList<String>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityRescueRequestBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        actions()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) } }
    }
    override fun onResume() { super.onResume(); model.load() }
    override fun onStop() { model.hide(); super.onStop() }
    private fun actions() {
        binding.prepare.setOnClickListener { model.prepare(binding.order.selectedItemPosition, binding.rescuer.text.toString(), binding.reason.text.toString()) }
        binding.send.setOnClickListener { model.send(binding.request.selectedItemPosition) }
        binding.refresh.setOnClickListener { model.refresh(binding.request.selectedItemPosition) }
        binding.showMaterial.setOnClickListener { model.showMaterial(binding.request.selectedItemPosition) }
        binding.nextPage.setOnClickListener { model.nextPage() }
        binding.back.setOnClickListener { finish() }
        binding.request.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { model.show(position) }
        }
    }
    private fun render(state: RescueRequestUi) {
        val orderLabels = state.orders.map { it.label }
        if (orders != orderLabels) {
            orders = orderLabels; binding.order.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, orders)
        }
        val requestLabels = state.requests.map { "${it.target.source} · ${it.target.localOrderId} · ${it.key}" }
        if (requests != requestLabels) {
            requests = requestLabels; binding.request.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, requests)
            binding.request.setSelection(requests.lastIndex.coerceAtLeast(0))
        }
        binding.status.text = state.message; binding.detail.text = state.detail
        binding.prepare.isEnabled = !state.busy && state.orders.isNotEmpty()
        binding.send.isEnabled = !state.busy && state.requests.isNotEmpty()
        binding.refresh.isEnabled = !state.busy && state.requests.isNotEmpty()
        binding.showMaterial.isEnabled = !state.busy && state.requests.isNotEmpty()
        binding.order.isEnabled = !state.busy; binding.request.isEnabled = !state.busy
        binding.rescuer.isEnabled = !state.busy; binding.reason.isEnabled = !state.busy
        binding.nextPage.visibility = if (state.nextCursor == null) View.GONE else View.VISIBLE
        binding.nextPage.isEnabled = !state.busy
    }
}
