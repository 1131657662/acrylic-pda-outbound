package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryRecoveredConfirmationTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun explicitIntentAndLostResponseRetainOriginalKeyAcrossReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredConfirmationFixture(folder.root).use { f ->
            assertThrows(IllegalStateException::class.java) { f.repository.queue(f.id, false) }
            assertNull(f.db.temporaryRecoveredConfirmations().get(f.id))
            val original = f.repository.queue(f.id, true)
            f.afterResponse = { throw java.io.IOException("Synthetic response loss after signature") }
            assertThrows(java.io.IOException::class.java) { f.repository.resume(f.id) }
            assertEquals(original.commandJson, f.db.temporaryRecoveredConfirmations().get(f.id)!!.commandJson)
            assertNull(f.db.temporaryRecoveredConfirmations().receipt(f.id)); f.source.local.reopen()
            assertEquals(original.commandId, f.repository.queue(f.id, true).commandId)
            f.afterResponse = {}; assertEquals("CONFIRMED", f.repository.resume(f.id).state)
            assertArrayEquals(f.commands[0], f.commands[1]); assertArrayEquals(f.response, f.db.temporaryRecoveredConfirmations().receipt(f.id)!!.receiptBytes)
            f.source.local.reopen(); assertEquals("CONFIRMED", f.repository.read(f.id)!!.state)
            assertEquals(original.commandJson, f.repository.queue(f.id, true).commandJson)
            assertEquals("CONFIRMED", f.repository.resume(f.id).state); assertEquals(2, f.commands.size)
            assertNull(f.db.temporaryHandoverConfirmations().get(f.id)); assertNull(f.db.temporaryHandoverActivations().get(f.id))
            assertEquals("RECONCILING", f.db.sync().recovery()!!.mode)
        }
    }
    @Test fun lastRoomWriteRollsBackReceiptAndPreservesOriginalIntent() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredConfirmationFixture(folder.root).use { f ->
            val original = f.repository.queue(f.id, true)
            f.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_recovered_confirmation BEFORE UPDATE ON temporary_recovered_confirmation
                WHEN NEW.state='CONFIRMED' BEGIN SELECT RAISE(ABORT,'last confirmation SQL'); END""")
            assertThrows(Exception::class.java) { f.repository.resume(f.id) }
            assertNull(f.db.temporaryRecoveredConfirmations().receipt(f.id))
            assertEquals(original.commandJson, f.db.temporaryRecoveredConfirmations().get(f.id)!!.commandJson)
            assertEquals("ACTION_REQUIRED", f.db.temporaryRecoveredConfirmations().get(f.id)!!.state)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_recovered_confirmation")
            assertEquals(original.commandId, f.repository.queue(f.id, true).commandId)
            assertEquals("CONFIRMED", f.repository.resume(f.id).state)
            assertArrayEquals(f.commands[0], f.commands[1]); assertNotNull(f.source.repository.read(f.id))
        }
    }
    @Test fun concurrentRepositoriesKeepOneDurableCommand() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredConfirmationFixture(folder.root).use { f ->
            val rows = coroutineScope { listOf(async(Dispatchers.IO) { f.repository.queue(f.id, true) },
                async(Dispatchers.IO) { f.repository.queue(f.id, true) }).awaitAll() }
            assertEquals(rows[0].commandJson, rows[1].commandJson)
            assertEquals(rows[0].commandId, f.db.temporaryRecoveredConfirmations().get(f.id)!!.commandId)
        }
    }
}
