package com.acrylic.m71demo.ui

import com.acrylic.m71demo.data.*

data class DemoUiState(
    val order: DemoOrder? = null,
    val lines: List<OrderLine> = emptyList(),
    val candidate: InputCapture? = null,
    val candidateDetail: String = "",
    val soundError: String = "",
    val recent: List<ScanRecord> = emptyList(),
    val pending: Int = 0,
    val queued: Int = 0,
    val message: String = "正在加载离线测试数据…",
    val blocked: Boolean = false,
)
