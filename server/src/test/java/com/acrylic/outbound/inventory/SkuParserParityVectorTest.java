package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.BusinessException;
import com.acrylic.outbound.inventory.parser.SkuParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

class SkuParserParityVectorTest {
    @Test @EnabledIfEnvironmentVariable(named="SKU_PARITY_OUTPUT",matches=".+")
    void sameOriginalBytesProduceFixedServerResultsForAndroidConsumer() throws Exception {
        Path output=Path.of(System.getenv("SKU_PARITY_OUTPUT")).toAbsolutePath().normalize();
        Path workspace=Path.of("..").toRealPath();
        assertThat(output.startsWith(workspace.resolve(".local"))).isTrue();assertThat(output).doesNotExist();
        var inputs=SkuParityVectors.inputs();inputs.put("authorized-original-sample",Files.readAllBytes(workspace.resolve("docs/sku.txt")));
        var rows=new ArrayList<Object>();var parser=new SkuParser();
        for(var entry:inputs.entrySet()) {
            var row=new LinkedHashMap<String,Object>();row.put("name",entry.getKey());row.put("bytes",Base64.getEncoder().encodeToString(entry.getValue()));
            try { row.put("preview",parser.parse(entry.getValue())); }
            catch(BusinessException error) { row.put("errorCode",error.code());row.put("message",error.getMessage()); }
            rows.add(row);
        }
        Files.createDirectories(output.getParent());new ObjectMapper().writeValue(output.toFile(),rows);
        assertThat(rows).hasSizeGreaterThan(40);assertThat(Files.size(output)).isPositive();
    }
}
