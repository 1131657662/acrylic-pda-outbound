package com.acrylic.outbound.temporary.evidence;

import com.acrylic.outbound.pda.*;
import com.acrylic.outbound.temporary.TemporaryStore;
import com.acrylic.outbound.temporary.draft.DraftPreparationProof;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.springframework.stereotype.Component;
import static com.acrylic.outbound.submissions.SubmissionJson.*;

/** Trusted issuer proofs and original preparation bytes, independent of current stock. */
@Component
public class UnsubmittedSourcePreparation {
    private final OfflineSigner signer;
    private final PreparationStore formal;
    private final TemporaryStore temporary;

    public UnsubmittedSourcePreparation(OfflineSigner signer, PreparationStore formal, TemporaryStore temporary) {
        this.signer = signer; this.formal = formal; this.temporary = temporary;
    }

    JsonNode claims(String token, String audience) {
        require(token != null && token.length() <= 131072, "原授权签名不可核验");
        var parts = token.split("\\.", -1);
        require(parts.length == 3, "原授权签名不可核验");
        var header = parse(Base64.getUrlDecoder().decode(parts[0]));
        return DraftPreparationProof.verify(token, signer.publicKeyFor(text(header, "kid")), audience);
    }

    Parts formal(AssignmentStore.View authorization) {
        var receipt = formal.find(UUID.fromString(authorization.id()));
        require(receipt != null, "原正式准备资料缺失");
        equal(PreparationArchive.sha(receipt.manifest().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            receipt.manifestSha256(), "原准备摘要不符");
        var proof = claims(receipt.signedManifest(), "acrylic-pda-preparation");
        equal(text(proof, "manifestSha256"), receipt.manifestSha256(), "原准备签名摘要不符");
        equal(text(proof, "assignmentId"), authorization.id(), "原准备签名授权不符");
        var manifest = parse(receipt.manifest());
        equal(text(manifest, "assignmentId"), authorization.id(), "原准备授权不符");
        equal(text(manifest, "bundleId"), receipt.bundleId(), "原准备包不符");
        equal(text(manifest, "inventorySnapshotId"), authorization.inventorySnapshotId(), "原库存不符");
        equal(text(manifest, "deviceId"), authorization.deviceId(), "原准备设备不符");
        equal(text(manifest, "operatorId"), authorization.operatorId(), "原准备人员不符");
        equal(text(manifest, "assignmentEpoch"), authorization.epoch(), "原准备代次不符");
        equal(text(manifest, "serverRecoveryEpoch"), authorization.serverRecoveryEpoch(), "原准备恢复代次不符");
        for (var descriptor : array(manifest, "parts")) {
            if ("inventory".equals(text(descriptor, "name")))
                equal(text(descriptor, "sha256"), authorization.inventorySha256(), "原授权库存摘要不符");
        }
        return parts(manifest, name -> formal.content(UUID.fromString(authorization.id()), receipt.bundleId(), name));
    }

    Parts temporary(TemporaryStore.Stored authorization) {
        var receipt = authorization.receipt();
        equal(PreparationArchive.sha(receipt.manifest().getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            receipt.manifestSha256(), "原准备摘要不符");
        var proof = claims(receipt.signedManifest(), "acrylic-pda-temporary-preparation");
        equal(text(proof, "manifestSha256"), receipt.manifestSha256(), "原准备签名摘要不符");
        equal(text(proof, "authorizationId"), authorization.id().toString(), "原准备签名资格不符");
        equal(text(proof, "bundleId"), receipt.bundleId(), "原准备包不符");
        return parts(parse(receipt.manifest()), name -> temporary.content(authorization.id(), name));
    }

    private Parts parts(JsonNode manifest, java.util.function.Function<String, byte[]> content) {
        return new Parts(manifest, rows(part(manifest, "inventory", content.apply("inventory"))),
            rows(availability(part(manifest, "availability", content.apply("availability")))));
    }

    private JsonNode availability(JsonNode value) { return value.isArray() ? value : array(value, "items"); }

    private Map<String, JsonNode> rows(JsonNode values) {
        require(values.isArray(), "原准备行格式错误");
        var result = new HashMap<String, JsonNode>();
        for (var row : values) require(result.put(text(row, "barcode"), row) == null, "原准备条码重复");
        return Map.copyOf(result);
    }

    private JsonNode part(JsonNode manifest, String name, byte[] compressed) {
        var matches = new ArrayList<JsonNode>();
        for (var row : array(manifest, "parts")) if (name.equals(text(row, "name"))) matches.add(row);
        require(matches.size() == 1 && compressed != null, "原准备分片缺失或重复");
        var descriptor = matches.getFirst();
        require(compressed.length <= 33554432 && descriptor.path("compressedBytes").isIntegralNumber()
            && descriptor.path("compressedBytes").longValue() == compressed.length, "原压缩资料长度不符");
        equal(PreparationArchive.sha(compressed), text(descriptor, "compressedSha256"), "原压缩摘要不符");
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] bytes = gzip.readNBytes(33554433);
            require(bytes.length <= 33554432 && descriptor.path("bytes").isIntegralNumber()
                && bytes.length == descriptor.path("bytes").longValue(), "原资料长度不符");
            equal(PreparationArchive.sha(bytes), text(descriptor, "sha256"), "原资料摘要不符");
            return parse(bytes);
        } catch (IOException invalid) { throw bad("原准备分片损坏"); }
    }

    record Parts(JsonNode manifest, Map<String, JsonNode> inventory, Map<String, JsonNode> availability) { }
}
