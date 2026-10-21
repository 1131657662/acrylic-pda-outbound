package com.acrylic.outbound.pda.ui

import android.app.AlertDialog
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.acrylic.outbound.pda.databinding.DialogRescueSupervisorImportBinding

/** Imported material cannot choose a trust root; new authority requires the online rescuer identity. */
internal object RescueSupervisorImportDialog {
    fun show(activity: ComponentActivity, imported: (String, CharArray) -> Unit, material: String = "") {
        val binding = DialogRescueSupervisorImportBinding.inflate(activity.layoutInflater)
        binding.material.setText(material)
        var confirmed = false
        val dialog = AlertDialog.Builder(activity).setTitle("主管只读救援原件").setView(binding.root)
            .setPositiveButton("核验并保存", null).setNegativeButton("取消", null).create()
        dialog.setOnDismissListener { binding.material.text.clear(); binding.password.text.clear() }
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (confirmed) return@setOnClickListener
                confirmed = true
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                val text = binding.material.text.toString(); val password = binding.password.text.toString().toCharArray()
                binding.material.text.clear(); binding.password.text.clear()
                imported(text, password); dialog.dismiss()
            }
        }
        dialog.show()
    }
}
