package com.acrylic.outbound.reports;

import java.io.IOException;
import java.nio.file.*;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.cfg.*;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class SnapshotJson {
    private static ObjectMapper mapper(int maxStringLength) {
        var factory = JsonFactory.builder().streamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(30).maxStringLength(maxStringLength).maxNumberLength(40).build())
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        var mapper = JsonMapper.builder(factory).addModule(new JavaTimeModule())
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return mapper;
    }
    public static ReportSnapshot read(Path input) throws IOException {
        Values.require(Files.size(input) <= 512L * 1024 * 1024, "JSON快照超过512MiB");
        var snapshot = mapper(1000).readValue(input.toFile(), ReportSnapshot.class);
        SnapshotValidator.validate(snapshot);
        return snapshot;
    }
    public static void write(ReportSnapshot snapshot, Path output) throws IOException {
        SnapshotValidator.validate(snapshot);
        mapper(1000).writerWithDefaultPrettyPrinter().writeValue(output.toFile(), snapshot);
    }
    public static ReportSnapshot readConfirmedV1(Path input) throws IOException {
        Values.require(Files.size(input) <= 512L * 1024 * 1024, "JSON快照超过512MiB");
        // Original receipts can carry an opaque JWT; report fields still pass the 1000-character validator.
        return ConfirmedV1Adapter.adapt(mapper(65536).readValue(input.toFile(), ConfirmedV1Input.class));
    }
    public static void writeConfirmedV1(ConfirmedV1Input input, Path output) throws IOException {
        ConfirmedV1Adapter.adapt(input);
        mapper(65536).writerWithDefaultPrettyPrinter().writeValue(output.toFile(), input);
    }
    private SnapshotJson() { }
}
