package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** Synthetic scale input only; it does not claim original inventory or hardware throughput. */
@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RescueScaleTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun largeMixedRoomOrderExportsEveryOriginalRowWithoutChangingBusinessFacts() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { f ->
            seed(f, 12_639)
            val grant = RescueReadGrant(RescueReadIdentity(f.session.getString("deviceId"),
                f.session.getString("userId"), f.session.getString("warehouseId"))) {}
            val before = f.db.scans().total(f.assignment)
            val started = System.nanoTime()
            val snapshot = RescueRoomSnapshot(f.db).read(RescueTarget("FORMAL", f.assignment), grant)
            val facts = RescueFacts.summary(snapshot)
            assertEquals("12639", facts.getString("captures")); assertEquals("12639", facts.getString("scans"))
            assertEquals("1263", facts.getString("void")); assertEquals("11376", facts.getString("active"))
            assertEquals("0", facts.getString("pending"))
            assertEquals((11_376L * 300_500L).toString(), facts.getString("weightGrams"))
            assertEquals("00300012639", snapshot.scans.last().getString("barcode"))
            val bytes = RescueBundle.build(snapshot, signer())
            val output = File("build/rescue-scale-vectors").apply { mkdirs() }
            output.resolve("large.zip").writeBytes(bytes)
            output.resolve("metrics.json").writeText(JSONObject().put("synthetic", true).put("rows", 12_639)
                .put("bytes", bytes.size).put("snapshotAndBundleMs", (System.nanoTime() - started) / 1_000_000).toString())
            assertEquals(before, f.db.scans().total(f.assignment)); assertNull(f.db.submissions().forOrder(f.assignment))
            assertEquals("SCANNING", f.db.work().state(f.assignment))
        }
    }
    private fun seed(f: WorkFixture, count: Int) {
        val first = checkNotNull(f.db.captures().get(f.scan().id)); val scan = checkNotNull(f.db.scans().get(first.id))
        f.db.runInTransaction {
            for (number in 2..count) {
                val barcode = "003" + number.toString().padStart(8, '0')
                val context = first.context.copy(orderScanSeq = number.toLong(), deviceCaptureSeq = number.toLong(), rawInput = barcode, barcode = barcode)
                val capture = first.copy(id = "synthetic-scale-$number", context = context)
                f.db.captures().insert(capture)
                f.db.scans().insert(scan.copy(captureId = capture.id, orderScanSeq = number.toLong(), barcode = barcode,
                    baleId = "synthetic-bale-$number", localSeq = number.toLong() + 3, status = if (number % 10 == 0) "VOID" else "ACTIVE"))
            }
            val active = count - count / 10
            f.db.openHelper.writableDatabase.execSQL("UPDATE work_order SET actualCount=?,weightGrams=? WHERE assignmentId=?",
                arrayOf(active, active * scan.weightGrams, f.assignment))
            f.db.openHelper.writableDatabase.execSQL("UPDATE work_line SET plannedCount=?,actualCount=?,weightGrams=? WHERE assignmentId=? AND deliveryCode=?",
                arrayOf(count, active, active * scan.weightGrams, f.assignment, scan.deliveryCode))
        }
    }
    private fun signer(): RescueSigner {
        val key = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return RescueSigner(key.public.encoded) { text -> Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private); update(text.toByteArray(Charsets.UTF_8)); Base64.getEncoder().encodeToString(sign())
        } }
    }
}
