package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.independent.*
import com.acrylic.outbound.pda.rescue.*
import com.acrylic.outbound.pda.security.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.util.Base64
import java.util.UUID

/** Synthetic authority/JCA only; consumes original product vault, Room, scan and export APIs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = RescueSignedFileFixture.TestApp::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class IndependentCardWorkExportTest {
    @Test fun cardProvisionedNonemptyIndependentTaskExportsAfterLogoutWithoutChangingRoom() {
        IndependentCardUiFixture().use { fixture ->
            fixture.provision(); provisionRescue(fixture)
            fixture.base.server.shutdown(); fixture.choose()
            assertTrue(fixture.model.state.value.message.contains("原校验器核验通过"))
            val selection = checkNotNull(fixture.model.cardSelection())
            runBlocking(Dispatchers.IO) {
                val originalCard = fixture.card.readBytes()
                val credentials = fixture.base.credentials
                val grant = IndependentCredentialCard.read(originalCard.inputStream()).use {
                    credentials.unlock(it.credential(selection))
                }
                val task = createAndScan(fixture, grant)
                val db = fixture.base.app.database
                fixture.base.vault.clear()
                assertThrows(IllegalStateException::class.java) { grant.recheck() }
                val before = digest(db)
                export(fixture, task)
                assertEquals(before, digest(db))
                assertArrayEquals(originalCard, fixture.card.readBytes())
                assertEquals(2, fixture.base.server.requestCount)
            }
        }
    }

    private fun provisionRescue(fixture: IndependentCardUiFixture) {
        val envelope = JSONObject().put("code", "OK").put("data", JSONObject()).put("message", "synthetic")
            .put("requestId", "synthetic-own-rescue").put("serverTime", fixture.base.authority.issued.toString())
            .put("serverRecoveryEpoch", "1").put("serviceMode", "NORMAL")
        fixture.base.server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(envelope.toString()))
        runBlocking(Dispatchers.IO) {
            RescueCredentials.shared(fixture.base.app, fixture.base.app.session)
                .provisionOwn("synthetic-own-rescue-read-only".toCharArray())
        }
        val request = fixture.base.request()
        assertEquals("GET", request.method); assertEquals("/api/v1/pda/devices/me", request.path)
    }

    private fun createAndScan(fixture: IndependentCardUiFixture, grant: IndependentCaptureGrant): IndependentTask {
        val app = fixture.base.app; val actor = grant.proof.identity
        val owner = IndependentTaskActor(actor.operatorId, actor.deviceId, actor.warehouseId)
        val sample = { TrustedTime.sample(app) }
        val catalog = IndependentSkuRepository(app.database, sample)
        val staged = catalog.stage(sku(), "synthetic-sku.txt")
        catalog.activate(staged.imported.id, false, staged.activeImportId)
        val repository = IndependentTaskRepository(app.database, sample) { owner }
        val command = IndependentTaskCommand(UUID.randomUUID().toString(), staged.imported.id,
            "合成车", "服务中断独立采集消费者", mapOf("003" to 2L))
        val task = repository.create(command, grant).task
        val store = IndependentScanStore(app.database, grant, IndependentScanEnvironment(sample, actor = { owner }))
        store.open(task.id); val saving = IndependentScanSaving(store); saving.start(task.id)
        val reception = IndependentScanReception(store)
        val captured = reception.receive(IndependentInput(task.id, "003A0000001", "BROADCAST", sample()))
        reception.validate(captured.capture.id)
        assertEquals("SAVED", saving.save(captured.capture.id))
        assertEquals(1L, app.database.independentTasks().state(task.id)?.actualCount)
        assertEquals(300500L, app.database.independentTasks().state(task.id)?.weightGrams)
        return task
    }

    private fun export(fixture: IndependentCardUiFixture, task: IndependentTask) {
        val app = fixture.base.app
        val readGrant = RescueCredentials.shared(app, app.session)
            .unlock(task.operatorId, "synthetic-own-rescue-read-only".toCharArray())
        val snapshot = RescueRoomSnapshot(app.database).read(RescueTarget("INDEPENDENT", task.id), readGrant)
        assertEquals(1, snapshot.captures.size); assertEquals(1, snapshot.scans.size)
        assertArrayEquals(sku(), snapshot.originals.getValue("independent-sku.bin"))
        assertTrue(snapshot.order.getBoolean("pendingReconciliation"))
        assertFalse(snapshot.order.getBoolean("serverAuthorizationVerified"))
        val public = Base64.getDecoder().decode(DeviceKeys().publicKey(false))
        val signer = RescueSigner(public, DeviceKeys()::sign)
        val material = java.io.ByteArrayOutputStream()
        RescuePackageDraft.create(snapshot.order) { readGrant.check(snapshot.order) }.use { draft ->
            draft.write({ material }, { material.toByteArray().inputStream() })
            draft.verify(material.toByteArray().inputStream())
            val generated = RescueExportFiles(app).generate(snapshot, readGrant to signer, draft.credential())
            assertTrue(generated.summary.contains("待主管对账"))
            val recovered = RescueExportFiles(app).recover(generated.id, readGrant)
            assertEquals(generated.sha256, recovered.sha256)
            val original = RescueEnvelope.decrypt(generated.file.readBytes(), draft.credential())
            assertArchiveHasNoCredentials(original, task, fixture.password())
            persistVector(snapshot, generated, material.toByteArray() to public)
        }
    }

    private fun persistVector(snapshot: RescueSnapshot, generated: RescueGeneratedFile, material: Pair<ByteArray, ByteArray>) {
        val root = File(checkNotNull(System.getenv("PDA_INDEPENDENT_CARD_VECTOR_DIR")))
        assertTrue(root.mkdirs() || root.isDirectory); assertTrue(root.listFiles().orEmpty().isEmpty())
        val order = snapshot.order
        val trust = JSONObject().put(order.getString("deviceId"), JSONObject()
            .put("publicKeyDerBase64", Base64.getEncoder().encodeToString(material.second))
            .put("operatorIds", JSONArray().put(order.getString("operatorId")))
            .put("warehouseIds", JSONArray().put(order.getString("warehouseId"))))
        mapOf("independent.pdarescue" to generated.file.readBytes(), "package-secret.json" to material.first,
            "external-trust.json" to trust.toString().toByteArray(Charsets.UTF_8),
            "order-original.json" to order.toString().toByteArray(Charsets.UTF_8)).forEach { (name, bytes) ->
            Files.write(root.toPath().resolve(name), bytes, CREATE_NEW)
        }
    }

    private fun assertArchiveHasNoCredentials(original: ByteArray, task: IndependentTask, password: CharArray) {
        try {
            java.util.zip.ZipInputStream(original.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val bytes = zip.readBytes().toString(Charsets.ISO_8859_1)
                    for (secret in listOf(task.signedCredential, String(password), "synthetic-bearer-not-for-persistence",
                        "synthetic-refresh-not-for-persistence", "synthetic-own-rescue-read-only")) assertFalse(bytes.contains(secret))
                    entry = zip.nextEntry
                }
            }
        } finally { password.fill('\u0000') }
    }

    private fun digest(db: com.acrylic.outbound.pda.data.PdaDatabase): String {
        val sqlite = db.openHelper.readableDatabase
        val tables = sqlite.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        val facts = tables.joinToString("\n") { table ->
            sqlite.query("SELECT * FROM `$table`").use { c ->
                val rows = buildList { while (c.moveToNext()) add((0 until c.columnCount).joinToString("|") { index ->
                    if (c.getType(index) == android.database.Cursor.FIELD_TYPE_BLOB) Base64.getEncoder().encodeToString(c.getBlob(index))
                    else c.getString(index).orEmpty()
                }) }
                table + rows.sorted().joinToString("\n")
            }
        }
        return RescueBundle.sha(facts.toByteArray(Charsets.UTF_8))
    }

    private fun sku() = ("MODEL".padEnd(20) + "SYNTH".padEnd(14) + "003A0000001".padEnd(13) +
        "300.500".padEnd(10)).toByteArray(Charset.forName("GB18030"))
}
