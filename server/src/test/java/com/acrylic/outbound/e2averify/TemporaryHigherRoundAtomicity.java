package com.acrylic.outbound.e2averify;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

final class TemporaryHigherRoundAtomicity {
    private final TemporaryRoundConfirmationHttpFixture f;
    TemporaryHigherRoundAtomicity(TemporaryRoundConfirmationHttpFixture f) { this.f=f; }
    void activation(ObjectNode command) throws Exception {
        var j=f.http.database.jdbc;var state=new TemporaryRoundConfirmationState(f);String before=state.snapshot();
        String auth=j.queryForList("SELECT HEX(id),authorization_epoch FROM temporary_authorization ORDER BY id").toString();
        j.execute("CREATE TRIGGER higher_activation_failure BEFORE INSERT ON operation_event FOR EACH ROW BEGIN "
            +"IF NEW.event_type='TEMPORARY_ACTIVATE_CONTINUATION' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='activation final audit failure'; END IF; END");
        try {
            var response=f.http.post(command,f.http.token,"third-activation-rollback");
            assertThat(response.statusCode()).as(new String(response.body())).isEqualTo(500);
            assertThat(state.snapshot()).isEqualTo(before);
            assertThat(j.queryForList("SELECT HEX(id),authorization_epoch FROM temporary_authorization ORDER BY id").toString()).isEqualTo(auth);
        } finally { j.execute("DROP TRIGGER higher_activation_failure"); }
    }
    byte[] confirmation(ObjectNode command) throws Exception {
        var ready=new CountDownLatch(2);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->confirm(command,"third-confirm-race-a",ready,start));
            var b=pool.submit(()->confirm(command,"third-confirm-race-b",ready,start));
            assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();start.countDown();
            byte[] first=a.get(40,TimeUnit.SECONDS);assertThat(b.get(40,TimeUnit.SECONDS)).isEqualTo(first);return first;
        }
    }
    private byte[] confirm(ObjectNode command,String label,CountDownLatch ready,CountDownLatch start) throws Exception {
        ready.countDown();if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Race did not start");
        return f.http.accepted(command,label);
    }
}
