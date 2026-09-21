package com.acrylic.outbound.inventory;

import com.acrylic.outbound.ledgerverify.*;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Callable;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Bridge to existing inventory factories; keeps new device keys for genuine challenge/login recovery. */
public final class LedgerVerifySeed {
    private final TemporaryFixture fixture;
    private final ApplicationContext context;
    private final UserManagementDriver manager;
    public final ObjectNode body;
    public final JsonNode authorization;
    public LedgerVerifySeed(ApplicationContext context,int failedInputs) throws Exception {
        this.context=context;
        LedgerVerifyEnvironment.require();
        var jdbc=context.getBean(JdbcTemplate.class);LedgerVerifyEnvironment.verifyConnected(jdbc);
        var web=new PublicationDriver(context.getBean(MockMvc.class),context.getBean(ObjectMapper.class),
            IdentityFixture.create(jdbc,context.getBean(PasswordEncoder.class),"WAREHOUSE_ADMIN"));
        jdbc.update("INSERT INTO user_role SELECT UUID_TO_BIN(?),id FROM role WHERE code='TEMPORARY_ORDER_CONFIRMER'",web.identity().user().id().toString());
        fixture=new TemporaryFixture(web,jdbc);saveKeys();fixture.stock();authorization=fixture.prepare();
        var management=IdentityFixture.create(jdbc,context.getBean(PasswordEncoder.class),"WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?))",management.user().id().toString(),warehouse());
        manager=new UserManagementDriver(new PublicationDriver(web.mvc(),web.json(),new IdentityFixture(management.user(),web.identity().warehouse())));
        fixture.pda.data(fixture.acknowledge(authorization,fixture.ack(authorization),UUID.randomUUID().toString()),fixture.tokens);
        var manifest=fixture.manifest(authorization);
        body=new LedgerSeedBody(new LedgerSeedBody.Material(fixture.tokens,authorization,manifest,fixture.part(authorization,"mapping"),
            stock(manifest.path("inventorySnapshotId").asText()))).build(failedInputs);
    }

