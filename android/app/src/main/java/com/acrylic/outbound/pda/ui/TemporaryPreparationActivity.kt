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
        binding.orders.setOnClickListener { startActivity(android.content.Intent(this, TemporaryOrdersActivity::class.java)) }
        binding.nextPreparation.setOnClickListener {
            val key = model.state.value.requestKey ?: return@setOnClickListener
            android.app.AlertDialog.Builder(this).setTitle("准备下一轮临时资料")
                .setMessage("新资料仅用于之后新建的临时单。旧单继续使用原资格和截止时间，原扫码与防重记录持续保留。原请求须已确认下载，全机未决输入须处理完成。")
                .setNegativeButton("返回", null).setPositiveButton("联网准备新资料") { _, _ -> model.prepareNext(key) }.show()
        }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) } }
    }
    override fun onResume() { super.onResume(); model.reload() }
    private fun render(state: TemporaryUi) {
        binding.status.text = state.status; binding.details.text = state.details
        binding.prepare.text = state.action; binding.prepare.isEnabled = state.allowed && !state.busy
        binding.nextPreparation.isEnabled = state.allowed && !state.busy && state.requestKey != null
        binding.progress.visibility = if (state.busy) View.VISIBLE else View.GONE
    }
}
