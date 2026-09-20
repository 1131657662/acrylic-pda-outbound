package com.acrylic.outbound.exports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import jakarta.validation.constraints.*;

public final class ExportModels {
    public static final String TYPE = "REPORT_RENDER_V1";
    public record Range(@NotNull LocalDate fromDate, @NotNull LocalDate throughDate) { }
    public record Command(@NotNull Format format, @NotNull @Pattern(regexp="ORIGINAL_CONFIRMED") String basis,
            @jakarta.validation.Valid Range range) { }
    public enum Format {
        XLSX("xlsx", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        SCAN_XLSX("xlsx", "scan-xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        PDF("pdf", "pdf", "application/pdf");
        public final String extension;
        public final String rendererFormat;
        public final String contentType;
        Format(String extension, String rendererFormat, String contentType) {
            this.extension=extension;this.rendererFormat=rendererFormat;this.contentType=contentType;
        }
        public String template() {
            return this==SCAN_XLSX?com.acrylic.outbound.reports.ScanRecordExcelRenderer.TEMPLATE:com.acrylic.outbound.reports.ReportSnapshot.TEMPLATE;
        }
    }
    public record Scope(UUID actor, UUID warehouse, UUID order) { }
    public record Request(Scope scope, UUID key, String epoch, Command command) { }
    public record Payload(Scope scope, Format format, String basis, String epoch, String snapshotHash,
            String snapshotId, String orderNo, long revision, String originalDay, String template,
            Instant expiresAt, Artifact artifact, Range range, Integer orderCount, UUID retryOf) { }
    public record Artifact(UUID token, String sha256, long bytes) { }
    public record View(UUID id, String orderNo, Format format, String basis, long resultVersion, long revision,
            String status, int attempts, String errorCode, Instant createdAt, Instant expiresAt,
            String originalDay, String snapshotId, String sha256, Long bytes, boolean downloadable,
            UUID orderId, Range range, int orderCount, UUID retryOf) { }
    public record Page(List<View> items, UUID nextCursor) { public Page { items=List.copyOf(items); } }
    private ExportModels() { }
}
