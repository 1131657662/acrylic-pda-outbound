package com.acrylic.m71demo

import com.acrylic.m71demo.device.*
import org.junit.Assert.*
import org.junit.Test

class KeyWizardSessionTest {
    @Test fun progressesOnlyAfterMatchingRelease() {
        val session = KeyWizardSession()
        val down = KeyWizardFixture.down(0)
        assertFalse(session.accept(down))
        assertEquals(0, session.samples.size)
        assertTrue(session.waitingRelease)
        assertTrue(session.accept(KeyWizardFixture.up(down)))
        assertEquals(2, session.pressNumber)
        assertEquals(KeyRole.FRONT_SCAN, session.role)
    }

    @Test fun allSevenKeysNeedTwoConsistentPresses() {
        val session = KeyWizardSession()
        repeat(13) { assertTrue(KeyWizardFixture.press(session)); assertFalse(session.complete) }
        assertTrue(KeyWizardFixture.press(session))
        assertTrue(session.complete)
        assertEquals(KeyRole.entries, session.samples.map { it.role }.distinct())
    }

    @Test fun scanCodeSeparatesThreeKeysWithTheSameAndroidKeyCode() {
        val session = KeyWizardFixture.complete()
        val scans = session.samples.take(6).map { it.down }
        assertEquals(1, scans.map { it.keyCode }.distinct().size)
        assertEquals(3, scans.map { it.scanCode }.distinct().size)
    }

    @Test fun virtualOrUnidentifiedKeysNeverPass() {
        for (bad in listOf(KeyWizardFixture.down(0).copy(physical = false),
            KeyWizardFixture.down(0).copy(scanCode = 0), KeyWizardFixture.down(0).copy(source = 0))) {
            val session = KeyWizardSession()
            session.accept(bad); session.accept(KeyWizardFixture.up(bad))
            assertEquals(0, session.samples.size)
        }
    }

    @Test fun holdRepeatsCannotAdvanceToNextKey() {
        val session = KeyWizardSession()
        val down = KeyWizardFixture.down(0)
        session.accept(down)
        repeat(20) { session.accept(down.copy(repeatCount = it + 1, eventTime = 1010L + it)) }
        assertEquals(0, session.samples.size)
        assertTrue(session.accept(KeyWizardFixture.up(down)))
        assertEquals(1, session.samples.size)
        assertEquals(20, session.samples.single().repeats)
        assertFalse(session.accept(KeyWizardFixture.up(down)))
    }

    @Test fun mismatchedSecondPressDoesNotReplaceFirstKey() {
        val session = KeyWizardSession()
        KeyWizardFixture.press(session)
        val wrong = KeyWizardFixture.down(2)
        session.accept(wrong); session.accept(KeyWizardFixture.up(wrong))
        assertEquals(1, session.samples.size)
        assertTrue(KeyWizardFixture.press(session))
    }

    @Test fun duplicatePhysicalKeyCannotBeAssignedToTwoRoles() {
        val session = KeyWizardSession()
        repeat(2) { KeyWizardFixture.press(session) }
        val wrong = KeyWizardFixture.down(0, 5000)
        session.accept(wrong); session.accept(KeyWizardFixture.up(wrong))
        assertEquals(2, session.samples.size)
        assertEquals(KeyRole.LEFT_SCAN, session.role)
    }

    @Test fun wrongReleaseAndSimultaneousKeysCancelPartialPress() {
        val session = KeyWizardSession()
        session.accept(KeyWizardFixture.down(0))
        session.accept(KeyWizardFixture.up(KeyWizardFixture.down(2)))
        assertFalse(session.waitingRelease)
        assertEquals(0, session.samples.size)
        session.accept(KeyWizardFixture.down(0))
        session.accept(KeyWizardFixture.down(2))
        assertFalse(session.waitingRelease)
    }

    @Test fun releaseAfterPauseCannotCompleteAnOldPress() {
        val session = KeyWizardSession()
        val down = KeyWizardFixture.down(0)
        session.accept(down); session.pause()
        assertFalse(session.accept(KeyWizardFixture.up(down)))
        assertEquals(0, session.samples.size)
        assertTrue(KeyWizardFixture.press(session))
    }

    @Test fun backDuringEarlierStepIsNotLearnedAsAScanKey() {
        val session = KeyWizardSession()
        val back = KeyWizardFixture.down(12)
        session.accept(back); session.accept(KeyWizardFixture.up(back))
        assertEquals(0, session.samples.size)
    }

    @Test fun tooFastDoubleTapDoesNotCountAsIndependentVerification() {
        val session = KeyWizardSession()
        KeyWizardFixture.press(session)
        val fast = KeyWizardFixture.down(0, 1150)
        session.accept(fast); session.accept(KeyWizardFixture.up(fast))
        assertEquals(1, session.samples.size)
        assertTrue(KeyWizardFixture.press(session))
    }

    @Test fun retryRemovesOnlyCurrentIncompleteKey() {
        val session = KeyWizardSession()
        repeat(5) { KeyWizardFixture.press(session) }
        session.retryCurrent()
        assertEquals(4, session.samples.size)
        assertEquals(KeyRole.RIGHT_SCAN, session.role)
    }

    @Test fun previousFromSummaryReopensLastKey() {
        val session = KeyWizardFixture.complete()
        session.previous()
        assertEquals(12, session.samples.size)
        assertEquals(KeyRole.BACK, session.role)
        assertFalse(session.complete)
    }
}
