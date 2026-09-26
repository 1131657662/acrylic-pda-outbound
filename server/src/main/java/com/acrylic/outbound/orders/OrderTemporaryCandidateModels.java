package com.acrylic.outbound.orders;

import com.acrylic.outbound.common.BusinessException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class OrderTemporaryCandidateModels {
    private OrderTemporaryCandidateModels() { }
    public record Check(String path,boolean advisoryOnly) {
        public static Check forOrder(String order) { return new Check("/orders/"+order+"/temporary-candidates",true); }
    }
    public record Filter(LocalDate fromDate,LocalDate throughDate,int page) { }
    public record Query(java.util.UUID warehouse,Range range,int page) { }
    public record Range(LocalDate fromDate,LocalDate throughDate) {
        static Range resolve(Filter filter,String createdAt) {
            var day=Instant.parse(createdAt).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
            if(filter.fromDate()==null && filter.throughDate()==null)return new Range(day.minusDays(7),day.plusDays(7));
            if(filter.fromDate()==null || filter.throughDate()==null || filter.fromDate().getYear()<2000
                || filter.throughDate().getYear()>9998 || filter.fromDate().isAfter(filter.throughDate())
                || ChronoUnit.DAYS.between(filter.fromDate(),filter.throughDate())>=366)
                throw new BusinessException(400,"TEMPORARY_CANDIDATE_RANGE_INVALID","请选择首尾完整、不超过366天的临时单接收日期范围");
            return new Range(filter.fromDate(),filter.throughDate());
        }
        Instant start() { return fromDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(); }
        Instant end() { return throughDate.plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(); }
    }
    public record Item(String submissionId,String orderId,String orderNo,String status,String vehicleNo,String containerNo,
        String plannedTotal,String actualCount,String receivedAt,boolean sameVehicle,boolean overlappingProducts) { }
    public record Page(List<Item> items,long total,int page,int pageSize) { }
    public record Response(String orderId,int revision,String orderVersion,Range range,String checkedAt,
        Page candidates,boolean hasFormalWork,boolean advisoryOnly,boolean releaseAllowed) { }
}
