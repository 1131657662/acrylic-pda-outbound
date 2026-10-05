package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.text.method.PasswordTransformationMethod
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
        binding.login.setOnClickListener {
            val password = binding.password.text.toString(); binding.password.text.clear()
            model.login(binding.username.text.toString(), password)
        }
        binding.showPassword.setOnCheckedChangeListener { _, checked ->
            binding.password.transformationMethod = if (checked) null else PasswordTransformationMethod.getInstance()
            binding.password.setSelection(binding.password.length())
        }
        binding.refresh.setOnClickListener { model.refresh() }
        binding.localSection.setOnClickListener { model.selectSection("LOCAL") }
        binding.historySection.setOnClickListener { model.selectSection("HISTORY") }
        binding.abnormalSection.setOnClickListener { model.selectSection("ABNORMAL") }
        binding.availableSection.setOnClickListener { model.selectSection("AVAILABLE") }
        binding.loadMore.setOnClickListener { model.loadMore() }
        binding.pairing.setOnClickListener { startActivity(android.content.Intent(this, PairingActivity::class.java)) }
        binding.deviceSettings.setOnClickListener { startActivity(android.content.Intent(this, DeviceSettingsActivity::class.java)) }
        binding.appUpdate.setOnClickListener { startActivity(android.content.Intent(this, PdaUpdateActivity::class.java)) }
        binding.temporaryPreparation.setOnClickListener { startActivity(android.content.Intent(this, TemporaryPreparationActivity::class.java)) }
        binding.handover.setOnClickListener { startActivity(android.content.Intent(this, HandoverActivity::class.java)) }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { render(it) } } }
    }

    override fun onResume() { super.onResume(); model.reloadLocal() }

    private fun render(state: PreparationUi) {
        val scrollPosition = binding.root.scrollY
        val loggedIn = state.bound && state.username.isNotBlank() && !state.loginRequired
        binding.title.text = if (loggedIn) "装车订单" else if (state.bound) "人员登录" else "设备登记"
        binding.environment.text = com.acrylic.outbound.pda.BuildConfig.PDA_ENVIRONMENT.ifBlank { "环境未配置" }
        binding.environment.visibility = if (loggedIn) View.GONE else View.VISIBLE
        binding.status.text = state.message
        binding.progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        binding.setup.visibility = if (state.bound) View.GONE else View.VISIBLE
        binding.actions.visibility = if (loggedIn) View.VISIBLE else View.GONE
        binding.loginPanel.visibility = if (state.bound && !loggedIn) View.VISIBLE else View.GONE
        binding.cards.visibility = if (loggedIn) View.VISIBLE else View.GONE
        binding.identity.text = (if (!loggedIn) "本机资料保留，请登录" else "当前人员：${state.username}") + state.deviceLabel
        listOf(binding.login, binding.username, binding.password, binding.refresh,
            binding.localSection, binding.historySection, binding.abnormalSection, binding.availableSection, binding.loadMore).forEach { it.isEnabled = !state.busy }
        binding.localSection.isSelected = state.section == "LOCAL"
        binding.historySection.isSelected = state.section == "HISTORY"
        binding.abnormalSection.isSelected = state.section == "ABNORMAL"
        binding.availableSection.isSelected = state.section == "AVAILABLE"
        binding.availableSection.visibility = if (state.formalOrders) View.VISIBLE else View.GONE
        binding.temporaryPreparation.visibility = if (state.temporaryVisible) View.VISIBLE else View.GONE
        binding.temporaryPreparation.isEnabled = !state.busy
        binding.handover.isEnabled = !state.busy
        binding.loadMore.visibility = if (loggedIn && ((state.section == "AVAILABLE" && state.availableHasMore) ||
            (state.section in setOf("LOCAL", "HISTORY") && state.localHasMore) ||
            (state.section == "LOCAL" && state.assignedHasMore) ||
            (state.section == "ABNORMAL" && state.abnormalHasMore))) View.VISIBLE else View.GONE
        if (loggedIn) cards.render(state)
        binding.root.post { binding.root.scrollTo(0, scrollPosition) }
    }

}
