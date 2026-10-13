package com.acrylic.outbound.pda

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.sync.RecoveryProtection
import org.hamcrest.CoreMatchers.containsString
import org.json.JSONObject
import java.io.File

/** Existing emulator identity only; no local signed-token, session or business seeding. */
internal object IndependentCredentialDeviceFixture {
    val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PdaApplication
    val directory get() = File(app.filesDir, "independent-credential-device").apply { mkdirs() }
    const val password = "synthetic-independent-native-passphrase"
    val credentials get() = IndependentCredentials.shared(app, app.session, RecoveryProtection(app.database))
    fun guard() {
        check(BuildConfig.DEBUG && android.os.Build.HARDWARE == "ranchu")
        check(InstrumentationRegistry.getArguments().getString("independentCredential") == "true")
        androidx.work.WorkManager.getInstance(app).cancelAllWork().result.get()
        app.session.settings.verifyKey(app.session.publicKey())
    }
    fun active(): JSONObject {
        val root = checkNotNull(IndependentCredentialVault(app).read())
        return root.getJSONObject("entries").getJSONObject(root.getString("active"))
    }
    fun click(id: Int) = onView(withId(id)).perform(independentSkuScroll(), click())
    fun secret(value: String = password) = onView(withId(R.id.password))
        .perform(independentSkuScroll(), replaceText(value), closeSoftKeyboard())
    fun message(text: String) = await { onView(withId(R.id.status)).check(matches(withText(containsString(text)))) }
    fun await(assertion: () -> Unit) {
        val end = System.nanoTime() + 40_000_000_000L
        var last: Throwable? = null
        while (System.nanoTime() < end) {
            try { assertion(); return } catch (error: Throwable) { last = error; Thread.sleep(150) }
        }
        throw AssertionError("Independent credential product state did not settle", last)
    }
    fun record(name: String, value: JSONObject) {
        File(directory, name).writeText(value.put("bootCount", TrustedTime.sample(app).boot).toString())
    }
}
