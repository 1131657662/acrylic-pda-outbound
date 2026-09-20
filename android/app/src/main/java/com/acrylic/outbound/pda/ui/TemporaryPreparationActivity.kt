package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.acrylic.outbound.pda.databinding.ActivityTemporaryPreparationBinding
import kotlinx.coroutines.launch

class TemporaryPreparationActivity : ComponentActivity() {
    private lateinit var binding: ActivityTemporaryPreparationBinding
    private val model: TemporaryPreparationViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityTemporaryPreparationBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(system.left, system.top, system.right, system.bottom); insets
        }
        binding.back.setOnClickListener { finish() }
        binding.prepare.setOnClickListener { model.prepare() }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) } }
    }
    override fun onResume() { super.onResume(); model.reload() }
    private fun render(state: TemporaryUi) {
        binding.status.text = state.status; binding.details.text = state.details
        binding.prepare.text = state.action; binding.prepare.isEnabled = state.allowed && !state.busy
        binding.progress.visibility = if (state.busy) View.VISIBLE else View.GONE
    }
}
