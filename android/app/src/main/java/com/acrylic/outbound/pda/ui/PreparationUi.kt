package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.data.ClaimIntent
import com.acrylic.outbound.pda.data.ClaimIntentHistory
import com.acrylic.outbound.pda.data.PreparedOrder
import org.json.JSONObject

data class LocalCard(val order: PreparedOrder, val readiness: String, val recalled: Boolean = false,
    val workState: String? = null, val submissionState: String? = null, val originalOperator: Boolean = false,
    val savedCount: Long = 0)

data class TemporaryHomeCard(val draft: com.acrylic.outbound.pda.temporary.TemporaryDraft, val savedCount: Long, val plannedCount: Long)

data class PreparationUi(val busy: Boolean = false, val message: String = "请先绑定设备并登录", val bound: Boolean = false,
    val username: String = "", val local: List<LocalCard> = emptyList(), val available: List<JSONObject> = emptyList(),
    val assigned: List<JSONObject> = emptyList(), val pending: List<ClaimIntent> = emptyList(), val deviceLabel: String = "",
    val section: String = "LOCAL", val availableCursor: String? = null, val assignedCursor: String? = null,
    val availableHasMore: Boolean = false, val assignedHasMore: Boolean = false, val loginRequired: Boolean = false,
    val formalOrders: Boolean = true, val temporaryVisible: Boolean = false, val localHasMore: Boolean = false,
    val rejectedHistory: List<ClaimIntentHistory> = emptyList(), val abnormalHasMore: Boolean = false,
    val temporaryLocal: List<TemporaryHomeCard> = emptyList())
