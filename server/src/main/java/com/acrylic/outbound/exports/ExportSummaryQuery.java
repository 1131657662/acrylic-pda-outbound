package com.acrylic.outbound.exports;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.exports.ExportModels.*;

@Service
public class ExportSummaryQuery {
    private final ExportRangeReader reader;
    private final JdbcTemplate jdbc;
    public ExportSummaryQuery(ExportRangeReader reader,JdbcTemplate jdbc) { this.reader=reader;this.jdbc=jdbc; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ExportSummary read(Scope scope,Range range) {
        return read(scope,range,"CURRENT_EFFECTIVE");
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ExportSummary read(Scope scope,Range range,String basis) {
        var cutoff=jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",Timestamp.class).toInstant();
        var request=new Request(scope,null,null,new Command(Format.XLSX,basis,range));
        ExportResultSelection.validate(request);
        return ExportSummary.from(reader.read(request,cutoff));
    }
}
