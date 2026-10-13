package com.acrylic.outbound.pda.rescue

import com.acrylic.outbound.pda.data.PdaDatabase
import java.util.concurrent.Callable

/** No WorkStore.open, TemporaryWorkStore.load, session/network access, or business writes. */
class RescueRoomSnapshot(private val db: PdaDatabase) {
    fun read(target: RescueTarget, grant: RescueReadGrant): RescueSnapshot {
        grant.recheck()
        return db.runInTransaction(Callable {
            val rows = RescueRows(db.openHelper.readableDatabase)
            val snapshot = when (target.source) {
                "FORMAL" -> RescueFormalSnapshot(db, rows).read(target.localOrderId, grant)
                "TEMP" -> RescueTemporarySnapshot(db, rows).read(target.localOrderId, grant)
                "INDEPENDENT" -> RescueIndependentSnapshot(db, rows).read(target.localOrderId, grant)
                else -> error("不支持的本机救援来源")
            }
            RescueFacts.summary(snapshot)
            grant.check(snapshot.order)
            snapshot
        })
    }
}
