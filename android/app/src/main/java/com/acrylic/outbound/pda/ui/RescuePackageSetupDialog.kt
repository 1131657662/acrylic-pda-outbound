package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.acrylic.outbound.pda.databinding.DialogRescuePackageSetupBinding

/** Visible custody steps; no secret is placed in a view or its saved state. */
internal class RescuePackageSetupDialog(activity: ComponentActivity) {
    val binding = DialogRescuePackageSetupBinding.inflate(activity.layoutInflater)
    private val dialog = AlertDialog.Builder(activity).setTitle("独立保管解密材料")
        .setView(binding.root).setPositiveButton("生成并核验加密包", null).setNegativeButton("取消", null).create()
    private var busy = false
    private var verified = false
    var saved = false
    fun show(confirm: () -> Unit, closed: () -> Unit) {
        dialog.setOnDismissListener { binding.packageCustody.isChecked = false; closed() }
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { confirm() }
            render("尚未保存解密材料或生成新包。", false)
        }
        binding.packageCustody.setOnCheckedChangeListener { _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !busy && verified && binding.packageCustody.isChecked
        }
        dialog.show()
    }
    fun render(message: String, checked: Boolean, working: Boolean = false) {
        busy = working; verified = checked; binding.packageSetupStatus.text = message
        binding.savePackageSecret.isEnabled = !working
        binding.verifyPackageSecret.isEnabled = !working && saved
        binding.packageCustody.isEnabled = !working && checked
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = !working && checked && binding.packageCustody.isChecked
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !working
        dialog.setCancelable(!working); dialog.setCanceledOnTouchOutside(false)
    }
    fun resetCustody() { binding.packageCustody.isChecked = false }
    fun dismiss() = dialog.dismiss()
}
