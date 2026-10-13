package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.relay.RelayBundleFixture;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

final class OriginalRelayHttpFixture {
    final SubmissionFixture original;final JdbcTemplate jdbc;final CorrectionExportHttpClient supervisor,operator;
    final IdentityFixture relayUser;final JsonNode order;final byte[] archive;
    OriginalRelayHttpFixture(WebApplicationContext context) throws Exception {
        jdbc=context.getBean(JdbcTemplate.class);var passwords=context.getBean(PasswordEncoder.class);
        var mvc=MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class)).apply(springSecurity()).build();
        var web=new PublicationDriver(mvc,context.getBean(ObjectMapper.class),IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN"));
        original=new SubmissionFixture(web,jdbc,true,false);relayUser=IdentityFixture.create(jdbc,passwords,"SUPERVISOR");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)",Ids.bytes(relayUser.user().id()),Ids.bytes(web.identity().warehouse()));
        supervisor=CorrectionExportHttpClient.login(context,new ExportModels.Scope(relayUser.user().id(),web.identity().warehouse(),UUID.randomUUID()));
        operator=CorrectionExportHttpClient.login(context,new ExportModels.Scope(web.identity().user().id(),web.identity().warehouse(),UUID.randomUUID()));
        order=SubmissionJson.object().put("source","FORMAL").put("orderId",original.order).put("localOrderId",original.assignment.path("id").asText())
            .put("deviceId",original.device.id()).put("operatorId",web.identity().user().id().toString()).put("warehouseId",web.identity().warehouse().toString())
            .put("timeTrust","UNCONFIRMED_EXPORT_TIME").put("state","FROZEN");
        archive=bundle(original.body);
    }
    byte[] bundle(JsonNode body) throws Exception { return RelayBundleFixture.build(RelayBundleFixture.files(order,body),original.device.key()); }
    JsonNode receive(byte[] bytes) throws Exception { return data(supervisor.archive("/original-relays",bytes,UUID.randomUUID()),200); }
    HttpResponse<byte[]> process(String id,UUID key) throws Exception {
        return supervisor.post("/original-relays/"+id+"/process",SubmissionJson.object(),key);
    }
    HttpResponse<byte[]> pdaSubmit(JsonNode body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:19434/api/v1/pda/orders/"+original.order+"/submissions"))
            .header("Authorization","Bearer "+original.tokens.path("accessToken").asText()).header("X-Server-Recovery-Epoch","1")
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(SubmissionJson.canonical(body))).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    JsonNode data(HttpResponse<byte[]> response,int expected) { return CorrectionExportHttpClient.data(response,expected); }
}
