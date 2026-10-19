package com.acrylic.outbound.pda.ui

import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding

/** Explicit secondary inputs; rendering scan state never resets the operator's edits. */
internal class IndependentWorkPanels(private val binding: ActivityIndependentWorkBinding) {
    fun bind() {
        binding.workManualToggle.setOnClickListener { toggleManual() }
        binding.workDetailsToggle.setOnClickListener { toggleDetails() }
    }

    fun acceptedManual() {
        binding.workManual.text.clear()
        binding.workManualPanel.isVisible = false
        binding.workManualToggle.text = "展开手输条码"
        closeInput(binding.workManualToggle)
    }

    private fun toggleManual() {
        val opening = !binding.workManualPanel.isVisible
        binding.workManualPanel.isVisible = opening
        binding.workManualToggle.text = if (opening) "收起手输条码" else "展开手输条码"
        if (opening) binding.workManual.requestFocus()
        else closeInput(binding.workManualToggle)
    }

    private fun toggleDetails() {
        val opening = !binding.workDetailsPanel.isVisible
        binding.workDetailsPanel.isVisible = opening
        binding.workDetailsToggle.text = if (opening) "收起核对 / 原件" else "核对 / 原件"
        if (!opening) {
            binding.workPassword.text.clear()
            closeInput(binding.workDetailsToggle)
        }
    }

    private fun closeInput(toggle: View) {
        binding.root.findFocus()?.clearFocus()
        binding.root.requestFocus()
        binding.root.context.getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(binding.root.windowToken, 0)
        toggle.requestRectangleOnScreen(android.graphics.Rect(0, 0, toggle.width, toggle.height))
    }
}
