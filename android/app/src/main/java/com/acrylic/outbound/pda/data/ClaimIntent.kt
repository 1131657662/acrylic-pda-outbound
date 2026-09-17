package com.acrylic.outbound.pda.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "claim_intent")
data class ClaimIntent(@PrimaryKey val orderId: String, val userId: String, val deviceId: String,
    val key: String, val body: String, val recoveryEpoch: String)
