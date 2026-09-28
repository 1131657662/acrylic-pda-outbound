package com.acrylic.outbound.recovery;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * G0-K1 定向反例（测试侧第 1 类 + 第 2 类）。
 *
 * <p>第 1 类—<b>现有代码特征</b>：真实 {@link RecoveryAdmittedDataSource} 代理 + Mockito fake delegate，
 * 记录实际可达的方法、参数与委托次数。它只说明“今天如何委托”，<b>不</b>证明效果门存在，也<b>不</b>证明
 * 真实驱动发生了隐式提交（这里只证明代理把 {@code setAutoCommit} 委托出去了）。
 *
 * <p>第 2 类—<b>候选信息流模型</b>：{@link CandidateGate} 按方法名 + SQL 文本 + prepare 绑定 + 批队列 +
 * 事务状态计算判定，调用者只能给**实际调用形态**，不能预填分类布尔值。SQL 分类是明确标注的**有限样本
 * 分类器**（不是通用解析器）。
 *
 * <p>本类不修改 src/main、不连接数据库、不启动服务、不使用随机等待。
 */
class G0K1JdbcBoundaryTest {

    // ------------------------------------------------------------------ 第 1 类：现有代理特征

    @Test void existingProxyDelegatesConnectionLevelEffectsWithoutAnyGate() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            admitted.commit();
            admitted.setAutoCommit(false);
            admitted.rollback();
        }

        verify(connection).commit();
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        System.out.println("[G0K1-char] connection-level effects delegated with no gate: commit=1 setAutoCommit(false)=1 rollback=1");
    }

    @Test void autoCommitResetIsDelegatedAndTheHookRunsOnlyAfterTheDelegate() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(false);
        when(connection.createStatement()).thenReturn(statement);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            admitted.createStatement().execute("UPDATE t SET v=1");
            admitted.setAutoCommit(true);      // 代理把它委托出去（此处不能证明真实驱动已提交）
        }

        var inOrder = inOrder(statement, connection);
        inOrder.verify(statement).execute("UPDATE t SET v=1");
        inOrder.verify(connection).setAutoCommit(true);
        System.out.println("[G0K1-char] setAutoCommit(true) delegated; state hook runs after the delegate -> gate must precede it");
    }

    @Test void sqlTextIsVisibleOnExecuteButNotOnNoArgPreparedExecutionOrBatch() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(Statement.class); var prepared = mock(PreparedStatement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement("UPDATE t SET v=?")).thenReturn(prepared);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            Statement guarded = admitted.createStatement();
            guarded.execute("UPDATE t SET v=1");
            guarded.addBatch("UPDATE t SET v=2");
            PreparedStatement guardedPrepared = admitted.prepareStatement("UPDATE t SET v=?");
            guardedPrepared.executeUpdate();
            guardedPrepared.addBatch();
            guardedPrepared.executeBatch();
        }

        verify(connection).prepareStatement("UPDATE t SET v=?");
        verify(statement).execute("UPDATE t SET v=1");
        verify(statement).addBatch("UPDATE t SET v=2");
        verify(prepared).executeUpdate();
        verify(prepared).addBatch();
        verify(prepared).executeBatch();
        System.out.println("[G0K1-char] SQL visible on execute(sql)/addBatch(sql); only at prepareStatement(sql); "
            + "NOT visible on no-arg execute*/addBatch/executeBatch");
    }

    @Test void callableStatementIsWrappedAndItsNoArgExecutionCarriesNoSql() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        var callable = mock(CallableStatement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareCall("{call p(?)}")).thenReturn(callable);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            CallableStatement guarded = admitted.prepareCall("{call p(?)}");
            guarded.setInt(1, 1);
            guarded.execute();
            guarded.executeUpdate();
        }

        verify(connection).prepareCall("{call p(?)}");
        verify(callable).setInt(1, 1);
        verify(callable).execute();
        verify(callable).executeUpdate();
        System.out.println("[G0K1-char] CallableStatement: call text visible at prepareCall; no-arg execute*/executeUpdate invisible");
    }

    @Test void updatableResultSetMutatorsAndUnknownResultSetMethodsAreDelegated() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(Statement.class); var result = mock(ResultSet.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT id FROM t")).thenReturn(result);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            ResultSet guarded = admitted.createStatement().executeQuery("SELECT id FROM t");
            guarded.insertRow();
            guarded.updateRow();
            guarded.deleteRow();
            guarded.updateObject(1, "x");
            guarded.moveToInsertRow();      // 不在只读白名单、也不是已知写入方法 ⇒ 必须保守拒绝
            guarded.getWarnings();          // 只读
        }

        verify(result).insertRow();
        verify(result).updateRow();
        verify(result).deleteRow();
        verify(result).updateObject(1, "x");
        verify(result).moveToInsertRow();
        verify(result).getWarnings();
        System.out.println("[G0K1-char] ResultSet mutators AND non-whitelisted methods are delegated by the current proxy "
            + "-> the gate must decide them explicitly (unknown methods refused before delegation)");
    }

    @Test void rawCommitSqlIsDelegatedEvenWhenAutoCommitIsDisabled() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(false);
        when(connection.createStatement()).thenReturn(statement);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            admitted.createStatement().execute("COMMIT");
        }

        verify(statement).execute("COMMIT");
        System.out.println("[G0K1-char] raw COMMIT delegated under autoCommit=false -> commit-class rule must outrank the exemption");
    }

    // ------------------------------------------------------------------ 第 2 类：候选信息流模型

    @Test void prepareBindingIsUsedForNoArgExecutionAndMissingBindingIsRefused() {
        var state = new CandidateGate.ConnState(true, false);
        var bound = new CandidateGate.StatementModel();
        bound.bind("SELECT 1 FROM dual");
        assertThat(gate("executeUpdate", null, bound, state, false, null))
            .isEqualTo(CandidateGate.Decision.NO_GATE);

        var boundWrite = new CandidateGate.StatementModel();
        boundWrite.bind("UPDATE t SET v=1");
        assertThat(gate("executeUpdate", null, boundWrite, state, false, null))
            .isEqualTo(CandidateGate.Decision.GATE);

        var unbound = new CandidateGate.StatementModel();
        assertThat(gate("executeUpdate", null, unbound, state, false, null))
            .as("无参且无 prepare 绑定 ⇒ 不可归类 ⇒ 委托前拒绝")
            .isEqualTo(CandidateGate.Decision.REFUSE_UNCLASSIFIED);
    }

    @Test void batchQueueDrivesTheDecisionAndUnknownItemsAreRefused() {
        var state = new CandidateGate.ConnState(true, false);
        var model = new CandidateGate.StatementModel();

        assertThat(gate("addBatch", "SELECT 1", model, state, false, null)).isEqualTo(CandidateGate.Decision.NO_GATE);
        assertThat(gate("executeBatch", null, model, state, false, null)).isEqualTo(CandidateGate.Decision.NO_GATE);

        assertThat(gate("addBatch", "DELETE FROM t", model, state, false, null)).isEqualTo(CandidateGate.Decision.GATE);
        assertThat(gate("executeBatch", null, model, state, false, null)).as("批中含已知写入 ⇒ 过门")
            .isEqualTo(CandidateGate.Decision.GATE);

        assertThat(gate("addBatch", null, model, state, false, null))
            .as("未知批项 ⇒ 委托前拒绝").isEqualTo(CandidateGate.Decision.REFUSE_UNCLASSIFIED);
    }

    @Test void clearBatchDropsThePreviousClassification() {
        var state = new CandidateGate.ConnState(true, false);
        var model = new CandidateGate.StatementModel();
        gate("addBatch", "DELETE FROM t", model, state, false, null);
        var beforeClear = model.batchSnapshot();
        assertThat(beforeClear).hasSize(1);
        assertThat(CandidateGate.decisionFromQueue(beforeClear)).as("清空前：批中含写入 ⇒ GATE")
            .isEqualTo(CandidateGate.Decision.GATE);

        assertThat(gate("clearBatch", null, model, state, false, null)).isEqualTo(CandidateGate.Decision.NO_GATE);
        assertThat(model.batchSnapshot()).as("清空后队列为空").isEmpty();
        assertThat(gate("executeBatch", null, model, state, false, null))
            .as("清空后不得复用旧分类").isEqualTo(CandidateGate.Decision.NO_GATE);

        // 负控：若清空未生效（仍保留旧队列），同一 executeBatch 会得到 GATE ⇒ 两条断言合起来检出该缺陷
        assertThat(CandidateGate.decisionFromQueue(beforeClear)).isNotEqualTo(CandidateGate.Decision.NO_GATE);
        System.out.println("[G0K1-flow] clearBatch: beforeClear=" + beforeClear + " afterClear=" + model.batchSnapshot());
    }

    @Test void fenceAfterAddBatchIsRecheckedAtExecuteBatchAndMissingRecheckIsDetectable() {
        var state = new CandidateGate.ConnState(true, false);
        var model = new CandidateGate.StatementModel();
        assertThat(gate("addBatch", "SELECT 1", model, state, false, null)).isEqualTo(CandidateGate.Decision.NO_GATE);
        gate("clearBatch", null, model, state, false, null);
        gate("addBatch", "SELECT 1", model, state, false, null);

        // 批已累积为只读；随后发生 F1；执行批时必须在入口重新判定
        var trace = new ArrayList<String>();
        assertThat(gate("executeBatch", null, model, state, true, trace))
            .as("F1 之后的批执行必须被拒（入口重新判定，不看 addBatch 时的结论）")
            .isEqualTo(CandidateGate.Decision.REFUSE_FENCED);
        assertThat(trace.toString()).contains("fenced=true");

        // 负控：撤掉执行时的重新判定，则同一序列会被放行 ⇒ 由严格性属性检出
        var lax = CandidateGate.gateLaxRecheck("executeBatch", null, model, state, true, trace);
        assertThat(lax).as("负控：撤掉重检后反例可达（应被性质断言拒绝）").isEqualTo(CandidateGate.Decision.NO_GATE);
        assertThat(CandidateGate.requireRecheckOnFence("executeBatch", lax, true))
            .as("独立性质断言必须检出缺失的重检").isFalse();
        System.out.println("[G0K1-flow] negative control (missing re-check at executeBatch): lax=" + lax
            + " propertyRequiresFenceRefusal=" + CandidateGate.requireRecheckOnFence("executeBatch", lax, true));
    }

    @Test void rawCommitInManualTransactionOutranksTheExemption() {
        var manual = new CandidateGate.ConnState(false, true);
        assertThat(gate("execute", "COMMIT", new CandidateGate.StatementModel(), manual, false, null))
            .isEqualTo(CandidateGate.Decision.GATE);
        assertThat(gate("execute", "SET AUTOCOMMIT=1", new CandidateGate.StatementModel(), manual, false, null))
            .isEqualTo(CandidateGate.Decision.GATE);
        assertThat(gate("execute", "UPDATE t SET v=1", new CandidateGate.StatementModel(), manual, false, null))
            .as("手动事务内非提交 SQL：效果延迟到 commit 门").isEqualTo(CandidateGate.Decision.NO_GATE);
        assertThat(gate("execute", "/*c*/ UPDATE t", new CandidateGate.StatementModel(), manual, false, null))
            .as("不可归类仍拒绝").isEqualTo(CandidateGate.Decision.REFUSE_UNCLASSIFIED);
    }

    @Test void unknownResultSetMethodIsRefusedBeforeDelegation() {
        var state = new CandidateGate.ConnState(true, false);
        var model = new CandidateGate.StatementModel();
        assertThat(gate("insertRow", null, model, state, false, null)).isEqualTo(CandidateGate.Decision.GATE);
        assertThat(gate("getWarnings", null, model, state, false, null)).isEqualTo(CandidateGate.Decision.NO_GATE);
        assertThat(gate("moveToInsertRow", null, model, state, false, null))
            .as("非白名单且非已知变更方法 ⇒ 委托前拒绝").isEqualTo(CandidateGate.Decision.REFUSE_UNCLASSIFIED);
        assertThat(gate("cancelRowUpdates", null, model, state, false, null))
            .isEqualTo(CandidateGate.Decision.REFUSE_UNCLASSIFIED);
    }

    @Test void oneActualCallCannotGetDifferentDecisionsFromDifferentEnums() {
        assertThat(CandidateGate.canonicalNamesAreUnique()).as("每个 Api 只有一个规范行名").isTrue();
        for (var api : CandidateGate.Api.values()) {
            assertThat(CandidateGate.classifyMethod(api.canonicalName()))
                .as("行名 %s 只能映射回 %s", api.canonicalName(), api).isEqualTo(api);
        }
        // setAutoCommit 的行由参数值决定：true/false 各走一行，且不因测试选行而改变同一实际调用的结论
        assertThat(CandidateGate.rowForSetAutoCommit(true)).isEqualTo(CandidateGate.Api.CONNECTION_SET_AUTOCOMMIT_TRUE);
        assertThat(CandidateGate.rowForSetAutoCommit(false)).isEqualTo(CandidateGate.Api.CONNECTION_SET_AUTOCOMMIT_FALSE);
        var manual = new CandidateGate.ConnState(false, true);
        var model = new CandidateGate.StatementModel();
        assertThat(CandidateGate.gateSetAutoCommit(true, model, manual, false, null))
            .as("手动事务未提交时 setAutoCommit(true) 是隐式提交 ⇒ 过门").isEqualTo(CandidateGate.Decision.GATE);
        var autoCommit = new CandidateGate.ConnState(true, false);
        assertThat(CandidateGate.gateSetAutoCommit(true, model, autoCommit, false, null))
            .as("已是自动提交时 setAutoCommit(true) 无效果 ⇒ 不设门").isEqualTo(CandidateGate.Decision.NO_GATE);
        assertThat(CandidateGate.gateSetAutoCommit(false, model, autoCommit, false, null))
            .isEqualTo(CandidateGate.Decision.NO_GATE);
        // 同一实际调用（方法名 + 参数 + 状态）只产生一个判定
        assertThat(gate("execute", "UPDATE t SET v=1", model, autoCommit, false, null))
            .isEqualTo(gate("execute", "UPDATE t SET v=1", model, autoCommit, false, null));
    }

    // ------------------------------------------------------------------ 辅助

    private static CandidateGate.Decision gate(String method, String sql, CandidateGate.StatementModel model,
            CandidateGate.ConnState state, boolean fenced, List<String> trace) {
        return CandidateGate.gate(method, sql, model, state, fenced, trace);
    }

    /** 测试源码内的候选信息流模型：按实际调用形态计算判定。 */
    static final class CandidateGate {
        enum Decision { NO_GATE, GATE, REFUSE_UNCLASSIFIED, REFUSE_FENCED }

        enum SqlKind { READ_ONLY, WRITE, COMMIT_CLASS, UNKNOWN }

        enum Api {
            CONNECTION_COMMIT("commit"), CONNECTION_SET_AUTOCOMMIT_TRUE("setAutoCommitTrue"),
            CONNECTION_SET_AUTOCOMMIT_FALSE("setAutoCommitFalse"), STATEMENT_EXECUTE("execute"),
            STATEMENT_EXECUTE_QUERY("executeQuery"), STATEMENT_EXECUTE_LARGE("executeLargeUpdate"),
            PREPARED_EXECUTE_NO_ARGS("executeUpdate"), CALLABLE_EXECUTE("callableExecute"),
            BATCH_ADD("addBatch"), BATCH_EXECUTE("executeBatch"), BATCH_EXECUTE_LARGE("executeLargeBatch"),
            BATCH_CLEAR("clearBatch"), RAW_TRANSACTION_CONTROL("rawTransactionControl"),
            ROLLBACK_OR_SAVEPOINT("rollback"), RESULTSET_MUTATOR("insertRow"), RESULTSET_READ("getWarnings"),
            RESULTSET_UNKNOWN("moveToInsertRow"), CLOSE_OR_ABORT("close"), SPRING_SESSION_COMMIT("springSessionCommit"),
            UNCLASSIFIABLE("unclassifiable");

            private final String canonicalName;
            Api(String canonicalName) { this.canonicalName = canonicalName; }
            String canonicalName() { return canonicalName; }
        }

        record ConnState(boolean autoCommit, boolean transactionActive) { }

        /** 语句模型：prepare 绑定、批队列、清空。 */
        static final class StatementModel {
            private SqlKind bound;
            private final List<SqlKind> batch = new ArrayList<>();
            void bind(String sql) { bound = classifySql(sql); }
            SqlKind bound() { return bound; }
            void add(String sql) { batch.add(sql == null ? SqlKind.UNKNOWN : classifySql(sql)); }
            void clear() { batch.clear(); }
            List<SqlKind> batchSnapshot() { return List.copyOf(batch); }
        }

        private static final Map<String, Api> BY_NAME = new LinkedHashMap<>();
        static {
            for (var api : Api.values()) BY_NAME.put(api.canonicalName(), api);
        }

        static boolean canonicalNamesAreUnique() { return BY_NAME.size() == Api.values().length; }

        /** 方法名 → Api（行标识）。无法识别的形态一律归 {@link Api#UNCLASSIFIABLE}（保守拒绝）。 */
        static Api classifyMethod(String method) {
            if (method == null) return Api.UNCLASSIFIABLE;
            return switch (method) {
                case "commit" -> Api.CONNECTION_COMMIT;
                case "setAutoCommit", "setAutoCommitTrue" -> Api.CONNECTION_SET_AUTOCOMMIT_TRUE;
                case "setAutoCommitFalse" -> Api.CONNECTION_SET_AUTOCOMMIT_FALSE;
                case "execute" -> Api.STATEMENT_EXECUTE;
                case "executeQuery" -> Api.STATEMENT_EXECUTE_QUERY;
                case "executeLargeUpdate" -> Api.STATEMENT_EXECUTE_LARGE;
                case "executeUpdate" -> Api.PREPARED_EXECUTE_NO_ARGS;
                case "callableExecute" -> Api.CALLABLE_EXECUTE;
                case "addBatch" -> Api.BATCH_ADD;
                case "executeBatch" -> Api.BATCH_EXECUTE;
                case "executeLargeBatch" -> Api.BATCH_EXECUTE_LARGE;
                case "clearBatch" -> Api.BATCH_CLEAR;
                case "rawTransactionControl" -> Api.RAW_TRANSACTION_CONTROL;
                case "rollback", "setSavepoint", "releaseSavepoint" -> Api.ROLLBACK_OR_SAVEPOINT;
                case "insertRow", "updateRow", "deleteRow", "updateObject", "updateString" -> Api.RESULTSET_MUTATOR;
                case "getWarnings", "next", "getString", "getInt", "wasNull", "resultSetClose" -> Api.RESULTSET_READ;
                case "moveToInsertRow", "cancelRowUpdates", "moveToCurrentRow" -> Api.RESULTSET_UNKNOWN;
                case "close", "abort" -> Api.CLOSE_OR_ABORT;
                case "springSessionCommit" -> Api.SPRING_SESSION_COMMIT;
                default -> Api.UNCLASSIFIABLE;
            };
        }

        /** {@code setAutoCommit(boolean)} 的行由**参数值**决定（不是调用者预填的分类）。 */
        static Api rowForSetAutoCommit(boolean value) {
            return value ? Api.CONNECTION_SET_AUTOCOMMIT_TRUE : Api.CONNECTION_SET_AUTOCOMMIT_FALSE;
        }

        /** 覆盖 {@code setAutoCommit} 的入口形状：先按参数选行，再走同一判定。 */
        static Decision gateSetAutoCommit(boolean value, StatementModel model, ConnState state, boolean fenced,
                List<String> trace) {
            return gate(rowForSetAutoCommit(value).canonicalName(), null, model, state, fenced, trace);
        }

        /** 有限样本 SQL 分类器（明确标注：不是通用解析器；不可判定即 UNKNOWN）。 */
        static SqlKind classifySql(String sql) {
            if (sql == null) return SqlKind.UNKNOWN;
            String text = sql.strip();
            if (text.isEmpty() || text.contains(";") || text.startsWith("/*") || text.startsWith("--")) {
                return SqlKind.UNKNOWN;
            }
            String upper = text.toUpperCase(java.util.Locale.ROOT);
            if (upper.startsWith("COMMIT") || upper.startsWith("SET AUTOCOMMIT=1") || upper.startsWith("START TRANSACTION")) {
                return SqlKind.COMMIT_CLASS;
            }
            String keyword = upper.split("\\s+", 2)[0];
            return switch (keyword) {
                case "SELECT", "WITH", "SHOW", "DESC", "DESCRIBE", "EXPLAIN", "ROLLBACK" -> SqlKind.READ_ONLY;
                case "INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE", "CREATE", "ALTER", "DROP", "TRUNCATE",
                     "RENAME", "GRANT", "REVOKE", "LOAD", "CALL" -> SqlKind.WRITE;
                default -> SqlKind.UNKNOWN;
            };
        }

        static Decision decisionFromQueue(List<SqlKind> queue) {
            if (queue.isEmpty()) return Decision.NO_GATE;
            boolean anyEffect = false;
            for (SqlKind kind : queue) {
                if (kind == SqlKind.UNKNOWN) return Decision.REFUSE_UNCLASSIFIED;
                if (kind != SqlKind.READ_ONLY) anyEffect = true;
            }
            return anyEffect ? Decision.GATE : Decision.NO_GATE;
        }

        /** 独立性质断言：围栏时执行的效果类调用必须被拒。 */
        static boolean requireRecheckOnFence(String method, Decision actual, boolean fenced) {
            return !fenced || actual == Decision.REFUSE_FENCED;
        }

        /** 负控专用：撤掉“执行批时重新判定围栏”。 */
        static Decision gateLaxRecheck(String method, String sql, StatementModel model, ConnState state,
                boolean fenced, List<String> trace) {
            return decisionFromQueue(model.batchSnapshot());
        }

        static Decision gate(String method, String sql, StatementModel model, ConnState state, boolean fenced,
                List<String> trace) {
            if (trace != null) trace.add("gate(" + method + ") fenced=" + fenced);
            if (fenced) return Decision.REFUSE_FENCED;
            Api api = classifyMethod(method);
            return switch (api) {
                case CONNECTION_COMMIT, SPRING_SESSION_COMMIT -> Decision.GATE;
                case CONNECTION_SET_AUTOCOMMIT_TRUE ->
                    state.transactionActive() && !state.autoCommit() ? Decision.GATE : Decision.NO_GATE;
                case CONNECTION_SET_AUTOCOMMIT_FALSE, ROLLBACK_OR_SAVEPOINT, CLOSE_OR_ABORT, RESULTSET_READ ->
                    Decision.NO_GATE;
                case RESULTSET_MUTATOR -> Decision.GATE;
                case RESULTSET_UNKNOWN, UNCLASSIFIABLE -> Decision.REFUSE_UNCLASSIFIED;
                case BATCH_CLEAR -> { model.clear(); yield Decision.NO_GATE; }
                case BATCH_ADD -> {
                    model.add(sql);
                    yield decisionFromQueue(List.of(model.batchSnapshot().getLast()));
                }
                case BATCH_EXECUTE, BATCH_EXECUTE_LARGE -> decisionFromQueue(model.batchSnapshot());
                case RAW_TRANSACTION_CONTROL -> sqlDecision(classifySql(sql), state);
                case STATEMENT_EXECUTE, STATEMENT_EXECUTE_QUERY, STATEMENT_EXECUTE_LARGE, PREPARED_EXECUTE_NO_ARGS,
                     CALLABLE_EXECUTE -> sqlDecision(sql != null ? classifySql(sql) : model.bound(), state);
            };
        }

        /** 提交类优先；不可归类拒绝；手动事务内非提交 SQL 延迟到 commit 门。 */
        private static Decision sqlDecision(SqlKind kind, ConnState state) {
            if (kind == null || kind == SqlKind.UNKNOWN) return Decision.REFUSE_UNCLASSIFIED;
            if (kind == SqlKind.COMMIT_CLASS) return Decision.GATE;
            if (!state.autoCommit()) return Decision.NO_GATE;
            return kind == SqlKind.READ_ONLY ? Decision.NO_GATE : Decision.GATE;
        }
    }

    private static RecoveryAdmittedDataSource adapter(DataSource source, RecoveryControlFixture fixture) {
        return new RecoveryAdmittedDataSource(source,
            new RecoveryControlLocation(fixture.root, fixture.installation, List.of(fixture.restoreRoot)), fixture.checkpoint);
    }
}
