package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.OutboundApplication;
import com.acrylic.outbound.inventory.ReadOnlyNameHttpChecks;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.context.WebApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Fresh authenticated MySQL, nonempty upgrade, real HTTP issuance and application restart. */
@EnabledIfEnvironmentVariable(named="E2A_CROSS_VERIFY",matches="true")
class E2aReadOnlyNameHttpIT {
    @Test void originalNamesStayBoundAcrossReplayAndRestart() throws Exception {
        var p=new E2aCrossSourceProfile();p.connect();Files.createFile(p.attempt.resolve("test-started.marker"));
        var source=new DriverManagerDataSource(p.url,p.user,System.getenv("E2A_CROSS_PASSWORD"));var jdbc=new JdbcTemplate(source);
        p.migrate(jdbc,"34");var seed=E2aV34Seed.seed(jdbc,p.attempt.resolve("storage"));p.migrate(jdbc,"100");
        seedLegacyIndependent(jdbc,seed);
        var queries=List.of("SELECT * FROM temporary_authorization ORDER BY id","SELECT * FROM independent_capture_credential ORDER BY id");
        var oldColumns=queries.stream().map(q->jdbc.queryForList(q).getFirst().keySet()).toList();
        var before=oldRows(jdbc,oldColumns);p.migrate(jdbc);assertThat(oldRows(jdbc,oldColumns)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(operator_name_proof) FROM temporary_authorization",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(operator_name_proof) FROM independent_capture_credential",Long.class)).isZero();
        Files.writeString(p.attempt.resolve("name-proof-nonempty-migration.json"),new ObjectMapper().writeValueAsString(before),StandardOpenOption.CREATE_NEW);
        E2aCrossSourceHttpSetup.signing(p.attempt);ReadOnlyNameHttpChecks.State state;
        try(var context=SpringApplication.run(OutboundApplication.class,options(p,source))) {
            state=ReadOnlyNameHttpChecks.run((WebApplicationContext)context,p.attempt);
        }
        try(var context=SpringApplication.run(OutboundApplication.class,options(p,source))) {
            ReadOnlyNameHttpChecks.resumed((WebApplicationContext)context,state);
        }
        Files.writeString(p.attempt.resolve("test-completed.marker"),"Name display proof: real HTTP/MySQL/replay/restart/rollback; synthetic registered identity only",StandardOpenOption.CREATE_NEW);
    }
    private static List<String> oldRows(JdbcTemplate jdbc,List<Set<String>> columns) {
        var json=new ObjectMapper().findAndRegisterModules();var tables=List.of("temporary_authorization","independent_capture_credential");
        return java.util.stream.IntStream.range(0,2).mapToObj(i->{
            try {return json.writeValueAsString(jdbc.queryForList("SELECT "+String.join(",",columns.get(i))+" FROM "+tables.get(i)+" ORDER BY id"));}
            catch(Exception error){throw new IllegalStateException(error);}
        }).toList();
    }
    private static void seedLegacyIndependent(JdbcTemplate jdbc,Map<String,String> seed) {
        jdbc.update("""
            INSERT INTO independent_capture_credential(id,operator_id,device_id,warehouse_id,device_key_sha256,user_auth_version,
              device_version,recovery_epoch,issued_at,expires_at,signed_credential,credential_sha256)
            VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),REPEAT('a',64),1,1,1,
              UTC_TIMESTAMP(3),DATE_ADD(UTC_TIMESTAMP(3),INTERVAL 1 DAY),'synthetic-old-credential',REPEAT('b',64))
            """,UUID.randomUUID().toString(),seed.get("operatorId"),seed.get("deviceId"),seed.get("warehouseId"));
    }
    private static String[] options(E2aCrossSourceProfile p,DriverManagerDataSource source) {
        return new String[]{"--server.address=127.0.0.1","--server.port="+System.getenv("E2A_HTTP_PORT"),"--spring.flyway.enabled=false",
            "--spring.datasource.url="+source.getUrl(),"--spring.datasource.username="+source.getUsername(),"--spring.datasource.password="+source.getPassword(),
            "--outbound.storage="+p.attempt.resolve("storage"),"--outbound.signing.private-key="+p.attempt.resolve("private/server-private.der"),
            "--outbound.signing.public-key="+p.attempt.resolve("private/server-public.der"),"--outbound.worker.enabled=false",
            "--outbound.bootstrap.enabled=false","--spring.session.jdbc.cleanup-cron=-","--server.servlet.session.cookie.secure=false",
            "--outbound.independent.enabled=true"};
    }
}
