package com.acrylic.outbound.pda.independent

import com.acrylic.outbound.pda.PdaApplication
import com.acrylic.outbound.pda.security.TrustedTime
import com.acrylic.outbound.pda.security.DeviceKeys
import com.acrylic.outbound.pda.rescue.RescueSigner
import java.util.Base64
import com.acrylic.outbound.pda.sync.RecoveryProtection

/** One process-owned queue without changing inherited application initialization. */
internal object IndependentWorkRuntime {
    private var owner: PdaApplication? = null
    private var engine: IndependentScanEngine? = null
    @Synchronized fun shared(app: PdaApplication): IndependentScanEngine {
        if (owner === app) return checkNotNull(engine)
        check(owner == null) { "独立作业运行树已变化，请保留原实例" }
        val credentials = IndependentCredentials.shared(app, app.session, RecoveryProtection(app.database))
        val environment = IndependentScanEnvironment({ TrustedTime.sample(app) }, {
            val session = app.session.requireSession(); app.session.publicKey()
            check(session.getString("deviceId") == app.session.settings.device)
            IndependentTaskActor(session.getString("userId"), session.getString("deviceId"), session.getString("warehouseId"))
        }, app.workSound::play, {
            val keys = DeviceKeys()
            RescueSigner(Base64.getDecoder().decode(keys.publicKey(false)), keys::sign)
        })
        return IndependentScanEngine(app.database, environment, credentials::unlock).also { owner = app; engine = it }
    }
}
