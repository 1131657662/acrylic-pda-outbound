package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.ExportModels;
import com.acrylic.outbound.submissions.SubmissionJson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic qualification setup; every relay and withdrawal boundary uses the real HTTP route. */
final class OriginalRelayBoundaryChecks {
    private OriginalRelayBoundaryChecks() { }
    static void verify(WebApplicationContext context,Path evidence) throws Exception {
        temporary(context,evidence);withdrawn(context,evidence);permissions(context);recovery(context,evidence);
        for(boolean collector:List.of(true,false))lockRevocation(context,collector,evidence);
    }
    private static void temporary(WebApplicationContext context,Path evidence) throws Exception {
        var f=new OriginalRelayHttpFixture(context);var temp=new TemporarySubmissionFixture(f.original);
        var identity=f.order.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)identity)
            .put("source","TEMP").put("orderId",temp.a.client).put("localOrderId",temp.a.client);
        byte[] archive=com.acrylic.outbound.relay.RelayBundleFixture.build(
            com.acrylic.outbound.relay.RelayBundleFixture.files(identity,temp.body),f.original.device.key());
        String id=f.receive(archive).path("relayId").asText();var first=f.data(f.process(id,UUID.randomUUID()),200);
        assertThat(first.at("/businessReceipt/status").asText()).isEqualTo("REVIEW_REQUIRED");
        assertThat(f.data(f.process(id,UUID.randomUUID()),200)).isEqualTo(first);
        assertThat(temp.read()).isEqualTo(first.path("businessReceipt"));
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM inventory_ledger l INNER JOIN dispatch_record d ON d.id=l.dispatch_id WHERE d.submission_id=UUID_TO_BIN(?)",
            Integer.class,temp.id())).isZero();
        assertThat(temp.submissions()).isEqualTo(1);
        Files.write(evidence.resolve("temp-review-receipt.json"),SubmissionJson.canonical(first).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void withdrawn(WebApplicationContext context,Path evidence) throws Exception {
        var f=new OriginalRelayHttpFixture(context);String id=f.receive(f.archive).path("relayId").asText();
        var orders=new OrderTestDriver(f.original.web);
        var command=SubmissionJson.object().put("type","FAULT_WITHDRAWAL").put("reason","ISOLATED old relay qualification")
            .put("expectedVersion",orders.detail(f.original.order).at("/order/rowVersion").asText());
        var withdrawal=f.operator.post("/orders/"+f.original.order+"/change-requests",command,UUID.randomUUID());f.data(withdrawal,200);
        assertThat(f.process(id,UUID.randomUUID()).statusCode()).isEqualTo(409);
        f.data(f.operator.post("/orders/"+f.original.order+"/publish",
            orders.release(orders.detail(f.original.order).at("/order/rowVersion").asText(),"1"),UUID.randomUUID()),200);
        assertThat(f.process(id,UUID.randomUUID()).statusCode()).isEqualTo(409);
        assertThat(f.pdaSubmit(f.original.body).statusCode()).isEqualTo(409);
        assertThat(f.supervisor.get("/original-relays/"+id+"/original").body()).isEqualTo(f.archive);
        assertThat(f.original.count("dispatch_record")).isZero();assertThat(f.original.count("inventory_ledger")).isZero();
        Files.write(evidence.resolve("withdrawal.json"),withdrawal.body());
    }
    private static void permissions(WebApplicationContext context) throws Exception {
        var f=new OriginalRelayHttpFixture(context);String id=f.receive(f.archive).path("relayId").asText();
        var other=IdentityFixture.create(f.jdbc,context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class),"SUPERVISOR");
        var cross=CorrectionExportHttpClient.login(context,new ExportModels.Scope(other.user().id(),other.warehouse(),null));
        assertThat(cross.get("/original-relays/"+id).statusCode()).isEqualTo(404);
        assertThat(cross.archive("/original-relays",f.archive,UUID.randomUUID()).statusCode()).isEqualTo(400);
        var bearer=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:19434/api/v1/original-relays/"+id
            +"?warehouseId="+f.original.web.identity().warehouse())).header("Authorization","Bearer "+f.original.tokens.path("accessToken").asText()).GET().build();
        assertThat(java.net.http.HttpClient.newHttpClient().send(bearer,java.net.http.HttpResponse.BodyHandlers.ofByteArray()).statusCode()).isEqualTo(401);
        assertThat(f.original.count("inventory_ledger")).isZero();
    }
    private static void lockRevocation(WebApplicationContext context,boolean collector,Path evidence) throws Exception {
        var f=new OriginalRelayHttpFixture(context);String id=f.receive(f.archive).path("relayId").asText();
        UUID actor=collector?f.original.web.identity().user().id():f.relayUser.user().id();UUID key=UUID.randomUUID();
        try(var connection=f.jdbc.getDataSource().getConnection();var pool=Executors.newSingleThreadExecutor()) {
            connection.setAutoCommit(false);
            try(var lock=connection.prepareStatement("SELECT id FROM app_user WHERE id=? FOR UPDATE")) {
                lock.setBytes(1,Ids.bytes(actor));lock.executeQuery().close();
            }
            var response=pool.submit(()->f.process(id,key));awaitBlocked(f);
            try(var update=connection.prepareStatement("DELETE FROM user_role WHERE user_id=?")) {
                update.setBytes(1,Ids.bytes(actor));assertThat(update.executeUpdate()).isPositive();
            }
            connection.commit();var rejected=response.get(30,TimeUnit.SECONDS);assertThat(rejected.statusCode()).isEqualTo(403);
            Files.write(evidence.resolve((collector?"collector":"relay")+"-lock-revocation.json"),rejected.body());
        }
        assertThat(f.original.count("inventory_ledger")).isZero();assertThat(f.original.count("dispatch_record")).isZero();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM api_idempotency_request WHERE principal_id=? AND route_scope='relay.process' AND idempotency_key=?",
            Integer.class,Ids.bytes(f.relayUser.user().id()),key.toString())).isZero();
    }
    private static void awaitBlocked(OriginalRelayHttpFixture f) throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);int waits=0;
        while(waits==0 && System.nanoTime()<until) {
            waits=f.jdbc.queryForObject("SELECT COUNT(*) FROM performance_schema.data_lock_waits",Integer.class);
            if(waits==0)Thread.sleep(50);
        }
        assertThat(waits).isPositive();
    }
    private static void recovery(WebApplicationContext context,Path evidence) throws Exception {
        var f=new OriginalRelayHttpFixture(context);String id=f.receive(f.archive).path("relayId").asText();
        try {
            f.jdbc.update("UPDATE server_state SET service_mode='RECOVERY_PENDING' WHERE id=1");
            var fenced=f.process(id,UUID.randomUUID());assertThat(fenced.statusCode()).isEqualTo(503);
            Files.write(evidence.resolve("reconciling-refused.json"),fenced.body());
            assertThat(f.supervisor.archive("/original-relays",f.archive,UUID.randomUUID()).statusCode()).isEqualTo(503);
            f.jdbc.update("UPDATE server_state SET service_mode='NORMAL',recovery_epoch=2 WHERE id=1");
            var epoch=f.process(id,UUID.randomUUID());assertThat(epoch.statusCode()).isEqualTo(409);
            Files.write(evidence.resolve("changed-epoch-refused.json"),epoch.body());
        } finally { f.jdbc.update("UPDATE server_state SET service_mode='NORMAL',recovery_epoch=1 WHERE id=1"); }
        assertThat(f.original.count("dispatch_record")).isZero();assertThat(f.original.count("inventory_ledger")).isZero();
        assertThat(f.supervisor.get("/original-relays/"+id+"/original").body()).isEqualTo(f.archive);
    }
}
