package com.acrylic.outbound.pda.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.acrylic.outbound.pda.work.*
import com.acrylic.outbound.pda.submission.*

@Database(entities = [PreparedOrder::class, PreparedBale::class, ClaimIntent::class, WorkOrder::class,
    WorkLine::class, WorkDevice::class, WorkCapture::class, WorkScan::class, WorkGuard::class, WorkEvent::class, LocalSubmission::class], version = 3, exportSchema = true)
abstract class PdaDatabase : RoomDatabase() {
    abstract fun preparations(): PreparationDao
    abstract fun work(): WorkDao
    abstract fun captures(): CaptureDao
    abstract fun scans(): ScanDao
    abstract fun submissions(): SubmissionDao

    companion object {
        fun open(context: Context): PdaDatabase = Room.databaseBuilder(context, PdaDatabase::class.java, "outbound-pda.db")
            .openHelperFactory { FullSyncOpenHelper(it) }
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(WorkMigration, SubmissionMigration)
            .addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.query("PRAGMA synchronous").use { check(it.moveToFirst() && it.getInt(0) == 2) { "本地数据库耐久配置不正确" } }
                }
            }).build()
    }
}
