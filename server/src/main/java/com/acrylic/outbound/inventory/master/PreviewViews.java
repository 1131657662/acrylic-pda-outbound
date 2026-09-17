package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.ImportSummary;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PreviewViews {
    private final PublicationPreview previews;
    public PreviewViews(PublicationPreview previews) { this.previews = previews; }
    public Summary summary(UUID warehouse, UUID imported) {
        var p = previews.read(warehouse, imported);
        var d = p.differences();
        return new Summary(p.imported(), p.baseline(), d.added(), d.removed(), d.changed(), d.unchanged(), p.mappingChanges().size());
    }
    public Page<?> page(UUID warehouse, UUID imported, Query query) {
        int limit = Page.checkedLimit(query.limit());
        var p = previews.read(warehouse, imported);
        String snapshot = p.baseline().activeSnapshotId() == null ? "NONE" : p.baseline().activeSnapshotId();
        if (!snapshot.equals(query.expectedSnapshot()) || !p.baseline().mappingRevision().equals(query.expectedMappingRevision()))
            throw new BusinessException(409, "PUBLICATION_BASELINE_CHANGED", "库存版本或映射已变化，请重新核对差异");
        if (query.kind().equals("mappings")) return Page.from(p.mappingChanges().stream()
            .filter(c -> c.deliveryCode().compareTo(query.after()) > 0).limit(limit + 1L).toList(), limit, PublicationPreview.MappingChange::deliveryCode);
        if (!query.kind().equals("changes")) throw new BusinessException(400, "INVALID_REQUEST", "未知差异类别");
        return Page.from(p.differences().changes().stream().filter(c -> c.barcode().compareTo(query.after()) > 0)
            .limit(limit + 1L).toList(), limit, InventoryComparison.Change::barcode);
    }
    public record Summary(ImportSummary imported, WarehouseBaseline.Baseline baseline,
                          int added, int removed, int changed, int unchanged, int mappingChangeCount) { }
    public record Query(String kind, String after, int limit, String expectedSnapshot, String expectedMappingRevision) { }
}
