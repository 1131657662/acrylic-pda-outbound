package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Byte storage through real HTTP with independent identities and existing correction behavior. */
public final class CorrectionAttachmentChecks {
    private final SupervisorCorrectionFixture f;
    private final CorrectionAttachmentHttp maker, checker, other;
    private final IdentityFixture outsider;
    private final Path attempt;

    public CorrectionAttachmentChecks(WebApplicationContext context, Path attempt) throws Exception {
        this.attempt = attempt; f = new SupervisorCorrectionFixture(context);
        outsider = IdentityFixture.create(f.jdbc, context.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(outsider.user().id()), f.warehouse);
        assertThat(f.permissions(outsider.user().id())).contains("outbound:correct").doesNotContain("correction:approve");
        maker = new CorrectionAttachmentHttp(f.source.operator().username(), attempt.resolve("attachment-http.log"));
        checker = new CorrectionAttachmentHttp(f.checker.user().username(), attempt.resolve("attachment-http.log"));
        other = new CorrectionAttachmentHttp(outsider.user().username(), attempt.resolve("attachment-http.log"));
    }

    public static void seedLegacy(WebApplicationContext context) throws Exception {
        var fixture = new SupervisorCorrectionFixture(context);
        new SupervisorCorrectionHttp(fixture.source.operator().username()).command(fixture.create(), 200);
    }

    public void verify() throws Exception {
        var original = f.immutableSource(); byte[] bytes = new byte[]{10, 20, 30, 40};
        var uploaded = uploads(bytes); String id = uploaded.path("attachmentId").asText();
        var foreign = other.upload(f.warehouse, UUID.randomUUID().toString(), bytes, CorrectionAttachmentHttp.sha(bytes), 200, false).path("data");
        new CorrectionAttachmentRejections(f, maker, attempt).verify(uploaded, foreign);
        var create = withAttachment(f.create(), uploaded); var created = maker.command(create, 200);
        var receipt = SupervisorCorrectionReceipt.verify(created, attempt.resolve("private/server-public.der"));
        String document = receipt.path("documentId").asText();
        assertThat(maker.command(create, 200)).isEqualTo(created);
        assertThat(checker.read(f.warehouse, id, true, 200)).isEqualTo(bytes);
        assertThat(count("correction_attachment_use", "document_id", document)).isEqualTo(1);
        assertThat(count("correction_command_receipt", "document_id", document)).isEqualTo(1);
        assertThat(f.immutableSource()).isEqualTo(original);
        Files.write(attempt.resolve("attachment-original.bin"), bytes);
        Files.write(attempt.resolve("attachment-create-response.json"), created);
        var recovery = SubmissionJson.object().put("username", f.source.operator().username()).put("warehouse", f.warehouse)
            .put("attachment", id).put("document", document);
        Files.writeString(attempt.resolve("attachment-restart.json"), recovery.toString());
        Files.writeString(attempt.resolve("real-records.tsv"), f.warehouse + "\t" + f.order + "\t" + document + "\t" + id + "\n");
    }

    private JsonNode uploads(byte[] bytes) throws Exception {
        String key = UUID.randomUUID().toString(), hash = CorrectionAttachmentHttp.sha(bytes);
        var uploaded = maker.upload(f.warehouse, key, bytes, hash, 200, true).path("data");
        assertThat(maker.upload(f.warehouse, key, bytes, hash, 200, false).path("data")).isEqualTo(uploaded);
        byte[] changed = new byte[]{99};
        code(maker.upload(f.warehouse, key, changed, CorrectionAttachmentHttp.sha(changed), 409, false), "IDEMPOTENCY_CONTENT_MISMATCH");
        code(maker.upload(f.warehouse, UUID.randomUUID().toString(), bytes, "0".repeat(64), 400, false), "ATTACHMENT_HASH_MISMATCH");
        uploadLimits();
        String id = uploaded.path("attachmentId").asText();
        assertThat(maker.read(f.warehouse, id, false, 200)).isEqualTo(bytes);
        code(SubmissionJson.parse(other.read(f.warehouse, id, false, 404)), "CORRECTION_ATTACHMENT_NOT_FOUND");
        code(SubmissionJson.parse(other.read(outsider.warehouse().toString(), id, false, 404)), "CORRECTION_ATTACHMENT_NOT_FOUND");
        code(SubmissionJson.parse(checker.read(f.warehouse, id, true, 404)), "CORRECTION_ATTACHMENT_NOT_FOUND");
        code(checker.upload(f.warehouse, UUID.randomUUID().toString(), bytes, hash, 403, false), "CORRECTION_PERMISSION_DENIED");
        code(maker.upload(outsider.warehouse().toString(), UUID.randomUUID().toString(), bytes, hash, 404, false), "CORRECTION_DOCUMENT_NOT_FOUND");
        return uploaded;
    }

    private void uploadLimits() throws Exception {
        byte[] large = new byte[10 * 1024 * 1024];
        var maximum = maker.upload(f.warehouse, UUID.randomUUID().toString(), large, CorrectionAttachmentHttp.sha(large), 200, true).path("data");
        assertThat(maker.read(f.warehouse, maximum.path("attachmentId").asText(), false, 200)).isEqualTo(large);
        byte[] oversized = new byte[large.length + 1];
        code(maker.upload(f.warehouse, UUID.randomUUID().toString(), oversized, CorrectionAttachmentHttp.sha(oversized), 400, true), "ATTACHMENT_LIMIT_EXCEEDED");
        code(maker.upload(f.warehouse, UUID.randomUUID().toString(), new byte[0], CorrectionAttachmentHttp.sha(new byte[0]), 400, false), "INVALID_CORRECTION_ATTACHMENT");
        new CorrectionAttachmentRejections(f, maker, attempt).capacity(maximum);
    }

    public static void restart(Path attempt) throws Exception {
        var facts = SubmissionJson.parse(Files.readAllBytes(attempt.resolve("attachment-restart.json")));
        var maker = new CorrectionAttachmentHttp(facts.path("username").asText(), attempt.resolve("attachment-http.log"));
        assertThat(maker.read(facts.path("warehouse").asText(), facts.path("attachment").asText(), false, 200))
            .isEqualTo(Files.readAllBytes(attempt.resolve("attachment-original.bin")));
    }

    static ObjectNode withAttachment(ObjectNode create, JsonNode attachment) {
        var line = (ObjectNode) create.path("lines").get(0);
        line.put("evidenceId", attachment.path("attachmentId").asText()).put("evidenceSha256", attachment.path("evidenceSha256").asText());
        line.putArray("attachments").add(attachment.deepCopy()); return create;
    }

    static void code(JsonNode result, String code) { assertThat(result.path("code").asText()).isEqualTo(code); }

    private long count(String table, String column, String id) {
        return f.jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=UUID_TO_BIN(?)", Long.class, id);
    }
}
