package com.acrylic.outbound.pda.support

import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION
import com.acrylic.outbound.pda.temporary.LocalSourceBindingMigration
import com.acrylic.outbound.pda.sync.SyncResult
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The infrastructure verifies itself, independently of any business feature: the memory database opens, the page
 * builder really emits a JSON null cursor (the trap that broke a paging loop once), the last-page assertion rejects
 * a phantom "more", the raw versioned database opens at the requested version, and a migration run through it keeps
 * pre-existing rows.
 */
@RunWith(RobolectricTestRunner::class)
class TestInfrastructureTest {

    @Test fun memoryDatabaseOpensAndIsEmpty() {
        val db = TestInfrastructure.memoryDatabase()
        try { assertEquals(0, db.sourceBindings().count()) } finally { db.close() }
    }

    @Test fun pageBuilderEmitsJsonNullCursorAndNotTheStringNull() {
        val page = TestInfrastructure.page(listOf(JSONObject().put("scopeId", "s1")))
        assertTrue("nextCursor 应为 JSON null", page.isNull("nextCursor"))
        assertFalse("nextCursor 不应被写成字符串 null", page.optString("nextCursor") == "null" && !page.isNull("nextCursor"))
        assertEquals(1, page.getJSONArray("items").length())
    }

    @Test fun lastPageAssertionRejectsPhantomMore() {
        TestInfrastructure.assertLastPage(SyncResult("来源绑定无变化"))
        val rejected = runCatching { TestInfrastructure.assertLastPage(SyncResult("仍在读取", more = true)) }
        assertTrue("more=true 不应被当作最后一页", rejected.isFailure)
    }

    @Test fun rawVersionedDatabaseOpensAtRequestedVersionAndMigrationKeepsRows() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "infrastructure-migration.db"
        TestInfrastructure.openRawVersionedDatabase(context, name, 27, listOf(
            """CREATE TABLE IF NOT EXISTS local_draft_queue(clientOrderId TEXT NOT NULL PRIMARY KEY,
                state TEXT NOT NULL,verifiedRequests INTEGER NOT NULL,totalRequests INTEGER NOT NULL,
                autoRetry INTEGER NOT NULL,error TEXT NOT NULL)""",
            "INSERT INTO local_draft_queue VALUES('c1','READY',0,0,1,'')",
        )).use { helper ->
            val database = helper.writableDatabase
            assertEquals(27, database.version)
            LocalSourceBindingMigration.migrate(database)
            assertEquals(1, TestInfrastructure.count(database, "local_draft_queue"))
            assertEquals("READY", TestInfrastructure.text(database, "SELECT state FROM local_draft_queue WHERE clientOrderId='c1'"))
            assertEquals(0, TestInfrastructure.count(database, "local_source_binding"))
        }
        context.deleteDatabase(name)
    }

    /** Documents that the shared constant is the sanctioned source of the version expectation. */
    @Test fun currentSchemaVersionIsExposedForTests() {
        TestInfrastructure.assertDatabaseVersion(PDA_DATABASE_VERSION, TestInfrastructure.currentSchemaVersion())
    }
}
