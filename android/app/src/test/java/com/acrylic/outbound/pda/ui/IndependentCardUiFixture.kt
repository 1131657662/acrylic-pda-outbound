package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Button
import androidx.lifecycle.ViewModelProvider
import com.acrylic.outbound.pda.R
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.Shadows

/** Reuses the real generation flow, then opens a separate consumer page. */
internal class IndependentCardUiFixture : AutoCloseable {
    // Lifecycle's JVM singleton otherwise retains the Application from a previous Robolectric test.
    init { resetApplicationFactory() }
    val base = IndependentSetupUiFixture()
    lateinit var card: File
    private var controller: org.robolectric.android.controller.ActivityController<IndependentCredentialActivity>? = null
    val activity get() = checkNotNull(controller).get()
    val model get() = ViewModelProvider(activity)[IndependentCredentialViewModel::class.java]
    fun provision(claims: JSONObject = base.authority.claims()) {
        base.open(); card = base.save(); base.verify(card); base.reply(claims); base.confirm(); base.request()
        controller = Robolectric.buildActivity(IndependentCredentialActivity::class.java).setup()
        awaitReady()
    }
    private fun awaitReady() {
        assertSame(base.app, model.getApplication<android.app.Application>())
        base.await { !model.state.value.busy && activity.findViewById<Button>(R.id.verifyCard).isEnabled }
    }
    fun begin(): Intent {
        awaitReady()
        activity.findViewById<Button>(R.id.verifyCard).performClick()
        val intent = checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult) { model.state.value.message }.intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertFalse(activity.findViewById<Button>(R.id.generateSetup).isEnabled)
        return intent
    }
    fun receive(intent: Intent, file: File?) {
        Shadows.shadowOf(activity).receiveResult(intent, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            file?.let { Intent().setData(Uri.fromFile(it)) })
        awaitReady()
    }
    fun choose(file: File? = card) = receive(begin(), file)
    fun recreate() {
        controller = checkNotNull(controller).recreate()
        awaitReady()
    }
    fun password() = JSONObject(card.readText()).getString("credential").toCharArray()
    override fun close() {
        try { controller?.pause()?.stop()?.destroy(); base.close() }
        finally { resetApplicationFactory() }
    }
    private fun resetApplicationFactory() {
        org.robolectric.util.ReflectionHelpers.setStaticField(
            ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
    }
}
