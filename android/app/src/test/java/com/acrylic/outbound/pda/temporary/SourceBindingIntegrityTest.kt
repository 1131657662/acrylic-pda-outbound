package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.support.TestInfrastructure
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.util.UUID

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SourceBindingIntegrityTest {
    private val db = TestInfrastructure.memoryDatabase()
    private val auth = JSONObject().put("userId", id()).put("deviceId", id()).put("warehouseId", id())
        .put("serverRecoveryEpoch", "1").put("localSessionId", id()).put("userAuthVersion", "1").put("deviceVersion", "1")
    @After fun close() { db.close() }

    @Test fun acceptedRelinkReplacesTargetAndReplayDoesNotRewriteProjection() {
        val original = row(); install(original)
        val relink = copy(original).put("mappingVersion", "3").put("canonicalOrderId", id()).put("orderNo", "RELINKED")
        install(relink)
        val expected = db.sourceBindings().get(original.getString("scopeId"))!!
        assertEquals(relink.getString("canonicalOrderId"), expected.canonicalOrderId)
        assertEquals("3", expected.mappingVersion)
        assertEquals("来源绑定无变化", install(relink).message)
        assertEquals(expected, db.sourceBindings().get(expected.scopeId))
    }

    @Test fun staleOrConflictingProjectionCannotOverwriteInstalledMapping() {
        val original = row().put("mappingVersion", "3").put("workRound", "2"); install(original)
        val before = db.sourceBindings().all()
        for ((key, value) in mapOf("mappingVersion" to "2", "canonicalOrderId" to id(), "clientOrderId" to id(), "workRound" to "1")) {
            assertThrows(key, Exception::class.java) { install(copy(original).put(key, value)) }
            assertEquals(before, db.sourceBindings().all())
        }
    }

    @Test fun malformedLastRowRollsBackEntirePageAndPreservesOriginalMapping() {
        val original = row(); install(original)
        val before = db.sourceBindings().all()
        val relink = copy(original).put("mappingVersion", "3").put("canonicalOrderId", id())
        val bad = row().put("mappingOrigin", "INITIAL")
        assertThrows(Exception::class.java) { install(relink, bad) }
        assertEquals(before, db.sourceBindings().all())
    }

    @Test fun lastSqlWriteFailureRollsBackEarlierMappingInstallations() {
        val first = row(); val second = row()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER binding_failure BEFORE INSERT ON local_source_binding " +
            "WHEN NEW.scopeId='${second.getString("scopeId")}' BEGIN SELECT RAISE(ABORT, 'binding last write'); END")
        assertThrows(Exception::class.java) { install(first, second) }
        assertEquals(0, db.sourceBindings().count())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER binding_failure")
        install(first, second); assertEquals(2, db.sourceBindings().count())
    }

    @Test fun identityAndAuthorizationChangesAfterHttpPreventInstallation() {
        for (field in listOf("userId", "deviceId", "warehouseId", "serverRecoveryEpoch", "localSessionId", "userAuthVersion", "deviceVersion")) {
            val initial = auth.getString(field)
            val repo = SourceBindingRepository(db, { auth }) { _, _ ->
                auth.put(field, "changed"); TestInfrastructure.page(listOf(row()))
            }
            assertThrows(field, Exception::class.java) { repo.installBindings() }
            assertEquals(0, db.sourceBindings().count()); auth.put(field, initial)
        }
    }

    @Test fun terminalRoundCannotReopenButNewRoundCanProceed() {
        val completed = row().put("phase", "COMPLETED"); install(completed)
        assertThrows(Exception::class.java) { install(copy(completed).put("phase", "FROZEN")) }
        install(copy(completed).put("workRound", "2").put("phase", "FROZEN"))
        assertEquals("2", db.sourceBindings().get(completed.getString("scopeId"))!!.workRound)
    }

    @Test fun malformedPageAndDuplicateScopesNeverPartiallyInstall() {
        val original = row()
        assertThrows(Exception::class.java) { install(original, copy(original)) }
        for (key in listOf("scopeId", "clientOrderId", "canonicalOrderId", "mappingId", "mappingVersion", "workRound", "phase")) {
            assertThrows(key, Exception::class.java) { install(copy(original).put(key, "invalid")) }
        }
        assertEquals(0, db.sourceBindings().count())
    }

    private fun install(vararg rows: JSONObject) = SourceBindingRepository(db, { auth }) { _, _ ->
        TestInfrastructure.page(rows.toList())
    }.installBindings()
    private fun row() = JSONObject().put("scopeId", id()).put("clientOrderId", id()).put("canonicalOrderId", id())
        .put("mappingId", id()).put("mappingVersion", "2").put("mappingOrigin", "CONTROLLED_LINK")
        .put("installRequired", true).put("workRound", "1").put("phase", "FROZEN").put("orderNo", "SYNTHETIC")
    private fun copy(row: JSONObject) = JSONObject(row.toString())
    private fun id() = UUID.randomUUID().toString()
}
