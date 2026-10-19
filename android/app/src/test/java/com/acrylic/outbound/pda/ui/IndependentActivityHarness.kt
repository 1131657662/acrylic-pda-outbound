package com.acrylic.outbound.pda.ui

import android.content.Intent
import android.os.Looper
import android.view.KeyEvent
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.acrylic.outbound.pda.databinding.ActivityIndependentWorkBinding
import com.acrylic.outbound.pda.independent.IndependentActivityFixture
import org.robolectric.Robolectric
import org.robolectric.Shadows

internal class IndependentActivityHarness : AutoCloseable {
    private var paused = false
    val fixture = IndependentActivityFixture()
    private val controller = Robolectric.buildActivity(IndependentWorkActivity::class.java,
        Intent(fixture.app, IndependentWorkActivity::class.java).putExtra("taskId", fixture.id)).setup().visible()
    val activity get() = controller.get()
    val binding = ActivityIndependentWorkBinding.bind(activity.findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0))

    init {
        await { fixture.engine.state.value.task != null && fixture.engine.state.value.queued == 0 }
        binding.workDetailsToggle.performClick()
        binding.workPassword.setText(String(fixture.scan.task.identity.password))
        binding.workAuthorize.performClick()
        await { binding.workValidate.isEnabled }
        binding.workDetailsToggle.performClick()
    }

    fun manual(value: String = "003A0000001") {
        if (!binding.workManual.isShown) binding.workManualToggle.performClick()
        binding.workManual.setText(value)
        binding.workManual.requestFocus()
    }

    fun key(code: Int) {
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun ime(visible: Boolean) {
        val insets = WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.ime(),
            if (visible) Insets.of(0, 0, 0, 240) else Insets.NONE).setVisible(WindowInsetsCompat.Type.ime(), visible).build()
        ViewCompat.dispatchApplyWindowInsets(binding.root, insets)
    }

    fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 15_000_000_000
        while (System.nanoTime() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if (predicate()) return
            Thread.sleep(10)
        }
        error("Activity did not settle: ${fixture.engine.state.value}")
    }

    fun pause() {
        if (!paused) { controller.pause().stop(); paused = true }
    }
    override fun close() {
        pause()
        controller.destroy()
        fixture.close()
    }
}
