package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;

/** Synthetic nonempty predecessor data, real MySQL/Flyway; no production database or original identity. */
@SpringBootTest(properties="spring.flyway.enabled=false") @AutoConfigureMockMvc
class FaultWithdrawalMigrationIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private static DriverManagerDataSource source;

    @DynamicPropertySource static void predecessor(DynamicPropertyRegistry properties) {
        String url=System.getenv("OUTBOUND_DB_URL"),user=System.getenv("OUTBOUND_DB_USER");
        assertThat(url).matches("jdbc:mysql://127\\.0\\.0\\.1:19330/acrylic_e2a_cross_20260925_[1-9][0-9]*");
        source=new DriverManagerDataSource(url,user,System.getenv("OUTBOUND_DB_PASSWORD"));
        var db=new JdbcTemplate(source);String database=url.substring(url.lastIndexOf('/')+1);
        String run=database.substring("acrylic_e2a_cross_20260925_".length());
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(database);
        assertThat(db.queryForObject("SELECT CURRENT_USER()",String.class)).isEqualTo(user+"@127.0.0.1");
        assertThat(db.queryForObject("SELECT @@datadir",String.class).replace('\\','/')).isEqualToIgnoringCase(
            Path.of("D:/acrylic-pda-outbound/.local/e2a-cross-20260925-"+run+"/data").toAbsolutePath().toString().replace('\\','/')+"/");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class)).isZero();
        Flyway.configure().dataSource(source).target("82").load().migrate();
    }

    @Test void migrationPreservesLegacyRowsAndBackfillsTheirImmutableOperationRevision() throws Exception {
        var f=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,true,false);
        seedCaptures(f);
        var original=captures();var before=checksums();
        var migrated=Flyway.configure().dataSource(source).load().migrate();
        assertThat(migrated.migrationsExecuted).isEqualTo(2);
        assertThat(captures()).isEqualTo(original);assertThat(checksums()).containsAllEntriesOf(before);
        assertThat(jdbc.queryForList("SELECT operation_revision FROM submitted_capture ORDER BY id",Integer.class)).containsExactly(1,1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fault_withdrawal_stock",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_change_request",Integer.class)).isZero();
        Flyway.configure().dataSource(source).load().validate();
    }
    private void seedCaptures(SubmissionFixture f) {
        UUID scope=Ids.next();var id=UUID.fromString(f.id());
        jdbc.update("INSERT INTO submission_scope(id,warehouse_id,source_order_id) VALUES(?,?,?)",Ids.bytes(scope),
            Ids.bytes(f.web.identity().warehouse()),Ids.bytes(UUID.fromString(f.order)));
        jdbc.update("""
            INSERT INTO submission(id,scope_id,assignment_id,freeze_device_id,operator_id,work_round,source_recovery_epoch,status,created_at,updated_at)
            VALUES(?,?,?,?,?,1,1,'REGISTERED',UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """,Ids.bytes(id),Ids.bytes(scope),Ids.bytes(UUID.fromString(f.assignment.path("id").asText())),
            Ids.bytes(UUID.fromString(f.tokens.path("deviceId").asText())),Ids.bytes(f.web.identity().user().id()));
        for(var row:f.body.path("captures"))jdbc.update("""
            INSERT INTO submitted_capture(id,submission_id,order_id,origin_device_id,device_capture_seq,order_scan_seq,record_sha256)
            VALUES(?,?,?,?,?,?,?)
            """,Ids.bytes(UUID.fromString(row.path("id").asText())),Ids.bytes(id),Ids.bytes(UUID.fromString(f.order)),
            Ids.bytes(UUID.fromString(row.path("originDeviceId").asText())),row.path("deviceCaptureSeq").asLong(),
            row.path("orderScanSeq").asLong(),SubmissionJson.sha(row));
    }
    private List<Map<String,Object>> captures() {
        return jdbc.queryForList("SELECT HEX(id) AS id,HEX(submission_id) AS submission_id,HEX(order_id) AS order_id,"+
            "HEX(origin_device_id) AS device_id,device_capture_seq,order_scan_seq,record_sha256 FROM submitted_capture ORDER BY id");
    }
    private Map<String,Long> checksums() {
        var result=new TreeMap<String,Long>();
        for(String table:jdbc.queryForList("SHOW TABLES",String.class)) {
            if(Set.of("flyway_schema_history","submitted_capture","fault_withdrawal_stock").contains(table))continue;
            assertThat(table).matches("[a-z_]+");
            result.put(table,jdbc.queryForObject("CHECKSUM TABLE `"+table+"`",(rs,n)->rs.getLong("Checksum")));
        }
        return result;
    }
}
