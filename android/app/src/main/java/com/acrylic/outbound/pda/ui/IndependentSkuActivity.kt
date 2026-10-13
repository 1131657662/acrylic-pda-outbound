package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.*
import androidx.lifecycle.*
import com.acrylic.outbound.pda.databinding.ActivityIndependentSkuBinding
import kotlinx.coroutines.launch

class IndependentSkuActivity : ComponentActivity() {
    private lateinit var binding: ActivityIndependentSkuBinding
    private val model: IndependentSkuViewModel by viewModels()
    private var renderedImport: String? = null
    private var historyIds = emptyList<String>()
    private val selectFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) model.pickerCancelled() else model.choose(uri)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityIndependentSkuBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        actions()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) } }
    }
    private fun actions() {
        binding.choose.setOnClickListener { selectFile.launch(arrayOf("text/plain", "application/octet-stream")) }
        binding.active.setOnClickListener { model.showActive() }
        binding.reload.setOnClickListener { model.load() }
        binding.historyLatest.setOnClickListener { model.history(false) }
        binding.historyOlder.setOnClickListener { model.history(true) }
        binding.openHistory.setOnClickListener { model.openHistory(binding.history.selectedItemPosition) }
        binding.issuesOnly.setOnCheckedChangeListener { _, checked -> model.issuesOnly(checked) }
        binding.activate.setOnClickListener { model.activate(binding.confirmDuplicates.isChecked) }
        binding.cancel.setOnClickListener { model.cancel() }
        binding.previous.setOnClickListener { model.page(-1) }
        binding.next.setOnClickListener { model.page(1) }
        binding.back.setOnClickListener { finish() }
    }
    private fun render(state: IndependentSkuUi) {
        val review = state.review; val id = review?.imported?.id
        if (renderedImport != id) { binding.confirmDuplicates.isChecked = false; renderedImport = id }
        binding.status.text = state.message
        binding.summary.text = IndependentSkuDisplay.summary(review)
        binding.issuesOnly.isChecked = state.issuesOnly
        binding.issuesOnly.isEnabled = !state.busy
        binding.rows.text = IndependentSkuDisplay.rows(review, state.page, state.issuesOnly)
        binding.original.text = review?.let { "本机导入编号 ${it.imported.id}\n原件 SHA-256 ${it.imported.sha256}" }.orEmpty()
        binding.progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        binding.confirmDuplicates.visibility = if ((review?.preview?.duplicateRows ?: 0) > 0) View.VISIBLE else View.GONE
        binding.confirmDuplicates.isEnabled = !state.busy && review?.decision == null
        binding.activate.isEnabled = !state.busy && review?.canActivate == true
        binding.cancel.isEnabled = !state.busy && review != null && review.decision == null
        listOf(binding.choose, binding.active, binding.reload).forEach { it.isEnabled = !state.busy }
        renderHistory(state)
        val count = IndependentSkuDisplay.filtered(review, state.issuesOnly).size
        binding.previous.isEnabled = !state.busy && state.page > 0
        binding.next.isEnabled = !state.busy && (state.page + 1) * 25 < count
    }
    private fun renderHistory(state: IndependentSkuUi) {
        val ids = state.history.map { it.imported.id }
        if (historyIds != ids) {
            historyIds = ids
            binding.history.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
                state.history.map { "${it.imported.displayName} · ${it.imported.id}" })
        }
        binding.historyLatest.isEnabled = !state.busy
        binding.historyOlder.isEnabled = !state.busy && state.history.size == 25
        binding.openHistory.isEnabled = !state.busy && ids.isNotEmpty()
        binding.history.isEnabled = !state.busy
    }
}
