package com.acrylic.outbound.e2averify;

import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;

/**
 * R07-1 model verification: V41 -> V42 on a non-empty isolated MySQL. It checks the new tables, the CHECK
 * invariants, the append-only triggers, the single-open-case rule and that an activated case cannot be reopened.
 */
@EnabledIfEnvironmentVariable(named = "E2A_CROSS_VERIFY", matches = "true")
class E2aHandoverModelIT {
    @Test void handoverModelAppliesOnNonemptyMysqlAndKeepsItsInvariants() throws Exception {
        var profile = new E2aCrossSourceProfile(); var jdbc = profile.connect();
        Files.createFile(profile.attempt.resolve("test-started.marker"));
        profile.migrate(jdbc, "34"); E2aV34Seed.seed(jdbc, profile.attempt.resolve("storage"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM submission", Integer.class)).isPositive();
        profile.migrate(jdbc, "42");

        var tables = jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() "
            + "AND TABLE_NAME LIKE 'handover%'", String.class);
        assertThat(tables).containsExactlyInAnyOrder("handover_case","handover_evidence","handover_checklist","handover_command_receipt");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM handover_case", Integer.class)).isZero();

        // A valid case row built from the migrated seed facts.
        String insert = """
            INSERT INTO handover_case(id,warehouse_id,scope_id,submission_id,canonical_order_id,source_kind,
              from_work_round,to_work_round,status,stop_state,integrity_state,original_result_state,physical_state,
              open_flag,input_high_watermark,requested_by,requested_at,row_version,created_at,updated_at)
            SELECT UUID_TO_BIN(?),c.warehouse_id,c.id,s.id,c.canonical_order_id,'FORMAL',CAST(s.work_round AS SIGNED),
              CAST(s.work_round AS SIGNED)+1,'REQUESTED','REQUESTED','COMPLETE','NONE_VERIFIED','MATCHED',1,7,
              s.operator_id,UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
            FROM submission_scope c JOIN submission s ON s.id=c.current_submission_id
            WHERE c.canonical_order_id IS NOT NULL LIMIT 1
            """;
        String caseId = java.util.UUID.randomUUID().toString();
        assertThat(jdbc.update(insert, caseId)).as("case insert needs a scope with a canonical order").isEqualTo(1);

        // Same scope cannot open a second case while the first stays open.
        assertThatThrownBy(() -> jdbc.update(insert, java.util.UUID.randomUUID().toString()))
            .as("second open case in the same scope").isInstanceOf(DataAccessException.class);

        // Evidence and checklist rows are append-only, and a case is never deleted.
        jdbc.update("INSERT INTO handover_evidence(id,case_id,kind,device_id,payload_sha256,high_watermark,receipt_bytes,recorded_at) "
            + "VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),'STOP_PROOF',UUID_TO_BIN(?),UNHEX(REPEAT('ab',32)),7,UNHEX('00'),UTC_TIMESTAMP(6))",
            java.util.UUID.randomUUID().toString(), caseId, java.util.UUID.randomUUID().toString());
        assertThatThrownBy(() -> jdbc.update("UPDATE handover_evidence SET high_watermark=8"))
            .as("evidence update is refused").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM handover_evidence"))
            .as("evidence delete is refused").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM handover_case"))
            .as("case delete is refused").isInstanceOf(DataAccessException.class);

        // Same command key can only ever be recorded once.
        jdbc.update("INSERT INTO handover_command_receipt(id,case_id,original_key,command_type,command_sha256,"
            + "canonical_command_bytes,response_bytes,response_sha256,actor_id,expected_json,status,event_id,recorded_at) "
            + "VALUES(UUID_TO_BIN(?),UUID_TO_BIN(?),UUID_TO_BIN(?),'REQUEST',UNHEX(REPEAT('cd',32)),UNHEX('01'),UNHEX('02'),"
            + "UNHEX(REPEAT('ef',32)),UUID_TO_BIN(?),'{}','RECORDED',UUID_TO_BIN(?),UTC_TIMESTAMP(6))",
            java.util.UUID.randomUUID().toString(), caseId, java.util.UUID.randomUUID().toString(),
            java.util.UUID.randomUUID().toString(), java.util.UUID.randomUUID().toString());
        assertThatThrownBy(() -> jdbc.update("DELETE FROM handover_command_receipt"))
            .as("receipt delete is refused").isInstanceOf(DataAccessException.class);

        // An activated case is terminal: no way back to an active state, and the activation receipt is frozen.
        jdbc.update("UPDATE handover_case SET stop_state='DEVICE_ACKED',physical_state='MATCHED',"
            + "original_result_state='NONE_VERIFIED',integrity_state='COMPLETE',activation_receipt=UNHEX('03'),"
            + "activation_sha256=UNHEX(REPEAT('11',32)),activated_at=UTC_TIMESTAMP(6),status='ACTIVATED' WHERE id=UUID_TO_BIN(?)", caseId);
        assertThat(jdbc.queryForObject("SELECT status FROM handover_case WHERE id=UUID_TO_BIN(?)", String.class, caseId))
            .isEqualTo("ACTIVATED");
        assertThatThrownBy(() -> jdbc.update("UPDATE handover_case SET status='APPROVED' WHERE id=UUID_TO_BIN(?)", caseId))
            .as("activated case cannot be reopened").isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE handover_case SET activation_receipt=UNHEX('04') WHERE id=UUID_TO_BIN(?)", caseId))
            .as("activation receipt cannot be rewritten").isInstanceOf(DataAccessException.class);

        // A case that claims success must not be able to activate: SUCCEEDED belongs to the correction path.
        assertThatThrownBy(() -> jdbc.update("UPDATE handover_case SET original_result_state='SUCCEEDED' WHERE id=UUID_TO_BIN(?)", caseId))
            .as("SUCCEEDED cannot satisfy the activation CHECK").isInstanceOf(DataAccessException.class);

        Files.writeString(profile.attempt.resolve("test-completed.marker"),
            "V41->V42 applied on non-empty MySQL; tables, CHECKs, append-only triggers, single open case, terminal activation\n");
    }
}
