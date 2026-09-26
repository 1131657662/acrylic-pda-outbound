package com.acrylic.outbound.inventory;

import com.acrylic.outbound.exports.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.acrylic.outbound.temporary.command.CommandOperation;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

public final class TemporaryExportChecks {
    private final WebApplicationContext context;
    public TemporaryExportChecks(WebApplicationContext context) { this.context=context; }
    public void verify(Path output) throws Exception {
        Files.createDirectories(output);assertThat(context.getBeansOfType(CommandOperation.class)).isEmpty();
        var browser=SubmissionJson.object().putArray("fixtures");
        for(boolean shortShipment:List.of(false,true)) {
            var f=new TemporaryExportFixture(context,shortShipment,output);String prefix=shortShipment?"short":"normal";
            for(String format:List.of("XLSX","SCAN_XLSX","PDF"))single(f,format,prefix);
            summary(f,prefix);new TemporaryExportFailureChecks(f).verify();
            browser.addObject().put("username",f.f.operator().getUsername()).put("password","Test-password-123")
                .put("warehouse",f.warehouse).put("order",f.order);
        }
        Files.writeString(output.getParent().resolve("private/temporary-export-browser.json"),SubmissionJson.canonical(browser));
        formalAndPc();
    }
    private void single(TemporaryExportFixture f,String format,String prefix) throws Exception {
        String key=UUID.randomUUID().toString();var response=f.create(format,key);var snapshot=f.snapshot(response);
        var order=snapshot.orders().getFirst();assertThat(order.source()).isEqualTo("TEMP_PDA");
        assertThat(order.sequenceProof().reference()).contains(f.f.source.a.client,f.decision.path("receiptId").asText());
        assertThat(order.originalLines()).extracting(l->l.captureId())
            .containsExactlyElementsOf(f.f.source.body.path("scans").findValuesAsText("captureId"));
        assertThat(order.originalLines()).extracting(l->l.barcode())
            .containsExactlyElementsOf(f.f.source.body.path("scans").findValuesAsText("barcode"));
        assertThat(order.originalLines()).extracting(l->Long.toString(l.deviceCaptureSeq()))
            .containsExactlyElementsOf(f.f.source.body.path("captures").findValuesAsText("deviceCaptureSeq"));
        var repeated=f.create(format,key);assertThat(repeated.path("id")).isEqualTo(response.path("id"));
        byte[] bytes=f.generate(response,prefix+"-"+format.toLowerCase(Locale.ROOT)+(format.equals("PDF")?".pdf":".xlsx"));
        assertThat(bytes).startsWith(format.equals("PDF")?new byte[]{'%','P','D','F'}:new byte[]{'P','K'});
        assertThat(f.snapshot(response)).isEqualTo(snapshot);
        assertThat(f.http.get("/exports/"+response.path("id").asText()+"?warehouseId="+f.warehouse).path("downloadable").asBoolean()).isTrue();
    }
    private void summary(TemporaryExportFixture f,String prefix) throws Exception {
        String day=LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();
        String path="/exports/summary?warehouseId="+f.warehouse+"&fromDate="+day+"&throughDate="+day;
        var summary=f.http.get(path);assertThat(summary.path("orderCount").asInt()).isEqualTo(1);
        assertThat(summary.path("actualCount").asInt()).isEqualTo(2);
        var command=SubmissionJson.object().put("format","XLSX").put("basis","ORIGINAL_CONFIRMED");
        command.putObject("range").put("fromDate",day).put("throughDate",day);
        var view=f.http.post("/exports?warehouseId="+f.warehouse,command,UUID.randomUUID().toString());
        assertThat(f.snapshot(view).orders()).extracting(o->o.id()).containsExactly(f.order);
        f.generate(view,prefix+"-summary.xlsx");
    }
    private void formalAndPc() throws Exception {
        for(boolean pda:List.of(true,false)) {
            var jdbc=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            var identity=IdentityFixture.create(jdbc,context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class),"WAREHOUSE_ADMIN");
            var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(com.acrylic.outbound.common.RequestIdFilter.class))
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
            var api=new PublicationDriver(mvc,context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),identity);
            String order;
            if(pda) { var formal=new SubmissionFixture(api,jdbc,false,false);formal.submit();order=formal.order; }
            else { var pc=new PcFixture(api,false,1,1);pc.start();for(int i=0;i<3;i++)pc.add(i);pc.submit("");order=pc.order; }
            var scope=new ExportModels.Scope(identity.user().id(),identity.warehouse(),UUID.fromString(order));
            // The receipt was just created by this fixture; a cutoff taken before that commit makes the
            // boundary check ("receipt newer than cutoff") fail on timing alone.
            var snapshot=context.getBean(ExportSnapshotReader.class).read(scope,ExportModels.Format.XLSX,Instant.now().plusSeconds(2));
            assertThat(snapshot.orders().getFirst().source()).isEqualTo(pda?"FORMAL_PDA":"FORMAL_PC");
        }
    }
}
