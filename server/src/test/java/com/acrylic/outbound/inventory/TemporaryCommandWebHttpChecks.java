package com.acrylic.outbound.inventory;

import com.acrylic.outbound.auth.SessionUser;
import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A3: the temporary command chain over a real HTTP <em>Web session</em> (not a PDA bearer). A supervisor logs in
 * through {@code /auth/web/login}, reads the precondition facts from the A1 read surface, and posts LINK then
 * NORMAL_CONFIRM using exactly what the facts returned. It drives the endpoints directly, so it does not depend
 * on whether the review page currently enables its buttons.
 */
public final class TemporaryCommandWebHttpChecks {
    private static final String BASE = "http://127.0.0.1:19434/api/v1";
    private static final String PASSWORD = "Test-password-123";
    private static final List<String> EXPECTED_KEYS = List.of("canonicalOrderId","mappingVersion","currentSubmissionId",
        "scopeRound","slotVersion","decisionVersion","receivedPayloadSha256","declaredUploadSha256","planSha256",
        "targetRevision","userAuthVersion","candidateContextSha256","evidenceSetId","evidenceSetSha256","verificationId",
        "verificationVersion","verificationAlgorithmVersion","dispositionManifestSha256","dispositionManifestVersion",
        "retainedManifestSha256","retainedManifestVersion");

    private final WebApplicationContext context;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String csrfHeader;private String csrfToken;private String epoch = "";private String loginBody = "";private boolean loggedIn;
    private final Map<String,String> cookies = new LinkedHashMap<>();

    private TemporaryCommandWebHttpChecks(WebApplicationContext context) { this.context = context; }

    public static void verify(WebApplicationContext context) throws Exception { new TemporaryCommandWebHttpChecks(context).check(); }

