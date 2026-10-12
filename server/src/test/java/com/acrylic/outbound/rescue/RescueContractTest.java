package com.acrylic.outbound.rescue;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import static org.assertj.core.api.Assertions.*;

/** Parses the public contract with the existing server YAML dependency, including external pointers. */
class RescueContractTest {
    private final Path root=Path.of("..","contracts").toAbsolutePath().normalize();
    private Map<?,?> load(Path path) throws Exception {
        try(var input=Files.newInputStream(path)) { return new Yaml().load(input); }
    }
    @Test void publicRescuePathsHaveResolvableSchemasAndEstablishedSecuritySchemes() throws Exception {
        var spec=load(root.resolve("openapi.yaml"));var paths=(Map<?,?>)spec.get("paths");
        for(String route:List.of("/pda/rescue-requests","/pda/rescue-requests/{id}","/rescue-requests/{id}",
            "/rescue-requests/{id}/approve","/rescue-grants/{id}/revoke")) {
            var reference=((Map<?,?>)paths.get(route)).get("$ref").toString();
            assertThat(resolve(root.resolve("openapi.yaml"),reference)).isInstanceOf(Map.class);
        }
        var schemes=(Map<?,?>)((Map<?,?>)spec.get("components")).get("securitySchemes");
        assertThat(schemes.containsKey("pdaBearer")).isTrue();assertThat(schemes.containsKey("webSession")).isTrue();
        inspect(root.resolve("rescue-paths.yaml"),load(root.resolve("rescue-paths.yaml")));
        inspect(root.resolve("rescue-schemas.yaml"),load(root.resolve("rescue-schemas.yaml")));
    }
    private void inspect(Path source,Object value) throws Exception {
        if(value instanceof Map<?,?> map) {
            if(map.containsKey("$ref")) assertThat(resolve(source,map.get("$ref").toString())).isNotNull();
            for(var item:map.values()) inspect(source,item);
        } else if(value instanceof List<?> list) for(var item:list) inspect(source,item);
    }
    private Object resolve(Path source,String reference) throws Exception {
        var parts=reference.split("#",2);Path target=parts[0].isEmpty()?source:source.getParent().resolve(parts[0]);
        Object value=load(target);
        for(String part:parts[1].substring(1).split("/")) value=((Map<?,?>)value).get(part.replace("~1","/").replace("~0","~"));
        return value;
    }
}
