package com.acrylic.outbound.temporary.review;

import com.acrylic.outbound.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class TemporaryDuplicateModels {
    private TemporaryDuplicateModels() { }
    public record Filter(LocalDate fromDate,LocalDate throughDate,String cursor,int limit) { }
    public record Range(LocalDate fromDate,LocalDate throughDate) {
        static Range resolve(Filter input,Instant receivedAt) {
            var day=receivedAt.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            if(input.fromDate()==null && input.throughDate()==null) return new Range(day.minusDays(7),day.plusDays(7));
            if(input.fromDate()==null || input.throughDate()==null || input.fromDate().getYear()<2000
                || input.throughDate().getYear()>9998 || input.fromDate().isAfter(input.throughDate())
                || ChronoUnit.DAYS.between(input.fromDate(),input.throughDate())>=366)
                throw new BusinessException(400,"TEMPORARY_DUPLICATE_RANGE_INVALID","请选择首尾完整、不超过366天的有效建单日期范围");
            return new Range(input.fromDate(),input.throughDate());
        }
        Instant start() { return fromDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(); }
        Instant end() { return throughDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(); }
    }
    public record Head(String id,String orderNo,String status,int revision,String rowVersion,String revisionId,
        String vehicleNo,String containerNo,Instant createdAt,Instant updatedAt,String inventorySnapshotId,String mappingRevision,
        boolean hasAssignment,boolean hasPcWork,boolean hasSubmission,boolean hasResult) { }
    public record Product(String deliveryCode,String productId,String model,String name,String plannedCount) { }
    public record Candidate(Head order,List<Product> products,List<String> signals,List<String> blockers,boolean linkAvailable) { }
    public record Snapshot(long total,String fingerprint) { }
    public record Response(JsonNode context,Range range,Instant checkedAt,String contextSha256,String candidateSetSha256,
        String commandContextSha256,long total,List<Candidate> items,String nextCursor,boolean hasMore,boolean writeAvailable,
        boolean releaseAllowed) { }
}
