package com.acrylic.outbound.e2averify;

import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import static com.acrylic.outbound.submissions.SubmissionJson.*;
import static org.assertj.core.api.Assertions.*;

/** Real authenticated read boundaries plus a source beyond the Android bulk pass limit. Synthetic rows only. */
final class TemporaryBindingHttpChecks {
    private final ContinuationReceiptHttpFixture http;
    private int requests;
    TemporaryBindingHttpChecks(ContinuationReceiptHttpFixture http) { this.http=http; }

    void prepare() throws Exception {
        var db=http.database;
        db.tx.executeWithoutResult(status->{for(int i=1;i<=1001;i++)insert(new UUID(-2L,i),db.id("warehouseId"));});
        UUID warehouse=UUID.randomUUID();
        db.jdbc.update("INSERT INTO warehouse(id,code,name) VALUES(UUID_TO_BIN(?),?,?)",warehouse.toString(),"D04-FOREIGN","Synthetic other warehouse");
        UUID foreign=insert(new UUID(-2L,2000),warehouse);
        UUID client=UUID.fromString(db.jdbc.queryForObject("SELECT BIN_TO_UUID(client_order_id) FROM submission_scope WHERE id=UUID_TO_BIN(?)",
            String.class,db.seed.get("mappedScopeId")));
        var first=read("limit=50",http.token,200);
        assertThat(first.path("items").findValuesAsText("clientOrderId")).doesNotContain(client.toString(),foreign.toString());
        assertThat(first.path("items")).hasSize(50);
        var exact=read("clientOrderId="+client,http.token,200);
        assertThat(exact.path("items")).hasSize(1);
        assertThat(exact.path("items").get(0).path("scopeId").asText()).isEqualTo(db.seed.get("mappedScopeId"));
        assertThat(exact.path("nextCursor").isNull()).isTrue();
        assertThat(read("clientOrderId="+client,http.otherDeviceToken,200).path("items")).isEmpty();
        assertThat(read("clientOrderId="+foreign,http.token,200).path("items")).isEmpty();
        assertThat(read("clientOrderId="+UUID.randomUUID(),http.token,200).path("items")).isEmpty();
        read("clientOrderId=invalid",http.token,400);read("cursor=1-1-1-1-1",http.token,400);
        read("clientOrderId="+client,"invalid",401);
        Files.writeString(db.profile.attempt.resolve("binding-boundaries.marker"),"1001 synthetic newer sources; exact root read; other device/warehouse/input/authentication denied");
    }

    private UUID insert(UUID scope,UUID warehouse) {
        var db=http.database;UUID client=UUID.randomUUID();
        db.jdbc.update("INSERT INTO temporary_work_origin(creator_device_id,client_order_id,authorization_id) VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?))",
            db.seed.get("deviceId"),client.toString(),db.seed.get("authorizationId"));
        db.jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id,work_round,creator_device_id,client_order_id,canonical_order_id,mapping_version,current_submission_id) "
            +"VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),NULL,1,UUID_TO_BIN(?),UUID_TO_BIN(?),NULL,0,NULL)",scope.toString(),warehouse.toString(),db.seed.get("deviceId"),client.toString());
        db.jdbc.update("INSERT INTO temporary_source_mapping(id,warehouse_id,scope_id,mapping_version,canonical_order_id,origin,observed_at) "
            +"VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),0,NULL,'SOURCE_REGISTERED',UTC_TIMESTAMP(3))",UUID.randomUUID().toString(),warehouse.toString(),scope.toString());
        return client;
    }

    private JsonNode read(String query,String token,int expected) throws Exception {
        String path="/api/v1/pda/temporary-source-bindings?"+query;
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+http.app.getWebServer().getPort()+path))
            .timeout(Duration.ofSeconds(20)).header("Authorization","Bearer "+token).header("X-Server-Recovery-Epoch","1").GET().build();
        var response=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofByteArray());
        String name="binding-read-"+(++requests);
        Files.write(http.database.profile.attempt.resolve("http/"+name+".json"),response.body(),StandardOpenOption.CREATE_NEW);
        Files.writeString(http.database.profile.attempt.resolve("http/"+name+"-status.txt"),response.statusCode()+" "+path,StandardOpenOption.CREATE_NEW);
        assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(expected);
        return parse(response.body()).path("data");
    }
}
