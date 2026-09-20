package com.acrylic.outbound.pda

import android.app.Application
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.preparation.PreparationRepository
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.work.*
import com.acrylic.outbound.pda.device.WorkSound
import com.acrylic.outbound.pda.submission.*
import com.acrylic.outbound.pda.recall.*
import com.acrylic.outbound.pda.continuation.*
import com.acrylic.outbound.pda.renewal.AuthorizationRenewalRepository
import com.acrylic.outbound.pda.sync.SyncRepository
import com.acrylic.outbound.pda.update.PdaUpdateClient

open class PdaApplication : Application(), androidx.work.Configuration.Provider {
    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        com.acrylic.outbound.pda.sync.SyncScheduler.start(this)
    }
    val database by lazy { PdaDatabase.open(this) }
    val session by lazy { PdaSession(this, ConnectionSettings(this), DeviceKeys(), SessionVault(this),
        com.acrylic.outbound.pda.sync.RecoveryProtection(database)) {
            com.acrylic.outbound.pda.sync.SyncScheduler.resume(this)
        } }
    val pairing by lazy { com.acrylic.outbound.pda.pairing.PairingRepository(
        com.acrylic.outbound.pda.pairing.PairingVault(this), session.settings, DeviceKeys()) }
    val repository by lazy { PreparationRepository(this, database, session) }
    val temporary by lazy { com.acrylic.outbound.pda.temporary.TemporaryRepository(database,
        com.acrylic.outbound.pda.temporary.TemporarySession(session), java.io.File(cacheDir, "temporary-preparations")) }
    val workSound by lazy { WorkSound(this) }
    val work by lazy { WorkEngine(WorkStore(database, SessionWorkAccess(this, session))) { workSound.play(it) } }
    val submissions by lazy { SubmissionRepository(database, session) }
    val freezer by lazy { SubmissionFreeze(WorkStore(database, SessionWorkAccess(this, session)), session::now) }
    val continuations by lazy { ContinuationRepository(WorkStore(database, SessionWorkAccess(this, session)), session, work::resumedContinuation) }
    val recalls by lazy { RecallRepository(database, session, work::pauseForRecall) }
    val recoveryFreezer by lazy { RecoveryFreeze(WorkStore(database, SessionWorkAccess(this, session)), session::now) }
    val renewals by lazy { AuthorizationRenewalRepository(database, session) }
    val sync by lazy { SyncRepository(database, session) }
    val barcodeSync by lazy { com.acrylic.outbound.pda.sync.BarcodeRepository(database, session) }
    val auditSync by lazy { com.acrylic.outbound.pda.sync.AuditRepository(database, session) }
    val archives by lazy { com.acrylic.outbound.pda.retention.SubmissionArchiveRepository(database, session) }
    val auditArchives by lazy { com.acrylic.outbound.pda.retention.AuditArchiveRepository(database, session) }
    val backgroundSync by lazy { com.acrylic.outbound.pda.sync.BackgroundSync(sync, barcodeSync, auditSync, auditArchives, session::requireSession) }
    val retention by lazy { com.acrylic.outbound.pda.retention.RetentionCleaner(database,
        { session.requireSession().getString("serverRecoveryEpoch") }, session::now) }
    val updates by lazy { PdaUpdateClient(this, session.settings, database) { work.state.value } }
}
