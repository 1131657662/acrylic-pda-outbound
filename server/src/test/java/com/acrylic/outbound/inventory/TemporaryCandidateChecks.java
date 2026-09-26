package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Synthetic preparation; actual internal success and authenticated formal API calls. */
public final class TemporaryCandidateChecks {
    private final WebApplicationContext context;
    private TemporaryConfirmationFixture f;private TemporaryCandidateHttp http;private OrderTestDriver plans;
    private String warehouse;private String order;private String sourceOrder;
    public TemporaryCandidateChecks(WebApplicationContext context) { this.context=context; }
    public void verify(Path attempt) throws Exception {
        f=new TemporaryConfirmationFixture(context,false);f.execute(f.command("NORMAL_CONFIRM",f.operator()),f.operator());
        warehouse=f.source.f.web.identity().warehouse().toString();plans=new OrderTestDriver(f.source.f.web);
        sourceOrder=f.jdbc.queryForObject("SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)",String.class,f.source.id());
        http=new TemporaryCandidateHttp();http.login(f.operator().username());var original=f.state();
        createPublishRevise();datesAndPermissions();pcStart();
        assertThat(f.state()).as("Original TEMP result, receipt, mapping/accounting and evidence stay unchanged").isEqualTo(original);
        candidateIsolation();pendingAndPaging();exportBrowser(attempt);
    }
    private void createPublishRevise() throws Exception {
        var plan=plans.plan().put("vehicleNo",f.source.body.at("/temporaryOrder/vehicleNo").asText());
        String key=UUID.randomUUID().toString();var created=http.post(path(""),plan,key);order=created.path("orderId").asText();
        hint(created);assertThat(http.post(path(""),plan,key)).isEqualTo(created);
        var result=candidates("");assertThat(result.at("/candidates/total").asLong()).isEqualTo(1);
        assertThat(result.at("/candidates/items/0/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(result.at("/candidates/items/0/sameVehicle").asBoolean()).isTrue();
        assertThat(result.at("/candidates/items/0/overlappingProducts").asBoolean()).isTrue();
        assertThat(result.path("hasFormalWork").asBoolean()).isFalse();assertThat(result.path("releaseAllowed").asBoolean()).isFalse();
        var published=http.post(path("/"+order+"/publish"),plans.release("1","1"),UUID.randomUUID().toString());hint(published);
        var revision=plans.revision(plan.deepCopy().put("vehicleNo","不同车辆仍按产品提示"),"2").put("expectedPolicyVersion","1");
        var revised=http.post(path("/"+order+"/revisions"),revision,UUID.randomUUID().toString());hint(revised);
        result=candidates("");assertThat(result.path("revision").asInt()).isEqualTo(2);
        assertThat(result.at("/candidates/items/0/sameVehicle").asBoolean()).isFalse();
        assertThat(result.at("/candidates/items/0/overlappingProducts").asBoolean()).isTrue();
        assertThat(http.post(path(""),plan,key)).isEqualTo(created);
    }
    private void datesAndPermissions() throws Exception {
        var received=f.jdbc.queryForObject("SELECT received_at FROM submission_payload WHERE submission_id=UUID_TO_BIN(?)",java.sql.Timestamp.class,f.source.id());
        var range=candidates("").path("range");var start=java.time.LocalDate.parse(range.path("fromDate").asText()).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
        var end=java.time.LocalDate.parse(range.path("throughDate").asText()).plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
        try {
            receivedAt(start);assertThat(candidates("").at("/candidates/total").asInt()).isEqualTo(1);
            receivedAt(end.minusMillis(1));assertThat(candidates("").at("/candidates/total").asInt()).isEqualTo(1);
            receivedAt(end);assertThat(candidates("").at("/candidates/total").asInt()).isZero();
            receivedAt(start.minusMillis(1));assertThat(candidates("").at("/candidates/total").asInt()).isZero();
        } finally { receivedAt(received.toInstant()); }
        http.error(path("/"+order+"/temporary-candidates")+"&fromDate=2026-09-01",400);
        http.error(path("/"+order+"/temporary-candidates")+"&page=0",400);
        http.error(path("/"+sourceOrder+"/temporary-candidates"),409);
        var other=IdentityFixture.create(f.jdbc,f.bean(org.springframework.security.crypto.password.PasswordEncoder.class),"ORDER_VIEWER");
        http.error("/orders/"+order+"/temporary-candidates?warehouseId="+other.warehouse(),404);
        var outsider=new TemporaryCandidateHttp();outsider.login(other.user().username());
        outsider.error("/orders/"+order+"/temporary-candidates?warehouseId="+other.warehouse(),404);
        var denied=IdentityFixture.create(f.jdbc,f.bean(org.springframework.security.crypto.password.PasswordEncoder.class),"INVENTORY_VIEWER");
        f.jdbc.update("INSERT INTO user_warehouse VALUES(?,UUID_TO_BIN(?))",Ids.bytes(denied.user().id()),warehouse);
        var noRead=new TemporaryCandidateHttp();noRead.login(denied.user().username());noRead.error(path("/"+order+"/temporary-candidates"),403);
        new TemporaryCandidateHttp().error(path("/"+order+"/temporary-candidates"),401);
    }
    private void pcStart() throws Exception {
        var draft=http.post(path(""),plans.plan(),UUID.randomUUID().toString());String id=draft.path("orderId").asText();
        var start=SubmissionJson.object().put("expectedVersion","1").put("writerId",UUID.randomUUID().toString())
            .put("policyVersion","1").put("reason","隔离验证PC嵌套发布");String key=UUID.randomUUID().toString();
        var receipt=http.post(path("/"+id+"/pc/start"),start,key);
        assertThat(receipt.at("/temporaryCandidateCheck/path").asText()).isEqualTo("/orders/"+id+"/temporary-candidates");
        assertThat(http.post(path("/"+id+"/pc/start"),start,key)).isEqualTo(receipt);
        var candidates=http.get(path("/"+id+"/temporary-candidates"));
        assertThat(candidates.path("hasFormalWork").asBoolean()).isTrue();assertThat(candidates.at("/candidates/total").asInt()).isEqualTo(1);
    }
    private void pendingAndPaging() throws Exception {
        for(int i=0;i<20;i++) {
            var pending=new TemporarySubmissionFixture(new SubmissionFixture(f.source.f));
            assertThat(f.transport.http(pending,"/pda/temporary-orders/submissions",pending.body).path("status").asText()).isEqualTo("REVIEW_REQUIRED");
        }
        var first=candidates("");var second=candidates("&page=2");
        assertThat(first.at("/candidates/total").asInt()).isEqualTo(21);assertThat(first.at("/candidates/items")).hasSize(20);
        assertThat(second.at("/candidates/items")).hasSize(1);assertThat(first.at("/candidates/items/0/status").asText()).isEqualTo("SUCCEEDED");
        assertThat(second.at("/candidates/items/0/status").asText()).isEqualTo("REVIEW_REQUIRED");
        var ids=new HashSet<String>();first.at("/candidates/items").forEach(row->ids.add(row.path("submissionId").asText()));
        assertThat(ids).hasSize(20).contains(f.source.id()).doesNotContain(second.at("/candidates/items/0/submissionId").asText());
        assertThat(candidates("&page=999").at("/candidates/page").asInt()).isEqualTo(2);
    }
    private void candidateIsolation() throws Exception {
        var other=new TemporaryConfirmationFixture(context,false);
        other.execute(other.command("NORMAL_CONFIRM",other.operator()),other.operator());
        assertThat(candidates("").at("/candidates/total").asInt()).as("Other warehouse success is excluded").isEqualTo(1);
        var queries=f.bean(com.acrylic.outbound.orders.OrderTemporaryCandidateQueries.class);
        var detail=f.bean(com.acrylic.outbound.orders.OrderQueries.class).detail(UUID.fromString(warehouse),UUID.fromString(order),null);
        var unknown=new com.acrylic.outbound.orders.OrderViews.Line("999",UUID.randomUUID().toString(),UUID.randomUUID().toString(),"X","Y",1);
        var view=new com.acrylic.outbound.orders.OrderViews.Detail(detail.order(),detail.plan(),List.of(unknown));
        var day=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        var query=new com.acrylic.outbound.orders.OrderTemporaryCandidateModels.Query(UUID.fromString(warehouse),
            new com.acrylic.outbound.orders.OrderTemporaryCandidateModels.Range(day.minusDays(7),day.plusDays(7)),1);
        assertThat(queries.read(view,query).total()).as("Neither vehicle nor product matches").isZero();
        var plan=detail.plan();var sameVehicle=new com.acrylic.outbound.orders.OrderViews.Revision(plan.id(),plan.revision(),plan.inventorySnapshotId(),
            plan.mappingRevision(),f.source.body.at("/temporaryOrder/vehicleNo").asText(),plan.containerNo(),plan.notes(),plan.plannedTotal(),plan.reason(),plan.createdBy(),plan.createdAt());
        var vehicleOnly=queries.read(new com.acrylic.outbound.orders.OrderViews.Detail(detail.order(),sameVehicle,List.of(unknown)),query);
        assertThat(vehicleOnly.total()).isEqualTo(1);assertThat(vehicleOnly.items().getFirst().overlappingProducts()).isFalse();
    }
    private void exportBrowser(Path attempt) throws Exception {
        var fixture=SubmissionJson.object().put("username",f.operator().username()).put("password","Test-password-123")
            .put("warehouse",warehouse).put("order",order).put("sourceOrder",sourceOrder);
        fixture.set("candidates",candidates(""));Files.writeString(attempt.resolve("private/temporary-candidates-browser.json"),SubmissionJson.canonical(fixture));
    }
    private void hint(JsonNode receipt) {
        assertThat(receipt.at("/temporaryCandidateCheck/path").asText()).isEqualTo("/orders/"+order+"/temporary-candidates");
        assertThat(receipt.at("/temporaryCandidateCheck/advisoryOnly").asBoolean()).isTrue();
    }
    private void receivedAt(java.time.Instant time) { f.jdbc.update("UPDATE submission_payload SET received_at=? WHERE submission_id=UUID_TO_BIN(?)",java.sql.Timestamp.from(time),f.source.id()); }
    private JsonNode candidates(String extra) throws Exception { return http.get(path("/"+order+"/temporary-candidates")+extra); }
    private String path(String suffix) { return "/orders"+suffix+"?warehouseId="+warehouse; }
}
