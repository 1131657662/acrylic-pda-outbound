package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.ImportFiles;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeFiles;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeObjectFiles;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeOpen;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakePage;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakePageItemWriter;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakePageItems;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakePageObjects;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeRawObjects;
import com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeTransactions;
import com.acrylic.outbound.temporary.protocol.E2aProtocol;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

final class E2aV36PageWriterChecks {
    private E2aV36PageWriterChecks() { }

    static void verify(JdbcTemplate jdbc, Map<String, String> seed, Path storage) throws Exception {
        var transactions = new SourceEvidenceIntakeTransactions(new DataSourceTransactionManager(jdbc.getDataSource()));
        var rawRepository = new SourceEvidenceIntakeRawObjects(jdbc);
        var pageRepository = new SourceEvidenceIntakePageObjects(jdbc);
        var stage = new SourceEvidenceIntakePageItems(jdbc);
        var rawFiles = new SourceEvidenceIntakeObjectFiles(storage.toString());
        var pageFiles = new SourceEvidenceIntakeFiles(storage.toString());
        var writer = new SourceEvidenceIntakePageItemWriter(transactions, rawRepository, rawFiles, stage);
        UUID rawOriginal = UUID.randomUUID();
        byte[] bytes = "v36-writer-original-bytes".getBytes(StandardCharsets.UTF_8);
        var storedRaw = rawFiles.store(new ByteArrayInputStream(bytes), bytes.length, ImportFiles.sha256(bytes));
        var rawBinding = new SourceEvidenceIntakeRawObjects.Binding(id(seed, "warehouseId"), id(seed, "mappedScopeId"),
            id(seed, "mappedSubmissionId"), 1, id(seed, "deviceId"), id(seed, "operatorId"),
            "CAPTURE_RECORDS", rawOriginal);
        transactions.write(status -> rawRepository.insert(rawBinding, storedRaw, Instant.now()));
        UUID record = UUID.randomUUID();
        UUID open = UUID.randomUUID();
        E2aV36StageChecks.receipt(jdbc, seed, open.toString());
        var binding = binding(seed, open);
        var page = page(seed, open, rawOriginal, record, storedRaw, "writer-first");
        var prepared = writer.preflight(binding, page);
        var storedPage = pageFiles.storePage(page.pageCoreBytes());
        var saved = transactions.write(status -> {
            var object = pageRepository.insert(binding, page.pageNo(), storedPage, Instant.now());
            writer.stageNew(binding, page, object, prepared);
            return object;
        });
        transactions.read(status -> {
            writer.verifyExisting(binding, page, saved, prepared);
            require(stage.existing(new SourceEvidenceIntakePageItems.PageContext(binding, 1, saved.id())).size() == 1,
                "Real-file PAGE item was not staged");
            return null;
        });
        verifyRollback(jdbc, seed, transactions, pageRepository, pageFiles, writer, rawOriginal, record, storedRaw);
    }

    private static void verifyRollback(JdbcTemplate jdbc, Map<String, String> seed,
        SourceEvidenceIntakeTransactions transactions, SourceEvidenceIntakePageObjects pageRepository,
        SourceEvidenceIntakeFiles pageFiles, SourceEvidenceIntakePageItemWriter writer,
        UUID rawOriginal, UUID record, SourceEvidenceIntakeObjectFiles.Stored raw) throws Exception {
        UUID open = UUID.randomUUID();
        E2aV36StageChecks.receipt(jdbc, seed, open.toString());
        var binding = binding(seed, open);
        var page = page(seed, open, rawOriginal, record, raw, "writer-conflict");
        var prepared = writer.preflight(binding, page);
        var durable = pageFiles.storePage(page.pageCoreBytes());
        try {
            transactions.write(status -> {
                var object = pageRepository.insert(binding, 1, durable, Instant.now());
                writer.stageNew(binding, page, object, prepared);
                return null;
            });
            throw new AssertionError("Cross-page record key was accepted");
        } catch (BusinessException expected) {
            require("E2A_PAGE_ITEM_DUPLICATE".equals(expected.code()), "Wrong duplicate error");
        }
        var missing = transactions.read(status -> pageRepository.findAfterSourceLock(binding, 1));
        require(missing == null, "Failed PAGE left a database object");
        pageFiles.verify(durable);
    }

    private static SourceEvidenceIntakePage.Parsed page(Map<String, String> seed, UUID open,
        UUID rawOriginal, UUID record, SourceEvidenceIntakeObjectFiles.Stored raw, String manifest) {
        var core = SubmissionJson.object().put("manifestId", manifest).put("manifestVersion", 1)
            .put("snapshotId", "writer-snapshot").put("pageNo", 1).put("pageCount", 1)
            .put("isFinal", true).put("itemCount", 1).put("byteCount", raw.bytes());
        core.putNull("prevPageHash");
        core.putArray("items").add(SubmissionJson.object().put("itemKind", "CAPTURE")
            .put("originalRecordId", record.toString()).put("objectKind", "CAPTURE_RECORDS")
            .put("originalObjectId", rawOriginal.toString()).put("itemBytesSha256", raw.sha256())
            .put("byteCount", raw.bytes()).put("originalDeviceSeq", 1).put("recordSha256", "b".repeat(64))
            .put("recordStatus", "CAPTURED").putNull("baleId").putNull("originalCycle").putNull("captureId"));
        var request = SubmissionJson.object().put("operation", "PAGE").put("openOriginalKey", open.toString());
        request.set("pageCore", core);
        request.put("pageHash", E2aProtocol.pageHash(core));
        var original = new SourceEvidenceIntakeOpen.Parsed(new byte[] {1}, "a".repeat(64), manifest, 1,
            "writer-snapshot", seed.get("authorizationId"), id(seed, "mappedSubmissionId"), 1);
        return SourceEvidenceIntakePage.parse(request.toString().getBytes(StandardCharsets.UTF_8), open, original);
    }

    private static SourceEvidenceIntakePageObjects.Binding binding(Map<String, String> seed, UUID open) {
        return new SourceEvidenceIntakePageObjects.Binding(id(seed, "warehouseId"), id(seed, "mappedScopeId"),
            id(seed, "mappedSubmissionId"), 1, id(seed, "deviceId"), id(seed, "operatorId"), open);
    }

    private static UUID id(Map<String, String> seed, String key) {
        return UUID.fromString(seed.get(key));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
