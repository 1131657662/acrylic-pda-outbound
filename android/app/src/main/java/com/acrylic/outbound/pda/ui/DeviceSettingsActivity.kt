package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityDeviceSettingsBinding
import kotlinx.coroutines.*

class DeviceSettingsActivity : ComponentActivity() {
    private lateinit var binding: ActivityDeviceSettingsBinding
    private val app get() = application as PdaApplication
    private var busy = false
    private var signedIn = false
    private var warehouseName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityDeviceSettingsBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        bindActions()
        loadIdentity()
    }

    private fun bindActions() {
        binding.back.setOnClickListener { if (!busy) finish() }
        binding.sound.isChecked = app.workSound.enabled
        binding.sound.setOnCheckedChangeListener { _, enabled -> app.workSound.enabled = enabled }
        binding.copyKey.setOnClickListener { copyKey() }
        binding.checkConnection.setOnClickListener { checkConnection() }
        binding.appUpdate.setOnClickListener { startActivity(android.content.Intent(this, PdaUpdateActivity::class.java)) }
        binding.logout.setOnClickListener { confirmLogout() }
        binding.advanced.setOnClickListener {
            binding.maintenance.visibility = if (binding.maintenance.visibility == View.GONE) View.VISIBLE else View.GONE
            binding.advanced.text = if (binding.maintenance.visibility == View.VISIBLE) "收起身份与版本详情" else "身份与版本详情"
        }
        binding.diagnosticDetailsToggle.setOnClickListener {
            val show = binding.diagnosticDetails.visibility == View.GONE
            binding.diagnosticDetails.visibility = if (show) View.VISIBLE else View.GONE
            binding.diagnosticDetailsToggle.text = if (show) "收起原始详情" else "展开原始详情"
        }
        binding.bind.setOnClickListener { bindDevice() }
        binding.retryIdentity.setOnClickListener { loadIdentity() }
    }

    private fun loadIdentity() = execute {
        val (saved, registration) = withContext(Dispatchers.IO) { app.session.saved() to app.pairing.saved() }
        signedIn = saved != null
        val settings = app.session.settings
        val result = registration?.optJSONObject("result")
        val identity = if (result?.optString("deviceId") == settings.device) result.optJSONObject("approval") else null
        warehouseName = identity?.optString("warehouseName").orEmpty()
        binding.environment.text = "环境：${BuildConfig.PDA_ENVIRONMENT.ifBlank { "未配置" }}"
        binding.version.text = "App 版本：${BuildConfig.VERSION_NAME}"
        binding.server.text = "服务地址：${settings.server.ifBlank { "尚未绑定" }}"
        binding.device.text = "设备标识：${settings.device.ifBlank { "尚未登记" }}"
        binding.deviceName.text = if (identity == null) "设备：${if (settings.device.isBlank()) "尚未登记" else "已绑定，可联网核对名称"}"
            else "设备：${identity.getString("name")}\n编号：${identity.getString("deviceCode")}\n仓库：${identity.getString("warehouseName")}"
        binding.identity.text = saved?.let { "当前人员：${it.optString("username")}" } ?: "当前未登录"
        binding.syncStatus.text = if (saved == null) "登录后将自动同步，原作业资料保留"
            else com.acrylic.outbound.pda.sync.SyncScheduleState(this).message()
        binding.bindFields.visibility = if (settings.device.isBlank()) View.VISIBLE else View.GONE
        showDiagnosis(ConnectionDiagnosis(when {
            saved?.has("blockedReason") == true -> "当前登录不可用，请返回人员登录。原作业资料保留。"
            saved == null -> ConnectionDiagnosis.signedOut().summary
            else -> "尚未检查后台连接。本机资料可读取不代表后台可达或作业资格有效。"
        }))
    }

    private fun setBusy(value: Boolean) {
        busy = value
        listOf(binding.back, binding.copyKey, binding.checkConnection, binding.bind, binding.retryIdentity, binding.appUpdate)
            .forEach { it.isEnabled = !value }
        binding.logout.isEnabled = signedIn && !value
    }

    private fun execute(action: suspend () -> Unit) {
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            var lease: AutoCloseable? = null
            try { lease = com.acrylic.outbound.pda.update.UpdateGate.enterBusiness(); action() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                showDiagnosis(ConnectionDiagnosis(error.message ?: "操作未完成，请保留资料并重试。"))
            } finally { lease?.close(); setBusy(false) }
        }
    }

    private fun copyKey() = execute {
        val key = withContext(Dispatchers.IO) {
            check(app.pairing.saved() == null || app.session.settings.device.isNotBlank()) { "请通过短码登记恢复原申请" }
            app.session.publicKey()
        }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PDA登记公钥", key))
        showDiagnosis(ConnectionDiagnosis("公钥已复制，不包含私钥。"))
    }

    private fun checkConnection() = execute {
        showDiagnosis(ConnectionDiagnosis("正在核对后台连接，请稍候"))
        val diagnosis = try {
            val device = withContext(Dispatchers.IO) {
                if (app.session.saved() == null) null else app.session.call("pda/devices/me")
            }
            if (device == null) ConnectionDiagnosis.signedOut() else {
                binding.deviceName.text = "设备：${device.getString("name")}\n编号：${device.getString("deviceCode")}" +
                    if (warehouseName.isNotBlank()) "\n仓库：$warehouseName" else ""
                ConnectionDiagnosis.success()
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            ConnectionDiagnosis.failure(error)
        }
        showDiagnosis(diagnosis)
    }

    private fun showDiagnosis(diagnosis: ConnectionDiagnosis) {
        binding.status.text = diagnosis.summary
        binding.diagnosticDetails.text = diagnosis.details
        binding.diagnosticDetails.visibility = View.GONE
        binding.diagnosticDetailsToggle.visibility = if (diagnosis.details.isEmpty()) View.GONE else View.VISIBLE
        binding.diagnosticDetailsToggle.text = "展开原始详情"
    }

    private fun bindDevice() {
        val server = binding.serverInput.text.toString()
        val device = binding.deviceInput.text.toString()
        execute {
            withContext(Dispatchers.IO) {
                check(app.pairing.saved() == null) { "已有配对申请，请返回短码登记恢复原申请" }
                app.session.settings.bind(server, device, app.session.publicKey())
            }
            binding.bindFields.visibility = View.GONE
            showDiagnosis(ConnectionDiagnosis("设备绑定已保存，请返回人员登录。"))
        }
    }

    private fun confirmLogout() {
        if (busy || !signedIn) return
        AlertDialog.Builder(this).setTitle("退出当前人员登录？")
            .setMessage("设备身份、已下载资料和原作业记录保留。退出不会放弃订单或释放占用。")
            .setNegativeButton("返回", null).setPositiveButton("退出登录") { _, _ -> logout() }.show()
    }

    private fun logout() = execute {
        val failure = withContext(Dispatchers.IO) {
            try { app.session.logout(); null }
            catch (error: Exception) { if (error is CancellationException) throw error; error }
        }
        // logout clears the local vault even when its network request fails.
        val saved = withContext(Dispatchers.IO) { app.session.saved() }
        signedIn = saved != null
        binding.identity.text = if (signedIn) "本机登录仍保留" else "当前未登录"
        if (!signedIn) binding.syncStatus.text = "登录后将自动同步，原作业资料保留"
        showDiagnosis(ConnectionDiagnosis(when {
            signedIn -> "本机登录未清除，请重试；原资料保留。"
            failure != null -> "本机已退出；后台注销未确认。原资料和占用保留。"
            else -> "已退出登录；原资料和占用保留。"
        }))
    }
}
