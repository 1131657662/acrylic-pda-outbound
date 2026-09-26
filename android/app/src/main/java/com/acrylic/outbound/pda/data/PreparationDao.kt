package com.acrylic.outbound.pda.data

import androidx.room.*

@Dao
interface PreparationDao {
    @Query("SELECT * FROM prepared_order ORDER BY activatedAt DESC") fun orders(): List<PreparedOrder>
    @Query("""SELECT * FROM prepared_order WHERE
        (CASE WHEN EXISTS (SELECT 1 FROM local_submission WHERE assignmentId=prepared_order.assignmentId AND currentMarker=1 AND state='SYNCED')
            AND NOT EXISTS (SELECT 1 FROM local_recall WHERE assignmentId=prepared_order.assignmentId AND currentMarker=1)
            THEN 1 ELSE 0 END) = :history
        ORDER BY activatedAt DESC, assignmentId DESC LIMIT :limit OFFSET :offset""")
    fun ordersPage(history: Boolean, limit: Int, offset: Int): List<PreparedOrder>
    @Query("SELECT * FROM prepared_order WHERE assignmentId=:id") fun order(id: String): PreparedOrder?
    @Query("SELECT COUNT(*) FROM prepared_bale WHERE assignmentId=:id") fun count(id: String): Int
    @Query("SELECT * FROM prepared_bale WHERE assignmentId=:id AND barcode=:barcode") fun bale(id: String, barcode: String): PreparedBale?
    @Insert fun insertOrder(order: PreparedOrder)
    @Insert fun insertBales(bales: List<PreparedBale>)
    @Query("UPDATE prepared_order SET ackAt=COALESCE(ackAt,:ack) WHERE assignmentId=:id") fun acknowledged(id: String, ack: String)
    @Query("UPDATE prepared_order SET assignmentJson=:current, authorizationHistoryJson=:history WHERE assignmentId=:id")
    fun replaceAuthorization(id: String, current: String, history: String)
    @Query("SELECT * FROM claim_intent WHERE orderId=:order AND userId=:user AND deviceId=:device")
    fun intent(order: String, user: String, device: String): ClaimIntent?
    @Query("SELECT * FROM claim_intent WHERE userId=:user AND deviceId=:device") fun pending(user: String, device: String): List<ClaimIntent>
    @Insert fun insertIntent(intent: ClaimIntent)
    @Query("UPDATE claim_intent SET status=:status,lastError=:message WHERE orderId=:order AND userId=:user AND deviceId=:device AND `key`=:key")
    fun markIntent(order: String, user: String, device: String, key: String, status: String, message: String?)
    @Insert fun insertHistory(history: ClaimIntentHistory)
    @Query("SELECT * FROM claim_intent_history WHERE orderId=:order AND userId=:user AND deviceId=:device ORDER BY archivedAt DESC")
    fun history(order: String, user: String, device: String): List<ClaimIntentHistory>
    @Query("SELECT * FROM claim_intent_history WHERE userId=:user AND deviceId=:device ORDER BY archivedAt DESC, `key` DESC LIMIT :limit OFFSET :offset")
    fun historyPage(user: String, device: String, limit: Int, offset: Int): List<ClaimIntentHistory>
    @Query("DELETE FROM claim_intent WHERE orderId=:order AND userId=:user AND deviceId=:device AND `key`=:key AND status='REJECTED'")
    fun removeRejected(order: String, user: String, device: String, key: String): Int
    @Transaction fun replaceRejected(old: ClaimIntent, next: ClaimIntent, archivedAt: Long) {
        check(old.status == "REJECTED" && old.orderId == next.orderId && old.userId == next.userId && old.deviceId == next.deviceId)
        check(intent(old.orderId, old.userId, old.deviceId) == old)
        insertHistory(ClaimIntentHistory(old.key, old.orderId, old.userId, old.deviceId, old.body,
            old.recoveryEpoch, old.status, old.lastError, archivedAt))
        check(removeRejected(old.orderId, old.userId, old.deviceId, old.key) == 1)
        insertIntent(next)
    }
    @Query("DELETE FROM claim_intent WHERE orderId=:order AND userId=:user AND deviceId=:device")
    fun resolveIntent(order: String, user: String, device: String)
}
