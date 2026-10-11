package com.acrylic.outbound.pda.ui

import android.content.Intent
import android.os.Bundle
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.view.*
import androidx.lifecycle.*
import com.acrylic.outbound.pda.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val model: PreparationViewModel by viewModels()
    private lateinit var cards: OrderCards
    private lateinit var temporary: TemporaryHomeCards
    private var page = "WORK"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getString("page") ?: "WORK"
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityMainBinding.inflate(layoutInflater); setContentView(binding.root)
        cards = OrderCards(binding.cards, model); temporary = TemporaryHomeCards(binding.temporaryCards)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val system = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(system.left, system.top, system.right, system.bottom); insets
        }
        loginActions(); navigationActions()
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect { render(it) } } }
    }
    private fun loginActions() {
        binding.login.setOnClickListener {
            val password = binding.password.text.toString(); binding.password.text.clear()
            model.login(binding.username.text.toString(), password)
        }
        binding.showPassword.setOnCheckedChangeListener { _, checked ->
            binding.password.transformationMethod = if (checked) null else PasswordTransformationMethod.getInstance()
            binding.password.setSelection(binding.password.length())
        }
        binding.pairing.setOnClickListener { startActivity(Intent(this, PairingActivity::class.java)) }
        binding.deviceSettings.setOnClickListener { startActivity(Intent(this, DeviceSettingsActivity::class.java)) }
        binding.appUpdate.setOnClickListener { startActivity(Intent(this, PdaUpdateActivity::class.java)) }
    }
    private fun navigationActions() {
        binding.workPage.setOnClickListener { select("WORK", "LOCAL") }
        binding.dataPage.setOnClickListener { select("DATA", "HISTORY") }
        binding.myPage.setOnClickListener { select("MY") }
        binding.refresh.setOnClickListener { model.refresh() }
        binding.localSection.setOnClickListener { model.selectSection("LOCAL") }
        binding.historySection.setOnClickListener { model.selectSection("HISTORY") }
        binding.abnormalSection.setOnClickListener { model.selectSection("ABNORMAL") }
        binding.availableSection.setOnClickListener { model.selectSection("AVAILABLE") }
        binding.loadMore.setOnClickListener { model.loadMore() }
        binding.temporaryOrders.setOnClickListener { startActivity(Intent(this, TemporaryOrdersActivity::class.java)) }
        binding.temporaryPreparation.setOnClickListener { startActivity(Intent(this, TemporaryPreparationActivity::class.java)) }
    }
    private fun select(next: String, section: String? = null) {
        page = next; section?.let(model::selectSection)
        render(model.state.value); binding.root.scrollTo(0, 0)
    }
    override fun onResume() { super.onResume(); model.reloadLocal() }
    override fun onSaveInstanceState(outState: Bundle) { outState.putString("page", page); super.onSaveInstanceState(outState) }
    private fun render(state: PreparationUi) {
        val position = binding.root.scrollY
        val loggedIn = state.bound && state.username.isNotBlank() && !state.loginRequired
        renderIdentity(state, loggedIn); renderPages(state, loggedIn); renderControls(state, loggedIn)
        if (loggedIn && page != "MY") cards.render(state)
        if (loggedIn && page == "WORK" && state.section == "LOCAL") temporary.render(state)
        binding.root.post { binding.root.scrollTo(0, position) }
    }
    private fun renderIdentity(state: PreparationUi, loggedIn: Boolean) {
        binding.title.text = if (loggedIn) "${when (page) { "DATA" -> "数据"; "MY" -> "我的"; else -> "作业" }} · ${state.username}"
            else if (state.bound) "人员登录" else "设备登记"
        binding.environment.text = com.acrylic.outbound.pda.BuildConfig.PDA_ENVIRONMENT.ifBlank { "环境未配置" }
        binding.environment.visibility = if (!loggedIn || page == "MY") View.VISIBLE else View.GONE
        binding.status.text = state.message
        binding.status.visibility = if (!loggedIn || state.busy || state.message.contains("失败") ||
            state.message.contains("拒绝") || state.message.contains("未完成") || state.message.contains("待核对")) View.VISIBLE else View.GONE
        binding.diagnostics.text = state.message
        binding.diagnostics.visibility = if (loggedIn && page != "WORK") View.VISIBLE else View.GONE
        binding.identity.text = (if (!loggedIn) "本机资料保留，请登录" else "当前人员：${state.username}") + state.deviceLabel
        binding.progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        binding.setup.visibility = if (state.bound) View.GONE else View.VISIBLE
        binding.loginPanel.visibility = if (state.bound && !loggedIn) View.VISIBLE else View.GONE
    }
    private fun renderPages(state: PreparationUi, loggedIn: Boolean) {
        binding.navigation.visibility = if (loggedIn) View.VISIBLE else View.GONE
        binding.actions.visibility = if (loggedIn && page == "WORK") View.VISIBLE else View.GONE
        binding.dataActions.visibility = if (loggedIn && page == "DATA") View.VISIBLE else View.GONE
        binding.myActions.visibility = if (!loggedIn || page == "MY") View.VISIBLE else View.GONE
        val onlyTemporary = page == "WORK" && state.section == "LOCAL" && state.local.isEmpty() && state.pending.isEmpty() && state.assigned.isEmpty() && state.temporaryLocal.isNotEmpty()
        binding.cards.visibility = if (loggedIn && page != "MY" && !onlyTemporary) View.VISIBLE else View.GONE
        binding.temporaryCards.visibility = if (loggedIn && page == "WORK" && state.section == "LOCAL") View.VISIBLE else View.GONE
        binding.availableSection.visibility = if (state.formalOrders) View.VISIBLE else View.GONE
        binding.temporaryPreparation.visibility = if (state.temporaryVisible) View.VISIBLE else View.GONE
        binding.temporaryOrders.visibility = binding.temporaryPreparation.visibility
        binding.workPage.isSelected = page == "WORK"; binding.dataPage.isSelected = page == "DATA"; binding.myPage.isSelected = page == "MY"
    }
    private fun renderControls(state: PreparationUi, loggedIn: Boolean) {
        listOf(binding.login, binding.username, binding.password, binding.refresh, binding.temporaryOrders, binding.temporaryPreparation,
            binding.localSection, binding.historySection, binding.abnormalSection, binding.availableSection, binding.loadMore,
            binding.workPage, binding.dataPage, binding.myPage).forEach { it.isEnabled = !state.busy }
        binding.localSection.isSelected = state.section == "LOCAL"; binding.historySection.isSelected = state.section == "HISTORY"
        binding.abnormalSection.isSelected = state.section == "ABNORMAL"; binding.availableSection.isSelected = state.section == "AVAILABLE"
        binding.loadMore.visibility = if (loggedIn && page != "MY" && ((state.section == "AVAILABLE" && state.availableHasMore) ||
            (state.section in setOf("LOCAL", "HISTORY") && state.localHasMore) || (state.section == "LOCAL" && state.assignedHasMore) ||
            (state.section == "ABNORMAL" && state.abnormalHasMore))) View.VISIBLE else View.GONE
    }
}
