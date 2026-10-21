package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.view.*
import androidx.lifecycle.*
import com.acrylic.outbound.pda.databinding.ActivityIndependentCredentialBinding
import kotlinx.coroutines.launch

class IndependentCredentialActivity : ComponentActivity() {
    private lateinit var binding: ActivityIndependentCredentialBinding
    private val model: IndependentCredentialViewModel by viewModels()
    private var setupActive = false
    private val generatedSetup = IndependentSetupFlow(this, { model.reload(it) }, {
        setupActive = it
        if (::binding.isInitialized) render(model.state.value)
    })
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityIndependentCredentialBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom)); insets
        }
        binding.generateSetup.setOnClickListener { if (!model.state.value.busy) generatedSetup.show() }
        binding.provision.setOnClickListener { model.provision(password()) }
        binding.verify.setOnClickListener { model.verify(password()) }
        binding.revoke.setOnClickListener { model.revoke() }
        binding.reload.setOnClickListener { model.reload() }
        binding.back.setOnClickListener { finish() }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) } }
    }
    override fun onDestroy() { generatedSetup.close(); super.onDestroy() }
    private fun password(): CharArray = binding.password.text.toString().toCharArray().also { binding.password.text.clear() }
    private fun render(state: IndependentCredentialUi) {
        binding.status.text = state.message; binding.original.text = state.original
        listOf(binding.provision, binding.verify, binding.revoke, binding.reload, binding.password, binding.generateSetup).forEach { it.isEnabled = !state.busy && !setupActive }
    }
}
