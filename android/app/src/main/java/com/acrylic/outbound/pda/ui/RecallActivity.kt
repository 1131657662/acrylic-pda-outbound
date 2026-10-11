package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivityRecallBinding
import com.acrylic.outbound.pda.recall.LocalRecall
import com.acrylic.outbound.pda.work.WorkCapture
import kotlinx.coroutines.*
import org.json.JSONObject

class RecallActivity : ComponentActivity() {
    private lateinit var binding: ActivityRecallBinding
    private val app get() = application as PdaApplication
    private val assignment get() = intent.getStringExtra("assignmentId").orEmpty()
    private var busy = false
    private var reviewed = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (assignment.isBlank()) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityRecallBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        app.work.pause()
        binding.back.setOnClickListener { if (!busy) finish() }
        binding.freeze.setOnClickListener { confirmFreeze() }
        binding.upload.setOnClickListener { run { app.recalls.upload(assignment) } }
        binding.refresh.setOnClickListener { run { app.recalls.check(assignment) } }
        binding.continueWork.setOnClickListener {
            AlertDialog.Builder(this).setTitle("核验原车受控续作")
                .setMessage("请确认实物与主管批准的保留、卸货及隔离清单一致。联网验签后更新有效装载数，保留原记录和防重占用；未获批准的包不能减少。")
                .setNegativeButton("继续核对", null).setPositiveButton("核验并续作") { _, _ -> run {
                    app.continuations.activate(assignment)
                    runOnUiThread { startActivity(android.content.Intent(this, WorkActivity::class.java).putExtra("assignmentId", assignment)); finish() }
                } }.show()
        }
        run { app.recalls.check(assignment) }
    }
    private fun render(local: LocalRecall?, rows: List<WorkCapture>) {
        reviewed = app.recoveryFreezer.fingerprint(rows)
        binding.reason.text = local?.requestJson?.let { JSONObject(it).getString("reason") } ?: "尚未取得召回申请，请联网查询"
        binding.pending.text = if (rows.isEmpty()) "未保存及失败输入：0" else rows.joinToString("\n") {
            "${it.context.orderScanSeq}. 原输入：${it.context.rawInput}\n${it.error.ifBlank { "尚未保存，请核对实物" }}"
        }
        binding.freeze.isEnabled = local != null && local.packageId == null && local.state != "WITHDRAWN"
        binding.upload.isEnabled = local?.packageId != null && local.state !in setOf("RECALLED", "WITHDRAWN")
        binding.refresh.isEnabled = true; binding.back.isEnabled = true
        binding.continueWork.isEnabled = local?.state == "RECALLED"
        binding.status.text = when (local?.state) {
            "WITHDRAWN" -> "后台已撤回原领取 · 原记录保留，不能继续扫码或上传；请核对实物，新设备重新领取并从头作业"
            "RECALLED" -> "停止证明已验签 · 订单占用仍保留，等待主管处理"
            "RECOVERY_FROZEN" -> "恢复包已固定，请上传或重试原包；当前不能放行"
            "RECALL_PENDING" -> "已收到召回并持久停扫，请核对全部未保存及失败输入"
            else -> "本页暂停扫码；离线时无法取得新召回指令"
        }
    }
    private fun confirmFreeze() {
        if (busy) return
        val fingerprint = reviewed
        AlertDialog.Builder(this).setTitle("核对并停止原机")
            .setMessage("请核对全部未保存及失败输入与实物。确认后保留原序号和已保存明细，固定恢复包；零有效包也需主管另行核验。")
            .setNegativeButton("继续核对", null).setPositiveButton("我已逐项核对") { _, _ -> run {
                check(app.work.state.value.queued == 0) { "输入仍在落盘，请稍后重新核对" }
                app.recoveryFreezer.freeze(assignment, fingerprint)
            } }.show()
    }
    private fun run(action: () -> Unit) {
        if (busy) return
        busy = true; binding.error.text = ""
        listOf(binding.freeze, binding.upload, binding.refresh, binding.back, binding.continueWork).forEach { it.isEnabled = false }
        lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { com.acrylic.outbound.pda.update.UpdateGate.business(action) } }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                binding.error.text = error.message ?: "操作未确认，原资料保留，请重试"
            } finally {
                try {
                    val data = withContext(Dispatchers.IO) { Triple(app.database.recalls().header(assignment), app.recoveryFreezer.unresolved(assignment), app.database.work().order(assignment)) }
                    binding.totals.text = data.third?.let { "${it.vehicleNo} · 已保存 ${it.actualCount} 包 · ${WorkRenderer.kg(it.weightGrams)} kg" } ?: "缺少原作业记录，请保留资料联系主管；不能生成零包证明"
                    render(data.first, data.second)
                } catch (error: Exception) { binding.error.text = "无法读取原资料，请停止并联系主管"; binding.back.isEnabled = true }
                busy = false
            }
        }
    }
}
