package com.acrylic.outbound.pda.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import com.acrylic.outbound.pda.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

/** New card UI consumes nonempty Room records; synthetic person, HTTP, JCA and documents. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SupervisorPagesExportTest {
    @Test fun supervisorCardExportsOnlySignedOriginalOrderAfterOfflineLogout() {
        TemporaryPagesExportFixture().use { f ->
            val other = java.util.UUID.randomUUID().toString()
            runBlocking(Dispatchers.IO) { f.base.work.drafts.create(other); f.base.work.store.load(f.base.work.id) }
            f.base.populate()
            val setup = SupervisorPagesCardSetup(f); val card = setup.create(); val originalCard = card.readBytes()
            val expected = SupervisorPagesExportEvidence.expected(f)
            f.base.stopServer(); f.logout(); val before = TemporaryPagesRoomDigest.read(f)
            f.openExport(); unlock(f, setup, card)
            assertEquals(listOf(f.base.work.id), f.model.state.value.orders.map { it.target.localOrderId })
            f.readSnapshot(); val material = f.generate(); val materialBytes = material.readBytes()
            val copy = copy(f)
            val files = SupervisorPagesExportEvidence.unpack(copy, material)
            SupervisorPagesExportEvidence.verify(f, files, expected)
            assertNoSupervisorSecret(files, card, setup, material)
            SupervisorPagesExportEvidence.persist(f, copy, material, expected)
            denyOtherOrder(f, setup, card, other)
            assertEquals(before, TemporaryPagesRoomDigest.read(f))
            assertArrayEquals(originalCard, card.readBytes()); assertArrayEquals(materialBytes, material.readBytes())
            assertNull(f.base.vault.read()); assertEquals(2, f.base.server.requestCount)
            assertEquals("", f.activity.findViewById<EditText>(R.id.credential).text.toString())
        }
    }

    private fun unlock(f: TemporaryPagesExportFixture, setup: SupervisorPagesCardSetup, card: File) {
        val id = "supervisor:" + setup.authority.grantId
        val index = f.model.state.value.identities.indexOfFirst { it.id == id }
        assertTrue(index >= 0); f.activity.findViewById<Spinner>(R.id.identity).setSelection(index)
        f.activity.findViewById<Button>(R.id.supervisorCardUnlock).performClick()
        val picker = checkNotNull(Shadows.shadowOf(f.activity).nextStartedActivityForResult).intent
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, picker.action)
        Shadows.shadowOf(f.activity).receiveResult(picker, Activity.RESULT_OK, Intent().setData(Uri.fromFile(card)))
        f.base.await { !f.model.state.value.busy }
        assertTrue(f.model.state.value.message, f.model.state.value.unlocked)
    }

    private fun copy(f: TemporaryPagesExportFixture): File {
        f.copy(null); assertTrue(f.model.state.value.canCopy)
        assertTrue(f.model.state.value.message.contains("取消"))
        val copy = File(f.activity.cacheDir, "synthetic-supervisor-copy-" + java.util.UUID.randomUUID() + ".pdarescue")
        f.copy(copy); assertTrue(f.model.state.value.copyState.contains("目标写入与回读一致"))
        assertArrayEquals(f.packages.single().readBytes(), copy.readBytes()); return copy
    }

    private fun denyOtherOrder(f: TemporaryPagesExportFixture, setup: SupervisorPagesCardSetup, card: File, other: String) {
        f.activity.findViewById<Button>(R.id.lockScope).performClick()
        f.model.prefer("TEMP", other); unlock(f, setup, card)
        assertEquals(other, f.model.state.value.orders[f.model.state.value.selected].target.localOrderId)
        f.activity.findViewById<Button>(R.id.readSnapshot).performClick()
        f.base.await { !f.model.state.value.busy }
        assertFalse(f.model.state.value.canGenerate); assertFalse(f.model.state.value.canCopy)
        assertTrue(f.model.state.value.message, f.model.state.value.message == "救援凭据不适用于本单")
        assertEquals(1, f.packages.size)
    }

    private fun assertNoSupervisorSecret(files: Map<String, ByteArray>, card: File, setup: SupervisorPagesCardSetup, material: File) {
        val content = files.values.joinToString { it.toString(Charsets.ISO_8859_1) }
        listOf(JSONObject(card.readText()).getString("credential"), setup.token,
            JSONObject(material.readText()).getString("packagePassword"), "synthetic-temporary-pages-bearer",
            "synthetic-temporary-pages-refresh", java.util.Base64.getEncoder().encodeToString(setup.authority.key.private.encoded))
            .forEach { assertFalse(content.contains(it)) }
        val order = JSONObject(files.getValue("order.json").toString(Charsets.UTF_8))
        assertEquals(setup.actor.getString("userId"), order.getString("operatorId"))
        assertNotEquals(setup.authority.rescuer, order.getString("operatorId"))
    }
}
