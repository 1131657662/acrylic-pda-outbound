package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivitySubmissionBinding
import com.acrylic.outbound.pda.submission.*
import kotlinx.coroutines.*
import org.json.JSONObject

class SubmissionActivity : ComponentActivity() {
    private lateinit var binding: ActivitySubmissionBinding
    private val app get() = application as PdaApplication
    private var assignment = ""
    private var local: LocalSubmission? = null
    private var busy = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); assignment = intent.getStringExtra("assignmentId").orEmpty()
        if (assignment.isBlank()) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivitySubmissionBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        app.work.pause(); binding.back.setOnClickListener { if (!busy) finish() }
        binding.freeze.setOnClickListener { confirmFreeze() }
        binding.upload.setOnClickListener { upload() }
        binding.refresh.setOnClickListener { run { local?.let { app.submissions.upload(it.id) } } }
        run { }
    }
    private fun confirmFreeze() {
        if (busy || local != null) return
        AlertDialog.Builder(this).setTitle("确认结单并冻结")
            .setMessage("请核对实物、车号和数量。冻结后不能扫码或撤销；短装需要主管批准。是否冻结本单？")
            .setNegativeButton("返回核对", null).setPositiveButton("确认冻结") { _, _ ->
                val reason = binding.reason.text.toString()
                run { check(app.work.state.value.queued == 0) { "输入仍在落盘，请稍后核对" }; app.freezer.freeze(assignment, reason) }
            }.show()
    }
    private fun upload() {
        val item = local ?: return
        run {
            app.database.submissions().queue(item.id); UploadWorker.schedule(app, item.id)
            app.submissions.upload(item.id)
        }
    }
    private fun run(action: () -> Unit) {
        if (busy) return
        busy = true; buttons(false); binding.error.text = ""
        lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (error: Exception) { binding.error.text = error.message ?: "操作未确认，原记录保留" }
            finally {
                val data = withContext(Dispatchers.IO) { app.database.submissions().forOrder(assignment) to app.database.work().order(assignment) }
                local = data.first; val order = data.second; busy = false
                binding.summary.text = order?.let { "${it.vehicleNo}\n实装 ${it.actualCount} / ${it.plannedTotal} 包 · ${WorkRenderer.kg(it.weightGrams)} kg" } ?: "未找到本地作业"
                render()
            }
        }
    }
    private fun render() {
        val item = local
        binding.status.text = when (item?.state) {
            "SYNCED" -> "云端已确认出库 · 回执已验签"
            "REVIEW_REQUIRED" -> "已接收，待主管审核 · 不可放行"
            "ABORTED", "REJECTED" -> "批次已终结，实物及占用保留 · 须受控处理"
            "RESULT_UNKNOWN" -> "上传结果待核对 · 不可放行"
            "ACTION_REQUIRED" -> "上传待处理 · 不可放行"
            "FROZEN" -> "本单已冻结 · 尚未上传 · 不可放行"
            else -> "本地记录 · 尚未结单 · 不可放行"
        }
        binding.reason.isEnabled = item == null; binding.freeze.isEnabled = item == null
        binding.upload.isEnabled = item != null && item.state !in setOf("SYNCED", "ABORTED", "REJECTED")
        binding.refresh.isEnabled = item != null; binding.back.isEnabled = true
        binding.freeze.visibility = if (item == null) android.view.View.VISIBLE else android.view.View.GONE
        binding.reason.visibility = if (item == null) android.view.View.VISIBLE else android.view.View.GONE
        binding.upload.visibility = if (item != null && item.state !in setOf("SYNCED", "ABORTED", "REJECTED")) android.view.View.VISIBLE else android.view.View.GONE
        binding.refresh.visibility = if (item != null && item.state !in setOf("SYNCED", "ABORTED", "REJECTED")) android.view.View.VISIBLE else android.view.View.GONE
        binding.notice.text = item?.let { "批次：${it.id}\n${it.error}\n" + if (it.autoRetry) "已排队：WiFi恢复后重试原批次" else "冻结正文和明细保留在本机" }
            ?: "全部产品足量可直接提交；短装必须填写原因。整单0包不能结单。"
        if (item?.state == "REVIEW_REQUIRED") binding.notice.append("\n请由主管在Web订单详情核对并审批，再点查询回执。")
    }
    private fun buttons(enabled: Boolean) {
        binding.freeze.isEnabled = enabled; binding.upload.isEnabled = enabled; binding.refresh.isEnabled = enabled; binding.back.isEnabled = enabled
    }
}
