package com.acrylic.m71demo.ui

import android.view.View
import com.acrylic.m71demo.databinding.ActivityKeyCheckBinding
import com.acrylic.m71demo.device.*

class KeyWizardRenderer(private val view: ActivityKeyCheckBinding) {
    fun render(session: KeyWizardSession, armed: Boolean, busy: Boolean, applied: Boolean, error: String) {
        view.progress.text = "已记录 ${session.samples.size} / 14 次 · 7 个键各按两次"
        view.prompt.text = when {
            applied -> "配置已应用"
            session.complete -> "请核对按键位置"
            !armed -> "准备好后点击继续"
            session.waitingRelease -> "请松开\n${session.role!!.label}"
            else -> "请按 ${session.role!!.label}\n第 ${session.pressNumber} / 2 次"
        }
        view.instructions.text = instructions(session, armed, applied)
        view.feedback.text = if (error.isNotBlank()) error else if (busy) "正在保存进度，请稍候" else session.message
        view.result.text = session.samples.groupBy { it.role }.entries.joinToString("\n") { (role, samples) ->
            "${role.label}：${samples.first().down.keyCode} / ${samples.first().down.scanCode}（${samples.size}/2）" }
        view.primary.visibility = if (armed && !session.complete) View.GONE else View.VISIBLE
        view.primary.text = when { applied -> "重新验证全部"; session.complete -> "位置正确，确认并应用"; else -> "开始 / 继续验证" }
        view.primary.isEnabled = !busy && error.isBlank()
        view.previous.isEnabled = !busy && !applied && session.samples.isNotEmpty() && error.isBlank()
        view.retry.isEnabled = !busy && !applied && error.isBlank()
        view.leave.isEnabled = !busy
    }

    private fun instructions(s: KeyWizardSession, armed: Boolean, applied: Boolean): String = when {
        applied -> "已保存本机真实按键配置。回到扫码页即生效，不用改代码。\n此结果只验证按键，不代表扫码性能或断电验收全部通过。"
        s.complete -> "确认你每一步按的都是屏幕提示的位置。按错了可用“上一个键”退回重测。\n确认后才替换应用按键配置。"
        !armed -> "顺序：正面扫描 → 左扫描 → 右扫描 → 回车 → K1 → K2 → 返回。\n每个键按两次，每次松开后稍停。没有倒计时，可以独立完成。\n扫描头对空白处，本页扫描不会计包。"
        else -> s.role!!.hint + "\n按下并松开后会自动继续；按错可点重测。本页不执行保存或菜单动作。"
    }
}
