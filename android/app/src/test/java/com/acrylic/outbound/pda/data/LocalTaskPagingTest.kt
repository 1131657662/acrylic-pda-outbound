package com.acrylic.outbound.pda.data

import com.acrylic.outbound.pda.recall.LocalRecall
import com.acrylic.outbound.pda.submission.LocalSubmission
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
class LocalTaskPagingTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun verifiedCompletionMovesToHistoryButRecallRemainsActionable() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { fixture ->
            val id = fixture.assignment
            assertEquals(id, fixture.db.preparations().ordersPage(false, 20, 0).single().assignmentId)
            assertTrue(fixture.db.preparations().ordersPage(true, 20, 0).isEmpty())
            fixture.db.submissions().insert(LocalSubmission("batch", id, "{}", "sha", "SYNCED"))
            assertTrue(fixture.db.preparations().ordersPage(false, 20, 0).isEmpty())
            assertEquals(id, fixture.db.preparations().ordersPage(true, 20, 0).single().assignmentId)
            fixture.db.recalls().insert(LocalRecall("recall", id, "{}"))
            assertEquals(id, fixture.db.preparations().ordersPage(false, 20, 0).single().assignmentId)
            assertTrue(fixture.db.preparations().ordersPage(true, 20, 0).isEmpty())
        }
    }
}
