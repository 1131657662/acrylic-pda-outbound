package com.acrylic.outbound.pda.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.acrylic.outbound.pda.work.*
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.recall.*
import com.acrylic.outbound.pda.continuation.*

@Database(entities = [PreparedOrder::class, PreparedBale::class, ClaimIntent::class, WorkOrder::class,
    WorkLine::class, WorkDevice::class, WorkCapture::class, WorkScan::class, WorkGuard::class, WorkEvent::class, LocalSubmission::class, LocalRecall::class, LocalContinuation::class, LocalDisposition::class], version = 8, exportSchema = true)
abstract class PdaDatabase : RoomDatabase() {
    abstract fun preparations(): PreparationDao
    abstract fun work(): WorkDao
    abstract fun captures(): CaptureDao
    abstract fun scans(): ScanDao
    abstract fun submissions(): SubmissionDao
    abstract fun continuations(): ContinuationDao
    abstract fun dispositions(): DispositionDao
    abstract fun recalls(): RecallDao

    companion object {
        fun open(context: Context): PdaDatabase = Room.databaseBuilder(context, PdaDatabase::class.java, "outbound-pda.db")
            .openHelperFactory { FullSyncOpenHelper(it) }
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(WorkMigration, SubmissionMigration, RecallMigration, ContinuationMigration, DispositionMigration, ClaimIntentMigration, AuthorizationMigration)
            .addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.query("PRAGMA synchronous").use { check(it.moveToFirst() && it.getInt(0) == 2) { "本地数据库耐久配置不正确" } }
                }
            }).build()
    }
}
