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

class PdaApplication : Application() {
    val database by lazy { PdaDatabase.open(this) }
    val session by lazy { PdaSession(this, ConnectionSettings(this), DeviceKeys(), SessionVault(this),
        com.acrylic.outbound.pda.sync.RecoveryProtection(database)) }
    val pairing by lazy { com.acrylic.outbound.pda.pairing.PairingRepository(
        com.acrylic.outbound.pda.pairing.PairingVault(this), session.settings, DeviceKeys()) }
    val repository by lazy { PreparationRepository(this, database, session) }
    val workSound by lazy { WorkSound(this) }
    val work by lazy { WorkEngine(WorkStore(database, SessionWorkAccess(this, session))) { workSound.play(it) } }
    val submissions by lazy { SubmissionRepository(database, session) }
    val freezer by lazy { SubmissionFreeze(WorkStore(database, SessionWorkAccess(this, session)), session::now) }
    val continuations by lazy { ContinuationRepository(WorkStore(database, SessionWorkAccess(this, session)), session, work::resumedContinuation) }
    val recalls by lazy { RecallRepository(database, session, work::pauseForRecall) }
    val recoveryFreezer by lazy { RecoveryFreeze(WorkStore(database, SessionWorkAccess(this, session)), session::now) }
    val renewals by lazy { AuthorizationRenewalRepository(database, session) }
    val sync by lazy { SyncRepository(database, session) }
    val updates by lazy { PdaUpdateClient(this, session.settings, database) { work.state.value } }
}
