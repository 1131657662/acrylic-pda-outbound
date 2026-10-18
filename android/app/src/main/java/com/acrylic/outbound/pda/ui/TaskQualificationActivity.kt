package com.acrylic.outbound.pda.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityTaskQualificationBinding
import com.acrylic.outbound.pda.network.SessionIdentity
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.Callable

class TaskQualificationActivity : ComponentActivity() {
    private lateinit var binding: ActivityTaskQualificationBinding
    private val app get() = application as PdaApplication
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityTaskQualificationBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding.back.setOnClickListener { finish() }
        binding.retry.setOnClickListener { load() }
        binding.qualificationDetails.setOnClickListener {
            val visible = binding.rawDetails.visibility != View.VISIBLE
            binding.rawDetails.visibility = if (visible) View.VISIBLE else View.GONE
            binding.qualificationDetails.text = if (visible) "收起原始原因" else "展开原始原因"
        }
    }

    override fun onResume() { super.onResume(); load() }

    private fun load() {
        if (busy) return
        busy = true; binding.retry.isEnabled = false
        binding.summary.text = "正在只读核对本单资料与资格"
        binding.rawDetails.text = ""; binding.rawDetails.visibility = View.GONE
        binding.qualificationDetails.visibility = View.GONE
        binding.qualificationDetails.text = "展开原始原因"
        lifecycleScope.launch {
            try {
                val report = withContext(Dispatchers.IO) { read() }
                binding.summary.text = report.summary(); binding.rawDetails.text = report.details()
                binding.qualificationDetails.visibility = if (report.details().isBlank()) View.GONE else View.VISIBLE
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                binding.summary.text = "本单核对未完成。原资料与记录保留；请用原人员、原设备重新核对，或从数据页使用预置救援凭据只读导出。"
                binding.rawDetails.text = error.message ?: "原因尚待核对"
                binding.qualificationDetails.visibility = View.VISIBLE
            } finally { busy = false; binding.retry.isEnabled = true }
        }
    }

    private fun read(): TaskQualificationReport {
        val id = intent.getStringExtra("localOrderId").orEmpty()
        check(UUID.fromString(id).toString() == id) { "原单标识格式错误" }
        val source = intent.getStringExtra("source")
        check(source in setOf("FORMAL", "TEMP")) { "不支持的原单来源" }
        val auth = app.session.requireSession()
        return app.database.runInTransaction(Callable {
            val report = if (source == "FORMAL") FormalTaskQualification(app).read(id) else TemporaryTaskQualification(app).read(id)
            SessionIdentity.requireSame(auth, app.session.saved())
            report
        })
    }
}
