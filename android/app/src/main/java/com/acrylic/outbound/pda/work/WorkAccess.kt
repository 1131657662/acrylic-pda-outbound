package com.acrylic.outbound.pda.work

import com.acrylic.outbound.pda.data.PreparedOrder
import com.acrylic.outbound.pda.security.TimeSample
import java.time.Instant

/** Each business write rechecks identity and the offline deadline. Tests replace only identity/time boundaries. */
interface WorkAccess {
    fun authorize(order: PreparedOrder): Instant
    fun identity(order: PreparedOrder)
    fun sample(): TimeSample
}
