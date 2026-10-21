package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityRescueCredentialBinding
import com.acrylic.outbound.pda.rescue.RescueCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RescueCredentialActivity : ComponentActivity() {
    private lateinit var binding: ActivityRescueCredentialBinding
    private var busy = false
    private var statusReadGeneration = 0
    private val generatedSetup = OwnRescueSetupFlow(this) { message -> statusReadGeneration++; binding.status.text = message }
    private val app get() = application as PdaApplication
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityRescueCredentialBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding.identity.text = runCatching { app.session.requireSession().getString("username") }.getOrDefault("请先联网登录原人员")
        binding.generateSetup.setOnClickListener { if (!busy) { statusReadGeneration++; generatedSetup.show() } }
        binding.provision.setOnClickListener {
            val password = binding.password.text.toString().toCharArray(); binding.password.text.clear()
            run("正在在线核验，凭据不上传服务器", "本人只读凭据已预置；请单独保管。") { credentials().provisionOwn(password) }
        }
        binding.revoke.setOnClickListener { run("正在撤销本机本人救援资格", "本机本人救援资格已撤销；原作业资料保留。") { credentials().revokeOwn() } }
        binding.back.setOnClickListener { finish() }
    }
    private fun credentials() = RescueCredentials.shared(app, app.session)
    override fun onDestroy() { generatedSetup.close(); super.onDestroy() }
    override fun onResume() {
        super.onResume()
        val generation = ++statusReadGeneration
        if (!busy) lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                runCatching {
                    val user = app.session.saved()?.optString("userId").orEmpty()
                    if (credentials().identities().any { it.operatorId == user })
                        "已保存本人应急解锁方式，使用时仍须核验原设备和凭据。请在设备外独立保管口令。"
                    else "未发现此人的未撤销应急解锁方式。请在故障前联网登录并设置；不影响普通领取和扫码。"
                }.getOrDefault("应急解锁状态未能读取，请保留原资料并核对；不要清空设备。")
            }
            if (!busy && generation == statusReadGeneration) binding.status.text = status
        }
    }
    private fun run(message: String, success: String, action: () -> Unit) {
        if (busy) return
        busy = true
        statusReadGeneration++
        binding.status.text = message; binding.provision.isEnabled = false; binding.revoke.isEnabled = false
        binding.generateSetup.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching(action) }
            binding.status.text = result.fold({ success },
                { it.message ?: "救援凭据未保存，请保留原资料" })
            binding.provision.isEnabled = true; binding.revoke.isEnabled = true
            binding.generateSetup.isEnabled = true
            busy = false
        }
    }
}
