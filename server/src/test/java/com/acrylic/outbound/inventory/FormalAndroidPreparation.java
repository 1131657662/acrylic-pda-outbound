package com.acrylic.outbound.inventory;

import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signed synthetic preparation; no submission or local scan facts are manufactured here. */
public final class FormalAndroidPreparation {
    public static void write(WebApplicationContext context, Path directory) throws Exception {
        var json=context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class);
        var mvc=MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class)).apply(springSecurity()).build();
        var identity=IdentityFixture.create(context.getBean(JdbcTemplate.class),context.getBean(PasswordEncoder.class),"WAREHOUSE_ADMIN");
        var web=new PublicationDriver(mvc,json,identity);var orders=new OrderTestDriver(web);
        String suffix=UUID.randomUUID().toString().replace("-","").substring(0,7).toUpperCase();
        web.publish(web.stage(SkuFixture.concat(SkuFixture.row("003X"+suffix,"300.5"),SkuFixture.row("571X"+suffix,"301"))));
        orders.policy("0",false);var plan=orders.plan();
        plan.withArray("lines").forEach(line->((ObjectNode)line).put("plannedCount",1));
        String order=orders.create(plan).path("orderId").asText();
        web.data(orders.command("/"+order+"/publish",orders.release("1","1"),UUID.randomUUID().toString()));
        var pda=new PdaTestDriver(web);var tokens=pda.login(pda.register());
        var assignment=pda.data(pda.claim(order,UUID.randomUUID().toString()),tokens);
        String base="/api/v1/pda/assignments/"+assignment.path("id").asText();
        var receipt=pda.data(post(base+"/preparation").header("X-Server-Recovery-Epoch","1"),tokens);
        export(directory,pda,tokens,assignment,receipt,base);
    }

    private static void export(Path directory,PdaTestDriver pda,com.fasterxml.jackson.databind.JsonNode tokens,
        com.fasterxml.jackson.databind.JsonNode assignment,com.fasterxml.jackson.databind.JsonNode receipt,String base) throws Exception {
        var value=SubmissionJson.object().put("fixturePurpose","SYNTHETIC_FORMAL_ANDROID_REAL_HTTP")
            .put("baseUrl","http://127.0.0.1:19434/api/v1/").put("accessToken",tokens.path("accessToken").asText());
        value.set("assignment",assignment);value.set("receipt",receipt);
        value.set("signingKey",pda.data(get("/api/v1/pda/signing-key"),tokens));
        var session=value.putObject("session").put("serverRecoveryEpoch","1");
        for(String field:List.of("userId","deviceId","warehouseId","userAuthVersion","deviceVersion"))session.set(field,tokens.get(field));
        var gzip=value.putObject("gzip");
        for(var part:SubmissionJson.parse(receipt.path("manifest").asText()).path("parts")) {
            String name=part.path("name").asText();
            byte[] bytes=pda.send(get(base+"/content/"+name).param("bundleId",receipt.path("bundleId").asText()),tokens)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            gzip.put(name,Base64.getEncoder().encodeToString(bytes));
        }
        Files.writeString(directory.resolve("formal-http-input.json"),SubmissionJson.canonical(value),StandardOpenOption.CREATE_NEW);
    }
}
