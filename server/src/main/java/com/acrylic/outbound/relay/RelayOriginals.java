package com.acrylic.outbound.relay;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** Same scoped, sealed original read for processing, submission queries and independent review. */
@Component
public class RelayOriginals {
    private final RelayEnabled enabled;private final RelayAccess access;private final RelayStore store;private final RelayFiles files;
    public RelayOriginals(RelayEnabled enabled,RelayAccess access,RelayStore store,RelayFiles files) {
        this.enabled=enabled;this.access=access;this.store=store;this.files=files;
    }
    public Selected read(UUID warehouse,UUID id) {
        enabled.require();access.read(warehouse);var row=store.get(id);RelayIntake.scope(row,warehouse);
        return new Selected(row,RelayArchive.read(files.read(row.archiveSha256())));
    }
    public record Selected(RelayStore.Row row,RelayArchive.Bundle bundle) { }
}
