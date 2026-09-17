package com.acrylic.m71demo.workflow

data class IncomingBarcode(
    val id: String,
    val orderId: String,
    val raw: String,
    val source: String,
    val receivedAt: Long,
    val receivedElapsedNs: Long,
    val bootId: String,
)
