package com.acrylic.outbound.pda.device

import android.os.Build
import android.view.KeyEvent

/** Only the physically verified M71 firmware receives custom mappings; scanner trigger keys are untouched. */
class WorkKeys(private val fingerprint: String = Build.FINGERPRINT) {
    private val down = HashSet<Int>()
    fun command(event: KeyEvent): String? {
        if (event.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0) return null
        val verified = fingerprint == "Mobydata/m71/m71:9/M01.04.04.20230626/jenkins06261456:user/dev-keys" && event.source == 257
        return when {
            verified && event.keyCode == 288 && event.scanCode == 315 -> "VALIDATE"
            verified && event.keyCode == 289 && event.scanCode == 316 -> "DISCARD"
            event.keyCode == KeyEvent.KEYCODE_F1 -> "VALIDATE"
            event.keyCode == KeyEvent.KEYCODE_F2 -> "DISCARD"
            event.keyCode in setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER) -> "CONFIRM"
            else -> null
        }
    }
    fun firstDown(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) { down.remove(event.keyCode); return false }
        return event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 && down.add(event.keyCode)
    }
    fun reset() = down.clear()
}
