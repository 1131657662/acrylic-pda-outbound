package com.acrylic.outbound.pda.temporary

import com.acrylic.outbound.pda.security.JwtVerifier
import com.acrylic.outbound.pda.submission.SubmissionJson
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class) @Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class TemporarySubmissionStorageTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun multiMegabyteBodyReopensAndTransportReconstructsOriginalBytes() = runBlocking<Unit>(Dispatchers.IO) {
        TemporaryWorkFixture(folder.root).use { f ->
            f.start(); f.scan(); val frozen = TemporaryFreeze(f.store).freeze(f.id, "短装")
            // Storage/transport boundary: padding is not submitted as valid business evidence.
            val body = JSONObject(frozen.payload).put("padding", "中".repeat(800000)); val payload = SubmissionJson.canonical(body)
            val local = frozen.copy(payload = payload, sha256 = JwtVerifier.sha(payload.toByteArray(Charsets.UTF_8)))
            f.db.openHelper.writableDatabase.execSQL("UPDATE temporary_submission SET payload=?,sha256=? WHERE id=?", arrayOf(payload, local.sha256, local.id))
            f.reopen(); assertEquals(local, f.db.temporarySubmissions().get(local.id))
            val bytes = ByteArrayOutputStream(); var manifest: JSONObject? = null; var chunks = 0
            val connection = object : TemporaryConnection by f.fixture {
                override fun original(request: TemporaryConnection.Original): ByteArray {
                    val data = call(TemporaryConnection.Call(request.path, request.expected,
                        request.body?.let { JSONObject(it.toString(Charsets.UTF_8)) }, epoch = request.epoch))
                    return JSONObject().put("code", "OK").put("data", data).put("serviceMode", "NORMAL")
                        .put("serverRecoveryEpoch", request.epoch).toString().toByteArray()
                }
                override fun call(request: TemporaryConnection.Call): JSONObject {
                    when {
                        request.path.endsWith("/upload") -> manifest = request.body
                        request.path.contains("/chunks/") -> { assertEquals(chunks.toString(), request.path.substringAfterLast('/')); chunks++; bytes.write(checkNotNull(request.raw)) }
                        request.path.endsWith("/finalize") -> assertArrayEquals(payload.toByteArray(Charsets.UTF_8), bytes.toByteArray())
                        else -> fail("unexpected path")
                    }
                    return JSONObject().put("status", "RECEIVING")
                }
            }
            val expected = checkNotNull(f.fixture.auth)
            TemporarySubmissionTransport(connection, expected, TemporarySubmissionResponses(f.db, connection, expected, local)).send(local)
            assertEquals(local.sha256, manifest!!.getString("payloadSha256")); assertEquals(chunks.toString(), manifest!!.getString("chunkCount"))
            assertEquals(frozen.clientOrderId, manifest!!.getJSONObject("source").getString("clientOrderId"))
            assertTrue(manifest!!.isNull("assignmentId")); assertTrue(chunks > 4)
        }
    }
}
