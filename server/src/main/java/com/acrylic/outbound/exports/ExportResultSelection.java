package com.acrylic.outbound.exports;

import com.acrylic.outbound.common.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.exports.ExportModels.*;

/** Selects a result version inside the caller's consistent snapshot. */
@Component
public class ExportResultSelection {
    private final JdbcTemplate jdbc;
    public ExportResultSelection(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public long version(UUID order,Command command,Instant cutoff) {
        if(!"CURRENT_EFFECTIVE".equals(command.basis())) return command.version();
        Long version=jdbc.queryForObject("SELECT MAX(result_version) FROM order_result_version WHERE order_id=? AND created_at<=?",
                Long.class,Ids.bytes(order),Timestamp.from(cutoff));
        if(version==null) throw new BusinessException(409,"EXPORT_SOURCE_UNAVAILABLE","截止时没有已确认结果");
        return version;
    }
    public static void validate(Request request) {
        var c=request.command();boolean latest="CURRENT_EFFECTIVE".equals(c.basis());
        boolean corrected="CORRECTED".equals(c.basis());
        if(!java.util.Set.of("ORIGINAL_CONFIRMED","CURRENT_EFFECTIVE","CORRECTED").contains(c.basis()))
            throw new BusinessException(400,"EXPORT_BASIS_INVALID","导出口径无效");
        if((latest && c.resultVersion()!=null) || (corrected && c.version()<2) || (!latest && !corrected && c.version()!=1))
            throw new BusinessException(400,"EXPORT_VERSION_INVALID","最新净值不指定版本；原确认为版本1；指定更正须大于1");
        if(corrected && request.scope().order()==null)
            throw new BusinessException(400,"EXPORT_RANGE_INVALID","指定更正版本仅支持单订单");
        if(!"ORIGINAL_CONFIRMED".equals(c.basis()) && c.format()==Format.SCAN_XLSX)
            throw new BusinessException(400,"EXPORT_FORMAT_UNSUPPORTED","扫码记录Excel仅支持原确认口径");
    }
}
