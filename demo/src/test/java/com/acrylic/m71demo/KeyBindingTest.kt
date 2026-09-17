package com.acrylic.m71demo

import android.app.Application
import android.content.Context
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.acrylic.m71demo.device.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class KeyBindingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private fun key(sample: KeyStroke, action: Int = 0) = KeyEvent(sample.downTime, sample.eventTime,
        action, sample.keyCode, 0, 0, 9, sample.scanCode, 0, sample.source)

    @Test fun legacySettingsWorkBeforeWizardIsApplied() {
        context.getSharedPreferences("demo", Context.MODE_PRIVATE).edit().putInt("f1", 288).commit()
        val binding = KeyBinding(context, Telemetry(context))
        assertEquals("VALIDATE", binding.action(key(KeyWizardFixture.down(8))))
        assertEquals("旧配置 / 待完整验证", binding.profileStatus)
    }

    @Test fun savedProfileMatchesScanCodeWithoutDependingOnAndroidDeviceId() {
        KeyCalibrationStore(context).apply(KeyWizardFixture.complete())
        val binding = KeyBinding(context, Telemetry(context))
        assertEquals("VALIDATE", binding.action(key(KeyWizardFixture.down(8))))
        binding.reset()
        assertNull(binding.action(key(KeyWizardFixture.down(8).copy(scanCode = 999))))
        assertEquals("按键配置已确认", binding.profileStatus)
    }

    @Test fun scanningKeysAreNeverRemappedToBusinessConfirmation() {
        KeyCalibrationStore(context).apply(KeyWizardFixture.complete())
        val binding = KeyBinding(context, Telemetry(context))
        for (i in listOf(0, 2, 4)) {
            val key = key(KeyWizardFixture.down(i))
            assertNull(binding.action(key))
            assertFalse(binding.isCommand(key))
            binding.reset()
        }
    }

    @Test fun reloadAppliesOnlyCompletedOperatorConfirmedConfiguration() {
        val binding = KeyBinding(context, Telemetry(context))
        KeyCalibrationStore(context).saveDraft(KeyWizardFixture.complete())
        binding.reload()
        assertEquals("旧配置 / 待完整验证", binding.profileStatus)
        KeyCalibrationStore(context).apply(KeyWizardFixture.complete())
        binding.reload()
        assertEquals("CONFIRM", binding.action(key(KeyWizardFixture.down(6))))
    }
}
