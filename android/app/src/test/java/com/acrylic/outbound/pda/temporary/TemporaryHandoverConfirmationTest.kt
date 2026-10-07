package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.IOException

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryHandoverConfirmationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun explicitConfirmationPersistsIntentBeforeTransport() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverConfirmationFixture(folder.root).use { f ->
            assertThrows(Exception::class.java) { f.repository.queue(f.id, false) }
            assertNull(f.db.temporaryHandoverConfirmations().get(f.id))
            val row = f.repository.queue(f.id, true)
            assertEquals("PENDING", row.state); assertTrue(f.commands.isEmpty())
            f.stage.local.reopen()
            assertEquals(row, f.db.temporaryHandoverConfirmations().get(f.id))
            assertEquals(row.commandId, f.repository.queue(f.id, true).commandId)
            f.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM temporary_draft").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
        }
    }

    @Test fun responseLossAndReopenRetryOriginalIntentAndStoreOnlyOneReceipt() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverConfirmationFixture(folder.root).use { f ->
            val intent = f.repository.queue(f.id, true)
            f.afterResponse = { throw IOException("lost response") }
            assertThrows(IOException::class.java) { f.repository.resume(f.id) }
            assertNull(f.db.temporaryHandoverConfirmations().receipt(f.id))
            assertTrue(f.db.temporaryHandoverConfirmations().get(f.id)!!.autoRetry)
            f.stage.local.reopen(); f.afterResponse = {}
            assertEquals("CONFIRMED", f.repository.resume(f.id).state)
            assertArrayEquals(f.commands.first(), f.commands.last())
            assertEquals(intent.boundCommandJson, f.db.temporaryHandoverConfirmations().get(f.id)!!.boundCommandJson)
            assertArrayEquals(f.response, f.db.temporaryHandoverConfirmations().receipt(f.id)!!.receiptBytes)
            f.stage.local.reopen()
            assertEquals("CONFIRMED", f.repository.read(f.id)!!.state)
            assertEquals("CONFIRMED", f.repository.resume(f.id).state)
            assertEquals(2, f.commands.size)
            assertEquals("STAGED", f.db.temporaryHandoverStages().header(f.id)!!.state)
        }
    }

    @Test fun lastProgressWriteFailureRollsBackReceiptWithoutLosingIntent() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverConfirmationFixture(folder.root).use { f ->
            val intent = f.repository.queue(f.id, true)
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER temp_confirm_last_write AFTER UPDATE OF state ON temporary_handover_confirmation WHEN NEW.state='CONFIRMED' BEGIN SELECT RAISE(ABORT,'D06_CONFIRM_LAST_WRITE'); END")
            assertThrows(Exception::class.java) { f.repository.resume(f.id) }
            assertNull(f.db.temporaryHandoverConfirmations().receipt(f.id))
            assertEquals(intent.boundCommandJson, f.db.temporaryHandoverConfirmations().get(f.id)!!.boundCommandJson)
            assertEquals("ACTION_REQUIRED", f.db.temporaryHandoverConfirmations().get(f.id)!!.state)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER temp_confirm_last_write")
            f.stage.local.reopen(); f.repository.queue(f.id, true)
            assertEquals("CONFIRMED", f.repository.resume(f.id).state)
            assertArrayEquals(f.commands.first(), f.commands.last())
        }
    }

    @Test fun identityChangeAfterResponsePreventsLocalConfirmation() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverConfirmationFixture(folder.root).use { f ->
            val original = f.stage.session.toString(); f.repository.queue(f.id, true)
            f.afterResponse = { f.stage.session.put("userId", java.util.UUID.randomUUID().toString()) }
            assertThrows(Exception::class.java) { f.repository.resume(f.id) }
            assertNull(f.db.temporaryHandoverConfirmations().receipt(f.id))
            assertFalse(f.db.temporaryHandoverConfirmations().get(f.id)!!.autoRetry)
            assertThrows(Exception::class.java) { f.repository.queue(f.id, true) }
            f.stage.local.auth = JSONObject(original); f.afterResponse = {}
            f.repository.queue(f.id, true); assertEquals("CONFIRMED", f.repository.resume(f.id).state)
        }
    }

    @Test fun alteredSignedActorOrCapabilitiesCannotConfirm() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverConfirmationFixture(folder.root).use { f ->
            f.repository.queue(f.id, true)
            f.transform = { it.getJSONObject("actor").put("deviceVersion", "999") }
            assertThrows(Exception::class.java) { f.repository.resume(f.id) }
            assertNull(f.db.temporaryHandoverConfirmations().receipt(f.id))
            f.response = null; f.transform = { it.getJSONObject("capabilities").put("localDeletionAllowed", true) }
            f.repository.queue(f.id, true)
            assertThrows(Exception::class.java) { f.repository.resume(f.id) }
            assertNull(f.db.temporaryHandoverConfirmations().receipt(f.id))
        }
    }

    @Test fun expiredQualificationAndDisabledDevelopmentKeepOriginalStage() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverConfirmationFixture(folder.root).use { f ->
            assertThrows(Exception::class.java) { TemporaryHandoverConfirmationRepository(f.db, f.connection, false).queue(f.id, true) }
            f.repository.queue(f.id, true); f.stage.local.time = f.stage.local.time.plusSeconds(172801)
            assertThrows(Exception::class.java) { f.repository.resume(f.id) }
            assertTrue(f.commands.isEmpty()); assertNotNull(f.db.temporaryHandoverStages().header(f.id))
            assertFalse(f.db.temporaryHandoverConfirmations().get(f.id)!!.autoRetry)
        }
    }
}