    private void check() throws Exception {
        var f = new TemporaryConfirmationFixture(context,false);
        SessionUser supervisor = f.supervisor();
        grant(f,supervisor,"temporary_order:link");
        String warehouse = f.source.f.web.identity().warehouse().toString();
        String submission = f.source.id();
        f.jdbc.update("UPDATE outbound_order SET created_at=DATE_SUB(created_at,INTERVAL 30 DAY) "
            + "WHERE source='WEB' AND status='READY' AND warehouse_id=UUID_TO_BIN(?)",warehouse);
        String temp = string(f,"SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)",submission);
        String target = webOrder(f,supervisor,warehouse,temp,"FH-WEB-0001");
        assertThat(candidateTotal(f,warehouse)).isEqualTo(1);

        // Without a Web session the read surface must stay closed.
        assertThat(send(HttpRequest.newBuilder(URI.create(factsUrl(warehouse,submission))).GET().build()).statusCode())
            .as("facts without a session").isEqualTo(401);

        csrf();
        loginBody=new String(login(supervisor.username()).body(),StandardCharsets.UTF_8);loggedIn=true;

        // 1) A1 facts surface: it must return exactly the echoable field set the command body needs.
        var facts = data(get(factsUrl(warehouse,submission)),200,"facts#1");
        var keys = new TreeSet<String>();facts.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("expected","warehouseId","submissionId","workRound","authorizationId",
            "freezeDeviceId","sourceRecoveryEpoch","source");
        var expectedKeys = new TreeSet<String>();facts.path("expected").fieldNames().forEachRemaining(expectedKeys::add);
        assertThat(expectedKeys).containsExactlyInAnyOrderElementsOf(EXPECTED_KEYS);
        assertThat(facts.path("expected").path("userAuthVersion").asText()).isEqualTo(supervisor.authVersion()+"");
        assertThat(facts.path("expected").path("candidateContextSha256").isNull()).isTrue();
        assertThat(facts.path("source").path("kind").asText()).isEqualTo("TEMP");
        assertThat(facts.path("submissionId").asText()).isEqualTo(submission);
        assertThat(facts.path("warehouseId").asText()).isEqualTo(warehouse);

        // 2) LINK over the real Web session, echoing the facts verbatim.
        var linkReceipt = receipt(post("/temporary-commands",command("LINK",facts,candidateDigest(warehouse,submission),
            "人工核对候选后确认受控关联")));
        assertThat(linkReceipt.path("status").asText()).isEqualTo("RECORDED");
        assertThat(linkReceipt.path("commandType").asText()).isEqualTo("LINK");
        assertThat(canonical(f)).isEqualTo(target);
        assertThat(longValue(f,"SELECT mapping_version FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)",submission)).isEqualTo(2);
        assertThat(orderStatus(f,temp)).isEqualTo("LINKED");
        assertThat(orderStatus(f,target)).isEqualTo("READY");

        // 3) Fresh facts after the switch, then NORMAL_CONFIRM.
        var afterLink = data(get(factsUrl(warehouse,submission)),200,"facts#2");
        assertThat(afterLink.path("expected").path("mappingVersion").asText()).isEqualTo("2");
        assertThat(afterLink.path("expected").path("canonicalOrderId").asText()).isEqualTo(target);
        var confirmReceipt = receipt(post("/temporary-commands",command("NORMAL_CONFIRM",afterLink,
            candidateDigest(warehouse,submission),"人工核对无误后确认临时出库")));
        assertThat(confirmReceipt.path("status").asText()).isEqualTo("RECORDED");
        assertThat(confirmReceipt.path("commandType").asText()).isEqualTo("NORMAL_CONFIRM");
        for(String key:List.of("writeConfirmationAvailable","releaseAllowed","stockReleased","mayResumeScanning","localDeletionAllowed"))
            assertThat(confirmReceipt.path("capabilities").path(key).asBoolean()).isFalse();

        // 4) Same landing points as the PDA-session attempt: everything on the linked formal order.
        assertThat(string(f,"SELECT BIN_TO_UUID(order_id) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?)",submission)).isEqualTo(target);
        assertThat(longValue(f,"SELECT COUNT(*) FROM order_result_version WHERE submission_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?)",submission,temp)).isZero();
        assertThat(longValue(f,"SELECT current_result_version FROM outbound_order WHERE id=UUID_TO_BIN(?)",target)).isEqualTo(1);
        assertThat(orderStatus(f,target)).isEqualTo("COMPLETED");
        assertThat(orderStatus(f,temp)).isEqualTo("LINKED");
        assertThat(string(f,"SELECT phase FROM order_submission_slot WHERE order_id=UUID_TO_BIN(?) AND current_submission_id=UUID_TO_BIN(?)",target,submission))
            .isEqualTo("COMPLETED");
        assertThat(longValue(f,"SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?) AND state='DISPATCHED'",submission,target)).isPositive();
        assertThat(longValue(f,"SELECT COUNT(*) FROM bale_commitment WHERE submission_id=UUID_TO_BIN(?) AND order_id<>UUID_TO_BIN(?)",submission,target)).isZero();
        assertThat(longValue(f,"SELECT COUNT(*) FROM dispatch_record WHERE submission_id=UUID_TO_BIN(?) AND order_id=UUID_TO_BIN(?)",submission,target)).isPositive();
        assertThat(string(f,"SELECT status FROM submission WHERE id=UUID_TO_BIN(?)",submission)).isEqualTo("SUCCEEDED");
        assertThat(longValue(f,"SELECT d.decision_version FROM temporary_decision_state d JOIN submission s ON s.scope_id=d.scope_id WHERE s.id=UUID_TO_BIN(?)",submission)).isEqualTo(2);
        assertThat(longValue(f,"SELECT COUNT(*) FROM temporary_command_receipt WHERE submission_id=UUID_TO_BIN(?)",submission)).isEqualTo(2);
    }

