package com.acrylic.outbound.pda.data

import androidx.room.*

@Dao
interface PreparationDao {
    @Query("SELECT * FROM prepared_order ORDER BY activatedAt DESC") fun orders(): List<PreparedOrder>
    @Query("SELECT * FROM prepared_order WHERE assignmentId=:id") fun order(id: String): PreparedOrder?
    @Query("SELECT COUNT(*) FROM prepared_bale WHERE assignmentId=:id") fun count(id: String): Int
    @Query("SELECT * FROM prepared_bale WHERE assignmentId=:id AND barcode=:barcode") fun bale(id: String, barcode: String): PreparedBale?
    @Insert fun insertOrder(order: PreparedOrder)
    @Insert fun insertBales(bales: List<PreparedBale>)
    @Query("UPDATE prepared_order SET ackAt=COALESCE(ackAt,:ack) WHERE assignmentId=:id") fun acknowledged(id: String, ack: String)
    @Query("SELECT * FROM claim_intent WHERE orderId=:order") fun intent(order: String): ClaimIntent?
    @Query("SELECT * FROM claim_intent WHERE userId=:user AND deviceId=:device") fun pending(user: String, device: String): List<ClaimIntent>
    @Insert fun insertIntent(intent: ClaimIntent)
    @Query("UPDATE claim_intent SET status=:status,lastError=:message WHERE orderId=:order")
    fun markIntent(order: String, status: String, message: String?)
    @Query("DELETE FROM claim_intent WHERE orderId=:order AND userId=:user AND deviceId=:device")
    fun resolveIntent(order: String, user: String, device: String)
}
