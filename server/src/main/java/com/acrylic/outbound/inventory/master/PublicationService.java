package com.acrylic.outbound.inventory.master;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class PublicationService {
    private final PublicationPreparation prepare;
    private final PublicationMappings mappings;
    private final SnapshotWriter writer;
    private final PublicationReceipts receipts;
    public PublicationService(PublicationPreparation prepare, PublicationMappings mappings, SnapshotWriter writer, PublicationReceipts receipts) {
        this.prepare = prepare; this.mappings = mappings; this.writer = writer; this.receipts = receipts;
    }
    @Transactional(rollbackFor=Exception.class, isolation=Isolation.READ_COMMITTED)
    public SnapshotQueries.Receipt publish(MutationAccess.Context context, Command input) throws java.io.IOException {
        var work = prepare.load(context, input);
        if (work.replay() != null) return receipts.get(context.warehouse(), work.replay());
        var products = mappings.apply(work.preview(), context, work.actor());
        UUID snapshot = writer.create(new SnapshotWriter.Data(context.warehouse(), work.actor(), work.preview(), work.rows(), products));
        receipts.save(context, input, work, snapshot);
        return receipts.get(context.warehouse(), snapshot);
    }
    public record Command(@NotNull UUID importId, @NotNull @jakarta.validation.Valid Decisions decisions) { }
    public record Decisions(@JsonProperty(required=true) UUID expectedActiveSnapshot,
        @NotNull @Pattern(regexp="[0-9]{1,18}") String expectedMappingRevision,
        @NotNull Boolean acceptIdenticalDuplicates, @NotNull Boolean acceptMappingChanges,
        @NotNull @Min(0) Integer confirmedRemovedCount, @Size(max=500) String reason) { }
}
