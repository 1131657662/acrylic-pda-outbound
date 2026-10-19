package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.RequestIdFilter;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** Seeds a real formal order before the P5 context is restarted for TCP transport checks. */
public record SubmissionHttpInput(String submissionId, String orderId, String accessToken,
        byte[] payload, ObjectNode body) {
    public static SubmissionHttpInput prepare(WebApplicationContext context, boolean large) throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(RequestIdFilter.class), context.getBean(FilterChainProxy.class))
            .apply(springSecurity()).build();
        var json = context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        var jdbc = context.getBean(JdbcTemplate.class);
        var passwords = context.getBean(PasswordEncoder.class);
        var web = new PublicationDriver(mvc, json, IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN"));
        var fixture = new SubmissionFixture(web, jdbc, true, false);
        if (large) fixture.addAcknowledgedFailures(1100);
        byte[] payload = SubmissionJson.canonical(fixture.body).getBytes(StandardCharsets.UTF_8);
        return new SubmissionHttpInput(fixture.id(), fixture.order,
            fixture.tokens.path("accessToken").asText(), payload, fixture.body.deepCopy());
    }
}
