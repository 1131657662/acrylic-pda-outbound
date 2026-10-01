package com.acrylic.outbound.pda

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION
import com.acrylic.outbound.pda.temporary.LocalSourceBindingMigration
import org.json.JSONObject
import java.io.File

/** Only isolated test databases are converted into the exported predecessor schema. */
internal object DraftMigrationFiles {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val schema get() = instrumentation.context.assets.open("room-27.json").bufferedReader().use {
        JSONObject(it.readText()).getJSONObject("database")
    }

    fun freeze(work: R06A11DeviceWork) {
        work.close()
        val path = database(work.facts)
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            check(db.version == PDA_DATABASE_VERSION)
            db.execSQL("DROP TABLE local_source_binding")
            db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42",
                arrayOf(schema.getString("identityHash")))
            db.version = schema.getInt("version")
            val before = DraftMigrationSnapshot.capture(db)
            DraftMigrationSnapshot.assertSame(before, before)
            File(work.root, "migration-before.json").writeText(before.toString())
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { check(it.moveToFirst() && it.getInt(0) == 0) }
        }
        path.copyTo(File(work.root, "database-before.db"))
    }

    fun migrate(facts: JSONObject) {
        SQLiteDatabase.openDatabase(database(facts).path, null, SQLiteDatabase.OPEN_READONLY).use {
            check(it.version == schema.getInt("version")) { "Not the predecessor database" }
            check(DraftMigrationSnapshot.capture(it).getJSONObject("local_draft_receipt").getInt("count") > 0)
        }
        val room = Room.databaseBuilder(context, PdaDatabase::class.java, facts.getString("database"))
            .addMigrations(LocalSourceBindingMigration).build()
        try {
            check(room.openHelper.writableDatabase.version == PDA_DATABASE_VERSION)
            room.openHelper.readableDatabase.query("SELECT COUNT(*) FROM local_source_binding").use {
                check(it.moveToFirst() && it.getLong(0) == 0L)
            }
        } finally { room.close() }
    }

    fun verify(work: R06A11DeviceWork) {
        work.close()
        val path = database(work.facts)
        SQLiteDatabase.openDatabase(path.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val after = DraftMigrationSnapshot.capture(db)
            val before = JSONObject(File(work.root, "migration-before.json").readText())
            DraftMigrationSnapshot.assertSame(before, after)
            File(work.root, "migration-after.json").writeText(after.toString())
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { check(it.moveToFirst() && it.getInt(0) == 0) }
        }
        path.copyTo(File(work.root, "database-after.db"))
    }

    private fun database(facts: JSONObject): File {
        val name = facts.getString("database")
        check(name.matches(Regex("r06a11-[0-9a-f-]{36}\\.db")))
        return context.getDatabasePath(name)
    }
}
