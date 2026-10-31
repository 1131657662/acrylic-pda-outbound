package com.acrylic.outbound.pda.ui

import com.acrylic.outbound.pda.BuildConfig

internal object TemporaryHandoverActionGate {
    const val CLOSED_MESSAGE = "临时交接操作已关闭；原资料保留，未执行任何操作"

    fun isEnabled(): Boolean = BuildConfig.DEBUG && BuildConfig.TEMPORARY_HANDOVER_DEVELOPMENT

    fun isBlocked(action: String): Boolean = isBlocked(action, isEnabled())

    internal fun isBlocked(action: String, developmentEnabled: Boolean): Boolean =
        action.startsWith("TEMP_") && !developmentEnabled
}
