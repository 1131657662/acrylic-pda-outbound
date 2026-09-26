package com.acrylic.outbound.jobs;

import com.acrylic.outbound.common.ServerState;
import org.springframework.stereotype.Component;

/** A running producer/worker never silently adopts a restored database generation. */
@Component
public class JobRecoveryFence {
    private final ServerState state;
    private final String startupEpoch;

    public JobRecoveryFence(ServerState state) {
        this.state = state;
        this.startupEpoch = state.current().epoch();
    }

    public String lock() {
        state.lockWritable(startupEpoch);
        return startupEpoch;
    }
}
