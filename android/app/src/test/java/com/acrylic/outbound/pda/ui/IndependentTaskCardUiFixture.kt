package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import com.acrylic.outbound.pda.R
import com.acrylic.outbound.pda.independent.IndependentSkuRepository
import com.acrylic.outbound.pda.security.TrustedTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.robolectric.Robolectric
import org.robolectric.Shadows
import java.io.File
import java.nio.charset.Charset

/** Real card, vault, task Activity and Room; only authority/JCA/file URI are synthetic. */
internal class IndependentTaskCardUiFixture(credentialSeconds: Long? = null) : AutoCloseable {
    val card = IndependentCardUiFixture()
    private val controller: org.robolectric.android.controller.ActivityController<IndependentTaskActivity>
    val activity get() = controller.get()
    val model get() = ViewModelProvider(activity)[IndependentTaskViewModel::class.java]
    val status get() = activity.findViewById<TextView>(R.id.status).text.toString()
    val draft get() = checkNotNull(model.state.value.draft)
    val pickerButton: Button get() {
        val id = activity.resources.getIdentifier("createWithCard", "id", activity.packageName)
        assertTrue("task page must consume the original card without transcribing its secret", id != 0)
        return checkNotNull(activity.findViewById(id))
    }
    init {
        card.provision(card.base.authority.claims().apply {
            credentialSeconds?.let { put("expiresAt", card.base.authority.issued.plusSeconds(it).toString()) }
        })
        runBlocking(Dispatchers.IO) {
            val sku = ("MODEL".padEnd(20) + "SYNTH".padEnd(14) + "003A0000001".padEnd(13) +
                "300.500".padEnd(10)).toByteArray(Charset.forName("GB18030"))
            val repository = IndependentSkuRepository(card.base.app.database) { TrustedTime.sample(card.base.app) }
            val staged = repository.stage(sku, "synthetic-task-card.txt")
            repository.activate(staged.imported.id, false, staged.activeImportId)
        }
        card.base.server.shutdown()
        controller = Robolectric.buildActivity(IndependentTaskActivity::class.java).setup()
        await { !model.state.value.busy && model.state.value.draft != null }
        text(R.id.vehicle, "合成车"); text(R.id.reason, "服务中断独立采集")
        text(R.id.quantity, "2"); activity.findViewById<Button>(R.id.add).performClick()
        assertEquals(2L, draft.total())
    }
    fun text(id: Int, value: String) { activity.findViewById<EditText>(id).setText(value) }
    fun begin(): Intent {
        pickerButton.performClick()
        val picker = checkNotNull(Shadows.shadowOf(activity).nextStartedActivityForResult) { status }.intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
        return picker
    }
    fun receive(picker: Intent, file: File? = card.card) {
        Shadows.shadowOf(activity).receiveResult(picker, if (file == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            file?.let { Intent().setData(Uri.fromFile(it)) })
        await { !model.state.value.busy && pickerButton.isEnabled == (model.state.value.task == null) }
    }
    fun await(predicate: () -> Boolean) = card.base.await(predicate)
    fun recreate() { controller.recreate(); await { !model.state.value.busy } }
    fun assertNoRequest(id: String = draft.id) = runBlocking(Dispatchers.IO) {
        assertNull(card.base.app.database.independentTasks().request(id))
        assertNull(card.base.app.database.independentTasks().task(id))
    }
    override fun close() {
        try { controller.pause().stop().destroy() } finally { card.close() }
    }
}
