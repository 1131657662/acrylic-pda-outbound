package com.acrylic.outbound.pda.temporary

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.IOException
import java.util.UUID

/** Generated signatures and real Room transactions; no original device or live HTTP claim. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporaryHandoverWorkTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun missingAckAndClosedGateKeepActivatedDraftBlocked() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            val draft = f.repository.install(f.id, f.original)
            assertThrows(Exception::class.java) { TemporaryHandoverWork(f.store, true).start(f.client) }
            f.acknowledge()
            assertThrows(Exception::class.java) { TemporaryHandoverWork(f.store, false).start(f.client) }
            assertEquals(draft, f.db.temporaryDrafts().get(f.client))
            assertEquals(1, f.db.temporaryScans().events(f.client).size)
        }
    }

    @Test fun lostAckRetriesOriginalKeyAndOpensExactlyOnceAcrossRestart() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            f.repository.install(f.id, f.original); f.stage.local.ack = f.downloadAck()
            val request = f.db.temporary().prepared(f.ticket)!!
            f.stage.local.beforeCall = { throw IOException("synthetic lost ACK response") }
            assertThrows(IOException::class.java) { TemporaryHandoverDownload(f.store, true).acknowledge(f.client) }
            assertNull(f.db.temporary().prepared(f.ticket)!!.ackJson)
            f.stage.local.reopen(); f.stage.local.beforeCall = {}; f.acknowledge()
            val first = TemporaryHandoverWork(f.store, true).start(f.client)
            f.stage.local.reopen()
            assertEquals(first, TemporaryHandoverWork(f.store, true).start(f.client))
            assertEquals(1, f.db.temporaryScans().events(f.client).count { it.type == "TEMP_HANDOVER_WORK_STARTED" })
            val calls = f.stage.local.calls.filter { it.key == request.ackKey }
            assertEquals(2, calls.size); assertEquals(calls[0].body.toString(), calls[1].body.toString())
            assertNull(f.db.temporaryContinuations().forTarget(f.client))
        }
    }

    @Test fun finalEventFailureRollsBackOpeningAndDeviceCounter() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            val draft = f.repository.install(f.id, f.original); f.acknowledge()
            val counter = f.db.work().device(draft.creatorDeviceId)
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_handover_open AFTER INSERT ON temporary_event WHEN NEW.type='TEMP_HANDOVER_WORK_STARTED' BEGIN SELECT RAISE(ABORT,'D06_OPEN_LAST_WRITE'); END")
            assertThrows(Exception::class.java) { TemporaryHandoverWork(f.store, true).start(f.client) }
            assertEquals(draft, f.db.temporaryDrafts().get(f.client)); assertEquals(counter, f.db.work().device(draft.creatorDeviceId))
            assertNotNull(f.db.temporary().prepared(f.ticket)!!.ackJson)
            assertEquals(1, f.db.temporaryScans().events(f.client).size)
            f.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_handover_open")
            assertEquals("SCANNING", TemporaryHandoverWork(f.store, true).start(f.client).state)
        }
    }

    @Test fun identitySessionRecoveryAndAuthorizationVersionChangesRejectOpening() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            val draft = f.repository.install(f.id, f.original); f.acknowledge()
            for (field in listOf("userId", "deviceId", "sessionId", "serverRecoveryEpoch", "userAuthVersion", "deviceVersion")) {
                val previous = f.stage.session.getString(field)
                f.stage.session.put(field, if (field.endsWith("Id")) UUID.randomUUID().toString() else "999")
                assertThrows(Exception::class.java) { TemporaryHandoverWork(f.store, true).start(f.client) }
                assertEquals(draft, f.db.temporaryDrafts().get(f.client)); f.stage.session.put(field, previous)
            }
        }
    }

    @Test fun invalidAckSignatureContextCannotOpen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            f.repository.install(f.id, f.original)
            f.stage.local.ack = f.downloadAck { it.put("userId", UUID.randomUUID().toString()) }
            assertThrows(Exception::class.java) { TemporaryHandoverDownload(f.store, true).acknowledge(f.client) }
            assertNull(f.db.temporary().prepared(f.ticket)!!.ackJson)
            assertEquals("HANDOVER_BLOCKED", f.db.temporaryDrafts().get(f.client)!!.state)
        }
    }

    @Test fun originalPlanAndRetainedGuardTamperingRejectsOpening() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            val draft = f.repository.install(f.id, f.original); f.acknowledge()
            f.db.temporaryDrafts().update(draft.copy(nextScanSeq = 99))
            assertThrows(Exception::class.java) { TemporaryHandoverWork(f.store, true).start(f.client) }
            f.db.temporaryDrafts().update(draft.copy(vehicleNo = "changed"))
            assertThrows(Exception::class.java) { TemporaryHandoverWork(f.store, true).start(f.client) }
            f.db.temporaryDrafts().update(draft)
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_handover_guard SET recordSha256=?", arrayOf("a".repeat(64)))
            assertThrows(Exception::class.java) { TemporaryHandoverWork(f.store, true).start(f.client) }
            assertEquals(draft, f.db.temporaryDrafts().get(f.client))
        }
    }

    @Test fun scanningStateWithoutOpeningAuditCannotAdmitInput() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryHandoverActivationFixture(folder.root).use { f ->
            val draft = f.repository.install(f.id, f.original); f.acknowledge()
            f.db.temporaryDrafts().update(draft.copy(state = "SCANNING"))
            assertThrows(Exception::class.java) { TemporaryHandoverWork(f.store, true).start(f.client) }
            val store = f.store; store.load(f.client)
            assertThrows(Exception::class.java) { TemporaryReception(store).receive(
                TemporaryInput(f.client, "00100000005", "BROADCAST", store.sample())) }
            assertTrue(f.db.temporaryScans().captures(f.client).isEmpty())
        }
    }
}
