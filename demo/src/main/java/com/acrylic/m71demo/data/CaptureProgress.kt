package com.acrylic.m71demo.data

/** Partial Room update: processing must never rewrite the original capture context. */
data class CaptureProgress(
    val id: String,
    val state: String,
    val error: String,
    val confirmedAtNs: Long?,
    val failureAcknowledged: Boolean,
    val inputCommittedNs: Long,
    val confirmCommittedNs: Long,
)
