package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.Spinner
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.rescue.OwnRescueSetupDraft
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.robolectric.Shadows
import org.robolectric.Robolectric
import androidx.lifecycle.ViewModelProvider

internal class OwnRescueCardUiFixture : AutoCloseable {
    val base = RescueSignedFileFixture()
    private val controller = Robolectric.buildActivity(RescueExportActivity::class.java).setup()
    val activity get() = controller.get()
    val model get() = ViewModelProvider(activity)[RescueExportViewModel::class.java]
    val card: File
    init {
        OwnRescueSetupDraft.create(base.app.session.requireSession(), base.digest).use { draft ->
            card = File(activity.cacheDir, "synthetic-own-card-" + java.util.UUID.randomUUID() + ".json")
            draft.write({ card.outputStream() }, { card.inputStream() }); draft.verify(card.inputStream())
            val password = draft.credential(base.app.session.requireSession(), base.digest)
            base.reply()
            runBlocking(Dispatchers.IO) { base.credentials.provisionOwn(password, draft.expectedSession()) }
            assertTrue(password.all { it.code == 0 }); assertEquals("GET", base.server.takeRequest().method)
        }
        reload()
    }
    fun reload() {
        model.loadIdentities()
        base.await { !model.state.value.busy && activity.findViewById<Spinner>(R.id.identity).count == model.state.value.identities.size }
    }
    fun begin(): Intent {
        activity.findViewById<Button>(R.id.ownCardUnlock).performClick()
        val picker = Shadows.shadowOf(activity).nextStartedActivityForResult.intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action); return picker
    }
    fun receive(picker: Intent, file: File?) {
        Shadows.shadowOf(activity).receiveResult(picker, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            file?.let { Intent().setData(Uri.fromFile(it)) })
        base.await { !model.state.value.busy }
    }
    fun choose(file: File? = card) { receive(begin(), file) }
    fun recreate() { controller.recreate(); base.await { !model.state.value.busy } }
    fun password() = JSONObject(card.readText()).getString("credential").toCharArray()
    override fun close() { controller.pause().stop().destroy(); base.close() }
}
