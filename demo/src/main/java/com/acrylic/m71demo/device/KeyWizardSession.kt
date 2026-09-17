package com.acrylic.m71demo.device

enum class KeyRole(val label: String, val hint: String) {
    FRONT_SCAN("正面扫描键", "按正面平时最常用的扫描键；扫描头对空白处"),
    LEFT_SCAN("左侧扫描键", "正对屏幕时，机身左侧的扫描键"),
    RIGHT_SCAN("右侧扫描键", "正对屏幕时，机身右侧的扫描键"),
    ENTER("回车键", "按键帽上的回车 / Enter 键"),
    K1("K1 键", "核对键帽后按 K1，应用将用于手输校验"),
    K2("K2 键", "核对键帽后按 K2，应用将用于辅助操作"),
    BACK("返回键", "按实体返回键；本页只测试，不会退出")
}

data class KeyStroke(val keyCode: Int, val scanCode: Int, val deviceId: Int, val source: Int,
    val action: Int, val repeatCount: Int, val eventTime: Long, val downTime: Long, val physical: Boolean) {
    fun sameKey(other: KeyStroke) = keyCode == other.keyCode && scanCode == other.scanCode && source == other.source
}

data class KeySample(val role: KeyRole, val down: KeyStroke, val up: KeyStroke, val repeats: Int)

/** A label is asserted by the operator; Android can verify events, not the printed key cap. */
class KeyWizardSession(val id: String = java.util.UUID.randomUUID().toString(),
    val samples: MutableList<KeySample> = mutableListOf()) {
    private var down: KeyStroke? = null
    private var repeats = 0
    private var releasedAt = Long.MIN_VALUE
    val complete get() = samples.size == KeyRole.entries.size * 2
    val role get() = KeyRole.entries.getOrNull(samples.size / 2)
    val pressNumber get() = samples.size % 2 + 1
    val waitingRelease get() = down != null
    var message = "按提示按下并松开，不需要在电脑上回复"
        private set

    fun accept(event: KeyStroke): Boolean {
        if (complete) return false
        if (!event.physical || event.scanCode <= 0 || event.keyCode <= 0 || event.source and 257 != 257) {
            return reject("没有有效实体键事件，请使用 PDA 自带键盘")
        }
        if (event.action == 0 && event.repeatCount > 0) {
            if (down?.sameKey(event) == true) repeats++
            return false
        }
        if (event.action == 0) return press(event)
        if (event.action == 1) return release(event)
        return false
    }

    private fun press(event: KeyStroke): Boolean {
        if (down != null) return if (down!!.sameKey(event)) false else reject("同时按了多个键，请全部松开后重试")
        if (releasedAt != Long.MIN_VALUE && event.eventTime - releasedAt < 300) {
            message = "请稍停一下，再按屏幕提示的键"; return false
        }
        if (event.keyCode in setOf(3, 26, 24, 25, 187) || (event.keyCode == 4 && role != KeyRole.BACK) ||
            (event.keyCode in setOf(66, 160) && role != KeyRole.ENTER)) return reject("这个键与提示不符，请核对键帽")
        if (samples.any { it.role != role && it.down.sameKey(event) }) return reject("这个键已经用于其他位置，请核对后重试")
        if (pressNumber == 2 && !samples.last().down.sameKey(event)) return reject("两次按键不一致，请再按同一个键，或点重测本键")
        down = event; repeats = 0; message = "已收到按下，请松开此键"
        return false
    }

    private fun release(event: KeyStroke): Boolean {
        val first = down ?: return false
        if (!first.sameKey(event) || first.deviceId != event.deviceId || first.downTime != event.downTime)
            return reject("按下和松开未配对，请全部松开后重试")
        if (event.eventTime < first.eventTime) return reject("按键时间异常，请重试")
        samples.add(KeySample(requireNotNull(role), first, event, repeats))
        down = null; releasedAt = event.eventTime
        message = if (complete) "全部按键已采集，请核对键的位置后应用" else "已记录。请按上方下一次提示"
        return true
    }

    private fun reject(reason: String): Boolean { down = null; repeats = 0; message = reason; return false }

    fun pause() { down = null; repeats = 0; releasedAt = Long.MIN_VALUE; message = "已保留进度，请从屏幕提示继续" }

    fun retryCurrent() {
        if (complete) repeat(2) { samples.removeAt(samples.lastIndex) }
        else if (samples.size % 2 == 1) samples.removeAt(samples.lastIndex)
        pause()
    }

    fun previous() {
        val start = if (complete) samples.size - 2 else (samples.size / 2 - 1).coerceAtLeast(0) * 2
        while (samples.size > start) samples.removeAt(samples.lastIndex)
        pause()
    }
}
