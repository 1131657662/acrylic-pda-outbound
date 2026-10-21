package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.acrylic.outbound.pda.databinding.DialogOwnRescueSetupBinding

/** Owns only the visible setup steps; no credential is displayed or saved in the view state. */
internal class OwnRescueSetupDialog(activity: ComponentActivity) {
    val binding = DialogOwnRescueSetupBinding.inflate(activity.layoutInflater)
    private val dialog = AlertDialog.Builder(activity).setTitle("安全生成本人应急解锁")
        .setView(binding.root).setPositiveButton("联网核验并预置", null).setNegativeButton("取消", null).create()
    fun show(confirm: () -> Unit, closed: () -> Unit) {
        dialog.setOnDismissListener { binding.custodyConfirmed.isChecked = false; closed() }
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { confirm() }
            render("尚未保存或预置。取消不会替换已有解锁方式。", false)
        }
        binding.custodyConfirmed.setOnCheckedChangeListener { _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy && verified && binding.custodyConfirmed.isChecked
        }
        dialog.show()
    }
    private var busy = false
    private var verified = false
    fun render(message: String, checked: Boolean, working: Boolean = false) {
        busy = working; verified = checked
        binding.setupStatus.text = message
        binding.saveCard.isEnabled = !working
        binding.verifyCard.isEnabled = !working && saved
        binding.custodyConfirmed.isEnabled = !working && checked
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !working && checked && binding.custodyConfirmed.isChecked
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !working
        dialog.setCancelable(!working); dialog.setCanceledOnTouchOutside(false)
    }
    var saved = false
    fun resetCustody() { binding.custodyConfirmed.isChecked = false }
    fun dismiss() = dialog.dismiss()
}