    private void saveKeys() throws Exception {
        var directory=LedgerVerifyEnvironment.ROOT.resolve("seed-keys-r2");Files.createDirectories(directory);
        Files.write(directory.resolve(fixture.device.id()+"-private.der"),fixture.device.key().getPrivate().getEncoded(),StandardOpenOption.CREATE_NEW);
        Files.write(directory.resolve(fixture.device.id()+"-public.der"),fixture.device.key().getPublic().getEncoded(),StandardOpenOption.CREATE_NEW);
    }
    private List<ObjectNode> stock(String snapshot) {
        return fixture.jdbc.query("SELECT BIN_TO_UUID(b.id),b.barcode,s.delivery_code,s.weight_g,s.raw_weight,s.model_snapshot,s.name_snapshot "
            +"FROM inventory_snapshot_bale s JOIN bale b ON b.id=s.bale_id WHERE s.snapshot_id=UUID_TO_BIN(?) ORDER BY b.barcode",
            (rs,n)->SubmissionJson.object().put("baleId",rs.getString(1)).put("barcode",rs.getString(2)).put("deliveryCode",rs.getString(3))
                .put("weightGrams",rs.getString(4)).put("rawWeight",rs.getString(5)).put("model",rs.getString(6)).put("name",rs.getString(7)),snapshot);
    }
    public JsonNode submit() throws Exception { return pda(command("/api/v1/pda/temporary-orders/submissions",body)); }
    public JsonNode abort() throws Exception { return pda(command(base()+"/abort",control().put("reason","保留原事实和实物"))); }
    public byte[] partialUpload() throws Exception {
        byte[] bytes=SubmissionJson.canonical(body).getBytes(StandardCharsets.UTF_8);
        if(bytes.length<=262144)throw new IllegalStateException("Partial scenario needs more than one chunk");
        var start=control().put("payloadSha256",SubmissionJson.sha(body)).put("byteCount",Integer.toString(bytes.length))
            .put("chunkCount",Integer.toString((bytes.length+262143)/262144));
        pda(command(base()+"/upload",start));
        byte[] first=Arrays.copyOf(bytes,262144);
        pda(post(base()+"/chunks/0").header("X-Server-Recovery-Epoch","1").contentType("application/octet-stream").content(first));
        return first;
    }
    public JsonNode relogin() throws Exception { fixture.tokens=fixture.pda.login(fixture.device);return fixture.tokens; }
    public String otherOrder() throws Exception {
        var orders=new OrderTestDriver(fixture.web);return orders.create(orders.plan()).path("orderId").asText();
    }
    public static JsonNode readRetained(ApplicationContext context,String submission) throws Exception {
        var jdbc=LedgerVerifyEnvironment.jdbc();
        var row=jdbc.queryForMap("SELECT u.username,BIN_TO_UUID(c.warehouse_id) warehouse,BIN_TO_UUID(s.freeze_device_id) device "
            +"FROM submission s JOIN app_user u ON u.id=s.operator_id JOIN submission_scope c ON c.id=s.scope_id WHERE s.id=UUID_TO_BIN(?)",submission);
        var user=context.getBean(com.acrylic.outbound.auth.IdentityStore.class).loadUserByUsername(row.get("username").toString());
        var web=new PublicationDriver(context.getBean(MockMvc.class),context.getBean(ObjectMapper.class),new IdentityFixture(user,UUID.fromString(row.get("warehouse").toString())));
        String device=row.get("device").toString();var directory=LedgerVerifyEnvironment.ROOT.resolve("seed-keys-r2");
        var factory=java.security.KeyFactory.getInstance("EC");
        var privateKey=factory.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(Files.readAllBytes(directory.resolve(device+"-private.der"))));
        var publicKey=factory.generatePublic(new java.security.spec.X509EncodedKeySpec(Files.readAllBytes(directory.resolve(device+"-public.der"))));
        var driver=new PdaTestDriver(web);var tokens=driver.login(new PdaTestDriver.Device(device,new java.security.KeyPair(publicKey,privateKey)));
        driver.data(get("/api/v1/pda/submissions/"+submission),tokens);return tokens;
    }
    public ObjectNode userChange() throws Exception { return manager.updateBody(manager.item(user())); }
    public JsonNode changeUser(ObjectNode body) throws Exception { return manager.web().data(manager.command("/"+user(),body,UUID.randomUUID().toString())); }
    public <T> T asPda(Callable<T> work) throws Exception {
        var previous=SecurityContextHolder.getContext();
        var principal=context.getBean(com.acrylic.outbound.pda.PdaSessions.class).authenticate(tokens().path("accessToken").asText());
        var current=SecurityContextHolder.createEmptyContext();
        current.setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));SecurityContextHolder.setContext(current);
        try { return work.call(); } finally { SecurityContextHolder.setContext(previous); }
    }
    public JsonNode pda(MockHttpServletRequestBuilder request) throws Exception { return fixture.pda.data(request,fixture.tokens); }
    public ResultActions sendPda(MockHttpServletRequestBuilder request) throws Exception { return fixture.pda.send(request,fixture.tokens); }
    public ResultActions sendWeb(MockHttpServletRequestBuilder request) throws Exception { return fixture.web.send(request); }
    public JsonNode web(MockHttpServletRequestBuilder request) throws Exception { return fixture.web.data(request); }
    public JsonNode tokens() { return fixture.tokens; }
    public String user() { return fixture.web.identity().user().id().toString(); }
    public String warehouse() { return fixture.web.identity().warehouse().toString(); }
    public String device() { return fixture.device.id(); }
    public String id() { return body.path("submissionId").asText(); }
    public String base() { return "/api/v1/pda/submissions/"+id(); }
    public ObjectNode control() {
        var value=SubmissionJson.object();
        for(String field:List.of("source","assignmentId","continuationId","authorizationId","freezeDeviceId","workRound","sourceRecoveryEpoch"))value.set(field,body.get(field));
        return value;
    }
    public MockHttpServletRequestBuilder command(String path,JsonNode value) {
        return post(path).header("X-Server-Recovery-Epoch","1").contentType("application/json").content(SubmissionJson.canonical(value));
    }
}
