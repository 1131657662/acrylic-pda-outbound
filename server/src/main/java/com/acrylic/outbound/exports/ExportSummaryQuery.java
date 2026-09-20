package com.acrylic.outbound.exports;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static com.acrylic.outbound.exports.ExportModels.*;

@Service
public class ExportSummaryQuery {
    private final ExportRangeReader reader;
    public ExportSummaryQuery(ExportRangeReader reader) { this.reader=reader; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ExportSummary read(Scope scope,Range range) {
        return ExportSummary.from(reader.read(new Request(scope,null,null,new Command(Format.XLSX,"ORIGINAL_CONFIRMED",range)),Instant.now()));
    }
}
