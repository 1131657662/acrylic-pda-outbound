package com.acrylic.outbound.inventory.master;

import com.acrylic.outbound.common.*;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.inventory.parser.ParsedBale;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PublicationPreparation {
    private final MutationAccess access;
    private final PublicationPreview preview;
    private final IdempotencyRequests requests;
    private final JsonText json;
    private final MasterDataReader reader;
    private final ImportFiles files;
    public PublicationPreparation(MutationAccess access, PublicationPreview preview, IdempotencyRequests requests,
                                  JsonText json, MasterDataReader reader, ImportFiles files) {
        this.access = access; this.preview = preview; this.requests = requests; this.json = json; this.reader = reader; this.files = files;
    }
    public Work load(MutationAccess.Context context, PublicationService.Command input) throws java.io.IOException {
        var locked = access.lock(context, "inventory:publish");
        String digest = json.digest(new Payload(context.warehouse(), input));
        var key = new IdempotencyRequests.Key(locked.actor(), "inventory.publish", context.key());
        UUID replay = requests.find(key, digest);
        if (replay != null) return new Work(locked.actor(), digest, replay, null, List.of());
        var data = preview.read(context.warehouse(), input.importId());
        if (data.imported().publishedSnapshotId() != null) {
            replay = UUID.fromString(data.imported().publishedSnapshotId());
            requests.record(key, digest, replay);
            return new Work(locked.actor(), digest, replay, null, List.of());
        }
        validate(data, input.decisions());
        if (!data.mappingChanges().isEmpty()) access.requireMapping(context.warehouse());
        files.verify(data.imported().fileSha256());
        var rows = reader.imported(input.importId());
        if (rows.size() != data.imported().validRows()) throw new IllegalStateException("Staged row count mismatch");
        return new Work(locked.actor(), digest, null, data, rows);
    }
    private void validate(PublicationPreview.Preview p, PublicationService.Decisions d) {
        String expected = d.expectedActiveSnapshot() == null ? null : d.expectedActiveSnapshot().toString();
        if (!Objects.equals(expected, p.baseline().activeSnapshotId()) || !d.expectedMappingRevision().equals(p.baseline().mappingRevision()))
            throw new BusinessException(409, "PUBLICATION_BASELINE_CHANGED", "库存版本或映射已变化，请重新核对差异");
        if (p.imported().errorRows() > 0 || p.imported().validRows() == 0)
            throw new BusinessException(409, "IMPORT_BLOCKED", "文件含错误或没有可发布记录，禁止发布有效子集");
        if (p.imported().duplicateRows() > 0 && !d.acceptIdenticalDuplicates())
            throw new BusinessException(409, "DEDUP_CONFIRMATION_REQUIRED", "需确认相同重复条码去重");
        if (!p.mappingChanges().isEmpty() && !d.acceptMappingChanges())
            throw new BusinessException(409, "MAPPING_CONFIRMATION_REQUIRED", "需核对并确认候选映射变更");
        if (d.confirmedRemovedCount() != p.differences().removed())
            throw new BusinessException(409, "REMOVAL_CONFIRMATION_REQUIRED", "请核对本次全量文件缺少的包数");
    }
    private record Payload(UUID warehouse, PublicationService.Command command) { }
    public record Work(UUID actor, String digest, UUID replay, PublicationPreview.Preview preview, List<ParsedBale> rows) { }
}
