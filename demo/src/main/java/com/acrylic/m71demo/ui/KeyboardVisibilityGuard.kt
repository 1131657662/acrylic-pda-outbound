package com.acrylic.m71demo.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager

/** Recover an unusable IME window without changing the user's system keyboard preference. */
class KeyboardVisibilityGuard(private val activity: Activity, private val input: View,
    private val isEditing: () -> Boolean) {
    private val content = activity.findViewById<View>(android.R.id.content)
    private var pending = false
    private val check = Runnable { recoverIfStillHidden() }
    private val listener = ViewTreeObserver.OnGlobalLayoutListener {
        if (isEditing() && content.height == 0 && !pending) {
            pending = true
            content.postDelayed(check, 250)
        }
    }

    fun attach() = content.viewTreeObserver.addOnGlobalLayoutListener(listener)

    fun detach() {
        content.removeCallbacks(check)
        if (content.viewTreeObserver.isAlive) content.viewTreeObserver.removeOnGlobalLayoutListener(listener)
    }

    private fun recoverIfStillHidden() {
        pending = false
        if (!isEditing() || content.height != 0 || !activity.hasWindowFocus() ||
            activity.isFinishing || activity.isDestroyed) return
        val keyboard = activity.getSystemService(InputMethodManager::class.java)
        keyboard.hideSoftInputFromWindow(input.windowToken, 0)
        AlertDialog.Builder(activity).setTitle("当前输入法无法显示")
            .setMessage("输入内容仍保留在当前页面，本包尚未保存。请选择其他输入法，或继续使用实体键输入。")
            .setPositiveButton("选择输入法") { _, _ -> keyboard.showInputMethodPicker() }
            .setNegativeButton("使用实体键", null).show()
    }
}
