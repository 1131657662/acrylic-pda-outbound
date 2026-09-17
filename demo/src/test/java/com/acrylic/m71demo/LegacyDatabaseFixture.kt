package com.acrylic.m71demo

import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.io.File

/** Builds an isolated historical database from the checked-in Room schema, copying test facts. */
object LegacyDatabaseFixture {
    fun rebuild(rig: TestRig, version: Int) {
        val path = rig.context.getDatabasePath(rig.db.openHelper.databaseName)
        rig.db.close()
        val source = File(path.path + ".source")
        check(path.renameTo(source))
        val schema = JSONObject(File("schemas/com.acrylic.m71demo.data.DemoDatabase/$version.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("ATTACH DATABASE ? AS source", arrayOf(source.path))
            copyEntities(db, schema)
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
            db.execSQL("CREATE UNIQUE INDEX active_order_barcode ON local_scan(orderId,barcode) WHERE status='ACTIVE'")
            db.version = version
            db.execSQL("DETACH DATABASE source")
        }
        check(source.delete())
    }

    private fun copyEntities(db: SQLiteDatabase, schema: JSONObject) {
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length()).joinToString(",") { "`" + fields.getJSONObject(it).getString("columnName") + "`" }
            db.execSQL("INSERT INTO `$table`($columns) SELECT $columns FROM source.`$table`")
            val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
            for (j in 0 until indices.length())
                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
        }
    }
}
