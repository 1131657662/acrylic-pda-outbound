package com.acrylic.outbound.inventory;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.exports.ExportModels;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.WebApplicationContext;

/** Synthetic task metadata only; no producer, renderer, worker, artifact or business success. */
public record ExportStatusHttpInput(String username, String reviewer, String role, String warehouse,
        String foreignUsername, List<Task> tasks) {
    public record Task(String id, String state, String format) { }

    public static ExportStatusHttpInput prepare(WebApplicationContext context) throws Exception {
        var jdbc = context.getBean(JdbcTemplate.class); var passwords = context.getBean(PasswordEncoder.class);
        var identity = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
        var input = new Inputs(jdbc, context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class), identity);
        var tasks = new ArrayList<Task>();
        for (int i = 0; i < 25; i++) tasks.add(task(input, "FAILED"));
        for (String state : List.of("PENDING", "RUNNING", "RETRY", "SUCCEEDED", "EXPIRED",
                "INVALIDATED_PAYLOAD", "INVALIDATED_NULL", "INVALIDATED_JOB_EXPIRED")) tasks.add(task(input, state));
        var foreign = IdentityFixture.create(jdbc, passwords, "WAREHOUSE_ADMIN");
        jdbc.update("INSERT INTO user_warehouse VALUES(?,?)", com.acrylic.outbound.common.Ids.bytes(foreign.user().id()),
            com.acrylic.outbound.common.Ids.bytes(identity.warehouse()));
        String user = identity.user().id().toString();
        String role = jdbc.queryForObject("SELECT BIN_TO_UUID(role_id) FROM user_role WHERE user_id=UUID_TO_BIN(?)", String.class, user);
        return new ExportStatusHttpInput(identity.user().getUsername(), user, role, identity.warehouse().toString(),
            foreign.user().getUsername(), List.copyOf(tasks));
    }

    private record Inputs(JdbcTemplate jdbc, com.fasterxml.jackson.databind.ObjectMapper json, IdentityFixture identity) { }

    private static Task task(Inputs input, String state) throws Exception {
        String id = UUID.randomUUID().toString();
        var format = state.equals("FAILED") || state.startsWith("INVALIDATED") ? ExportModels.Format.XLSX : ExportModels.Format.PDF;
        String jobState = state.equals("EXPIRED") || state.startsWith("INVALIDATED") ? "PENDING" : state;
        boolean expired = state.equals("EXPIRED") || state.endsWith("_EXPIRED");
        var payload = new ExportModels.Payload(new ExportModels.Scope(input.identity().user().id(), input.identity().warehouse(), UUID.randomUUID()),
            format, "ORIGINAL_CONFIRMED", state.equals("INVALIDATED_PAYLOAD") ? "0" : "1", "0".repeat(64),
            UUID.randomUUID().toString(), "SYNTHETIC-STATUS-ONLY", 1, "2026-10-20", format.template(),
            expired ? java.time.Instant.parse("2020-01-01T00:00:00Z") : java.time.Instant.now().plus(java.time.Duration.ofDays(7)),
            null, null, 1, null, 1L);
        boolean running = jobState.equals("RUNNING");
        input.jdbc().update("""
            INSERT INTO background_job(id,job_type,dedupe_key,status,payload,attempt_count,max_attempts,available_at,
              lease_owner,lease_token,lease_until,created_at,updated_at,recovery_epoch)
            VALUES(UUID_TO_BIN(?),'REPORT_RENDER_V1',?,?,?,?,3,UTC_TIMESTAMP(3),?,UUID_TO_BIN(?),?,
              '2026-10-20 00:00:00.000',UTC_TIMESTAMP(3),?)
            """, id, "synthetic-export-status:" + id, jobState, input.json().writeValueAsString(payload),
            Set.of("RUNNING", "SUCCEEDED", "FAILED").contains(jobState) ? 1 : 0,
            running ? "synthetic-status-only" : null, running ? UUID.randomUUID().toString() : null,
            running ? java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(3600)) : null,
            state.equals("INVALIDATED_NULL") ? null : state.equals("INVALIDATED_JOB_EXPIRED") ? "2" : "1");
        return new Task(id, state.startsWith("INVALIDATED") ? "INVALIDATED" : state, format.name());
    }

    public String path() { return "/exports?warehouseId=" + warehouse; }
    public List<String> selected(String status, String format) {
        return tasks.stream().filter(t -> status == null || t.state().equals(status))
            .filter(t -> format == null || t.format().equals(format)).map(Task::id).sorted(Comparator.reverseOrder()).toList();
    }
}
