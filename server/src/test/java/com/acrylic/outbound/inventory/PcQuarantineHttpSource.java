package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/** Synthetic isolation stock bootstrap; original STOP/PLAN/REVIEW/APPLY are actual session/CSRF HTTP. */
public final class PcQuarantineHttpSource {
    public final String warehouse,order,bale,makerUser,checkerUser;
    public final Path evidence;
    public final JdbcTemplate jdbc;
    private final CorrectionAttachmentHttp maker,checker;
    private final PcExceptionFixture fixture;
    public PcQuarantineHttpSource(WebApplicationContext context,Path attempt,JdbcTemplate database) throws Exception {
        jdbc=database; evidence=attempt.resolve("pc-quarantine-http"); Files.createDirectory(evidence);
        var passwords=context.getBean(PasswordEncoder.class); var identity=IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN");
        var api=new PublicationDriver(MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class)).apply(springSecurity()).build(),context.getBean(ObjectMapper.class),identity);
        fixture=new PcExceptionFixture(api); var supervisor=fixture.user(jdbc,passwords,"SUPERVISOR");
        warehouse=identity.warehouse().toString(); order=fixture.pc.order; makerUser=identity.user().username(); checkerUser=supervisor.identity().user().username();
        jdbc.update("INSERT INTO user_role SELECT ?,id FROM role WHERE code='WAREHOUSE_ADMIN'",Ids.bytes(supervisor.identity().user().id()));
        maker=new CorrectionAttachmentHttp(makerUser,evidence.resolve("maker-http.log")); checker=new CorrectionAttachmentHttp(checkerUser,evidence.resolve("checker-http.log"));
        maker.post(path("stop"),UUID.randomUUID().toString(),fixture.stopBody(),200);
        checker.post(path("plan"),UUID.randomUUID().toString(),fixture.plan(makerUser,"RELEASE","RELEASE","QUARANTINE").put("outcome","CANCEL"),200);
        checker.post(path("review"),UUID.randomUUID().toString(),fixture.decision("APPROVE"),200);
        maker.post(path("apply"),UUID.randomUUID().toString(),fixture.application(),200);
        bale=jdbc.queryForObject("SELECT BIN_TO_UUID(bale_id) FROM bale_commitment WHERE order_id=UUID_TO_BIN(?) AND pc_disposition_id IS NOT NULL",String.class,order);
        var source=SubmissionJson.object().put("warehouseId",warehouse).put("orderId",order).put("baleId",bale)
            .put("makerUsername",makerUser).put("checkerUsername",checkerUser).put("password","Test-password-123");
        Files.writeString(evidence.resolve("browser-input.json"),SubmissionJson.canonical(source));
    }
    private String path(String action) { return "/orders/"+order+"/pc/exception/"+action+"?warehouseId="+warehouse; }
    public JsonNode originals() {
        var result=SubmissionJson.object();
        for(String table:java.util.List.of("pc_entry","pc_exception_case","pc_exception_plan","pc_exception_review","pc_disposition","pc_command_receipt")) {
            var rows=result.putArray(table);
            jdbc.queryForList("SELECT * FROM "+table).forEach(row->{
                var encoded=SubmissionJson.object(); new java.util.TreeMap<>(row).forEach((key,value)->{
                    if(value==null) encoded.putNull(key);
                    else encoded.put(key,value instanceof byte[] bytes?java.util.HexFormat.of().formatHex(bytes):value.toString());
                }); rows.add(encoded);
            });
        }
        return result;
    }
}