    private ObjectNode command(String kind,JsonNode facts,String candidateSha,String reason) throws Exception {
        var body = SubmissionJson.object().put("commandVersion",1).put("commandType",kind)
            .put("commandId",UUID.randomUUID().toString()).put("transportRecoveryEpoch",epoch).put("reason",reason);
        for(String key:List.of("warehouseId","submissionId","workRound","authorizationId","freezeDeviceId","sourceRecoveryEpoch"))
            body.set(key,facts.get(key));
        body.set("source",facts.get("source"));
        var expected=(ObjectNode)facts.get("expected").deepCopy();
        expected.put("candidateContextSha256",candidateSha);
        body.set("expected",expected);
        return body;
    }

    private String candidateDigest(String warehouse,String submission) throws Exception {
        var page=data(get(BASE+"/temporary-reviews/"+submission+"/duplicate-candidates?"+query(warehouse)),200,"candidates");
        assertThat(page.path("total").asInt()).isEqualTo(1);
        return page.path("contextSha256").asText();
    }

    private String factsUrl(String warehouse,String submission) {
        return BASE+"/temporary-commands/facts?"+query(warehouse)+"&submissionId="+submission;
    }
    private String query(String warehouse) { return "warehouseId="+URLEncoder.encode(warehouse,StandardCharsets.UTF_8); }

