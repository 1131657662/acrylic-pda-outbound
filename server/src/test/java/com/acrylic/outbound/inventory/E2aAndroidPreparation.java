package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Synthetic preparation only; the Android upload, audit and evidence chain use the real HTTP server. */
public final class E2aAndroidPreparation {
    private E2aAndroidPreparation() { }

    public static void write(WebApplicationContext context, Path privateDirectory) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class);
        var mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class))
            .apply(springSecurity()).build();
        var identity = IdentityFixture.create(jdbc, context.getBean(PasswordEncoder.class), "WAREHOUSE_ADMIN");
        var f = new TemporaryFixture(new PublicationDriver(mvc, context.getBean(ObjectMapper.class), identity), jdbc);
        f.stock();
        var contract = TemporaryContractFixtureIT.exportPreparation(f);
        contract.put("fixturePurpose", "SYNTHETIC_ANDROID_ROOM_REAL_HTTP");
        contract.put("baseUrl", "http://127.0.0.1:19434/api/v1/");
        contract.put("accessToken", f.tokens.path("accessToken").textValue());
        contract.put("devicePrivateKey", Base64.getEncoder().encodeToString(f.device.key().getPrivate().getEncoded()));
        contract.put("devicePublicKey", Base64.getEncoder().encodeToString(f.device.key().getPublic().getEncoded()));
        Files.writeString(privateDirectory.resolve("android-http-input.json"), SubmissionJson.canonical(contract),
            StandardOpenOption.CREATE_NEW);
    }
}
