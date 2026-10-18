package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.ui.DeviceSettingsActivity
import com.acrylic.outbound.pda.ui.WorkActivity
import org.hamcrest.CoreMatchers.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/** One-shot synthetic HTTP order on a retained native identity; never changes earlier orders. */
class FormalPendingStatusDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private lateinit var directory: File

    @Test fun pendingInputBecomesSavedOnlyAfterActualProductConfirmation() {
        guard()
        revalidate()
        acknowledgePreviousSyntheticFailure()
        save("database-before.json")
        val web = webClient()
        val code = publishSyntheticStock(web)
        val assignment = prepare(web, code.take(3))
        File(directory, "assignment.txt").writeText(assignment)
        ActivityScenario.launch<WorkActivity>(Intent(app, WorkActivity::class.java)
            .putExtra("assignmentId", assignment)).use { scenario ->
            exercise(assignment, code)
            scenario.recreate()
            await { onView(withId(R.id.recentSaved)).check(matches(withText(containsString(code)))) }
            onView(withId(R.id.message)).check(matches(not(withText(containsString("待确认输入")))))
        }
        assertEquals(1, app.database.captures().page(assignment, 0, 2).size)
        assertEquals(1L, app.database.scans().total(assignment).count)
        assertNull(app.database.submissions().forOrder(assignment))
        save("database-after.json")
        File(directory, "result.json").writeText(JSONObject().put("assignmentId", assignment)
            .put("barcode", code).put("pendingBeforeSave", 1).put("savedBeforeConfirmation", 0)
            .put("savedAfterConfirmation", 1).put("recreated", true).put("noSubmission", true)
            .put("localDeletionAllowed", false).toString(2))
    }

    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        val args = InstrumentationRegistry.getArguments()
        check(args.getString("formalPendingStatus") == "true")
        check(app.session.settings.server == "http://127.0.0.1:19781/api/v1/")
        val run = checkNotNull(args.getString("evidenceRun"))
        check(run.matches(Regex("[a-z0-9-]{1,40}")))
        directory = File(app.filesDir, "formal-pending-status/$run")
        check(directory.mkdirs()) { "Preserve previous native attempt" }
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }

    private fun revalidate() {
        val before = app.session.requireSession()
        val key = app.session.publicKey()
        val database = DraftDiagnosticDatabaseSnapshot(app).read().toString(2)
        ActivityScenario.launch<DeviceSettingsActivity>(Intent(app, DeviceSettingsActivity::class.java)).use {
            await { onView(withId(R.id.checkConnection)).check(matches(isEnabled())) }
            onView(withId(R.id.checkConnection)).perform(independentSkuScroll(), click())
            await { assertTrue(runCatching { app.session.now() }.isSuccess) }
        }
        val after = app.session.requireSession()
        listOf("deviceId", "userId", "warehouseId", "localSessionId").forEach {
            assertEquals(before.getString(it), after.getString(it))
        }
        assertEquals(key, app.session.publicKey())
        assertEquals(database, DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
    }

    private fun webClient(): FaultRestartWebClient {
        val input = JSONObject(File(app.filesDir, "high-frequency/private/input.json").readText())
        check(input.getString("baseUrl") == app.session.settings.server)
        return FaultRestartWebClient(input.getString("baseUrl"), input).also { it.login() }
    }

    private fun publishSyntheticStock(web: FaultRestartWebClient): String {
        val code = "003F" + UUID.randomUUID().toString().replace("-", "").take(7).uppercase()
        val rows = listOf("003B0051601" to "300.5", "571B0051602" to "301", code to "300.5")
        val bytes = java.io.ByteArrayOutputStream()
        rows.forEach { (barcode, weight) ->
            val row = ByteArray(59) { ' '.code.toByte() }
            val encoding = java.nio.charset.Charset.forName("GB18030")
            listOf(0 to "H815B 1.7 RE 38 300", 20 to "有光短纤", 34 to barcode, 47 to weight)
                .forEach { (offset, value) -> value.toByteArray(encoding).copyInto(row, offset) }
            row[57] = 13; row[58] = 10; bytes.write(row)
        }
        val id = web.uploadStock(bytes.toByteArray())
        val diff = web.get("inventory/imports/$id/diff")
        web.post("inventory/imports/$id/publish", JSONObject()
            .put("expectedActiveSnapshot", diff.getJSONObject("baseline").get("activeSnapshotId"))
            .put("expectedMappingRevision", diff.getJSONObject("baseline").getString("mappingRevision"))
            .put("acceptIdenticalDuplicates", true).put("acceptMappingChanges", true)
            .put("confirmedRemovedCount", diff.getInt("removed")).put("reason", "隔离正式状态验证"))
        return code
    }

    private fun acknowledgePreviousSyntheticFailure() {
        val original = File(app.filesDir, "formal-pending-status/formal-status04/assignment.txt")
        if (!original.isFile) return
        val id = original.readText().trim()
        val failed = app.database.captures().failure(id) ?: return
        assertEquals("INVALID", failed.state)
        ActivityScenario.launch<WorkActivity>(Intent(app, WorkActivity::class.java).putExtra("assignmentId", id)).use {
            await { onView(withId(R.id.confirm)).check(matches(withText(containsString("本包未保存")))) }
            onView(withId(R.id.confirm)).perform(independentSkuScroll(), click())
            await { assertNull(app.database.captures().failure(id)) }
        }
        val after = checkNotNull(app.database.captures().get(failed.id))
        assertEquals(failed.copy(failureAckAt = after.failureAckAt), after)
        assertNotNull(after.failureAckAt)
        assertEquals(0L, app.database.scans().total(id).count)
    }

    private fun prepare(web: FaultRestartWebClient, product: String): String {
        val baseline = web.get("inventory/current")
        val plan = JSONObject().put("vehicleNo", "正式待确认状态隔离车").put("containerNo", "")
            .put("notes", "合成状态验证").put("expectedActiveSnapshot", baseline.get("activeSnapshotId"))
            .put("expectedMappingRevision", baseline.get("mappingRevision"))
            .put("lines", JSONArray().put(JSONObject().put("deliveryCode", product).put("plannedCount", 1)))
        val id = web.post("orders", plan).getString("orderId")
        File(directory, "order.txt").writeText(id)
        web.post("orders/$id/publish", JSONObject().put("expectedVersion", "1")
            .put("expectedPolicyVersion", "1").put("reason", "隔离正式状态入口验证"))
        val order = app.repository.list("pda/orders", "cursor").single { it.getString("id") == id }
        return app.repository.prepare(app.repository.claim(order, "隔离正式状态扫码")) {}.assignmentId
    }

    private fun exercise(id: String, code: String) {
        await { assertTrue(app.work.state.value.ready) }
        onView(withId(R.id.manualToggle)).perform(independentSkuScroll(), click())
        onView(withId(R.id.manual)).perform(independentSkuScroll(), replaceText(code), closeSoftKeyboard())
        onView(withId(R.id.validate)).perform(independentSkuScroll(), click())
        await {
            assertEquals("WAITING_CONFIRM", app.work.state.value.candidate?.state)
            onView(withId(R.id.message)).check(matches(withText(containsString("待确认输入 1"))))
        }
        assertEquals(0L, app.database.scans().total(id).count)
        onView(withId(R.id.totals)).check(matches(withText(containsString("已保存 0 / 计划 1"))))
        onView(withId(R.id.message)).check(matches(not(withText(containsString("待处理 0")))))
        onView(withId(R.id.confirm)).perform(independentSkuScroll(), click())
        await { assertEquals(1L, app.database.scans().total(id).count) }
        await { onView(withId(R.id.recentSaved)).check(matches(withText(containsString(code)))) }
        onView(withId(R.id.totals)).check(matches(withText(containsString("已保存 1 / 计划 1"))))
        onView(withId(R.id.message)).check(matches(not(withText(containsString("待确认输入")))))
    }

    private fun save(name: String) {
        File(directory, name).writeText(DraftDiagnosticDatabaseSnapshot(app).read().toString(2))
    }

    private fun await(check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
        var last: AssertionError? = null
        do {
            try { check(); return } catch (error: AssertionError) { last = error }
            Thread.sleep(50)
        } while (System.nanoTime() < end)
        throw AssertionError("Formal status did not reach expected state", last)
    }
}
