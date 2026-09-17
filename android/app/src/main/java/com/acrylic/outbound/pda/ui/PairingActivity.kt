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
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.databinding.ActivityPairingBinding
import com.acrylic.outbound.pda.pairing.PairingUi
import com.acrylic.outbound.pda.pairing.PairingViewModel
import kotlinx.coroutines.launch

class PairingActivity : ComponentActivity() {
    private lateinit var binding: ActivityPairingBinding
    private val model: PairingViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityPairingBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding.startPairing.setOnClickListener { model.start() }; binding.retryPairing.setOnClickListener { model.recover() }
        binding.confirmPairing.setOnClickListener { model.confirm() }; binding.cancelPairing.setOnClickListener { model.cancel() }
        binding.backToLogin.setOnClickListener { finish() }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { model.state.collect { render(it) } }; launch { model.pollWhileVisible() }
        } }
    }
    private fun render(ui: PairingUi) {
        val result = ui.saved?.optJSONObject("result"); val status = result?.optString("status") ?: "NEW"
        val operation = ui.saved?.optString("operation").orEmpty()
        binding.environment.text = "${BuildConfig.PDA_ENVIRONMENT}\n${ui.saved?.optString("server") ?: BuildConfig.PDA_SERVER_URL.ifBlank { "服务地址尚未配置，请联系管理员配置安装包" }}"
        binding.pairingStatus.text = if (ui.error.isNotEmpty()) "结果待核对：${ui.error}" else statusText(status, ui.bound, operation)
        binding.pairingCode.text = if (status in setOf("WAITING", "PENDING")) result!!.getString("shortCode").chunked(4).joinToString(" ") else ""
        binding.expiry.text = if (status in setOf("WAITING", "PENDING")) "有效至 ${java.time.Instant.parse(result!!.getString("expiresAt")).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0)}" else ""
        val approval = result?.optJSONObject("approval")
        binding.proposedIdentity.text = if (approval == null) "请管理员在同一后台的设备管理中输入登记码。" else
            "仓库：${approval.getString("warehouseName")}\n设备：${approval.getString("name")}\n编号：${approval.getString("deviceCode") }"
        controls(ui, status, operation)
    }
    private fun controls(ui: PairingUi, status: String, operation: String) {
        binding.progress.visibility = if (ui.busy) View.VISIBLE else View.GONE
        binding.startPairing.visibility = if (ui.saved == null || status in setOf("EXPIRED", "CANCELLED")) View.VISIBLE else View.GONE
        binding.startPairing.isEnabled = !ui.busy && BuildConfig.PDA_SERVER_URL.isNotBlank() && ui.error.isEmpty() && !ui.bound
        binding.retryPairing.visibility = if (ui.saved != null && !ui.bound) View.VISIBLE else View.GONE
        binding.confirmPairing.visibility = if (status == "PENDING") View.VISIBLE else View.GONE
        binding.cancelPairing.visibility = if (status in setOf("WAITING", "PENDING")) View.VISIBLE else View.GONE
        binding.retryPairing.isEnabled = !ui.busy
        binding.confirmPairing.isEnabled = !ui.busy && ui.error.isEmpty() && operation.isEmpty()
        binding.cancelPairing.isEnabled = !ui.busy && operation.isEmpty()
        binding.backToLogin.text = if (ui.bound) "进入人员登录" else "返回"
    }
    private fun statusText(status: String, bound: Boolean, operation: String): String {
        if (operation.isNotEmpty()) return "原登记操作待核对，请恢复原请求"
        return when (status) {
            "WAITING" -> "等待管理员核对登记码"
            "PENDING" -> "管理员已确认，请核对下方信息"
            "COMPLETED" -> if (bound) "本机绑定已保存，请使用人员账号登录" else "后台已登记，本机绑定待保存，请恢复原请求"
            "EXPIRED" -> "原申请已过期，可用原密钥重新申请"
            "CANCELLED" -> "原申请已取消，可重新申请"
            else -> "确认服务环境后开始登记；首次登记需要联网"
        }
    }
}
