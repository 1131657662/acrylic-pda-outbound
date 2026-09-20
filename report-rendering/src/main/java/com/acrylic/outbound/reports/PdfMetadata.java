package com.acrylic.outbound.reports;

import java.io.*;
import java.util.*;
import org.apache.pdfbox.Loader;

final class PdfMetadata {
    static void write(byte[] bytes, ReportSnapshot snapshot, OutputStream output) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            var info = document.getDocumentInformation(); var m = snapshot.metadata();
            info.setTitle("装车单"); info.setSubject(ReportText.identity(snapshot));
            info.setCustomMetadataValue("SnapshotId", m.snapshotId());
            info.setCustomMetadataValue("DataVersion", m.dataVersion());
            info.setCustomMetadataValue("DataCutoff", m.cutoff().toString());
            info.setCustomMetadataValue("GeneratedAt", m.generatedAt().toString());
            info.setCustomMetadataValue("TemplateVersion", ReportSnapshot.TEMPLATE);
            info.setCustomMetadataValue("PeriodBasis", "SHIPMENT_DATE_NET");
            info.setCustomMetadataValue("ResultBasis", m.basis().toString());
            info.setCustomMetadataValue("Provenance", m.provenance().toString());
            info.setCustomMetadataValue("OrderVersions", snapshot.orders().stream()
                    .map(o -> o.id() + ":" + o.revision() + ":" + o.resultVersion()).collect(java.util.stream.Collectors.joining(";")));
            var date = GregorianCalendar.from(m.generatedAt().atZone(java.time.ZoneOffset.UTC));
            info.setCreationDate(date); info.setModificationDate(date);
            document.save(output);
        }
    }
    private PdfMetadata() { }
}
