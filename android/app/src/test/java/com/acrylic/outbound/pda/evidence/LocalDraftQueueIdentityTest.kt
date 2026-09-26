package com.acrylic.outbound.pda.evidence

import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalDraftQueueIdentityTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun changedUserRemainsPausedAfterReturningToOriginalUser() = changed("userId")
    @Test fun changedWarehouseRemainsPausedAfterReturningToOriginalWarehouse() = changed("warehouseId")
    @Test fun changedDeviceRemainsPausedAfterReturningToOriginalDevice() = changed("deviceId")
    @Test fun changedEpochRemainsPausedAfterReturningToOriginalEpoch() = changed("serverRecoveryEpoch")
    @Test fun changedUserVersionRemainsPausedAfterReturningToOriginalVersion() = changed("userAuthVersion")

    private fun changed(field: String) = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); val plan = wire.repository().prepare(f.id); val requests = wire.all()
            val original = JSONObject(checkNotNull(f.fixture.auth).toString())
            f.fixture.auth = JSONObject(original.toString()).put(field, "changed")
            LocalDraftQueueGuard(f.db).reconcile(f.fixture.auth)
            assertFalse(f.db.localDraftQueue().get(f.id)!!.autoRetry)
            f.fixture.auth = original; f.reopen(); LocalDraftQueueGuard(f.db).reconcile(original)
            assertThrows(Exception::class.java) { wire.repository().resume(f.id) }
            assertTrue(wire.reads.isEmpty()); assertTrue(wire.posts.isEmpty())
            assertEquals(plan, wire.repository().prepare(f.id)); assertEquals(requests, wire.all())
            assertEquals("ORIGINALS_STORED", wire.repository().resume(f.id).state)
        }
    }

    @Test fun loginGenerationChangeDurablyRevokesAutomaticTransfer() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            val previous = JSONObject(checkNotNull(f.fixture.auth).toString())
            val current = JSONObject(previous.toString()).put("localSessionId", "new-login")
            f.db.runInTransaction { LocalDraftQueueGuard(f.db).sessionChanged(previous, current) }
            f.fixture.auth = current; f.reopen()
            assertFalse(f.db.localDraftQueue().get(f.id)!!.autoRetry)
            assertThrows(Exception::class.java) { wire.repository().resume(f.id) }
            assertTrue(wire.reads.isEmpty())
        }
    }

    @Test fun tokenRefreshInSameLoginDoesNotRevokeTransfer() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            val previous = JSONObject(checkNotNull(f.fixture.auth).toString())
            val current = JSONObject(previous.toString()).put("accessToken", "refreshed")
            f.db.runInTransaction { LocalDraftQueueGuard(f.db).sessionChanged(previous, current) }
            assertTrue(f.db.localDraftQueue().get(f.id)!!.autoRetry)
        }
    }

    @Test fun logoutRemainsPausedAcrossReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            LocalDraftStopRepository(f.store).stop(f.id)
            val wire = LocalDraftRecoveryFixture(f); wire.repository().prepare(f.id)
            f.db.runInTransaction { LocalDraftQueueGuard(f.db).sessionChanged(f.fixture.auth, null) }
            f.reopen(); LocalDraftQueueGuard(f.db).reconcile(f.fixture.auth)
            assertFalse(f.db.localDraftQueue().get(f.id)!!.autoRetry)
        }
    }
}
