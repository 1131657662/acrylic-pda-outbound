package com.acrylic.outbound.pda.ui

import android.content.ClipData
import android.content.ClipboardManager
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
import com.acrylic.outbound.pda.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val model: PreparationViewModel by viewModels()
    private lateinit var cards: OrderCards

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        cards = OrderCards(binding.cards, model)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(system.left, system.top, system.right, system.bottom); insets
        }
        binding.bind.setOnClickListener { model.bind(binding.server.text.toString(), binding.device.text.toString()) }
        binding.login.setOnClickListener {
            val password = binding.password.text.toString(); binding.password.text.clear()
            model.login(binding.username.text.toString(), password)
        }
        binding.refresh.setOnClickListener { model.refresh() }; binding.logout.setOnClickListener { model.logout() }
        binding.copyKey.setOnClickListener { copyKey() }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { render(it) } } }
    }

    private fun render(state: PreparationUi) {
        binding.status.text = state.message
        binding.progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        binding.setup.visibility = if (state.bound) View.GONE else View.VISIBLE
        binding.actions.visibility = if (state.username.isNotBlank()) View.VISIBLE else View.GONE
        binding.loginPanel.visibility = if (state.bound && state.username.isBlank()) View.VISIBLE else View.GONE
        binding.identity.text = if (state.username.isBlank()) "未登录 · 原订单资料保留" else "当前人员：${state.username}"
        listOf(binding.login, binding.bind, binding.copyKey, binding.refresh, binding.logout).forEach { it.isEnabled = !state.busy }
        cards.render(state)
    }

    private fun copyKey() {
        lifecycleScope.launch {
            try {
                val key = model.publicKey()
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PDA登记公钥", key))
                binding.status.text = "公钥已复制，请交管理员登记。此公钥不包含私钥。"
            } catch (error: Exception) { binding.status.text = error.message ?: "无法读取设备身份" }
        }
    }
}
