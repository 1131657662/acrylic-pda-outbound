package com.acrylic.outbound.pda.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "prepared_order", indices = [Index(value = ["orderId"], unique = true)])
data class PreparedOrder(
    @PrimaryKey val assignmentId: String,
    val orderId: String,
    val operatorId: String,
    val deviceId: String,
    val bundleId: String,
    val manifestSha256: String,
    val assignmentJson: String,
    val manifestJson: String,
    val planJson: String,
    val signingKeyJson: String,
    val signedManifest: String,
    val activatedAt: String,
    val rowCount: Int,
    val ackAt: String? = null
)
