package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.temporary.TemporaryRecoveredConfirmationFixture
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RecoveredHandoverCardReaderTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun recoveredCardRemainsReadOnlyEvenWhenAnOldPendingRowExists() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredConfirmationFixture(folder.root).use { f ->
            val reader = RecoveredHandoverCardReader(f.db, f.connection, true)
            val initial = reader.read(f.id)
            assertTrue(initial.actionAvailable); assertEquals("RECOVERED_READ", initial.action)
            assertEquals("核对本机原始资料", initial.label)
            assertTrue(initial.confirmation.contains("1. 保留")); assertTrue(initial.confirmation.contains("发货号 003"))
            assertNull(f.db.temporaryRecoveredConfirmations().get(f.id))
            f.repository.queue(f.id, true)
            val pending = reader.read(f.id)
            assertEquals("RECOVERED_READ", pending.action)
            assertTrue(pending.status.contains("接收确认与激活已取消"))
            assertEquals("RECONCILING", f.db.sync().recovery()!!.mode)
        }
    }
    @Test fun changedLoginAndClosedFeatureDisableReceivingWithoutLosingOriginals() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryRecoveredConfirmationFixture(folder.root).use { f ->
            val original = f.source.repository.read(f.id)
            assertFalse(RecoveredHandoverCardReader(f.db, f.connection, false).read(f.id).actionAvailable)
            f.source.session.put("localSessionId", UUID.randomUUID().toString())
            val card = RecoveredHandoverCardReader(f.db, f.connection, true).read(f.id)
            assertFalse(card.actionAvailable); assertTrue(card.status.contains("原资料保留"))
            assertEquals(original, f.db.temporaryRecoveredHandoverStages().get(f.id))
            assertNull(f.db.temporaryRecoveredConfirmations().get(f.id)); assertTrue(f.commands.isEmpty())
        }
    }
}
