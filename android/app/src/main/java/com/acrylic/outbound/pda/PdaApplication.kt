package com.acrylic.outbound.pda

import android.app.Application
import com.acrylic.outbound.pda.data.PdaDatabase
import com.acrylic.outbound.pda.network.*
import com.acrylic.outbound.pda.preparation.PreparationRepository
import com.acrylic.outbound.pda.security.*
import com.acrylic.outbound.pda.work.*
import com.acrylic.outbound.pda.device.WorkSound
import com.acrylic.outbound.pda.submission.*

class PdaApplication : Application() {
    val database by lazy { PdaDatabase.open(this) }
    val session by lazy { PdaSession(this, ConnectionSettings(this), DeviceKeys(), SessionVault(this)) }
    val repository by lazy { PreparationRepository(this, database, session) }
    val workSound by lazy { WorkSound(this) }
    val work by lazy { WorkEngine(WorkStore(database, SessionWorkAccess(this, session))) { workSound.play(it) } }
    val submissions by lazy { SubmissionRepository(database, session) }
    val freezer by lazy { SubmissionFreeze(WorkStore(database, SessionWorkAccess(this, session)), session::now) }
}
