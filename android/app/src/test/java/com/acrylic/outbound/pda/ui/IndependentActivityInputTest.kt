package com.acrylic.outbound.pda.ui

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.acrylic.outbound.pda.independent.IndependentActivityTestApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "mdpi", application = IndependentActivityTestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentActivityInputTest {
    @Test fun acceptedManualInputHandsFocusBackBeforeConfirmation() {
        IndependentActivityHarness().use { h ->
            h.manual()
            val ime = h.activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            ime.showSoftInput(h.binding.workManual, InputMethodManager.SHOW_IMPLICIT)
            assertTrue(Shadows.shadowOf(ime).isSoftInputVisible)
            h.ime(true)

            h.binding.workValidate.performClick()
            h.await { h.binding.workConfirm.isEnabled }

            assertEquals(View.GONE, h.binding.workManualPanel.visibility)
            assertFalse(h.binding.workManual.hasFocus())
            assertFalse(Shadows.shadowOf(ime).isSoftInputVisible)
            assertEquals("", h.binding.workManual.text.toString())
            h.fixture.read { f ->
                assertEquals("003A0000001", f.store.dao.captures(h.fixture.id).single().capture.rawInput)
                assertEquals(0L, f.store.dao.total(h.fixture.id).count)
            }
        }
    }

    @Test fun refusedManualInputRemainsEditableWithoutCreatingCapture() {
        IndependentActivityHarness().use { h ->
            h.manual("003A0000001")
            h.fixture.engine.pause()

            h.key(KeyEvent.KEYCODE_F1)
            h.await { h.fixture.engine.state.value.message.contains("本条未接收") }

            assertEquals("003A0000001", h.binding.workManual.text.toString())
            assertTrue(h.binding.workManual.isShown)
            h.fixture.read { assertTrue(it.store.dao.captures(h.fixture.id).isEmpty()) }
        }
    }

    @Test fun hardwareValidationAndHeldEnterPersistExactlyOneOriginal() {
        IndependentActivityHarness().use { h ->
            h.manual(); h.key(KeyEvent.KEYCODE_F1)
            h.await { h.binding.workConfirm.isEnabled }
            val capture = h.fixture.engine.state.value.candidate!!.capture

            h.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            h.await { h.fixture.engine.state.value.latestSaved?.scan?.captureId == capture.id }
            h.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            h.activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))

            h.fixture.read { f ->
                assertEquals(listOf(capture), f.store.dao.captures(h.fixture.id).map { it.capture })
                assertEquals(1L, f.store.dao.total(h.fixture.id).count)
            }
            assertTrue(h.binding.workRecent.text.contains(capture.barcode))
        }
    }

    @Test fun imeVisibleHardwareKeysDoNotCreateOrConfirmCapture() {
        IndependentActivityHarness().use { h ->
            h.manual(); h.ime(true)

            h.key(KeyEvent.KEYCODE_F1); h.key(KeyEvent.KEYCODE_ENTER)

            h.fixture.read { assertTrue(it.store.dao.captures(h.fixture.id).isEmpty()) }
            h.ime(false); h.key(KeyEvent.KEYCODE_F1)
            h.await { h.binding.workConfirm.isEnabled }
            h.ime(true); h.key(KeyEvent.KEYCODE_ENTER)
            h.fixture.read { assertEquals(0L, it.store.dao.total(h.fixture.id).count) }
        }
    }

    @Test fun hardwareDiscardRetainsInputAndReasonWithoutSaving() {
        IndependentActivityHarness().use { h ->
            h.manual(); h.key(KeyEvent.KEYCODE_F1)
            h.await { h.binding.workConfirm.isEnabled }
            h.binding.workDetailsToggle.performClick()
            h.binding.workReason.setText("合成实物核对，本车不装")

            h.key(KeyEvent.KEYCODE_F2)
            h.await { h.fixture.engine.state.value.queued == 0 && h.fixture.engine.state.value.candidate == null }

            h.fixture.read { f ->
                val original = f.store.dao.captures(h.fixture.id).single()
                assertEquals("DISCARDED", original.processing.state)
                assertEquals("003A0000001", original.capture.rawInput)
                assertEquals(0L, f.store.dao.total(h.fixture.id).count)
            }
            assertEquals("合成实物核对，本车不装", h.binding.workReason.text.toString())
        }
    }

    @Test fun backPausesWithoutSealingOrDroppingUnconfirmedOriginal() {
        IndependentActivityHarness().use { h ->
            h.manual(); h.key(KeyEvent.KEYCODE_F1)
            h.await { h.binding.workConfirm.isEnabled }
            val original = h.fixture.engine.state.value.candidate!!.capture

            h.binding.workBack.performClick(); h.pause()

            assertTrue(h.activity.isFinishing)
            assertFalse(h.fixture.engine.scanning)
            h.fixture.read { f ->
                assertEquals(original, f.store.dao.captures(h.fixture.id).single().capture)
                assertEquals("WAITING_CONFIRM", f.store.dao.pending(h.fixture.id)!!.processing.state)
                assertEquals("SCANNING", f.task.repository.read(h.fixture.id).state.state)
                assertEquals(0L, f.store.dao.total(h.fixture.id).count)
            }
        }
    }
}
