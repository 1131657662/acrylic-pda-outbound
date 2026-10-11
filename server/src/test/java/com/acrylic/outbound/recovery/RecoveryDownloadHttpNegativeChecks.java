package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.devices.DeviceCrypto;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** HTTP rejection probes restore all target rows; the additional session is explicitly synthetic. */
final class RecoveryDownloadHttpNegativeChecks {
    record Request(String base,String token,JsonNode manifest) { }
    private final JdbcTemplate jdbc;
    private final Path attempt;
    private final Request request;
    private RecoveryDownloadHttpNegativeChecks(JdbcTemplate jdbc,Path attempt,Request request) {
        this.jdbc=jdbc; this.attempt=attempt; this.request=request;
    }
    static void verify(WebApplicationContext app,Path attempt,Request request) throws Exception {
        new RecoveryDownloadHttpNegativeChecks(app.getBean(JdbcTemplate.class),attempt,request).verify();
    }
    private void verify() throws Exception {
        var q=request.manifest().path("preparedTicket").path("temporaryQualification");
        byte[] user=Ids.bytes(UUID.fromString(q.path("recipient").path("userId").asText()));
        byte[] session=jdbc.queryForObject("SELECT id FROM pda_session WHERE access_hash=?",byte[].class,DeviceCrypto.sha(request.token()));
        concurrent(); unadmitted(session); revoked(session); permissions(user); warehouse(user);
    }
    private void concurrent() throws Exception {
        var barrier=new CyclicBarrier(2); var outcomes=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        try(var workers=Executors.newFixedThreadPool(2)) {
            var calls=new ArrayList<Future<JsonNode>>();
            for(int i=0;i<2;i++) calls.add(workers.submit(()->{
                barrier.await(10,TimeUnit.SECONDS); var response=RecoveryReceivedDownloadHttpChecks.get(request.base()+"/manifest",request.token());
                if(response.statusCode()==200) assertThat(response.body()).isEqualTo(Files.readAllBytes(attempt.resolve("download-manifest.json")));
                else { assertThat(response.statusCode()).isEqualTo(503); assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo("CONTROL_COORDINATOR_BUSY"); }
                return SubmissionJson.object().put("status",response.statusCode()).put("bodySha256",RecoveryControlFormat.sha256(response.body()));
            }));
            for(var call:calls) outcomes.add(call.get(60,TimeUnit.SECONDS));
        }
        assertThat(outcomes).anyMatch(v->v.path("status").asInt()==200);
        Files.write(attempt.resolve("download-http-concurrent.json"),RecoveryControlJson.encode(outcomes));
    }
    private void unadmitted(byte[] session) throws Exception {
        byte[] extra=Ids.bytes(UUID.randomUUID()); String token=DeviceCrypto.secret();
        assertThat(jdbc.update("""
            INSERT INTO pda_session SELECT ?,device_id,user_id,user_auth_version,device_version,?,?,access_expires_at,expires_at,NULL,created_at
            FROM pda_session WHERE id=?
            """,extra,DeviceCrypto.sha(token),DeviceCrypto.sha(DeviceCrypto.secret()),session)).isEqualTo(1);
        try {
            assertThat(RecoveryReceivedDownloadHttpChecks.get("/devices/me",token).statusCode()).isEqualTo(200);
            refused(new Rejection("unadmitted-synthetic-session",token,503,"RECOVERY_SESSION_ACTOR"));
        } finally { assertThat(jdbc.update("DELETE FROM pda_session WHERE id=?",extra)).isEqualTo(1); }
    }
    private void revoked(byte[] session) throws Exception {
        assertThat(jdbc.update("UPDATE pda_session SET revoked_at=UTC_TIMESTAMP(3) WHERE id=? AND revoked_at IS NULL",session)).isEqualTo(1);
        try { refused(new Rejection("revoked-session",request.token(),401,"AUTH_EXPIRED")); }
        finally { assertThat(jdbc.update("UPDATE pda_session SET revoked_at=NULL WHERE id=?",session)).isEqualTo(1); }
    }
    private void permissions(byte[] user) throws Exception {
        byte[] permission=jdbc.queryForObject("SELECT id FROM permission WHERE code='pda:scan'",byte[].class);
        var roles=jdbc.query("SELECT role_id FROM user_role WHERE user_id=?",(rs,n)->rs.getBytes(1),user);
        var affected=roles.stream().filter(role->jdbc.queryForObject("SELECT COUNT(*) FROM role_permission WHERE role_id=? AND permission_id=?",Integer.class,role,permission)>0).toList();
        assertThat(affected).isNotEmpty();
        for(byte[] role:affected) assertThat(jdbc.update("DELETE FROM role_permission WHERE role_id=? AND permission_id=?",role,permission)).isEqualTo(1);
        try {
            assertThat(RecoveryReceivedDownloadHttpChecks.get("/devices/me",request.token()).statusCode()).isEqualTo(200);
            refused(new Rejection("revoked-scan-permission",request.token(),503,"RECOVERY_RECIPIENT_PERMISSION"));
        } finally { for(byte[] role:affected) assertThat(jdbc.update("INSERT INTO role_permission VALUES (?,?)",role,permission)).isEqualTo(1); }
    }
    private void warehouse(byte[] user) throws Exception {
        byte[] warehouse=Ids.bytes(UUID.fromString(request.manifest().path("preparedTicket").path("temporaryQualification").path("warehouseId").asText()));
        assertThat(jdbc.update("DELETE FROM user_warehouse WHERE user_id=? AND warehouse_id=?",user,warehouse)).isEqualTo(1);
        try { refused(new Rejection("revoked-warehouse",request.token(),401,"AUTH_EXPIRED")); }
        finally { assertThat(jdbc.update("INSERT INTO user_warehouse VALUES (?,?)",user,warehouse)).isEqualTo(1); }
    }
    private void refused(Rejection rejection) throws Exception {
        var response=RecoveryReceivedDownloadHttpChecks.get(request.base()+"/manifest",rejection.token());
        Files.write(attempt.resolve("download-http-"+rejection.name()+".json"),response.body());
        assertThat(response.statusCode()).as(rejection.name()).isEqualTo(rejection.status());
        assertThat(SubmissionJson.parse(response.body()).path("code").asText()).isEqualTo(rejection.code());
    }
    private record Rejection(String name,String token,int status,String code) { }
}
