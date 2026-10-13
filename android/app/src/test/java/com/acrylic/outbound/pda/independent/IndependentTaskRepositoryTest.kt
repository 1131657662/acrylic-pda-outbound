package com.acrylic.outbound.pda.independent

import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentTaskRepositoryTest {
    @Test fun createFixesOriginalCatalogIdentityAndPlanAcrossDatabaseReopen() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val command = f.command(); val grant = f.grant()
            val original = f.repository.create(command, grant)
            f.sku.reopen()
            assertEquals(original, f.repository.read(command.id))
            assertEquals("PLANNED", original.state.state); assertEquals(0L, original.state.actualCount)
            assertEquals(2L, original.state.nextEventSeq); assertEquals(1, original.events.size)
            assertEquals(command.sha(), original.task.commandSha256); assertEquals(2L, original.lines.single().plannedCount)
            assertEquals(grant.proof.originalToken, original.task.signedCredential)
            assertFalse(f.sku.db.independentTasks().request(command.id)!!.authorizationJson.contains("synthetic-bearer"))
        }
    }
    @Test fun laterCatalogAndCredentialCannotRewriteOriginalTaskOrDeadline() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val command = f.command(); val original = f.repository.create(command, f.grant())
            f.sku.activate("004A0000001"); f.identity.provision()
            assertEquals(original, f.repository.create(command, f.grant()))
            assertEquals(original, f.repository.read(command.id)); assertEquals(1L, f.sku.db.independentTasks().count())
        }
    }
    @Test fun changedRequestInputIsRejectedWithoutChangingOriginal() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val command = f.command(); val original = f.repository.create(command, f.grant())
            listOf(command.copy(vehicle = "沪B98765"), command.copy(reason = "other"), command.copy(quantities = mapOf("003" to 3L))).forEach {
                assertThrows(IllegalStateException::class.java) { f.repository.create(it, f.grant()) }
            }
            assertEquals(original, f.repository.read(command.id))
        }
    }
    @Test fun auditTailFailureRollsBackWholeTaskButPreservesDurableRequest() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val command = f.command(); val grant = f.grant()
            f.sku.db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_create BEFORE INSERT ON independent_task_event
                BEGIN SELECT RAISE(ABORT,'isolated audit tail'); END""")
            assertThrows(Exception::class.java) { f.repository.create(command, grant) }
            assertNull(f.sku.db.independentTasks().task(command.id)); assertTrue(f.sku.db.independentTasks().lines(command.id).isEmpty())
            assertNull(f.sku.db.independentTasks().state(command.id)); assertNotNull(f.repository.latestRequest())
            f.sku.reopen(); f.sku.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_create")
            val restored = f.repository.request(command.id); assertEquals(command, restored.first); assertNull(restored.second)
            assertEquals(command.id, f.repository.create(restored.first, f.grant()).task.id)
        }
    }
    @Test fun newCredentialCannotReplacePendingOriginalAuthorization() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val command = f.command(); val request = f.repository.prepare(command, f.grant())
            f.identity.provision()
            assertThrows(IllegalStateException::class.java) { f.repository.create(command, f.grant()) }
            assertEquals(request, f.repository.latestRequest()); assertNull(f.repository.request(command.id).second)
        }
    }
    @Test fun expiredOriginalAndRevokedGrantCannotCreateNewTask() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val grant = f.grant(); val request = f.repository.prepare(f.command(), grant)
            f.identity.advance(48*3600*1000L)
            assertThrows(IllegalStateException::class.java) { f.repository.create(f.repository.request(request.id).first, grant) }
            assertEquals(0L, f.sku.db.independentTasks().count()); assertEquals(request, f.repository.latestRequest())
        }
        IndependentTaskFixture().use { f ->
            val grant = f.grant(); f.identity.store.revoke()
            assertThrows(IllegalStateException::class.java) { f.repository.create(f.command(), grant) }
            assertEquals(0L, f.sku.db.independentTasks().count()); assertNull(f.repository.latestRequest())
        }
    }
    @Test fun foreignActorCannotReadOrCreateUsingSomeoneElsesGrant() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val command = f.command(); val original = f.repository.create(command, f.grant())
            f.actor = f.actor.copy(operatorId = java.util.UUID.randomUUID().toString())
            assertThrows(IllegalStateException::class.java) { f.repository.read(command.id) }
            assertThrows(IllegalStateException::class.java) { f.repository.create(f.command(), f.grant()) }
            assertTrue(f.repository.requests().isEmpty()); assertEquals(original.task, f.sku.db.independentTasks().task(command.id))
        }
    }
    @Test fun productMustComeFromOriginalEnabledFile() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            assertThrows(IllegalStateException::class.java) { f.repository.create(f.command().copy(quantities = mapOf("004" to 1L)), f.grant()) }
            val old = f.command(); f.sku.activate("004A0000001")
            assertThrows(IllegalStateException::class.java) { f.repository.create(old, f.grant()) }
            assertNull(f.repository.latestRequest()); assertEquals(0L, f.sku.db.independentTasks().count())
        }
    }
    @Test fun immutableRequestTaskPlanAndAuditCannotBeUpdatedOrDeleted() = runBlocking<Unit>(Dispatchers.IO) {
        IndependentTaskFixture().use { f ->
            val original = f.repository.create(f.command(), f.grant())
            val db = f.sku.db.openHelper.writableDatabase
            listOf("independent_task", "independent_task_line", "independent_task_request", "independent_task_event").forEach { table ->
                assertThrows(Exception::class.java) { db.execSQL("DELETE FROM $table") }
                val field = if (table == "independent_task_line") "model" else "id"
                assertThrows(Exception::class.java) { db.execSQL("UPDATE $table SET $field='changed'") }
            }
            assertEquals(original, f.repository.read(original.task.id))
        }
    }
}
