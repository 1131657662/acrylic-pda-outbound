package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryRecoveredConfirmationRetryTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun resumedSchedulerDoesNotBorrowAChangedLoginForOriginalIntent() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredConfirmationFixture(folder.root).use { f ->
            val row = f.repository.queue(f.id, true); val stage = f.source.repository.read(f.id)
            assertTrue(TemporaryRecoveredConfirmationWorker.sameLogin(stage, f.source.session))
            for (field in listOf("userId", "deviceId", "warehouseId", "sessionId", "localSessionId",
                "userAuthVersion", "deviceVersion", "serverRecoveryEpoch")) {
                val changed = JSONObject(f.source.session.toString()).put(field, "changed")
                assertFalse(field, TemporaryRecoveredConfirmationWorker.sameLogin(stage, changed))
            }
            assertEquals(listOf(f.id), f.db.temporaryRecoveredConfirmations().pending())
            assertEquals(row.commandJson, f.db.temporaryRecoveredConfirmations().get(f.id)!!.commandJson)
            assertNull(f.db.temporaryRecoveredConfirmations().receipt(f.id)); assertTrue(f.commands.isEmpty())
        }
    }
}
