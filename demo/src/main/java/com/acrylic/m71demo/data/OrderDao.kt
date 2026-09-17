package com.acrylic.m71demo.data

import androidx.room.*

@Dao
interface OrderDao {
    @Insert fun insert(order: DemoOrder)
    @Insert fun insertLines(lines: List<OrderLine>)
    @Update fun update(order: DemoOrder)
    @Update fun updateLine(line: OrderLine)
    @Query("UPDATE local_order SET nextOrderScanSeq=:next WHERE id=:id AND nextOrderScanSeq=:expected")
    fun advanceCaptureSequence(id: String, expected: Long, next: Long): Int
    @Query("UPDATE local_order SET actualCount=:count,weightGrams=:grams WHERE id=:id")
    fun updateTotals(id: String, count: Int, grams: Long): Int
    @Query("UPDATE local_order_line SET actualCount=actualCount+1,weightGrams=weightGrams+:grams WHERE orderId=:id AND deliveryCode=:code")
    fun addBale(id: String, code: String, grams: Long): Int
    @Query("SELECT * FROM local_order WHERE id=:id") fun get(id: String): DemoOrder?
    @Query("SELECT * FROM local_order ORDER BY createdAt DESC LIMIT 40") fun recent(): List<DemoOrder>
    @Query("SELECT * FROM local_order_line WHERE orderId=:id ORDER BY deliveryCode")
    fun lines(id: String): List<OrderLine>
    @Query("SELECT * FROM local_order_line WHERE orderId=:id AND deliveryCode=:code")
    fun line(id: String, code: String): OrderLine?
    @Insert fun insertSubmission(submission: FrozenSubmission)
    @Query("SELECT * FROM local_submission WHERE orderId=:id") fun submission(id: String): FrozenSubmission?
}
