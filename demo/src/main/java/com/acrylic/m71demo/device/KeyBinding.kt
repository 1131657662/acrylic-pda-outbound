package com.acrylic.m71demo.device

import android.content.Context
import android.view.KeyEvent

class KeyBinding(context: Context, private val telemetry: Telemetry) {
    private val prefs = context.getSharedPreferences("demo", Context.MODE_PRIVATE)
    private val calibration = KeyCalibrationStore(context)
    private var profile: KeyWizardSession? = null
    private val pressed = mutableSetOf<Int>()
    var profileStatus = "旧配置 / 待完整验证"
        private set

    init { reload() }

    fun reload() {
        profile = runCatching { calibration.activeProfile() }.getOrElse {
            telemetry.event("KEY_PROFILE_INVALID", mapOf("error" to it.javaClass.simpleName)); null
        }
        profileStatus = if (profile == null) "旧配置 / 待完整验证" else "按键配置已确认"
        reset()
    }

    fun isCommand(event: KeyEvent): Boolean = command(event) != null

    fun reset() { pressed.clear() }

    fun action(event: KeyEvent): String? {
        telemetry.event("KEY", mapOf("keyCode" to event.keyCode, "scanCode" to event.scanCode,
            "action" to event.action, "repeatCount" to event.repeatCount, "deviceId" to event.deviceId, "source" to event.source))
        if (event.action == KeyEvent.ACTION_UP) { pressed.remove(event.keyCode); return "UP" }
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount > 0 || !pressed.add(event.keyCode)) return "REPEAT"
        return command(event)
    }

    private fun command(event: KeyEvent): String? {
        profile?.let { saved ->
            val role = saved.samples.firstOrNull { it.down.keyCode == event.keyCode &&
                it.down.scanCode == event.scanCode && it.down.source == event.source }?.role
            return when (role) {
                KeyRole.ENTER -> "CONFIRM"
                KeyRole.K1 -> "VALIDATE"
                KeyRole.K2 -> "AUXILIARY"
                KeyRole.BACK -> "BACK"
                else -> null
            }
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "CONFIRM"
            prefs.getInt("f1", KeyEvent.KEYCODE_F1) -> "VALIDATE"
            prefs.getInt("f2", KeyEvent.KEYCODE_F2) -> "AUXILIARY"
            KeyEvent.KEYCODE_BACK -> "BACK"
            else -> null
        }
    }

}
