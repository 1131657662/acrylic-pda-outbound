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
import com.acrylic.outbound.pda.retention.*
import com.acrylic.outbound.pda.temporary.*
import com.acrylic.outbound.pda.evidence.LocalE2aStop
import com.acrylic.outbound.pda.evidence.LocalE2aStopDao
import com.acrylic.outbound.pda.evidence.LocalE2aStopMigration
import com.acrylic.outbound.pda.evidence.LocalE2aObject
import com.acrylic.outbound.pda.evidence.LocalE2aObjectDao
import com.acrylic.outbound.pda.evidence.LocalE2aTransfer
import com.acrylic.outbound.pda.evidence.LocalE2aRequest
import com.acrylic.outbound.pda.evidence.LocalE2aTransferDao
import com.acrylic.outbound.pda.evidence.LocalE2aTransferMigration
import com.acrylic.outbound.pda.evidence.LocalE2aReceipt
import com.acrylic.outbound.pda.evidence.LocalE2aQueue
import com.acrylic.outbound.pda.evidence.LocalE2aReceiptDao
import com.acrylic.outbound.pda.evidence.LocalE2aReceiptMigration
import com.acrylic.outbound.pda.evidence.LocalDraftStop
import com.acrylic.outbound.pda.evidence.LocalDraftStopItem
import com.acrylic.outbound.pda.evidence.LocalDraftStopDao
import com.acrylic.outbound.pda.evidence.LocalDraftStopMigration
import com.acrylic.outbound.pda.evidence.LocalDraftStopSignature
import com.acrylic.outbound.pda.evidence.LocalDraftStopSignatureDao
import com.acrylic.outbound.pda.evidence.LocalDraftStopSignatureMigration
import com.acrylic.outbound.pda.evidence.LocalDraftTransfer
import com.acrylic.outbound.pda.evidence.LocalDraftRequest
import com.acrylic.outbound.pda.evidence.LocalDraftReceipt
import com.acrylic.outbound.pda.evidence.LocalDraftTransferDao
import com.acrylic.outbound.pda.evidence.LocalDraftTransferMigration
import com.acrylic.outbound.pda.evidence.LocalDraftQueue
import com.acrylic.outbound.pda.evidence.LocalDraftQueueDao
import com.acrylic.outbound.pda.evidence.LocalDraftQueueMigration

/** The single source of truth for the Room schema version; migration tests must assert against this, not a literal. */
const val PDA_DATABASE_VERSION = 30

@Database(entities = [PreparedOrder::class, PreparedBale::class, ClaimIntent::class, WorkOrder::class,
    WorkLine::class, WorkDevice::class, WorkCapture::class, WorkScan::class, WorkGuard::class, WorkEvent::class, LocalSubmission::class, LocalRecall::class, LocalContinuation::class, LocalDisposition::class,
    LocalSyncState::class, LocalCloudOrder::class, LocalRecoveryControl::class, LocalCloudBarcode::class, AuditCheckpoint::class,
    RetainedBaseline::class, SubmissionArchive::class, AuditArchive::class, TemporaryRequest::class,
    TemporaryPreparation::class, TemporaryPartChunk::class, TemporaryDraft::class, TemporaryLine::class,
    TemporaryCapture::class, TemporaryScan::class, TemporaryGuard::class, TemporaryEvent::class, TemporarySubmission::class,
    ClaimIntentHistory::class, LocalE2aStop::class, LocalE2aObject::class,
    LocalE2aTransfer::class, LocalE2aRequest::class, LocalE2aReceipt::class, LocalE2aQueue::class,
    TemporarySuccessResponse::class, TemporarySuccessEvidence::class, TemporaryArchiveRequest::class,
    TemporaryArchiveResponse::class, TemporaryArchiveEvidence::class,
    LocalDraftStop::class, LocalDraftStopItem::class, LocalDraftStopSignature::class,
    LocalDraftTransfer::class, LocalDraftRequest::class, LocalDraftReceipt::class, LocalDraftQueue::class,
    LocalSourceBinding::class, LocalHandoverStage::class, LocalHandoverContinuation::class], version = PDA_DATABASE_VERSION, exportSchema = true)
abstract class PdaDatabase : RoomDatabase() {
    abstract fun preparations(): PreparationDao
    abstract fun work(): WorkDao
    abstract fun captures(): CaptureDao
    abstract fun scans(): ScanDao
    abstract fun submissions(): SubmissionDao
    abstract fun continuations(): ContinuationDao
    abstract fun dispositions(): DispositionDao
    abstract fun recalls(): RecallDao
    abstract fun sync(): SyncDao
    abstract fun barcodes(): BarcodeDao
    abstract fun audit(): AuditDao
    abstract fun retention(): RetentionDao
    abstract fun archives(): SubmissionArchiveDao
    abstract fun auditArchives(): AuditArchiveDao
    abstract fun temporary(): TemporaryDao
    abstract fun temporaryDrafts(): TemporaryDraftDao
    abstract fun temporaryScans(): TemporaryScanDao
    abstract fun temporarySubmissions(): TemporarySubmissionDao
    abstract fun temporarySuccess(): TemporarySuccessDao
    abstract fun temporaryArchives(): TemporaryArchiveDao
    abstract fun localE2aStops(): LocalE2aStopDao
    abstract fun localE2aObjects(): LocalE2aObjectDao
    abstract fun localE2aTransfers(): LocalE2aTransferDao
    abstract fun localE2aReceipts(): LocalE2aReceiptDao
    abstract fun localDraftStops(): LocalDraftStopDao
    abstract fun localDraftStopSignatures(): LocalDraftStopSignatureDao
    abstract fun localDraftTransfers(): LocalDraftTransferDao
    abstract fun localDraftQueue(): LocalDraftQueueDao
    abstract fun sourceBindings(): LocalSourceBindingDao
    abstract fun handoverStages(): LocalHandoverStageDao
    abstract fun handoverContinuations(): HandoverContinuationDao

    companion object {
        fun open(context: Context): PdaDatabase = Room.databaseBuilder(context, PdaDatabase::class.java, "outbound-pda.db")
            .openHelperFactory { FullSyncOpenHelper(it) }
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .addMigrations(WorkMigration, SubmissionMigration, RecallMigration, ContinuationMigration, DispositionMigration, ClaimIntentMigration, AuthorizationMigration, SyncMigration, BarcodeMigration, AuditMigration, RetentionMigration, SubmissionArchiveMigration, AuditArchiveMigration, TemporaryMigration, TemporaryWorkMigration, TemporarySubmissionMigration, ClaimIntentHistoryMigration, LocalE2aStopMigration)
            .addMigrations(LocalE2aTransferMigration, LocalE2aReceiptMigration, TemporarySuccessMigration, TemporaryArchiveMigration)
            .addMigrations(LocalDraftStopMigration)
            .addMigrations(LocalDraftStopSignatureMigration)
            .addMigrations(LocalDraftTransferMigration)
            .addMigrations(LocalDraftQueueMigration)
            .addMigrations(LocalSourceBindingMigration)
            .addMigrations(LocalHandoverStageMigration)
            .addMigrations(HandoverContinuationMigration)
            .addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.query("PRAGMA synchronous").use { check(it.moveToFirst() && it.getInt(0) == 2) { "本地数据库耐久配置不正确" } }
                }
            }).build()
    }
}
