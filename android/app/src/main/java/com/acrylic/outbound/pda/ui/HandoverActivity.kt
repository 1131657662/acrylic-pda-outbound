package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.acrylic.outbound.pda.databinding.ActivityHandoverBinding
import com.acrylic.outbound.pda.databinding.OrderCardBinding
import kotlinx.coroutines.launch

class HandoverActivity : ComponentActivity() {
    private lateinit var binding: ActivityHandoverBinding
    private val model: HandoverViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityHandoverBinding.inflate(layoutInflater); setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        binding.back.setOnClickListener { finish() }
        binding.receive.setOnClickListener { model.stage(binding.caseId.text.toString()) }
        binding.refresh.setOnClickListener { model.reload() }
        binding.more.setOnClickListener { model.more() }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) } }
    }

    override fun onResume() { super.onResume(); model.reload() }

    private fun render(state: HandoverUi) {
        binding.status.text = state.message
        binding.progress.visibility = if (state.busy) View.VISIBLE else View.GONE
        listOf(binding.receive, binding.refresh, binding.more, binding.caseId).forEach { it.isEnabled = !state.busy }
        binding.more.visibility = if (state.hasMore) View.VISIBLE else View.GONE
        binding.cards.removeAllViews()
        for (card in state.cards) {
            val row = OrderCardBinding.inflate(LayoutInflater.from(this), binding.cards, false)
            row.title.text = card.title
            row.details.text = "${card.status}\n交接编号 ${card.caseId}"
            row.action.text = card.label; row.action.isEnabled = !state.busy
            row.action.setOnClickListener { act(card) }
            binding.cards.addView(row.root)
        }
    }

    private fun act(card: HandoverCard) {
        if (card.action in setOf("WORK", "SUBMISSION", "RECALL")) {
            val page = when (card.action) {
                "SUBMISSION" -> SubmissionActivity::class.java
                "RECALL" -> RecallActivity::class.java
                else -> WorkActivity::class.java
            }
            startActivity(Intent(this, page).putExtra("assignmentId", card.assignment))
            return
        }
        if (card.action == "TEMP_CONFIRM") {
            AlertDialog.Builder(this).setTitle("确认临时交接实物与资料")
                .setMessage(card.confirmation).setNegativeButton("返回核对", null)
                .setPositiveButton("已核对，确认接收") { _, _ -> model.perform(card) }.show()
            return
        }
        if (card.action != "CONTINUE") { model.perform(card); return }
        AlertDialog.Builder(this).setTitle("核验接收机续作")
            .setMessage("请先核对实物与主管批准的交接资料。联网核验成功后才允许扫码，原记录和占用继续保留。")
            .setNegativeButton("返回核对", null).setPositiveButton("核验并续作") { _, _ -> model.perform(card) }.show()
    }
}
