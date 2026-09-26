package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Nonzero candidate facts remain independent of candidate query/DTO/receipt implementation. */
final class TemporaryCandidateVectors {
    final TemporaryConfirmationFixture source;
    private final TemporaryCandidateHttp http=new TemporaryCandidateHttp();
    private final OrderTestDriver plans;
    private final String warehouse;
    private String firstOrder;
    TemporaryCandidateVectors(WebApplicationContext context) throws Exception {
        source=new TemporaryConfirmationFixture(context,true);plans=new OrderTestDriver(source.source.f.web);
        warehouse=source.source.f.web.identity().warehouse().toString();http.login(source.operator().username());
        populate();
    }
    ObjectNode read() throws Exception {
        var result=SubmissionJson.object().put("payloadUtf8",SubmissionJson.canonical(source.source.body));
        result.set("facts",new TemporaryDecisionFacts(source.jdbc).before(source,source.operator().id()));
        var pages=result.putArray("pages");String cursor="";
        do {
            var page=http.get(path()+"&limit=5&cursor="+URLEncoder.encode(cursor,StandardCharsets.UTF_8));
            pages.add(page);cursor=page.path("nextCursor").asText("");
            assertThat(pages.size()).isLessThan(10);
        } while(!cursor.isEmpty());
        assertThat(pages.get(0).path("total").asInt()).isEqualTo(21);
        assertThat(pages).hasSize(5);return result;
    }
    void unresolvedRemainsBlocked() {
        var before=source.state();var command=source.command("NORMAL_CONFIRM",source.operator());
        assertThatThrownBy(()->source.execute(command,source.operator())).isInstanceOfSatisfying(BusinessException.class,
            error->assertThat(error.code()).isEqualTo("TEMPORARY_DUPLICATES_UNRESOLVED"));
        assertThat(source.state()).isEqualTo(before);
    }
    void staleCursor(ObjectNode vector) throws Exception {
        String cursor=vector.at("/pages/0/nextCursor").asText();
        var revision=plans.revision(plans.plan().put("vehicleNo","修改后仍有产品交集"),"1");
        http.post("/orders/"+firstOrder+"/revisions?warehouseId="+warehouse,revision,UUID.randomUUID().toString());
        http.error(path()+"&limit=5&cursor="+URLEncoder.encode(cursor,StandardCharsets.UTF_8),409);
    }
    private void populate() throws Exception {
        var received=source.jdbc.queryForObject("SELECT received_at FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",
            Timestamp.class,source.source.id()).toInstant();
        var day=received.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        for(int i=0;i<21;i++) {
            var plan=plans.plan().put("vehicleNo",i%2==0?source.source.body.at("/temporaryOrder/vehicleNo").asText():"其他车辆");
            var created=http.post("/orders?warehouseId="+warehouse,plan,UUID.randomUUID().toString());String id=created.path("orderId").asText();
            if(i==0)firstOrder=id;
            if(i<2) {
                var time=i==0?day.minusDays(7).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant():
                    day.plusDays(8).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().minusMillis(1);
                source.jdbc.update("UPDATE outbound_order SET created_at=? WHERE id=UUID_TO_BIN(?)",Timestamp.from(time),id);
            }
            if(i==2)http.post("/orders/"+id+"/publish?warehouseId="+warehouse,plans.release("1","1"),UUID.randomUUID().toString());
            if(i==3)pcStart(id);
        }
    }
    private void pcStart(String id) throws Exception {
        var body=SubmissionJson.object().put("expectedVersion","1").put("writerId",UUID.randomUUID().toString())
            .put("policyVersion","1").put("reason","隔离非零候选消费者验证");
        http.post("/orders/"+id+"/pc/start?warehouseId="+warehouse,body,UUID.randomUUID().toString());
    }
    private String path() { return "/temporary-reviews/"+source.source.id()+"/duplicate-candidates?warehouseId="+warehouse; }
}
