package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.RequestIdFilter;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Synthetic preparation only; the consumer subsequently uses a real Web session over TCP. */
public record SourceEvidenceHttpInput(String submission, String warehouse, String creator, String client,
        String reviewer, String username, String receipt, byte[] original, boolean accepted) {
    public static SourceEvidenceHttpInput prepare(WebApplicationContext context, boolean accepted) throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(RequestIdFilter.class), context.getBean(FilterChainProxy.class))
            .apply(springSecurity()).build();
        var inputs = new TemporarySourceEvidenceFixture.Inputs(context.getBean(JdbcTemplate.class), mvc,
            context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),
            context.getBean(PasswordEncoder.class), context.getEnvironment().getRequiredProperty("outbound.storage"));
        var fixture = new TemporarySourceEvidenceFixture(inputs, !accepted);
        byte[] bytes = SubmissionJson.canonical(fixture.temporary.body).getBytes(StandardCharsets.UTF_8);
        if (accepted) fixture.temporary.submit();
        else { fixture.begin(); fixture.chunk(0, java.util.Arrays.copyOfRange(bytes, 0, 262144)); }
        fixture.temporary.abort();
        var user = fixture.reviewer.identity().user();
        return new SourceEvidenceHttpInput(fixture.temporary.id(), fixture.reviewer.identity().warehouse().toString(),
            fixture.temporary.a.device(), fixture.temporary.a.client, user.id().toString(), user.username(),
            fixture.storedReceipt(), bytes, accepted);
    }

    public String path(String suffix) {
        return "/temporary-source-evidence/" + submission + suffix + "?warehouseId=" + warehouse
            + "&creatorDeviceId=" + creator + "&clientOrderId=" + client + "&workRound=1";
    }

    public ObjectNode evidence() {
        return SubmissionJson.object().put("submissionId", submission).put("warehouseId", warehouse)
            .put("creatorDeviceId", creator).put("clientOrderId", client).put("workRound", "1")
            .put("reviewerId", reviewer).put("accepted", accepted).put("originalStoredReceiptUtf8", receipt)
            .put("payloadSha256", ImportFiles.sha256(original)).put("payloadBytes", original.length);
    }
}
