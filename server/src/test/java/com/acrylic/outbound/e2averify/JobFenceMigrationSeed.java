package com.acrylic.outbound.e2averify;

import com.acrylic.outbound.common.Ids;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

final class JobFenceMigrationSeed {
    private final JdbcTemplate jdbc;
    private final Map<String, List<Map<String, Object>>> before = new LinkedHashMap<>();
    private final String columns = "BIN_TO_UUID(id) id,job_type,dedupe_key,status,CAST(payload AS CHAR) payload,"
        + "attempt_count,max_attempts,available_at,lease_owner,BIN_TO_UUID(lease_token) lease_token,"
        + "lease_until,last_error,created_at,updated_at";

    JobFenceMigrationSeed(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void seed() {
        for (String status : List.of("PENDING", "RETRY", "RUNNING", "SUCCEEDED", "FAILED")) {
            String key = "legacy-fence:" + status;
            jdbc.update("""
                INSERT INTO background_job(id,job_type,dedupe_key,status,payload,attempt_count,max_attempts,
                  available_at,lease_owner,lease_token,lease_until,last_error,created_at,updated_at)
                VALUES(?,'LEGACY.FENCE',?,?,JSON_OBJECT('original','retain'),1,3,'2026-09-20 00:00:00',
                  ?,?,?,?,'2026-09-19 00:00:00','2026-09-20 00:00:00')
                """, Ids.bytes(UUID.randomUUID()), key, status, status.equals("RUNNING") ? "original-owner" : null,
                status.equals("RUNNING") ? Ids.bytes(UUID.randomUUID()) : null,
                status.equals("RUNNING") ? java.sql.Timestamp.valueOf("2026-09-20 00:00:00") : null, "original-evidence");
            before.put(key, jdbc.queryForList("SELECT " + columns + " FROM background_job WHERE dedupe_key=?", key));
        }
    }

    void verify() {
        before.forEach((key, rows) -> assertThat(jdbc.queryForList("SELECT " + columns
            + " FROM background_job WHERE dedupe_key=?", key)).isEqualTo(rows));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM background_job WHERE job_type='LEGACY.FENCE' AND recovery_epoch IS NULL",
            Integer.class)).isEqualTo(5);
        assertThatThrownBy(() -> jdbc.update("UPDATE background_job SET recovery_epoch=1 WHERE job_type='LEGACY.FENCE'"))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
}
