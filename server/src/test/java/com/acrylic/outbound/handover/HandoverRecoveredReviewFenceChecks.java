package com.acrylic.outbound.handover;

import com.acrylic.outbound.common.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

/** A valid historical supervisor receipt cannot hide a missing live fence or an outer transaction. */
final class HandoverRecoveredReviewFenceChecks {
    static void verify(HandoverRecoveredTemporaryReview review,HandoverRecoveredTemporaryFixture f,JsonNode fault) throws Exception {
        var before=f.snapshots.capture();
        var outer=new TransactionTemplate(new DataSourceTransactionManager(f.jdbc.getDataSource()));
        assertThatThrownBy(()->outer.execute(status->inspect(review,f,fault))).hasMessage("CONTROL_OUTER_TRANSACTION_FORBIDDEN");
        byte[] device=Ids.bytes(f.binding.request().evidence().expected().deviceId());
        assertThatThrownBy(()->f.jdbc.update("UPDATE device SET status='ACTIVE' WHERE id=?",device))
            .hasMessageContaining("ordinary re-enable is forbidden");
        var revoked=f.jdbc.query("SELECT id,revoked_at FROM pda_session WHERE device_id=?",(rs,n)->new Session(rs.getBytes(1),rs.getTimestamp(2)),device);
        f.jdbc.update("UPDATE pda_session SET revoked_at=NULL WHERE device_id=?",device);
        try { assertThatThrownBy(()->inspect(review,f,fault)).isInstanceOf(com.acrylic.outbound.common.BusinessException.class); }
        finally { for(var session:revoked) f.jdbc.update("UPDATE pda_session SET revoked_at=? WHERE id=?",session.revoked(),session.id()); }
        assertThat(f.snapshots.capture()).isEqualTo(before);
    }

    private static Object inspect(HandoverRecoveredTemporaryReview review,HandoverRecoveredTemporaryFixture f,JsonNode fault) {
        return HandoverRecoveredReviewChecks.inspect(review,f.supervisor,fault,f.binding);
    }
    private record Session(byte[] id,java.sql.Timestamp revoked) { }
}