    private void csrf() throws Exception {
        String keep=loggedIn&&cookies.containsKey("OUTBOUND_SESSION")?cookies.get("OUTBOUND_SESSION"):null;
        var response=send(HttpRequest.newBuilder(URI.create(BASE+"/auth/csrf")).GET().build());
        assertThat(response.statusCode()).isEqualTo(200);
        var envelope=SubmissionJson.parse(response.body());
        epoch=envelope.path("serverRecoveryEpoch").asText(epoch);
        csrfHeader=envelope.at("/data/headerName").asText();csrfToken=envelope.at("/data/token").asText();
        if(keep!=null)cookies.put("OUTBOUND_SESSION",keep);
        assertThat(csrfHeader).isNotEmpty();assertThat(csrfToken).isNotEmpty();
    }
    private HttpResponse<byte[]> login(String username) throws Exception {
        String form="username="+URLEncoder.encode(username,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(PASSWORD,StandardCharsets.UTF_8)
            +"&_csrf="+URLEncoder.encode(csrfToken,StandardCharsets.UTF_8);
        var response=send(request("/auth/web/login").timeout(Duration.ofSeconds(20))
            .header("Content-Type","application/x-www-form-urlencoded").header(csrfHeader,csrfToken)
            .POST(HttpRequest.BodyPublishers.ofString(form,StandardCharsets.UTF_8)).build());
        assertThat(response.statusCode()).as("web login: "+new String(response.body(),StandardCharsets.UTF_8)).isEqualTo(200);
        return response;
    }
    private HttpResponse<byte[]> get(String url) throws Exception { return send(request(url).GET().build()); }
    private byte[] post(String path,ObjectNode body) throws Exception {
        var response=postOnce(path,body);
        if(response.statusCode()==403) { csrf(); response=postOnce(path,body); }
        assertThat(response.statusCode()).as("POST "+path+": "+new String(response.body(),StandardCharsets.UTF_8)).isEqualTo(200);
        return response.body();
    }
    private HttpResponse<byte[]> postOnce(String path,ObjectNode body) throws Exception {
        return send(request(path).timeout(Duration.ofSeconds(30))
            .header("Content-Type","application/json").header(csrfHeader,csrfToken)
            .header("X-Server-Recovery-Epoch",epoch.isEmpty()?"1":epoch)
            .POST(HttpRequest.BodyPublishers.ofByteArray(SubmissionJson.canonical(body).getBytes(StandardCharsets.UTF_8))).build());
    }
    /** The isolated server may mark its session cookie Secure; carry cookies explicitly over plain HTTP. */
    private HttpRequest.Builder request(String path) {
        var builder=HttpRequest.newBuilder(URI.create(path.startsWith("http")?path:BASE+path));
        if(!cookies.isEmpty())builder.header("Cookie",String.join("; ",cookies.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList()));
        return builder;
    }
    private HttpResponse<byte[]> send(HttpRequest request) throws Exception {
        var response=client.send(request,HttpResponse.BodyHandlers.ofByteArray());
        response.headers().allValues("Set-Cookie").forEach(header->{
            int split=header.indexOf('=');
            if(split>0)cookies.put(header.substring(0,split).trim(),header.substring(split+1,header.indexOf(';')>0?header.indexOf(';'):header.length()).trim());
        });
        return response;
    }
    private JsonNode data(HttpResponse<byte[]> response,int status) { return data(response,status,"read"); }
    private JsonNode data(HttpResponse<byte[]> response,int status,String what) {
        assertThat(response.statusCode()).as(what+" -> "+new String(response.body(),StandardCharsets.UTF_8)
            +" cookies="+cookies+" login="+loginBody).isEqualTo(status);
        return SubmissionJson.parse(response.body()).path("data");
    }
    private JsonNode receipt(byte[] body) { return SubmissionJson.parse(body); }

    private void grant(TemporaryConfirmationFixture f,SessionUser actor,String permission) {
        UUID role=Ids.next();
        f.jdbc.update("INSERT INTO role VALUES(?,?,?)",Ids.bytes(role),"WEB_"+role.toString().substring(20),"隔离Web命令权限");
        f.jdbc.update("INSERT INTO role_permission SELECT ?,id FROM permission WHERE code=?",Ids.bytes(role),permission);
        f.jdbc.update("INSERT INTO user_role VALUES(?,?)",Ids.bytes(actor.id()),Ids.bytes(role));
    }
    private String webOrder(TemporaryConfirmationFixture f,SessionUser actor,String warehouse,String template,String orderNo) {
        UUID order=Ids.next(),revision=Ids.next();
        f.jdbc.update("INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,current_revision,created_by,created_at,updated_at) "
            +"VALUES(?,?,?,'WEB','READY',NULL,?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))",Ids.bytes(order),Ids.bytes(UUID.fromString(warehouse)),
            orderNo,Ids.bytes(actor.id()));
        f.jdbc.update("""
            INSERT INTO order_revision(id,order_id,warehouse_id,revision,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,
             notes,planned_total,reason,created_by,created_at)
            SELECT ?,?,?,1,inventory_snapshot_id,mapping_revision,vehicle_no,container_no,notes,planned_total,reason,created_by,created_at
            FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1
            """,Ids.bytes(revision),Ids.bytes(order),Ids.bytes(UUID.fromString(warehouse)),template);
        f.jdbc.update("INSERT INTO order_line(revision_id,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count) "
            +"SELECT ?,delivery_code,mapping_id,product_id,model_snapshot,name_snapshot,planned_count FROM order_line "
            +"WHERE revision_id=(SELECT id FROM order_revision WHERE order_id=UUID_TO_BIN(?) AND revision=1)",Ids.bytes(revision),template);
        f.jdbc.update("UPDATE outbound_order SET current_revision=1 WHERE id=?",Ids.bytes(order));
        return order.toString();
    }
    private int candidateTotal(TemporaryConfirmationFixture f,String warehouse) {
        return f.bean(com.acrylic.outbound.temporary.review.TemporaryDecisionCandidates.class)
            .snapshot(UUID.fromString(warehouse),UUID.fromString(f.source.id()),f.source.body).path("total").asInt();
    }
    private String canonical(TemporaryConfirmationFixture f) {
        return string(f,"SELECT BIN_TO_UUID(canonical_order_id) FROM submission_scope WHERE current_submission_id=UUID_TO_BIN(?)",f.source.id());
    }
    private String orderStatus(TemporaryConfirmationFixture f,String order) {
        return string(f,"SELECT status FROM outbound_order WHERE id=UUID_TO_BIN(?)",order);
    }
    private static String string(TemporaryConfirmationFixture f,String sql,Object... args) { return f.jdbc.queryForObject(sql,String.class,args); }
    private static long longValue(TemporaryConfirmationFixture f,String sql,Object... args) { return f.jdbc.queryForObject(sql,Long.class,args); }
}
