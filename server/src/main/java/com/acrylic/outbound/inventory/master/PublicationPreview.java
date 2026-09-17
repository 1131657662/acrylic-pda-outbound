package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.auth.Access;
import com.acrylic.outbound.inventory.ImportQueries;
import com.acrylic.outbound.inventory.ImportSummary;
import com.acrylic.outbound.inventory.parser.ParsedBale;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicationPreview {
    private final Access access;
    private final ImportQueries imports;
    private final WarehouseBaseline baselines;
    private final MasterDataReader reader;
    private final MappingStore mappings;
    public PublicationPreview(Access access, ImportQueries imports, WarehouseBaseline baselines,
                              MasterDataReader reader, MappingStore mappings) {
        this.access = access; this.imports = imports; this.baselines = baselines; this.reader = reader; this.mappings = mappings;
    }
    @Transactional(readOnly=true)
    public Preview read(UUID warehouse, UUID importId) {
        access.require("inventory:read", warehouse);
        var baseline = baselines.read(warehouse, false);
        var summary = imports.detail(warehouse, importId);
        var incoming = reader.imported(importId);
        var differences = InventoryComparison.compare(reader.snapshot(baseline.activeSnapshotId()), incoming);
        return new Preview(summary, baseline, differences, mappingChanges(mappings.current(warehouse), incoming));
    }
    private List<MappingChange> mappingChanges(List<MappingStore.Mapping> existing, List<ParsedBale> incoming) {
        Map<String, MappingStore.Mapping> current = new HashMap<>();
        existing.forEach(mapping -> current.put(mapping.deliveryCode(), mapping));
        Map<String, ParsedBale> candidates = new TreeMap<>();
        incoming.forEach(bale -> candidates.putIfAbsent(bale.deliveryCode(), bale));
        List<MappingChange> changes = new ArrayList<>();
        candidates.forEach((code, bale) -> {
            var old = current.get(code);
            if (old == null || !old.model().equals(bale.model()) || !old.name().equals(bale.name()))
                changes.add(new MappingChange(code, old, bale.model(), bale.name(), old == null || old.enabled()));
        });
        return List.copyOf(changes);
    }
    public record MappingChange(String deliveryCode, MappingStore.Mapping previous, String model, String name, boolean enabled) { }
    public record Preview(ImportSummary imported, WarehouseBaseline.Baseline baseline,
                          InventoryComparison.Result differences, List<MappingChange> mappingChanges) { }
}
