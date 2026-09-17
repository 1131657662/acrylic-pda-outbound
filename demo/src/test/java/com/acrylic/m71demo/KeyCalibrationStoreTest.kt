package com.acrylic.m71demo

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.acrylic.m71demo.device.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class KeyCalibrationStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun interruptedDraftResumesWithoutActivatingMapping() {
        val store = KeyCalibrationStore(context, "test-build")
        val session = KeyWizardSession().apply { repeat(3) { KeyWizardFixture.press(this) } }
        store.saveDraft(session)
        val reopened = KeyCalibrationStore(context, "test-build")
        assertEquals(session.id, reopened.loadDraft()!!.id)
        assertEquals(session.samples, reopened.loadDraft()!!.samples)
        assertEquals(KeyRole.LEFT_SCAN, reopened.loadDraft()!!.role)
        assertNull(reopened.activeProfile())
    }

    @Test fun completedDraftStillNeedsOperatorToApplyIt() {
        val store = KeyCalibrationStore(context, "test-build")
        store.saveDraft(KeyWizardFixture.complete())
        assertTrue(store.loadDraft()!!.complete)
        assertNull(store.activeProfile())
    }

    @Test fun applyPersistsAllKeysAndCreatesEvidence() {
        val store = KeyCalibrationStore(context, "test-build")
        val session = KeyWizardFixture.complete()
        val path = store.apply(session)
        assertTrue(java.io.File(path).exists())
        val reopened = KeyCalibrationStore(context, "test-build")
        assertEquals(session.samples, reopened.activeProfile()!!.samples)
        val reports = java.io.File(context.getExternalFilesDir(null), "evidence/key-tests")
        assertTrue(reports.listFiles()!!.isNotEmpty())
    }

    @Test fun partialRunCannotReplaceAnExistingAppliedProfile() {
        val store = KeyCalibrationStore(context, "test-build")
        val existing = KeyWizardFixture.complete()
        store.apply(existing)
        val incomplete = KeyWizardSession().apply { KeyWizardFixture.press(this) }
        assertThrows(IllegalArgumentException::class.java) { store.apply(incomplete) }
        assertEquals(existing.id, store.activeProfile()!!.id)
    }

    @Test fun alteredFirmwareCannotSilentlyReuseProfile() {
        KeyCalibrationStore(context, "old-build").apply(KeyWizardFixture.complete())
        assertThrows(IllegalArgumentException::class.java) { KeyCalibrationStore(context, "new-build").activeProfile() }
        Unit
    }

    @Test fun corruptKeySequenceIsRejectedBeforeActivation() {
        val store = KeyCalibrationStore(context, "test-build")
        val session = KeyWizardFixture.complete()
        session.samples[2] = session.samples[0].copy(role = KeyRole.LEFT_SCAN)
        assertThrows(IllegalArgumentException::class.java) { store.apply(session) }
        assertNull(store.activeProfile())
    }

    @Test fun interruptedAtomicReplacementRestoresPreviousProfile() {
        val store = KeyCalibrationStore(context, "test-build")
        val expected = KeyWizardFixture.complete()
        val active = java.io.File(store.apply(expected))
        check(active.renameTo(java.io.File(active.path + ".bak")))
        assertEquals(expected.id, KeyCalibrationStore(context, "test-build").activeProfile()!!.id)
    }
}
