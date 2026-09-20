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
        var cutoff=jdbc.queryForObject("SELECT UTC_TIMESTAMP(6)",Timestamp.class).toInstant();
        return ExportSummary.from(reader.read(new Request(scope,null,null,new Command(Format.XLSX,"ORIGINAL_CONFIRMED",range)),cutoff));
    }
}
