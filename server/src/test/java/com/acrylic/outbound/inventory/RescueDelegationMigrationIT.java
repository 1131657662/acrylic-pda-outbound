package com.acrylic.outbound.inventory;

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
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;

/** Real nonempty predecessor upgrade; synthetic facts remain distinct from original device evidence. */
@SpringBootTest(properties="spring.flyway.enabled=false") @AutoConfigureMockMvc
class RescueDelegationMigrationIT {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder passwords;
    private static DriverManagerDataSource source;
    @DynamicPropertySource static void predecessor(DynamicPropertyRegistry properties) {
        String url=System.getenv("OUTBOUND_DB_URL"),user=System.getenv("OUTBOUND_DB_USER");
        assertThat(url).matches("jdbc:mysql://127\\.0\\.0\\.1:19330/acrylic_e2a_cross_20260925_[1-9][0-9]*");
        source=new DriverManagerDataSource(url,user,System.getenv("OUTBOUND_DB_PASSWORD"));
        var db=new JdbcTemplate(source);String database=url.substring(url.lastIndexOf('/')+1),run=database.substring("acrylic_e2a_cross_20260925_".length());
        assertThat(db.queryForObject("SELECT DATABASE()",String.class)).isEqualTo(database);
        assertThat(db.queryForObject("SELECT CURRENT_USER()",String.class)).isEqualTo(user+"@127.0.0.1");
        assertThat(db.queryForObject("SELECT @@datadir",String.class).replace('\\','/')).isEqualToIgnoringCase(
            Path.of("D:/acrylic-pda-outbound/.local/e2a-cross-20260925-"+run+"/data").toAbsolutePath().toString().replace('\\','/')+"/");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()",Integer.class)).isZero();
        Flyway.configure().dataSource(source).target("84").load().migrate();
    }
    @Test void newReadOnlyPermissionAndTablesPreserveAllPreexistingBusinessFacts() throws Exception {
        var f=new SubmissionFixture(new PublicationDriver(mvc,json,IdentityFixture.create(jdbc,passwords,"WAREHOUSE_ADMIN")),jdbc,true,false);
        assertThat(f.submit().path("status").asText()).isEqualTo("SUCCEEDED");var original=checksums();
        var oldPermissions=jdbc.queryForList("SELECT HEX(id),code,name FROM permission ORDER BY id");
        var oldRoles=jdbc.queryForList("SELECT HEX(role_id),HEX(permission_id) FROM role_permission ORDER BY role_id,permission_id");
        assertThat(Flyway.configure().dataSource(source).load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(checksums()).containsAllEntriesOf(original);
        assertThat(jdbc.queryForList("SELECT HEX(id),code,name FROM permission ORDER BY id")).containsAll(oldPermissions);
        assertThat(jdbc.queryForList("SELECT HEX(role_id),HEX(permission_id) FROM role_permission ORDER BY role_id,permission_id")).containsAll(oldRoles);
        assertThat(jdbc.queryForList("SELECT r.code FROM role_permission rp JOIN role r ON r.id=rp.role_id JOIN permission p ON p.id=rp.permission_id "+
            "WHERE p.code LIKE 'rescue:%'",String.class)).containsExactly("SUPERVISOR","SUPERVISOR");
        for(String table:List.of("rescue_request","rescue_request_scope","rescue_grant","rescue_revocation"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).isZero();
        Flyway.configure().dataSource(source).load().validate();
    }
    private Map<String,Long> checksums() {
        var result=new TreeMap<String,Long>();
        for(String table:jdbc.queryForList("SHOW TABLES",String.class)) {
            if(Set.of("flyway_schema_history","permission","role_permission").contains(table))continue;
            assertThat(table).matches("[a-z_]+");
            result.put(table,jdbc.queryForObject("CHECKSUM TABLE `"+table+"`",(rs,n)->rs.getLong("Checksum")));
        }
        return result;
    }
}
