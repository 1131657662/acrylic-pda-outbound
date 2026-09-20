package com.acrylic.outbound.reports;

import java.io.*;
import java.nio.file.*;

/** No services, credentials or database required. Never overwrites an existing report. */
public final class ReportCli {
    public static void main(String[] args) throws IOException {
        boolean confirmed = args.length == 4 && args[0].equals("--confirmed-v1");
        Values.require(args.length == 3 || confirmed, "用法: [--confirmed-v1] <snapshot.json> <xlsx|scan-xlsx|pdf> <output-file>");
        if (confirmed) args = java.util.Arrays.copyOfRange(args, 1, args.length);
        Values.require(java.util.Set.of("xlsx", "scan-xlsx", "pdf").contains(args[1]), "格式只能为xlsx、scan-xlsx或pdf");
        var snapshot = confirmed ? SnapshotJson.readConfirmedV1(Path.of(args[0])) : SnapshotJson.read(Path.of(args[0]));
        publish(snapshot, args[1], Path.of(args[2]));
    }
    private static void publish(ReportSnapshot snapshot, String format, Path path) throws IOException {
        var target = path.toAbsolutePath().normalize();
        Values.require(!Files.exists(target), "目标文件已存在，必须选择新路径");
        Files.createDirectories(target.getParent());
        var temp = Files.createTempFile(target.getParent(), "r08-", ".part");
        try {
            try (var output = Files.newOutputStream(temp)) {
                if (format.equals("xlsx")) new ExcelRenderer().render(snapshot, output);
                else if (format.equals("scan-xlsx")) new ScanRecordExcelRenderer().render(snapshot, output);
                else new PdfRenderer().render(snapshot, output);
            }
            // No REPLACE_EXISTING: races must fail, never overwrite an earlier result.
            Files.move(temp, target);
        } finally { Files.deleteIfExists(temp); }
        System.out.println("已生成 " + target + " / " + snapshot.metadata().provenance());
    }
    private ReportCli() { }
}
