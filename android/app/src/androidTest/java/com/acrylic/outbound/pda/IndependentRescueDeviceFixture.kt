package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.ui.RescueExportActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File

/** Retained original emulator task, rescue identity and Keystore; never seeds a substitute. */
internal object IndependentRescueDeviceFixture {
    val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    val directory get() = File(app.filesDir, "independent-rescue-device").apply { mkdirs() }
    val id get() = File(app.filesDir, "independent-scan-device/product-task-id.txt").readText()
    const val ownPassword = "synthetic-own-rescue-passphrase"
    const val packagePassword = "synthetic-package-passphrase-only"
    val credentials get() = RescueCredentials.shared(app, app.session)
    fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("independentRescue") == "true")
        assertNull(app.getSystemService(android.net.ConnectivityManager::class.java).activeNetwork)
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        check(app.database.independentTasks().task(id) != null)
    }
    fun grant() = credentials.unlock(checkNotNull(app.database.independentTasks().task(id)).operatorId, ownPassword.toCharArray())
    fun snapshot() = RescueRoomSnapshot(app.database).read(RescueTarget("INDEPENDENT", id), grant())
    fun intent() = Intent(app, RescueExportActivity::class.java).putExtra("source", "INDEPENDENT").putExtra("localOrderId", id)
    fun digest(): String {
        val s = snapshot()
        val bytes = (listOf(s.order.toString()) + s.captures.map { it.toString() } + s.scans.map { it.toString() } +
            s.events.map { it.toString() } + s.lines.map { it.toString() } + s.originals.toSortedMap().values.map { RescueBundle.sha(it) }).joinToString("\n")
        return RescueBundle.sha(bytes.toByteArray(Charsets.UTF_8))
    }
    fun unlock() {
        val operator = checkNotNull(app.database.independentTasks().task(id)).operatorId
        val index = credentials.choices().indexOfFirst { it.id == "own:$operator" }; check(index >= 0)
        // Choice is fixed from pre-provisioned identities; no identity input or synthetic grant.
        onView(withId(R.id.identity)).perform(independentSkuScroll(), click())
        androidx.test.espresso.Espresso.onData(org.hamcrest.Matchers.anything()).atPosition(index).perform(click())
        text(R.id.credential, ownPassword); click(R.id.unlock)
        await { onView(withId(R.id.status)).check(matches(withText(containsString("只读范围核验通过")))) }
    }
    fun text(id: Int, text: String) = onView(withId(id)).perform(independentSkuScroll(), replaceText(text), closeSoftKeyboard())
    fun click(id: Int) = onView(withId(id)).perform(independentSkuScroll(), click())
    fun await(seconds: Int = 40, assertion: () -> Unit) = IndependentCredentialDeviceFixture.await(seconds, assertion)
    fun preserve() {
        val files = RescueExportFiles(app); val access = grant()
        val candidate = files.available(access).first { it.summary.contains(id) }
        val generated = files.recover(candidate.id, access)
        File(directory, "original.pdarescue").writeBytes(generated.file.readBytes())
        File(directory, "result.json").writeText(JSONObject().put("taskId", id).put("packageId", generated.id)
            .put("sha256", generated.sha256).put("targetReadBackVerified", true).put("computerVerified", false)
            .put("localDeletionAllowed", false).put("pendingReconciliation", true).put("source", "INDEPENDENT").toString())
    }
}
