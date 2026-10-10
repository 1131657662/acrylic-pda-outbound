package com.acrylic.outbound.recovery;

import com.acrylic.outbound.common.Ids;
import com.acrylic.outbound.submissions.SubmissionJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static com.acrylic.outbound.recovery.RecoveryControlFormat.*;
import static org.assertj.core.api.Assertions.*;

/** Additional actual SQL owner/set/partial/concurrency probes on a successfully restored isolated target. */
final class RecoveryTemporaryEvidenceConflictChecks {
    static void verify(RecoveryTemporaryEvidenceTargetChecks.Context c,JsonNode complete) throws Exception {
        var id=c.source().identity; var body=SubmissionJson.parse(c.source().read().original("payload")); byte[] bale=Ids.bytes(uuid(body.at("/scans/0"),"baleId"));
        byte[] scope=Ids.bytes(id.scopeId());
        changed(c,"UPDATE bale SET dispatch_cycle=dispatch_cycle+1 WHERE id=?","UPDATE bale SET dispatch_cycle=dispatch_cycle-1 WHERE id=?",bale,"RECOVERY_UNSUBMITTED_OWNER");
        var other=c.jdbc().queryForMap("SELECT scope_id,id FROM source_evidence_set WHERE scope_id<>? AND warehouse_id=? LIMIT 1",scope,Ids.bytes(id.warehouseId()));
        c.jdbc().update("UPDATE bale_commitment SET source_scope_id=?,source_evidence_set_id=? WHERE bale_id=?",other.get("scope_id"),other.get("id"),bale);
        try { var before=c.snapshots().capture(); assertThatThrownBy(() -> c.restorer().restore(c.request())).hasMessage("RECOVERY_EVIDENCE_ROW_CONFLICT");
            assertThat(c.snapshots().capture()).isEqualTo(before); }
        finally { byte[] set=Base64.getDecoder().decode(text(new RecoveryTemporaryEvidenceRows(c.jdbc()).readOriginal(id).path("source_evidence_set").get(0),"id"));
            c.jdbc().update("UPDATE bale_commitment SET source_scope_id=?,source_evidence_set_id=? WHERE bale_id=?",scope,set,bale); }
        extra(c); missingPage(c); stockLock(c,bale); changedStock(c,bale); assertThat(c.snapshots().capture()).isEqualTo(complete);
    }
    private static void changed(RecoveryTemporaryEvidenceTargetChecks.Context c,String apply,String undo,byte[] id,String refusal) throws Exception {
        c.jdbc().update(apply,id);
        try { var before=c.snapshots().capture(); assertThatThrownBy(() -> c.restorer().restore(c.request())).hasMessage(refusal);
            assertThat(c.snapshots().capture()).isEqualTo(before); }
        finally { c.jdbc().update(undo,id); }
    }
    private static void extra(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        var table=RecoveryTemporaryEvidenceTable.OBJECT;
        var row=(com.fasterxml.jackson.databind.node.ObjectNode)new RecoveryTemporaryEvidenceRows(c.jdbc()).readOriginal(c.source().identity).path(table.table).get(0).deepCopy();
        UUID id=UUID.randomUUID(); row.put("id",RecoveryTemporaryEvidenceColumns.uuidBytes(id)).put("original_object_id",RecoveryTemporaryEvidenceColumns.uuidBytes(UUID.randomUUID()));
        c.jdbc().update(table.insert(),RecoveryTemporaryEvidenceColumns.values(row,table,table.columns));
        try { var before=c.snapshots().capture(); assertThatThrownBy(() -> c.restorer().restore(c.request())).hasMessage("RECOVERY_EVIDENCE_ROW_CONFLICT");
            assertThat(c.snapshots().capture()).isEqualTo(before); }
        finally { c.jdbc().update("DELETE FROM source_evidence_object WHERE id=?",Ids.bytes(id)); }
    }
    private static void missingPage(RecoveryTemporaryEvidenceTargetChecks.Context c) throws Exception {
        byte[] id=c.jdbc().queryForObject("SELECT id FROM source_evidence_object WHERE scope_id=? AND kind='MANIFEST_PAGE' LIMIT 1",byte[].class,Ids.bytes(c.source().identity.scopeId()));
        c.jdbc().update("DELETE FROM source_evidence_object WHERE id=?",id);
        assertThat(c.restorer().restore(c.request()).insertedRows()).isEqualTo(1);
    }
    private static void stockLock(RecoveryTemporaryEvidenceTargetChecks.Context c,byte[] bale) throws Exception {
        var executor=Executors.newSingleThreadExecutor(); var ds=c.jdbc().getDataSource(); var latch=new CountDownLatch(1);
        try(var holder=ds.getConnection()) {
            holder.setAutoCommit(false);
            try(var statement=holder.prepareStatement("SELECT bale_id FROM inventory_position WHERE bale_id=? FOR UPDATE")) {
                statement.setBytes(1,bale); statement.executeQuery().close();
            }
            var future=executor.submit(() -> { latch.countDown(); return c.restorer().restore(c.request()); });
            assertThat(latch.await(5,TimeUnit.SECONDS)).isTrue(); Thread.sleep(300);
            assertThat(future.isDone()).isFalse(); holder.rollback(); assertThat(future.get(15,TimeUnit.SECONDS).insertedRows()).isZero();
            Files.writeString(c.attempt().resolve("evidence-stock-lock.txt"),"PASS actual inventory_position row lock blocks competing restoration until release; no normal admission\n");
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(5,TimeUnit.SECONDS)).isTrue(); }
    }
    private static void changedStock(RecoveryTemporaryEvidenceTargetChecks.Context c,byte[] bale) throws Exception {
        var executor=Executors.newSingleThreadExecutor(); var latch=new CountDownLatch(1);
        try(var holder=c.jdbc().getDataSource().getConnection()) {
            holder.setAutoCommit(false);
            try(var statement=holder.prepareStatement("UPDATE inventory_position SET upstream_present=0 WHERE bale_id=?")) {
                statement.setBytes(1,bale); assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            var watching=watchStock(c.jdbc().getDataSource(),latch);
            var future=executor.submit(() -> new RecoveryTemporaryEvidenceRestore(watching,c.supply().authority(),c.keys()).restore(c.request()));
            assertThat(latch.await(10,TimeUnit.SECONDS)).isTrue(); assertThat(future.isDone()).isFalse();
            holder.commit(); var before=c.snapshots().capture();
            assertThatThrownBy(() -> future.get(15,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(RecoveryControlException.class).hasRootCauseMessage("RECOVERY_UNSUBMITTED_UNAVAILABLE");
            assertThat(c.snapshots().capture()).isEqualTo(before);
            Files.writeString(c.attempt().resolve("evidence-stock-current-read.txt"),"PASS concurrent committed stock change after transaction snapshot is refused with all-table/file rollback\n");
        } finally {
            executor.shutdownNow(); assertThat(executor.awaitTermination(5,TimeUnit.SECONDS)).isTrue();
            c.jdbc().update("UPDATE inventory_position SET upstream_present=1 WHERE bale_id=?",bale);
        }
    }
    private static javax.sql.DataSource watchStock(javax.sql.DataSource database,CountDownLatch latch) {
        return (javax.sql.DataSource)java.lang.reflect.Proxy.newProxyInstance(database.getClass().getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(p,m,a) -> {
            Object value=invoke(database,m,a);
            if(!m.getName().equals("getConnection")) return value;
            return java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(cp,cm,ca) -> {
                Object result=invoke(value,cm,ca);
                if(!cm.getName().equals("prepareStatement") || !ca[0].toString().startsWith("SELECT bale_id FROM inventory_position")) return result;
                return java.lang.reflect.Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(sp,sm,sa) -> {
                    if(sm.getName().startsWith("execute")) latch.countDown(); return invoke(result,sm,sa);
                });
            });
        });
    }
    private static Object invoke(Object target,java.lang.reflect.Method method,Object[] arguments) throws Throwable {
        try { return method.invoke(target,arguments); }
        catch(java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
    }
}
