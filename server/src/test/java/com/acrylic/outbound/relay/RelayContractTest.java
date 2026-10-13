package com.acrylic.outbound.relay;

import com.acrylic.outbound.pda.OfflineSigner;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Checks external pointers and the actual signed-result shape, independently of HTTP fixtures. */
class RelayContractTest {
    private final Path root=Path.of("..","contracts").toAbsolutePath().normalize();

    @Test void everyRelayRouteUsesWebSessionAndResolvableExternalReferences() throws Exception {
        var paths=map(load(root.resolve("openapi.yaml")).get("paths"));
        var expected=Map.of("/original-relays","post","/original-relays/{id}","get",
            "/original-relays/{id}/submission","get","/original-relays/{id}/process","post",
            "/original-relays/{id}/original","get","/original-relays/results/{id}","get");
        for(var route:expected.entrySet()) {
            var path=map(resolve(root.resolve("openapi.yaml"),map(paths.get(route.getKey())).get("$ref").toString()));
            var operation=map(path.get(route.getValue()));
            assertThat(operation.get("security")).isEqualTo(List.of(Map.of("webSession",List.of())));
            assertThat(map(operation.get("responses")).containsKey("200")).isTrue();
            if(route.getValue().equals("post"))assertHeaders(operation);
        }
        for(String file:List.of("relay-paths.yaml","relay-responses.yaml","relay-schemas.yaml"))
            inspect(root.resolve(file),load(root.resolve(file)));
    }

    @Test void signedResultSchemaMatchesEveryActualBindingField() throws Exception {
        var signer=mock(OfflineSigner.class);var store=mock(RelayStore.class);
        when(signer.sign(any())).thenReturn(new OfflineSigner.Signed("test-key","signed-token"));
        var bundle=bundle();var row=new RelayStore.Row(UUID.randomUUID(),bundle.original().warehouse(),bundle.archiveSha256(),"TRUSTED");
        var business=SubmissionJson.object().put("status","SUCCEEDED");
        var actual=SubmissionJson.parse(new RelayReceipts(signer,store).bind(row,bundle,business,UUID.randomUUID()).text());
        assertShape(actual,map(load(root.resolve("relay-schemas.yaml")).get("Result")));
        assertThat(actual.path("businessReceipt")).isEqualTo(business);
        assertThat(actual.path("operatorId").asText()).isEqualTo(bundle.original().user().toString());
    }

    @Test void receptionAndSubmissionAreStrictAndKeepDeletionClosed() throws Exception {
        var schemas=load(root.resolve("relay-schemas.yaml"));
        var reception=map(schemas.get("Reception"));var submission=map(schemas.get("Submission"));
        assertThat(reception.get("additionalProperties")).isEqualTo(false);
        assertThat(submission.get("additionalProperties")).isEqualTo(false);
        assertThat(map(map(reception.get("properties")).get("businessProcessed")).get("const")).isEqualTo(false);
        for(var schema:List.of(reception,submission,map(schemas.get("Result"))))
            assertThat(map(map(schema.get("properties")).get("localDeletionAllowed")).get("const")).isEqualTo(false);
        assertThat(((List<?>)submission.get("required")).contains("businessReceipt")).isTrue();
    }

    private void assertHeaders(Map<?,?> operation) throws Exception {
        var names=new HashSet<Object>();
        for(Object parameter:(List<?>)operation.get("parameters")) {
            var reference=map(parameter).get("$ref").toString();
            names.add(map(resolve(root.resolve("relay-paths.yaml"),reference)).get("name"));
        }
        assertThat(names).contains("warehouseId","X-Server-Recovery-Epoch","Idempotency-Key","X-CSRF-TOKEN");
    }

    private RelayArchive.Bundle bundle() throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));
        var order=SubmissionJson.object().put("source","FORMAL").put("orderId",UUID.randomUUID().toString())
            .put("localOrderId",UUID.randomUUID().toString()).put("deviceId",UUID.randomUUID().toString())
            .put("operatorId",UUID.randomUUID().toString()).put("warehouseId",UUID.randomUUID().toString())
            .put("timeTrust","UNCONFIRMED_EXPORT_TIME");
        var body=SubmissionJson.object().put("submissionId",UUID.randomUUID().toString()).put("assignmentId",order.path("localOrderId").asText())
            .put("freezeDeviceId",order.path("deviceId").asText()).put("workRound","1").put("sourceRecoveryEpoch","1").putNull("continuationId");
        body.putObject("source").put("kind","FORMAL").put("sourceOrderId",order.path("orderId").asText());
        return RelayArchive.read(RelayBundleFixture.build(RelayBundleFixture.files(order,body),generator.generateKeyPair()));
    }

    private void assertShape(JsonNode actual,Map<?,?> schema) throws Exception {
        var properties=map(schema.get("properties"));var fields=new HashSet<String>();actual.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrderElementsOf(properties.keySet().stream().map(Object::toString).toList());
        assertThat(schema.get("required")).isEqualTo(new ArrayList<>(properties.keySet()));
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        for(String field:fields)assertProperty(actual.get(field),map(properties.get(field)));
    }

    private void assertProperty(JsonNode actual,Map<?,?> schema) throws Exception {
        if(schema.containsKey("$ref"))schema=map(resolve(root.resolve("relay-schemas.yaml"),schema.get("$ref").toString()));
        if(schema.containsKey("const"))assertThat(actual.asText()).isEqualTo(schema.get("const").toString());
        if("string".equals(schema.get("type")))assertThat(actual.isTextual()).isTrue();
        if("boolean".equals(schema.get("type")))assertThat(actual.isBoolean()).isTrue();
        if("object".equals(schema.get("type")))assertThat(actual.isObject()).isTrue();
        if(schema.containsKey("pattern"))assertThat(actual.asText()).matches(schema.get("pattern").toString());
        if("uuid".equals(schema.get("format")))assertThat(UUID.fromString(actual.asText()).toString()).isEqualTo(actual.asText());
    }

    private Map<?,?> load(Path path) throws Exception {
        try(var input=Files.newInputStream(path)) { return new Yaml().load(input); }
    }
    private Map<?,?> map(Object value) { assertThat(value).isInstanceOf(Map.class);return (Map<?,?>)value; }
    private void inspect(Path source,Object value) throws Exception {
        if(value instanceof Map<?,?> map) {
            if(map.containsKey("$ref"))assertThat(resolve(source,map.get("$ref").toString())).isNotNull();
            for(Object item:map.values())inspect(source,item);
        } else if(value instanceof List<?> list)for(Object item:list)inspect(source,item);
    }
    private Object resolve(Path source,String reference) throws Exception {
        var parts=reference.split("#",2);var target=parts[0].isEmpty()?source:source.getParent().resolve(parts[0]);Object value=load(target);
        for(String part:parts[1].substring(1).split("/"))value=map(value).get(part.replace("~1","/").replace("~0","~"));
        assertThat(value).as("%s in %s",reference,source).isNotNull();return value;
    }
}
