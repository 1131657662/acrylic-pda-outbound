package com.acrylic.m71demo

import com.acrylic.m71demo.device.*

object KeyWizardFixture {
    val codes = listOf(293 to 317, 293 to 310, 293 to 311, 66 to 28, 288 to 315, 289 to 316, 4 to 158)
    fun down(index: Int, time: Long = 1000L + index * 1000L): KeyStroke {
        val code = codes[index / 2]
        return KeyStroke(code.first, code.second, 5, 257, 0, 0, time, time, true)
    }
    fun up(down: KeyStroke) = down.copy(action = 1, eventTime = down.eventTime + 100)
    fun press(session: KeyWizardSession): Boolean {
        val down = down(session.samples.size)
        session.accept(down)
        return session.accept(up(down))
    }
    fun complete() = KeyWizardSession().apply { repeat(14) { check(press(this)) } }
}
