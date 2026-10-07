package com.acrylic.outbound.pda.temporary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PdaDatabase
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.util.concurrent.TimeUnit

/** Actual HTTP mappings, durable Room restart and final-write failure; identities remain synthetic. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class SourceBindingRelinkHttpTest {
    @Test fun actualRelinkReplacesInstalledTargetOnlyAfterAtomicDurableWrite() {
        val path = System.getenv("D04_RELINK_HANDSHAKE")
        assumeTrue("Requires isolated standalone relink harness", !path.isNullOrBlank())
        val input = File(path!!); val attempt = input.parentFile!!.parentFile!!
        try { consume(JSONObject(input.readText()), attempt) }
        catch (error: Throwable) { File(attempt, "android-failed.marker").writeText(error.toString()); throw error }
    }

    private fun consume(auth: JSONObject, attempt: File) {
        val file = File(attempt, "private/android-relink.db")
        var original = JSONObject()
        withDatabase(file) { db ->
            val repository = SourceBindingRepository(db, { auth }) { route, epoch ->
                read(auth, route, epoch).also { original = JSONObject(it.toString()) }
            }
            repository.installSource(auth.getString("clientOrderId"))
            assertEquals(auth.getString("canonicalOrderId"), db.sourceBindings().get(auth.getString("scopeId"))!!.canonicalOrderId)
            File(attempt, "first-mapping.json").writeText(original.toString())
        }
        File(attempt, "android-first.marker").writeText("persisted")
        val next = JSONObject(await(File(attempt, "relink-ready.json")).readText())
        failedWriteKeepsOriginal(file, auth)
        withDatabase(file) { db ->
            val repository = SourceBindingRepository(db, { auth }) { route, epoch -> read(auth, route, epoch) }
            repository.installSource(auth.getString("clientOrderId"))
            val row = db.sourceBindings().get(auth.getString("scopeId"))!!
            assertEquals(next.getString("canonicalOrderId"), row.canonicalOrderId)
            assertEquals("3", row.mappingVersion)
            assertThrows(Exception::class.java) { SourceBindingRepository(db, { auth }) { _, _ -> original }.installBindings() }
            repository.installSource(auth.getString("clientOrderId")); assertEquals(row, db.sourceBindings().get(row.scopeId))
        }
        withDatabase(file) { db -> assertEquals(next.getString("canonicalOrderId"), db.sourceBindings().get(auth.getString("scopeId"))!!.canonicalOrderId) }
        File(attempt, "android-completed.marker").writeText("relinked; stale rejected; replay/restart and SQL rollback verified")
    }

    private fun failedWriteKeepsOriginal(file: File, auth: JSONObject) = withDatabase(file) { db ->
        val before = db.sourceBindings().all()
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER relink_failure BEFORE INSERT ON local_source_binding BEGIN SELECT RAISE(ABORT, 'relink write failure'); END")
        try {
            assertThrows(Exception::class.java) {
                SourceBindingRepository(db, { auth }) { route, epoch -> read(auth, route, epoch) }
                    .installSource(auth.getString("clientOrderId"))
            }
            assertEquals(before, db.sourceBindings().all())
        } finally { sql.execSQL("DROP TRIGGER relink_failure") }
    }

    private fun withDatabase(file: File, verify: (PdaDatabase) -> Unit) {
        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), PdaDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries().build()
        try { verify(db) } finally { db.close() }
    }

    private fun read(auth: JSONObject, route: String, epoch: String): JSONObject {
        val request = Request.Builder().url(auth.getString("baseUrl") + route)
            .header("Authorization", "Bearer " + auth.getString("token")).header("X-Server-Recovery-Epoch", epoch).build()
        return OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build().newCall(request).execute().use {
            val body = it.body!!.string(); check(it.isSuccessful) { "HTTP ${it.code}: $body" }
            JSONObject(body).getJSONObject("data")
        }
    }

    private fun await(file: File): File {
        val end = System.nanoTime() + TimeUnit.MINUTES.toNanos(3)
        while (!file.exists()) { check(System.nanoTime() < end) { "Missing ${file.name}" }; Thread.sleep(200) }
        return file
    }
}
