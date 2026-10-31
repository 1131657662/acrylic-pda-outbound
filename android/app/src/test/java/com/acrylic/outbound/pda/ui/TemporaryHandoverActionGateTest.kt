package com.acrylic.outbound.pda.ui

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.acrylic.outbound.pda.BuildConfig
import com.acrylic.outbound.pda.PdaTestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = PdaTestApplication::class)
class TemporaryHandoverActionGateTest {
    @After fun closeWorkManager() = WorkManagerTestInitHelper.closeWorkDatabase()

    @Test fun defaultOffRejectsStaleActionsBeforeSchedulingOrWriting() = runBlocking(Dispatchers.IO) {
        assertFalse(BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT)
        assertFalse(TemporaryHandoverActionGate.isEnabled())
        val app = ApplicationProvider.getApplicationContext<PdaTestApplication>()
        val id = UUID.randomUUID().toString()
        val actions = listOf("TEMP_CONFIRM", "TEMP_INSTALL", "TEMP_START", "TEMP_WORK", "TEMP_SUBMISSION")

        for (action in actions) {
            val model = HandoverViewModel(app)
            model.perform(HandoverCard(id, id, "TEMP", "stale", action, "stale"))
            assertFalse(action, model.state.value.busy)
            assertEquals(action, TemporaryHandoverActionGate.CLOSED_MESSAGE, model.state.value.message)
            assertTrue(WorkManager.getInstance(app).getWorkInfosForUniqueWork("temporary-handover-confirmation:$id").get().isEmpty())
        }
    }

    @Test fun developmentOverrideAllowsTemporaryActionsWithoutChangingFormalActions() {
        assertFalse(TemporaryHandoverActionGate.isBlocked("TEMP_CONFIRM", developmentEnabled = true))
        assertFalse(TemporaryHandoverActionGate.isBlocked("TEMP_START", developmentEnabled = true))
        assertTrue(TemporaryHandoverActionGate.isBlocked("TEMP_CONFIRM", developmentEnabled = false))
        assertFalse(TemporaryHandoverActionGate.isBlocked("CONTINUE", developmentEnabled = false))
    }
}
