package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.Spinner
import androidx.lifecycle.ViewModelProvider
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.rescue.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.robolectric.Shadows

/** Original vault and product Activity; random synthetic JCA and loopback HTTP only. */
internal class SupervisorRescueCardUiFixture(expiring: Boolean = false) : AutoCloseable {
    init { resetFactory() }
    val base = RescueSignedFileFixture()
    val activity get() = base.activity
    val model get() = base.model
    val token = if (expiring) base.authority.token(base.authority.claims(base.authority.now.plusSeconds(60))
        .put("deviceKeySha256", base.digest)) else base.token
    val card = File(activity.cacheDir, "synthetic-supervisor-consumer-" + java.util.UUID.randomUUID() + ".json")
    init {
        SupervisorRescueSetupDraft.create(base.app.session.requireSession(), token, base.authority.device to base.digest).use { draft ->
            draft.write({ card.outputStream() }, { card.inputStream() }); draft.verify(card.inputStream())
            base.reply(token); runBlocking(Dispatchers.IO) { base.credentials.importSupervisorSetup(draft) }; base.assertAuthorityGet()
        }
        reload()
    }
    fun reload() {
        model.loadIdentities()
        base.await { !model.state.value.busy && activity.findViewById<Spinner>(R.id.identity).count == model.state.value.identities.size }
    }
    fun begin(): Intent {
        activity.findViewById<Button>(R.id.supervisorCardUnlock).performClick()
        val intent = checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult).intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action); return intent
    }
    fun receive(picker: Intent, file: File?) {
        Shadows.shadowOf(activity).receiveResult(picker, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            file?.let { Intent().setData(Uri.fromFile(it)) })
        base.await { !model.state.value.busy }
    }
    fun choose(file: File? = card) { receive(begin(), file) }
    fun password() = JSONObject(card.readText()).getString("credential").toCharArray()
    fun grant() = base.credentials.unlockChoice("supervisor:" + base.authority.grantId, password())
    fun recreate() { base.recreate(); base.await { !model.state.value.busy } }
    override fun close() { try { base.close() } finally { resetFactory() } }
    private fun resetFactory() {
        org.robolectric.util.ReflectionHelpers.setStaticField(ViewModelProvider.AndroidViewModelFactory::class.java, "_instance", null)
    }
}
