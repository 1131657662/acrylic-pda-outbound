package com.acrylic.outbound.pda

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.evidence.*
import com.acrylic.outbound.pda.security.TimeSample
import com.acrylic.outbound.pda.temporary.*
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** One named database per case; no deletion/reset of the application's database or old test evidence. */
internal class R06A11DeviceWork private constructor(val root: File, val facts: JSONObject) : AutoCloseable {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    val id = facts.getString("orderId")
    val db = Room.databaseBuilder(context, PdaDatabase::class.java, facts.getString("database")).build()
    val signer = R06A11StopSigner(facts.optString("stopPublicKey").takeIf { it.isNotEmpty() })
    val connection = R06A11FixtureConnection(JSONObject(File(root, "contract.json").readText()), signer,
        facts.optJSONObject("session"))
    fun store() = TemporaryWorkStore(db, connection) { TimeSample(1000, connection.now().toEpochMilli(), 1) }
    fun repository() = LocalDraftRecoveryRepository { store() }
    fun requests() = db.localDraftTransfers().page(id, -1)
    fun stop() = checkNotNull(db.localDraftStops().get(id))

    fun seedStoppedWork() {
        TemporaryRepository(db, connection, File(root, "downloads")).prepare {}
        val store = store()
        val drafts = TemporaryDraftRepository(store)
        val created = drafts.create(id)
        val lines = store.source(id).products.keys.map { it to "2" }
        val planned = drafts.save(id, TemporaryPlanInput(created.revision, "DEVICE-FIXTURE", "", "synthetic", lines))
        drafts.start(id, planned.revision)
        val processor = TemporaryProcessor(store) {}
        val barcode = store.source(id).bales.keys.first()
        processor.reception.receive(TemporaryInput(id, barcode, "BROADCAST", store.sample()))
        processor.drain(id)
        LocalDraftStopRepository(store).stop(id)
        check(signer.calls == 1)
    }

    fun persist() {
        facts.put("session", connection.session).put("stopPublicKey", signer.publicKey)
        facts.put("sourceFixtureSha256", E2aWire.sha(File(root, "contract.json").readBytes()))
        File(root, "case.json").writeText(facts.toString())
    }

    override fun close() = db.close()

    companion object {
        fun create(label: String): R06A11DeviceWork {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val id = UUID.randomUUID().toString()
            val root = File(context.getExternalFilesDir(null), "r06a11-cases/$label-$id")
            check(root.mkdirs())
            val contract = File(context.getExternalFilesDir(null), "r06a11-contract.json")
            check(contract.isFile) { "Supply the isolated server fixture at runtime; never bundle it in APK" }
            contract.copyTo(File(root, "contract.json"))
            val facts = JSONObject().put("orderId", id).put("database", "r06a11-$id.db")
            return R06A11DeviceWork(root, facts)
        }

        fun reopen(path: String): R06A11DeviceWork {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val allowed = File(context.getExternalFilesDir(null), "r06a11-cases").canonicalFile
            val root = File(path).canonicalFile
            check(root.parentFile == allowed) { "Recovery path must be an isolated test case" }
            val facts = JSONObject(File(root, "case.json").readText())
            check(E2aWire.sha(File(root, "contract.json").readBytes()) == facts.getString("sourceFixtureSha256"))
            return R06A11DeviceWork(root, facts)
        }
    }
}
