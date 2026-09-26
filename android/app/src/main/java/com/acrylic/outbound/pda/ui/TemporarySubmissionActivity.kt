package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.databinding.ActivitySubmissionBinding
import com.acrylic.outbound.pda.temporary.*
import kotlinx.coroutines.*

class TemporarySubmissionActivity : ComponentActivity() {
    private lateinit var binding: ActivitySubmissionBinding
    private lateinit var abort: Button
    private lateinit var evidence: Button
    private var evidenceState: com.acrylic.outbound.pda.evidence.LocalE2aQueue? = null
    private val app get() = application as PdaApplication
    private var id = ""
    private var local: TemporarySubmission? = null
    private var busy = false
    private var reconciling = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); id = intent.getStringExtra("clientOrderId").orEmpty()
        if (id.isEmpty()) { finish(); return }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivitySubmissionBinding.inflate(layoutInflater); setContentView(binding.root)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        app.temporaryWork.pause(); binding.back.text = "返回临时订单"; binding.back.setOnClickListener { if (!busy) finish() }
        binding.freeze.setOnClickListener { freeze() }; binding.upload.setOnClickListener { upload() }
        binding.refresh.setOnClickListener { run { local?.let { app.temporarySubmissions.upload(it.id) } } }
        abort = Button(this).apply { text = "取消原批次 · 保留实物占用"; setOnClickListener { abort() } }
        (binding.refresh.parent as LinearLayout).addView(abort)
        evidence = Button(this).apply { text = "补传原机证据 · 保留待核对"; setOnClickListener { evidence() } }
        (binding.refresh.parent as LinearLayout).addView(evidence)
        run { }
    }
    private fun freeze() {
        if (busy || local != null) return
        AlertDialog.Builder(this).setTitle("核对临时单并冻结").setMessage("请核对车号、实物与各产品数量。冻结后不能继续扫码或撤销，上传后仍须独立确认，不得放行。")
            .setNegativeButton("返回核对", null).setPositiveButton("确认冻结") { _, _ ->
                val reason = binding.reason.text.toString()
                run { check(app.temporaryWork.state.value.queued == 0) { "输入仍在落盘，请稍后核对" }; TemporaryFreeze(app.temporaryStore()).freeze(id, reason) }
            }.show()
    }
    private fun upload() {
        val item = local ?: return
        run { app.database.temporarySubmissions().queue(item.id); TemporaryUploadWorker.schedule(app, item.id); app.temporarySubmissions.upload(item.id) }
    }
    private fun abort() {
        val item = local ?: return; if (busy) return
        val input = EditText(this).apply { hint = "取消原因"; setText(item.abortReason.orEmpty()); isEnabled = item.abortReason == null }
        AlertDialog.Builder(this).setTitle("取消原上传批次").setMessage("取消不会卸货、解除占用或恢复扫码。请保留实物，联系主管办理后续处置。")
            .setView(input).setNegativeButton("返回", null).setPositiveButton("确认取消") { _, _ ->
                val reason = input.text.toString(); run { app.temporarySubmissions.abort(item.id, reason) }
            }.show()
    }
    private fun evidence() {
        if (busy || local == null) return
        run {
            app.e2aRecovery.queue(id)
            com.acrylic.outbound.pda.evidence.E2aRecoveryWorker.schedule(app, id)
            app.e2aRecovery.resume(id)
        }
    }
    private fun run(action: () -> Unit) {
        if (busy) return
        busy = true; controls(false); binding.error.text = ""
        lifecycleScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (error: Exception) { binding.error.text = error.message ?: "操作未确认，请保留原记录" }
            finally {
                withContext(Dispatchers.IO) {
                    local = app.database.temporarySubmissions().forOrder(id)?.let { app.temporarySubmissions.checked(it.id) }
                    reconciling = app.database.sync().recovery()?.mode == "RECONCILING"
                    evidenceState = app.database.localE2aReceipts().queue(id)
                    val draft = app.database.temporaryDrafts().get(id); val total = app.database.temporaryScans().total(id)
                    "${draft?.vehicleNo.orEmpty()} · ${draft?.temporaryNo.orEmpty()}\n实装 ${total.count} 包 · ${WorkRenderer.kg(total.grams)} kg"
                }.let { binding.summary.text = it }
                busy = false; render()
            }
        }
    }
    private fun render() {
        val item = local; val terminal = item?.state in setOf("ABORTED", "SYNCED")
        binding.status.text = when (item?.state) {
            "SYNCED" -> "成功原回执已核验 · 保留实物及记录"
            "REVIEW_REQUIRED" -> "已接收，待独立确认 · 不得放行"
            "ABORTED" -> "取消回执已验签 · 实物及占用保留"
            "FROZEN" -> "原临时单已冻结 · 尚未上传 · 不得放行"
            "RESULT_UNKNOWN" -> "结果待核对 · 不得放行"
            "ACTION_REQUIRED" -> "上传待处理 · 不得放行"
            else -> "本机临时作业 · 尚未冻结 · 不得放行"
        }
        if (reconciling) binding.status.text = "恢复待对账 · 不得放行\n${binding.status.text}"
        renderNotice(item)
        controls(true)
        binding.freeze.visibility = if (item == null) View.VISIBLE else View.GONE
        binding.reason.visibility = binding.freeze.visibility
        binding.upload.isEnabled = item != null && !terminal; binding.refresh.isEnabled = item != null && !terminal
        abort.visibility = if (item != null && !terminal) View.VISIBLE else View.GONE
        evidence.visibility = if (item != null && !terminal) View.VISIBLE else View.GONE
    }
    private fun renderNotice(item: TemporarySubmission?) {
        binding.notice.text = item?.let { "批次：${it.id}\n${it.error}\n" +
            if (it.abortReason != null && it.state != "SYNCED") "原取消意图已保存，后续只恢复原取消请求" else "原资格、冻结正文与实物占用持续保留" }
            ?: "短装须填写原因；整单0包不能结单。"
        if (item?.autoRetry == true) binding.notice.append("\n已排队：Wi-Fi恢复后核对原批次")
        if (item?.state == "REVIEW_REQUIRED") binding.notice.append("\n独立确认入口尚未开放，请联系主管保留待办。")
        if (item?.state == "SYNCED") binding.notice.append("\n成功原件已保存在本机；归档入口尚未开放，不删除记录，不解除实物占用。")
        evidenceState?.let { state -> binding.notice.append("\n原机证据：" + when (state.state) {
            "PARTIAL" -> "回执已核验，仍待独立确认，不得放行"
            "ACTION_REQUIRED" -> "待处理 · ${state.error}"
            "WAITING_AUDIT" -> "操作记录仍在补传，原证据保留，稍后继续核对"
            else -> "原请求已保存，Wi-Fi恢复后继续核对 · ${state.error}"
        }) }
    }
    private fun controls(enabled: Boolean) {
        listOf(binding.back, binding.freeze, binding.upload, binding.refresh, binding.reason, abort, evidence).forEach { it.isEnabled = enabled }
    }
}
