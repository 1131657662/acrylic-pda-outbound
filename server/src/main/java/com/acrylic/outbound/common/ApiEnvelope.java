package com.acrylic.outbound.common;

import java.time.Instant;

public record ApiEnvelope<T>(String code, String message, T data, String requestId,
                             Instant serverTime, String serverRecoveryEpoch, String serviceMode) { }
