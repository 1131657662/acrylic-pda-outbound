package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic completed source and separated actors; subsequent attachment operations use TCP. */
public record CorrectionAttachmentAdmittedInput(String warehouse, String otherWarehouse,
        Actor owner, Actor reviewer, Actor foreign, ObjectNode create) {
    public record Actor(String id, String username) { }

    public static CorrectionAttachmentAdmittedInput prepare(WebApplicationContext context) throws Exception {
        var f = new SupervisorCorrectionFixture(context);
        var outsider = IdentityFixture.create(f.jdbc, context.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))", Ids.bytes(outsider.user().id()), f.warehouse);
        assertThat(f.permissions(outsider.user().id())).contains("outbound:correct").doesNotContain("correction:approve");
        return new CorrectionAttachmentAdmittedInput(f.warehouse, outsider.warehouse().toString(),
            new Actor(f.source.operator().id().toString(), f.source.operator().username()),
            new Actor(f.checker.user().id().toString(), f.checker.user().username()),
            new Actor(outsider.user().id().toString(), outsider.user().username()), f.create());
    }

    public ObjectNode command(JsonNode attachment) {
        return CorrectionAttachmentChecks.withAttachment(create.deepCopy(), attachment);
    }

    public JsonNode verifyReceipt(byte[] response, Path publicKey) throws Exception {
        return SupervisorCorrectionReceipt.verify(response, publicKey);
    }

    public String path(String attachment, boolean review) {
        return "/corrections/attachments/" + attachment + (review ? "/review" : "") + "?warehouseId=" + warehouse;
    }

    public ObjectNode evidence() {
        return SubmissionJson.object().put("warehouse", warehouse).put("otherWarehouse", otherWarehouse)
            .put("owner", owner.id()).put("reviewer", reviewer.id()).put("foreign", foreign.id());
    }
}
