package com.acrylic.outbound.pda

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.ui.RescueCredentialActivity
import com.acrylic.outbound.pda.ui.RescueExportActivity
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual isolated registered emulator identity/Keystore; host operates the real system SAF chooser. */
class RescueExportDeviceTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    private val directory get() = File(app.filesDir, "rescue-device").apply { mkdirs() }
    private val ownPassword = "synthetic-own-rescue-passphrase"
    private val packagePassword = "synthetic-package-passphrase-only"
    private fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("rescueExport") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
    }
    @Test fun provisionOwnCredentialThroughOnlineProductPageThenLogout() {
        guard()
        val input = JSONObject(File(app.filesDir, "fault-restart/private/input.json").readText()).getJSONObject("android")
        app.session.login(input.getString("username"), input.getString("password"))
        val identity = app.session.requireSession()
        ActivityScenario.launch<RescueCredentialActivity>(Intent(app, RescueCredentialActivity::class.java)).use {
            onView(withId(R.id.password)).perform(scrollTo(), replaceText(ownPassword), closeSoftKeyboard())
            onView(withId(R.id.provision)).perform(scrollTo(), click())
            await { onView(withId(R.id.status)).check(matches(withText(containsString("本人只读凭据已预置")))) }
        }
        val credentials = RescueCredentials.shared(app, app.session)
        assertEquals(identity.getString("userId"), credentials.identities().single().operatorId)
        app.session.logout(); assertNull(app.session.saved())
        File(directory, "provision.json").writeText(JSONObject().put("deviceId", identity.getString("deviceId"))
            .put("operatorId", identity.getString("userId")).put("warehouseId", identity.getString("warehouseId"))
            .put("onlineProductProvisioned", true).put("loggedOut", true).toString())
    }
    @Test fun stoppedServiceLoggedOutProductGeneratesAndCopiesWithRealSaf() {
        guard(); assertNull(app.session.saved())
        val id = JSONObject(File(app.filesDir, "scan-adjustment/temporary-result.json").readText()).getString("clientOrderId")
        val original = checkNotNull(app.database.temporarySubmissions().forOrder(id))
        val intent = Intent(app, RescueExportActivity::class.java).putExtra("source", "TEMP").putExtra("localOrderId", id)
        ActivityScenario.launch<RescueExportActivity>(intent).use {
            unlock()
            it.onActivity { activity -> diagnose(activity) }
            onView(withId(R.id.readSnapshot)).perform(scrollTo(), click())
            await { onView(withId(R.id.generate)).check(matches(isEnabled())) }
            onView(withId(R.id.packagePassword)).perform(scrollTo(), replaceText(packagePassword), closeSoftKeyboard())
            onView(withId(R.id.generate)).perform(scrollTo(), click())
            await { onView(withId(R.id.copyState)).check(matches(withText(containsString("本机包生成并回读通过")))) }
            onView(withId(R.id.copy)).perform(scrollTo(), click())
            File(directory, "ready-for-saf.txt").writeText("Actual ACTION_CREATE_DOCUMENT chooser pending; no copy success yet")
            await(90) { onView(withId(R.id.copyState)).check(matches(withText(containsString("目标写入与回读一致")))) }
            preservePackage(id)
        }
        assertEquals(original, app.database.temporarySubmissions().forOrder(id))
        assertNull(app.session.saved())
    }
    @Test fun newProcessRestoresOriginalPackageThenSafCancelKeepsOriginal() {
        guard(); assertNull(app.session.saved())
        val original = JSONObject(File(directory, "result.json").readText())
        ActivityScenario.launch<RescueExportActivity>(Intent(app, RescueExportActivity::class.java)).use {
            unlock()
            onView(withId(R.id.recoverPackage)).perform(scrollTo(), click())
            await { onView(withId(R.id.copyState)).check(matches(withText(containsString("原私有包回读通过")))) }
            onView(withId(R.id.copy)).perform(scrollTo(), click())
            File(directory, "ready-for-cancel.txt").writeText("Real chooser pending; host must press system back")
            await(60) { onView(withId(R.id.status)).check(matches(withText(containsString("目标选择已取消")))) }
            val access = credentials().unlock(original.getString("operatorId"), ownPassword.toCharArray())
            val recovered = RescueExportFiles(app).recover(original.getString("packageId"), access)
            assertEquals(original.getString("sha256"), recovered.sha256)
            File(directory, "cancel.json").writeText(JSONObject().put("privatePackagePreserved", true)
                .put("newProcess", true).put("newExternalCopyVerified", false).toString())
        }
    }
    private fun credentials() = RescueCredentials.shared(app, app.session)
    private fun diagnose(activity: RescueExportActivity) {
        val root = activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        File(directory, "unlocked.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val views = JSONObject().put("width", root.width).put("height", root.height)
        listOf(R.id.credential, R.id.unlock, R.id.status, R.id.order, R.id.readSnapshot, R.id.generate).forEach { id ->
            val view = activity.findViewById<android.view.View>(id); val position = IntArray(2); view.getLocationOnScreen(position)
            views.put(activity.resources.getResourceEntryName(id), JSONObject().put("height", view.height).put("width", view.width)
                .put("x", position[0]).put("y", position[1]).put("enabled", view.isEnabled))
        }
        File(directory, "unlocked-dimensions.json").writeText(views.toString())
    }
    private fun unlock() {
        await { onView(withId(R.id.unlock)).check(matches(isEnabled())) }
        onView(withId(R.id.credential)).perform(scrollTo(), replaceText(ownPassword), closeSoftKeyboard())
        onView(withId(R.id.unlock)).perform(scrollTo(), click())
        await { onView(withId(R.id.status)).check(matches(withText(containsString("只读范围核验通过")))) }
    }
    private fun preservePackage(orderId: String) {
        val identity = JSONObject(File(directory, "provision.json").readText())
        val access = credentials().unlock(identity.getString("operatorId"), ownPassword.toCharArray())
        val files = RescueExportFiles(app); val candidate = files.available(access).first()
        val generated = files.recover(candidate.id, access)
        File(directory, "original.pdarescue").writeBytes(generated.file.readBytes())
        File(directory, "result.json").writeText(JSONObject().put("packageId", generated.id).put("sha256", generated.sha256)
            .put("operatorId", identity.getString("operatorId")).put("clientOrderId", orderId).put("source", "TEMP")
            .put("serviceStopped", true).put("loggedOut", true).put("targetReadBackVerified", true)
            .put("computerVerified", false).put("localDeletionAllowed", false).toString())
    }
    private fun await(seconds: Long = 30, check: () -> Unit) {
        val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(seconds); var last: Throwable? = null
        do { try { check(); return } catch (error: Throwable) { last = error }; Thread.sleep(150) } while (System.nanoTime() < end)
        throw AssertionError("Rescue product did not reach expected state", last)
    }
}
