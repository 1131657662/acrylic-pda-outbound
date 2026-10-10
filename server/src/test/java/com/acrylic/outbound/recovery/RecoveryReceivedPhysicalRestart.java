package com.acrylic.outbound.recovery;

import com.acrylic.outbound.auth.IdentityStore;
import com.acrylic.outbound.handover.*;
import com.acrylic.outbound.e2averify.E2aMigrationSnapshot;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.WebApplicationContext;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Separate JVM reloads retained authority pins and original requests without enrolling a new identity. */
public final class RecoveryReceivedPhysicalRestart {
    private static final ObjectMapper JSON=mapper();
    static ObjectMapper mapper() {
        var module=new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(java.security.PublicKey.class,new JsonSerializer<>() {
            @Override public void serialize(java.security.PublicKey key,com.fasterxml.jackson.core.JsonGenerator out,SerializerProvider provider) throws java.io.IOException {
                out.writeStartObject(); out.writeStringField("algorithm",key.getAlgorithm());
                out.writeBinaryField("encoded",key.getEncoded()); out.writeEndObject();
            }
        });
        module.addDeserializer(java.security.PublicKey.class,new JsonDeserializer<>() {
            @Override public java.security.PublicKey deserialize(com.fasterxml.jackson.core.JsonParser in,DeserializationContext context) throws java.io.IOException {
                JsonNode value=in.getCodec().readTree(in);
                try { return java.security.KeyFactory.getInstance(text(value,"algorithm")).generatePublic(
                    new java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(text(value,"encoded")))); }
                catch(java.security.GeneralSecurityException error) { throw new java.io.IOException("Public key reload failed",error); }
            }
        });
        return new ObjectMapper().findAndRegisterModules().registerModule(module);
    }
    static void save(Path attempt,RecoveryReceivedArchiveSetup.Context context) throws Exception {
        var supply=RecoveryClosedTestSupply.of(context.source());
        var value=new Reload(supply.location,supply.checkpoint,supply.pins(),context.request(),context.nativeFacts(),ProcessHandle.current().pid());
        byte[] bytes=JSON.writeValueAsBytes(value);
        Files.write(attempt.resolve("physical-restart-input.json"),bytes);
        Files.writeString(attempt.resolve("physical-restart-input.sha256"),sha256(bytes));
    }
    public static void verify(WebApplicationContext app,Path attempt,Path original) throws Exception {
        byte[] bytes=Files.readAllBytes(attempt.resolve("physical-restart-input.json"));
        assertThat(sha256(bytes)).isEqualTo(Files.readString(attempt.resolve("physical-restart-input.sha256")));
        var reload=JSON.readValue(bytes,Reload.class); assertThat(ProcessHandle.current().pid()).isNotEqualTo(reload.sourcePid());
        var jdbc=app.getBean(JdbcTemplate.class); var authority=new RecoveryClosedAuthority(reload.location(),reload.checkpoint(),reload.pins());
        var transfer=readCanonical(Files.readAllBytes(original.resolve("received-transfer.json")));
        var keys=new RecoveryReceiptKeys(java.util.List.of(java.util.Base64.getDecoder().decode(text(transfer,"serverPublicKey"))));
        var consumer=new RecoveryReceivedTemporaryArchiveConsumer(jdbc.getDataSource(),authority,keys);
        var binding=new HandoverRecoveredTemporaryBinding(consumer,reload.request(),reload.nativeFacts());
        var history=new RecoveryReleaseNumberHistory.Request(reload.request(),keys,
            new RecoveryReleaseNumberHistory.Snapshot(original.resolve("checkpoint-snapshot.json"),"1e23ca32e6c67da0ab039022ef6d4917453ec961a061aa320de3efe602d5864b"),
            new RecoveryReleaseNumberHistory.Snapshot(original.resolve("checkpoint-source-final.json"),"3393867cb13b75b9228ff067a5482b60fac6d0ec1d7caa7f9516a70f1f18694c"));
        var fault=Files.readAllBytes(attempt.resolve("recovered-fault/fault-command.json"));
        var request=new HandoverRecoveredPhysicalReview.Request(fault,binding,new RecoveryReleaseReconciliation.Request(authority,history,keys));
        replay(app,attempt,request);
        Files.writeString(attempt.resolve("physical-restart-result.json"),JSON.writeValueAsString(java.util.Map.of(
            "sourcePid",reload.sourcePid(),"replayPid",ProcessHandle.current().pid(),"originalReceiptBytes",true,"databaseUnchanged",true,"releaseAllowed",false)));
    }
    private static void replay(WebApplicationContext app,Path attempt,HandoverRecoveredPhysicalReview.Request request) throws Exception {
        var jdbc=app.getBean(JdbcTemplate.class); var snapshots=new E2aMigrationSnapshot(jdbc,attempt,attempt.resolve("storage"));
        var original=Files.readAllBytes(attempt.resolve("recovered-fault/approval-receipt.json"));
        var receipt=com.acrylic.outbound.submissions.SubmissionJson.parse(original);
        var actor=app.getBean(IdentityStore.class).loadUserByUsername("recovered-"+text(receipt.path("actor"),"principalId"));
        var before=snapshots.capture();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor,"",actor.getAuthorities()));
        try {
            var result=app.getBean(HandoverRecoveredPhysicalReview.class).approve(
                Files.readAllBytes(attempt.resolve("recovered-fault/approval-command.json")),"physical-restart",request);
            assertThat(result.receipt()).isEqualTo(original); assertThat(result.proof().ordinaryWritesAllowed()).isFalse();
            assertThat(result.proof().facts().path("physicalState").asText()).isEqualTo("MATCHED");
        } finally { SecurityContextHolder.clearContext(); }
        var after=snapshots.capture(); assertThat(after).isEqualTo(before);
        Files.writeString(attempt.resolve("physical-restart-before.json"),before.toPrettyString());
        Files.writeString(attempt.resolve("physical-restart-after.json"),after.toPrettyString());
    }
    public record Reload(RecoveryControlLocation location,Path checkpoint,RecoveryClosedAuthority.Pins pins,
            RecoveryReceivedTemporaryArchiveConsumer.Request request,JsonNode nativeFacts,long sourcePid) { }
    private RecoveryReceivedPhysicalRestart() { }
}
