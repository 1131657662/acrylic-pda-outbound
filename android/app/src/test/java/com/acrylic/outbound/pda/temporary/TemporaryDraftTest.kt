package com.acrylic.outbound.pda.temporary

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
class TemporaryDraftTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun stableIdentityAndMultiProductPlanSurviveReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val saved = f.drafts.save(f.id, f.plan("3")); val lines = f.db.temporaryDrafts().lines(f.id)
            assertEquals(1L, saved.revision); assertTrue(saved.temporaryNo.matches(Regex("TMP-[A-F0-9]{4}-[0-9]{6,}")))
            f.reopen()
            assertEquals(saved, f.drafts.create(f.id)); assertEquals(lines, f.db.temporaryDrafts().lines(f.id))
            assertEquals(2, lines.size); assertEquals(6, lines.sumOf { it.plannedCount })
            assertEquals(0, f.db.work().unresolvedCount()); assertNull(f.db.preparations().order(f.id))
        }
    }
    @Test fun invalidQuantitiesAndDuplicatesDoNotReplaceSavedPlan() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val saved = f.drafts.save(f.id, f.plan()); val lines = f.db.temporaryDrafts().lines(f.id)
            for (bad in listOf("", "0", "-1", "1.5", "01", " 1", "1000000000", "9223372036854775808"))
                assertThrows(Exception::class.java) { f.drafts.save(f.id, f.plan(bad)) }
            val input = f.plan()
            assertThrows(Exception::class.java) { f.drafts.save(f.id, input.copy(lines = listOf(input.lines[0], input.lines[0]))) }
            assertThrows(Exception::class.java) { f.drafts.save(f.id, input.copy(lines = listOf("ZZZ" to "1"))) }
            assertThrows(Exception::class.java) { f.drafts.save(f.id, input.copy(vehicle = "")) }
            assertThrows(Exception::class.java) { f.drafts.save(f.id, input.copy(reason = "")) }
            assertEquals(saved, f.db.temporaryDrafts().get(f.id)); assertEquals(lines, f.db.temporaryDrafts().lines(f.id))
        }
    }
    @Test fun staleRevisionAndStartedPlanCannotBeOverwritten() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val old = f.plan(); val row = f.drafts.save(f.id, old)
            assertThrows(Exception::class.java) { f.drafts.save(f.id, old) }
            assertThrows(Exception::class.java) { f.drafts.start(f.id, old.revision) }
            val started = f.drafts.start(f.id, row.revision)
            assertThrows(Exception::class.java) { f.drafts.save(f.id, f.plan("4")) }
            assertEquals(started, f.drafts.start(f.id, row.revision))
        }
    }
    @Test fun planTransactionFailureKeepsOriginalRevisionLinesAndAudit() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val row = f.drafts.save(f.id, f.plan()); val lines = f.db.temporaryDrafts().lines(f.id)
            val events = f.db.temporaryScans().events(f.id)
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_temp_plan BEFORE INSERT ON temporary_event WHEN NEW.type='TEMP_PLAN_SAVED' BEGIN SELECT RAISE(ABORT,'injected plan failure'); END")
            assertThrows(Exception::class.java) { f.drafts.save(f.id, f.plan("5")) }
            assertEquals(row, f.db.temporaryDrafts().get(f.id)); assertEquals(lines, f.db.temporaryDrafts().lines(f.id))
            assertEquals(events, f.db.temporaryScans().events(f.id))
        }
    }
    @Test fun expiredSourceCannotCreateNewDraftButExistingIdentityRemainsReadable() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val old = f.db.temporaryDrafts().get(f.id)
            f.fixture.time = java.time.Instant.parse(f.catalog.manifest.getString("expiresAt"))
            assertThrows(Exception::class.java) { f.drafts.create(UUID.randomUUID().toString()) }
            assertThrows(Exception::class.java) { f.drafts.save(f.id, f.plan()) }
            assertEquals(old, f.drafts.create(f.id)); assertEquals(old?.authorizationId, f.catalog.preparation.authorizationId)
        }
    }
    @Test fun sessionChangedDuringSaveRollsBackWholePlan() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            val before = f.db.temporaryDrafts().get(f.id)
            f.fixture.auth!!.put("localSessionId", UUID.randomUUID().toString())
            assertThrows(Exception::class.java) { f.drafts.save(f.id, f.plan()) }
            assertEquals(before, f.db.temporaryDrafts().get(f.id)); assertTrue(f.db.temporaryDrafts().lines(f.id).isEmpty())
        }
    }
    @Test fun corruptStoredPlanCannotBeUsedAfterReopen() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_line SET plannedCount=999 WHERE clientOrderId=?", arrayOf(f.id))
            f.reopen()
            assertThrows(Exception::class.java) { f.receive() }
            assertEquals(0L, f.db.temporaryScans().total(f.id).count)
        }
    }
}
