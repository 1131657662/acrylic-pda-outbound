package com.acrylic.outbound.exports;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import jakarta.validation.constraints.*;

public final class ExportModels {
    public static final String TYPE = "REPORT_RENDER_V1";
    public record Range(@NotNull LocalDate fromDate, @NotNull LocalDate throughDate) { }
    public record Command(@NotNull Format format, @NotNull @Pattern(regexp="ORIGINAL_CONFIRMED|CURRENT_EFFECTIVE|CORRECTED") String basis,
            @jakarta.validation.Valid Range range, @Min(1) Long resultVersion) {
        public Command(Format format,String basis,Range range) { this(format,basis,range,null); }
        public long version() { return resultVersion==null?("CURRENT_EFFECTIVE".equals(basis)?0L:1L):resultVersion; }
    }
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
    public record Member(UUID orderId,long resultVersion) { }
    public record Payload(Scope scope, Format format, String basis, String epoch, String snapshotHash,
            String snapshotId, String orderNo, long revision, String originalDay, String template,
            Instant expiresAt, Artifact artifact, Range range, Integer orderCount, UUID retryOf, Long resultVersion,
            List<Member> members) {
        public Payload { members=members==null?List.of():List.copyOf(members); }
        public Payload(Scope scope,Format format,String basis,String epoch,String snapshotHash,String snapshotId,
                String orderNo,long revision,String originalDay,String template,Instant expiresAt,Artifact artifact,
                Range range,Integer orderCount,UUID retryOf,Long resultVersion) {
            this(scope,format,basis,epoch,snapshotHash,snapshotId,orderNo,revision,originalDay,template,
                    expiresAt,artifact,range,orderCount,retryOf,resultVersion,List.of());
        }
        public Payload(Scope scope,Format format,String basis,String epoch,String snapshotHash,String snapshotId,
                String orderNo,long revision,String originalDay,String template,Instant expiresAt,Artifact artifact,
                Range range,Integer orderCount,UUID retryOf) {
            this(scope,format,basis,epoch,snapshotHash,snapshotId,orderNo,revision,originalDay,template,
                    expiresAt,artifact,range,orderCount,retryOf,null);
        }
        public long version() { return resultVersion==null?1L:resultVersion; }
    }
    public record Artifact(UUID token, String sha256, long bytes) { }
    public record View(UUID id, String orderNo, Format format, String basis, long resultVersion, long revision,
            String status, int attempts, String errorCode, Instant createdAt, Instant expiresAt,
            String originalDay, String snapshotId, String sha256, Long bytes, boolean downloadable,
            UUID orderId, Range range, int orderCount, UUID retryOf, List<Member> members) { }
    public record Page(List<View> items, UUID nextCursor) { public Page { items=List.copyOf(items); } }
    private ExportModels() { }
}
