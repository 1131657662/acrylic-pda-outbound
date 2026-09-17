package com.acrylic.outbound.pda.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "prepared_bale", primaryKeys = ["assignmentId", "barcode"],
    foreignKeys = [ForeignKey(entity = PreparedOrder::class, parentColumns = ["assignmentId"], childColumns = ["assignmentId"])],
    indices = [Index(value = ["assignmentId", "baleId"], unique = true)])
data class PreparedBale(
    val assignmentId: String,
    val barcode: String,
    val baleId: String,
    val deliveryCode: String,
    val model: String,
    val name: String,
    val rawWeight: String,
    val weightGrams: Long,
    val dispatchCycle: Long,
    val available: Boolean
)
