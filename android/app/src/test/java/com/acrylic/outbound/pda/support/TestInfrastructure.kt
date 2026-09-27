package com.acrylic.outbound.pda.support

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.acrylic.outbound.pda.data.PDA_DATABASE_VERSION
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.sync.SyncResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Validation infrastructure (R01: Robolectric + MockWebServer baseline). Test-source only; it changes no
 * production behaviour. It exists so the next Android sub-item does not have to rediscover two things the hard way:
 *
 * 1. **A JSON `null` cursor is not the string `"null"`.** `JSONObject.optString("nextCursor")` returns the literal
 *    `"null"` for a JSON null, which silently makes a paging loop believe there is another page. Use
 *    [page] to build pages and [assertLastPage] to assert the "no more pages" outcome.
 * 2. **Migration tests must assert against [PDA_DATABASE_VERSION], never a literal.** Adding one table used to
 *    require editing fourteen test files; [openRawVersionedDatabase] plus [assertDatabaseVersion] keep that to one.
 *
 * The four reusable assertion templates are documented in [Templates]; each one is exercised by
 * `TestInfrastructureTest` so the infrastructure verifies itself independently of any business feature.
 */
object TestInfrastructure {

    /** In-memory Room database with main-thread queries allowed (Robolectric/JVM tier). */
    fun memoryDatabase(context: Context = ApplicationProvider.getApplicationContext()): PdaDatabase =
        Room.inMemoryDatabaseBuilder(context, PdaDatabase::class.java).allowMainThreadQueries().build()

    /** A server-shaped page: `items` plus an explicit cursor (`null` when the page is the last one). */
    fun page(items: List<JSONObject>, nextCursor: String? = null): JSONObject = JSONObject()
        .put("items", JSONArray().apply { items.forEach { put(it) } })
        .put("nextCursor", nextCursor ?: JSONObject.NULL)

    /** The install/paging contract: a last page must be reported as such (no phantom `more = true`). */
    fun assertLastPage(result: SyncResult, installed: Int? = null) {
        assertTrue("最后一页不应再要求继续拉取：${result.message}", !result.more)
        if (installed != null) assertTrue("应报告安装条数或明确无变化：${result.message}", result.message.isNotEmpty())
    }

    /**
     * Opens a raw SQLite database at [version] with [ddl] applied, so a migration can be exercised without
     * materialising the whole historical schema. Callers run `Migration.migrate(database)` and then assert that
     * pre-existing rows survived.
     */
    fun openRawVersionedDatabase(context: Context, name: String, version: Int, ddl: List<String>): SupportSQLiteOpenHelper {
        context.deleteDatabase(name)
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(database: SupportSQLiteDatabase) { ddl.forEach(database::execSQL) }
                override fun onUpgrade(database: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    /** The only sanctioned way to assert the current schema version in a test. */
    fun assertDatabaseVersion(expected: Int, actual: Int) {
        assertEquals("数据库版本应与 PDA_DATABASE_VERSION 一致（不要写死字面量）", expected, actual)
    }

    fun currentSchemaVersion(): Int = PDA_DATABASE_VERSION

    fun count(database: SupportSQLiteDatabase, table: String): Int =
        database.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }

    fun text(database: SupportSQLiteDatabase, sql: String): String =
        database.query(sql).use { it.moveToFirst(); it.getString(0) }

    /**
     * The four reusable assertion templates. This is documentation-as-code: a new sub-item copies these four shapes
     * and replaces the feature-specific parts.
     */
    object Templates {
        const val READ_INSTALL_LOCAL_STATE =
            "① 拉取 → 安装 → 本地一致：以固定载荷调用仓库，逐字段核对本地行等于服务端值"
        const val GATE_NOT_REQUIRED =
            "② installRequired=false 不安装：调用后行数为 0，且按主键取回为 null"
        const val IDEMPOTENT_REPEAT =
            "③ 重复拉取幂等：同一载荷调用两次，行数仍为 1（主键 + REPLACE upsert）"
        const val MIGRATION_KEEPS_DATA =
            "④ 迁移数据保留：用 openRawVersionedDatabase 建旧版本库并写入行，跑迁移后旧行仍在、新表存在"
    }
}
