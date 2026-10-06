package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import static org.assertj.core.api.Assertions.*;

final class TemporaryRoundConfirmationRejections {
    private final TemporaryRoundConfirmationHttpFixture f;
    private final TemporaryRoundConfirmationState state;
    TemporaryRoundConfirmationRejections(TemporaryRoundConfirmationHttpFixture f,TemporaryRoundConfirmationState state) { this.f=f;this.state=state; }
    void verify(ObjectNode command) throws Exception { permission(command);owner(command);auditRollback(command); }
    private void permission(ObjectNode command) throws Exception {
        var j=f.http.database.jdbc;var before=state.snapshot();
        byte[] permission=j.queryForObject("SELECT id FROM permission WHERE code='temporary_order:confirm'",byte[].class);
        var roles=j.queryForList("SELECT role_id FROM role_permission WHERE permission_id=?",byte[].class,permission);
        j.update("DELETE FROM role_permission WHERE permission_id=?",permission);
        try { f.reject(command,"PERMISSION_DENIED","confirmation-no-permission");assertThat(state.snapshot()).isEqualTo(before); }
        finally { for(var role:roles)j.update("INSERT INTO role_permission VALUES(?,?)",role,permission); }
    }
    private void owner(ObjectNode command) throws Exception {
        var j=f.http.database.jdbc;String bale=f.http.database.seed.get("reservedBaleId");
        j.update("UPDATE bale SET dispatch_cycle=2 WHERE id=UUID_TO_BIN(?)",bale);
        var before=state.snapshot();
        try { f.reject(command,"CONTINUATION_OWNER_CHANGED","confirmation-owner-changed");assertThat(state.snapshot()).isEqualTo(before); }
        finally { j.update("UPDATE bale SET dispatch_cycle=1 WHERE id=UUID_TO_BIN(?)",bale); }
    }
    private void auditRollback(ObjectNode command) throws Exception {
        var j=f.http.database.jdbc;var before=state.snapshot();
        j.execute("CREATE TRIGGER confirmation_final_failure BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            +"IF NEW.event_type='TEMPORARY_NORMAL_CONFIRM' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='confirmation final audit failure'; END IF; END");
        try {
            var response=f.http.post(command,f.http.token,"confirmation-final-audit-failure");
            assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(500);
            assertThat(state.snapshot()).isEqualTo(before);
        } finally { j.execute("DROP TRIGGER confirmation_final_failure"); }
    }
}
