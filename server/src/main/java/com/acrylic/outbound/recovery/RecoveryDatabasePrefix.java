package com.acrylic.outbound.recovery;

import java.util.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import com.acrylic.outbound.recovery.RecoveryProjectionRows.*;

/** Every database projection must be an exact prefix of the already verified external chain. */
final class RecoveryDatabasePrefix {
    private RecoveryDatabasePrefix() { }

    static List<Projection> validate(RecoveryProjectionRows rows, Binding binding, RecoveryDatabaseControl control) {
        var history = rows.history(binding.installation());
        require(!history.isEmpty() && history.size() <= 100000, "CONTROL_DATABASE_CHAIN_CONFLICT");
        var epochs = new ArrayList<Epoch>(); long numeric = binding.baseline(); UUID epoch = null;
        for (int index = 0; index < history.size(); index++) {
            var row = history.get(index); var record = control.at(index + 1L); var command = record.path("command");
            require(row.revision() == index + 1L && row.hash().equals(text(record, "recordSha256"))
                && row.previousHash().equals(text(record, "previousRecordSha256"))
                && row.epoch().equals(uuid(command, "epochId")) && row.phase().equals(text(command, "phase")),
                "CONTROL_DATABASE_CHAIN_CONFLICT");
            if (index == 0) require(row.hash().equals(binding.initialHash()), "CONTROL_DATABASE_CHAIN_CONFLICT");
            if (row.phase().equals("RECOVERING")) numeric = Math.addExact(numeric, 1);
            require(row.numeric() == numeric, "CONTROL_DATABASE_EPOCH_CONFLICT");
            if (!row.epoch().equals(epoch)) epochs.add(new Epoch(row.epoch(), numeric, row.revision(), row.hash()));
            epoch = row.epoch();
        }
        require(epochs.equals(rows.epochs(binding.installation())), "CONTROL_DATABASE_EPOCH_CONFLICT");
        return history;
    }
}
