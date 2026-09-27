package com.acrylic.outbound.pda.evidence
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION

import android.content.ContentValues
import android.database.DatabaseUtils
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.temporary.TemporaryFreeze
import com.acrylic.outbound.pda.temporary.TemporaryWorkFixture
import com.acrylic.outbound.pda.work.WorkFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalE2aStopMigrationTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun nonemptyRoomEighteenUpgradePreservesOriginalEvidence() = runBlocking<Unit>(Dispatchers.IO) {
        WorkFixture(folder.root).use { formal ->
            formal.scan()
            TemporaryWorkFixture(folder.newFolder(), formal.db).use { temporary ->
                temporary.start(); temporary.scan()
                TemporaryFreeze(temporary.store).freeze(temporary.id, "短装待核对")
                val saved = checkNotNull(formal.db.temporarySubmissions().forOrder(temporary.id))
                val before = rows(formal.db)
                rebuild(formal, before)
                formal.db = PdaDatabase.open(formal.context)
                assertEquals(PDA_DATABASE_VERSION, formal.db.openHelper.readableDatabase.version)
                assertEquals(before, rows(formal.db))
                assertEquals(saved, formal.db.temporarySubmissions().forOrder(temporary.id))
                assertNull(formal.db.localE2aStops().get(temporary.id))
                formal.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {
                    assertFalse(it.moveToFirst())
                }
            }
        }
    }

    private fun schema() = JSONObject(File(checkNotNull(System.getProperty("pda.schemas")), "18.json")
        .readText()).getJSONObject("database")

    private fun rows(db: PdaDatabase): Map<String, List<Map<String, Any?>>> {
        val entities = schema().getJSONArray("entities")
        return (0 until entities.length()).associate { index ->
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length()).joinToString(",") {
                "`${fields.getJSONObject(it).getString("columnName")}`"
            }
            table to db.openHelper.readableDatabase.query("SELECT $columns FROM `$table` ORDER BY rowid").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val values = ContentValues()
                        DatabaseUtils.cursorRowToContentValues(cursor, values)
                        add(values.valueSet().associate { (key, value) ->
                            key to if (value is ByteArray) value.toList() else value
                        })
                    }
                }
            }
        }
    }

    private fun rebuild(fixture: WorkFixture, rows: Map<String, List<Map<String, Any?>>>) {
        fixture.db.close()
        fixture.context.deleteDatabase("outbound-pda.db")
        fixture.context.openOrCreateDatabase("outbound-pda.db", 0, null).use { db ->
            val old = schema()
            val entities = old.getJSONArray("entities")
            for (index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices")
                if (indices != null) for (ordinal in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(ordinal).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                rows.getValue(table).forEach { values ->
                    val restored = ContentValues()
                    values.forEach { (key, value) ->
                        when (value) {
                            null -> restored.putNull(key)
                            is String -> restored.put(key, value)
                            is Int -> restored.put(key, value)
                            is Long -> restored.put(key, value)
                            is Boolean -> restored.put(key, value)
                            is List<*> -> restored.put(key, ByteArray(value.size) { (value[it] as Byte) })
                            else -> error("不支持的迁移值类型: $key")
                        }
                    }
                    db.insertOrThrow(table, null, restored)
                }
            }
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table VALUES(42,?)", arrayOf(old.getString("identityHash")))
            db.version = 18
        }
    }
}
