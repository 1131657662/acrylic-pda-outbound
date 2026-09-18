package com.acrylic.outbound.pda.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityDeviceSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DeviceSettingsActivity : ComponentActivity() {
    private lateinit var binding: ActivityDeviceSettingsBinding
    private val app get() = application as PdaApplication
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityDeviceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.back.setOnClickListener { if (!busy) finish() }
        binding.sound.setOnClickListener { toggleSound() }
        binding.copyKey.setOnClickListener { copyKey() }
        binding.checkConnection.setOnClickListener { checkConnection() }
        binding.logout.setOnClickListener { logout() }
        render()
    }

    private fun render() {
        val settings = app.session.settings
        binding.environment.text = "环境：${BuildConfig.PDA_ENVIRONMENT.ifBlank { "未配置" }}"
        binding.version.text = "App 版本：${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）"
        binding.server.text = "服务地址：${settings.server.ifBlank { "尚未绑定" }}"
        binding.device.text = if (settings.device.isBlank()) "设备身份：尚未登记" else "设备身份：${settings.device}"
        binding.sound.text = if (app.workSound.enabled) "声音：开" else "声音：关"
        binding.identity.text = identityText()
        binding.logout.isEnabled = runCatching { app.session.saved() != null }.getOrDefault(false) && !busy
        binding.back.isEnabled = !busy
        binding.sound.isEnabled = !busy
        binding.copyKey.isEnabled = !busy
        binding.checkConnection.isEnabled = !busy
    }

    private fun identityText(): String {
        val saved = runCatching { app.session.saved() }.getOrNull()
        return if (saved == null) "当前未登录；已下载的本机资料仍会保留。"
        else "当前人员：${saved.optString("username", "已登录")} · 仓库与权限由后台会话决定"
    }

    private fun toggleSound() {
        app.workSound.enabled = !app.workSound.enabled
        binding.status.text = if (app.workSound.enabled) "声音提示已开启" else "声音提示已关闭；结果仍以页面文字和数量为准"
        render()
    }

    private fun copyKey() {
        if (busy) return
        lifecycleScope.launch {
            try {
                val key = withContext(Dispatchers.IO) { app.session.publicKey() }
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PDA登记公钥", key))
                binding.status.text = "公钥已复制。公钥不包含私钥，请通过管理员登记流程使用。"
            } catch (error: Exception) {
                binding.status.text = error.message ?: "无法读取设备身份"
            }
        }
    }

    private fun checkConnection() {
        if (busy) return
        val saved = runCatching { app.session.saved() }.getOrNull()
        if (saved == null) {
            binding.status.text = "尚未登录，无法查询后台；请先返回登录。"
            return
        }
        busy = true
        render()
        binding.status.text = "正在查询后台连接……"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { app.session.call("pda/devices/me") }
                binding.status.text = "后台连接正常；设备身份和当前会话已核对。"
            } catch (error: Exception) {
                binding.status.text = error.message ?: "后台暂时无法连接，原资料已保留。"
            } finally {
                busy = false
                render()
            }
        }
    }

    private fun logout() {
        if (busy) return
        busy = true
        render()
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { app.session.logout() }
                binding.status.text = "已退出登录；本机资料和登记身份已保留。"
            } catch (error: Exception) {
                binding.status.text = error.message ?: "退出结果待核对；请返回登录页恢复会话。"
            } finally {
                busy = false
                render()
            }
        }
    }
}
