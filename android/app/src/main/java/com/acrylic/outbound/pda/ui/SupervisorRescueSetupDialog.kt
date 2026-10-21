package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.acrylic.outbound.pda.databinding.DialogSupervisorRescueSetupBinding

/** Visible custody steps only; original authority remains in the ephemeral draft. */
internal class SupervisorRescueSetupDialog(activity: ComponentActivity) {
    val binding = DialogSupervisorRescueSetupBinding.inflate(activity.layoutInflater)
    private val dialog = AlertDialog.Builder(activity).setTitle("安全生成主管救援解锁卡")
        .setView(binding.root).setPositiveButton("联网核验原授权并预置", null).setNegativeButton("取消", null).create()
    private var busy = false
    private var verified = false
    var saved = false
    fun show(confirm: () -> Unit, closed: () -> Unit) {
        dialog.setOnDismissListener { resetCustody(); closed() }
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { confirm() }
            render("原签名已本机核验；尚未在线核对或预置。取消不会替换原解锁方式。", false)
        }
        binding.custodyConfirmed.setOnCheckedChangeListener { _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy && verified && binding.custodyConfirmed.isChecked
        }
        dialog.show()
    }
    fun render(message: String, checked: Boolean, working: Boolean = false) {
        busy = working; verified = checked; binding.setupStatus.text = message
        binding.saveCard.isEnabled = !working; binding.verifyCard.isEnabled = !working && saved
        binding.custodyConfirmed.isEnabled = !working && checked
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !working && checked && binding.custodyConfirmed.isChecked
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !working
        dialog.setCancelable(!working); dialog.setCanceledOnTouchOutside(false)
    }
    fun resetCustody() { binding.custodyConfirmed.isChecked = false }
    fun dismiss() = dialog.dismiss()
}
