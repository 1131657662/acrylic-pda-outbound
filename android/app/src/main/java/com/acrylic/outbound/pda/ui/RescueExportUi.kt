package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.rescue.*

data class RescueExportUi(val busy: Boolean = false, val message: String = "请核验已预置的本地救援凭据",
    val identities: List<RescueCredentialChoice> = emptyList(), val orders: List<RescueOrderChoice> = emptyList(),
    val hasMore: Boolean = false, val selected: Int = 0, val summary: String = "", val unlocked: Boolean = false,
    val privatePackages: List<RescuePrivatePackage> = emptyList(),
    val canGenerate: Boolean = false, val canCopy: Boolean = false, val canExportText: Boolean = false,
    val textExportState: String = "读取快照后可直接导出简单的 out.txt 文件。",
    val copyState: String = "电脑副本尚未核验；生成本机包不代表已外置备份。")
