package com.acrylic.outbound.recovery;

import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import com.acrylic.outbound.recovery.RecoveryProjectionRows.*;

/** Checks the retained database prefix before trusting its latest row. */
final class RecoveryProjectionHistory {
    private static final Map<String, String> NEXT = Map.of(
        "QUIESCING", "RECOVERING", "RECOVERING", "VERIFYING", "VERIFYING", "RELEASE_READY");

    static Projection validate(RecoveryProjectionRows rows, Binding binding) {
        var history = rows.history(binding.installation());
        require(!history.isEmpty(), "CONTROL_DATABASE_PROJECTION_MISMATCH");
        require(history.size() <= 100000, "CONTROL_DATABASE_CHAIN_CONFLICT");
        var first = history.getFirst();
        require(first.revision() == 1 && first.hash().equals(binding.initialHash())
            && first.previousHash().equals(ZERO_HASH) && first.phase().equals("QUIESCING")
            && first.numeric() == binding.baseline(), "CONTROL_DATABASE_CHAIN_CONFLICT");
        var mappings = new ArrayList<Epoch>();
        mappings.add(new Epoch(first.epoch(), first.numeric(), first.revision(), first.hash()));
        for (int index = 1; index < history.size(); index++) {
            var previous = history.get(index - 1); var current = history.get(index);
            require(current.revision() == previous.revision() + 1 && current.previousHash().equals(previous.hash())
                && current.phase().equals(NEXT.get(previous.phase())), "CONTROL_DATABASE_CHAIN_CONFLICT");
            boolean changed = !current.epoch().equals(previous.epoch());
            require(changed == current.phase().equals("RECOVERING")
                && current.numeric() == (changed ? Math.addExact(previous.numeric(), 1) : previous.numeric()),
                "CONTROL_DATABASE_EPOCH_CONFLICT");
            if (changed) mappings.add(new Epoch(current.epoch(), current.numeric(), current.revision(), current.hash()));
        }
        require(mappings.equals(rows.epochs(binding.installation())), "CONTROL_DATABASE_EPOCH_CONFLICT");
        return history.getLast();
    }
}
