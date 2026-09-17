package com.acrylic.outbound.inventory;

import java.time.Instant;
import java.util.UUID;

public record ImportSummary(UUID id, UUID warehouseId, String originalFilename, String fileSha256,
                            String parserVersion, String status, int totalRows, int validRows,
                            int errorRows, int duplicateRows, int ignoredRows, int productCount,
                            String totalWeightGrams, Instant createdAt, String publishedSnapshotId) { }
