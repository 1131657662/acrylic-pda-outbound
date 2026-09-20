package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class OrderNumberMigrationIT {
    @Test @EnabledIfEnvironmentVariable(named="OUTBOUND_NUMBER_UPGRADE_DB_URL",matches=".+")
    void migrationPreservesEveryV30TableAndLegacyOrderNumber() {
        var source=new DriverManagerDataSource(System.getenv("OUTBOUND_NUMBER_UPGRADE_DB_URL"),
            System.getenv("OUTBOUND_DB_USER"),System.getenv("OUTBOUND_DB_PASSWORD"));
        var jdbc=new JdbcTemplate(source);
        Flyway.configure().dataSource(source).target("30").load().migrate();
        var identity=IdentityFixture.create(jdbc,new BCryptPasswordEncoder(),"WAREHOUSE_ADMIN");
        UUID order=Ids.next();String legacy="OB"+order.toString().replace("-","");
        jdbc.update("""
            INSERT INTO outbound_order(id,warehouse_id,order_no,source,status,created_by,created_at,updated_at)
            VALUES(?,?,?,'WEB','COMPLETED',?,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3))
            """,Ids.bytes(order),Ids.bytes(identity.warehouse()),legacy,Ids.bytes(identity.user().id()));
        var before=checksums(jdbc);

        var migration=Flyway.configure().dataSource(source).load().migrate();

        assertThat(migration.migrationsExecuted).isEqualTo(1);
        var after=checksums(jdbc);
        assertThat(after).containsAllEntriesOf(before).hasSize(before.size()+1);
        assertThat(jdbc.queryForObject("SELECT order_no FROM outbound_order WHERE id=?",String.class,Ids.bytes(order))).isEqualTo(legacy);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_number_counter",Integer.class)).isZero();
    }

    private Map<String,Long> checksums(JdbcTemplate jdbc) {
        var result=new TreeMap<String,Long>();
        for(String table:jdbc.queryForList("SHOW TABLES",String.class)) {
            if(table.equals("flyway_schema_history")) continue;
            if(!table.matches("[a-z_]+")) throw new IllegalStateException("Unexpected verification table");
            result.put(table,jdbc.queryForObject("CHECKSUM TABLE `"+table+"`",(rs,row)->rs.getLong("Checksum")));
        }
        return result;
    }
}
