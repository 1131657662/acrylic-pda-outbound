package com.acrylic.outbound.recovery;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * G0-K1 定向反例（测试侧第 1 类 + 第 2 类）。
 *
 * <p>第 1 类—<b>现有代码特征测试</b>：用真实 {@link RecoveryAdmittedDataSource} 代理配合 Mockito
 * fake delegate，记录现有代理实际可达的方法、参数与委托次数。它只说明“今天如何委托”，
 * <b>不</b>说明效果门已存在。
 *
 * <p>第 2 类—<b>候选协议模型</b>：{@link CandidateGateRules} 是测试源码内的规则模型，用于把
 * v5 冻结表的每一行变成可回放的判定；它<b>不是</b>产品实现，模型通过不等于实现通过。
 *
 * <p>本类不修改 src/main、不连接数据库、不启动应用、不做随机等待。
 */
class G0K1JdbcBoundaryTest {

    // ------------------------------------------------------------------ 第 1 类：现有代理特征

    @Test void existingProxyDelegatesConnectionLevelEffectsWithoutAnyGate() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            admitted.commit();                 // 显式提交
            admitted.setAutoCommit(false);     // 进入手动事务
            admitted.rollback();               // 回滚（非效果）
        }

        verify(connection).commit();
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        System.out.println("[G0K1-char] connection.commit=1 setAutoCommit(false)=1 rollback=1 -> 现有代理直接委托，无效果门");
    }

    @Test void implicitCommitThroughSetAutoCommitTrueIsDelegatedInsideTheProxy() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(false);
        when(connection.createStatement()).thenReturn(statement);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            admitted.createStatement().execute("UPDATE t SET v=1");   // 手动事务内的效果
            admitted.setAutoCommit(true);                             // 驱动隐式提交
        }

        verify(connection, times(1)).setAutoCommit(true);
        var inOrder = inOrder(statement, connection);
        inOrder.verify(statement).execute("UPDATE t SET v=1");
        inOrder.verify(connection).setAutoCommit(true);
        System.out.println("[G0K1-char] setAutoCommit(true) 在委托内部完成隐式提交；事后钩子无法拦截 -> 门必须在委托前");
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
            guarded.execute("UPDATE t SET v=1");                       // SQL 作为 args[0] 可见
            guarded.addBatch("UPDATE t SET v=2");                      // 批处理带 SQL，可见
            PreparedStatement guardedPrepared = admitted.prepareStatement("UPDATE t SET v=?");
            guardedPrepared.executeUpdate();                            // 无参：本次调用看不到 SQL
            guardedPrepared.addBatch();                                 // 无参批累积：内容不可见
            guardedPrepared.executeBatch();                             // 无参批执行：内容不可见
        }

        verify(connection).prepareStatement("UPDATE t SET v=?");
        verify(statement).execute("UPDATE t SET v=1");
        verify(statement).addBatch("UPDATE t SET v=2");
        verify(prepared).executeUpdate();
        verify(prepared).addBatch();
        verify(prepared).executeBatch();
        System.out.println("[G0K1-char] 信息流：execute(sql)/addBatch(sql) 的 SQL 在本次调用可见；"
            + "prepareStatement(sql) 的 SQL 只在 prepare 期可见；无参 execute*/addBatch/executeBatch 本次调用不可见"
            + " -> 候选规则必须记住 prepare 期 SQL，否则只能归类为不可归类");
    }

    @Test void updatableResultSetMutatorsAreWrappedAndDelegated() throws Exception {
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
        }

        verify(result).insertRow();
        verify(result).updateRow();
        verify(result).deleteRow();
        System.out.println("[G0K1-char] ResultSet.insertRow/updateRow/deleteRow 经代理委托 -> "
            + "v5 表把 ResultSet.* 一概列为“无持久化”是错误的；这三个是可持久化变更");
    }

    @Test void rawCommitSqlIsDelegatedEvenWhenAutoCommitIsDisabled() throws Exception {
        var fixture = RecoveryWriterLeaseTest.protocol();
        var source = mock(DataSource.class); var connection = mock(Connection.class);
        var statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(false);
        when(connection.createStatement()).thenReturn(statement);
        var adapter = adapter(source, fixture);

        try (Connection admitted = adapter.getConnection()) {
            admitted.createStatement().execute("COMMIT");     // 手动事务内的原始提交
        }

        verify(statement).execute("COMMIT");
        System.out.println("[G0K1-char] autoCommit=false 时原始 COMMIT 仍直接委托 -> "
            + "“autoCommit=false 整体免门”的规则会漏掉提交类 SQL，必须有更高优先级的提交类规则");
    }

    // ------------------------------------------------------------------ 第 2 类：候选规则模型

    @Test void candidateRulesMapEveryFrozenTableRowToADecision() {
        var rules = CandidateGateRules.class;
        assertThat(rules).isNotNull();
        // 连接级
        assertThat(decide(CandidateGateRules.Api.CONNECTION_COMMIT, Unknown.NONE, true, true))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.CONNECTION_SET_AUTOCOMMIT_TRUE, Unknown.NONE, false, true))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.CONNECTION_SET_AUTOCOMMIT_TRUE, Unknown.NONE, false, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        assertThat(decide(CandidateGateRules.Api.CONNECTION_SET_AUTOCOMMIT_FALSE, Unknown.NONE, true, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        // 语句级：只读白名单放行、写入过门、不可归类拒绝
        assertThat(decide(CandidateGateRules.Api.STATEMENT_EXECUTE, readOnly("SELECT 1"), true, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        assertThat(decide(CandidateGateRules.Api.STATEMENT_EXECUTE, write("UPDATE t SET v=1"), true, false))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.STATEMENT_EXECUTE, Unknown.SQL, true, false))
            .isEqualTo(CandidateGateRules.Decision.REFUSE_UNCLASSIFIED);
        // 查询方法不得仅凭方法名放行
        assertThat(decide(CandidateGateRules.Api.STATEMENT_EXECUTE_QUERY, Unknown.SQL, true, false))
            .isEqualTo(CandidateGateRules.Decision.REFUSE_UNCLASSIFIED);
        assertThat(decide(CandidateGateRules.Api.STATEMENT_EXECUTE_QUERY, readOnly("SELECT id FROM t"), true, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        // 批处理
        assertThat(decide(CandidateGateRules.Api.STATEMENT_BATCH_AUTOCOMMIT, readOnly("SELECT 1"), true, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        assertThat(decide(CandidateGateRules.Api.STATEMENT_BATCH_AUTOCOMMIT, write("DELETE FROM t"), true, false))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.STATEMENT_BATCH_AUTOCOMMIT, Unknown.SQL, true, false))
            .isEqualTo(CandidateGateRules.Decision.REFUSE_UNCLASSIFIED);
        // 结果集
        assertThat(decide(CandidateGateRules.Api.RESULTSET_MUTATOR, Unknown.NONE, true, false))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.RESULTSET_READ, Unknown.NONE, true, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        // 其余
        assertThat(decide(CandidateGateRules.Api.ROLLBACK_OR_SAVEPOINT, Unknown.NONE, false, true))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        assertThat(decide(CandidateGateRules.Api.CLOSE_OR_ABORT, Unknown.NONE, true, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        assertThat(decide(CandidateGateRules.Api.SPRING_SESSION_COMMIT, Unknown.NONE, false, true))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.UNCLASSIFIABLE, Unknown.SQL, true, false))
            .isEqualTo(CandidateGateRules.Decision.REFUSE_UNCLASSIFIED);
    }

    @Test void rawCommitOverridesTheAutoCommitFalseExemptionAtEveryLevel() {
        assertThat(decide(CandidateGateRules.Api.RAW_TRANSACTION_CONTROL, commitSql("COMMIT"), false, true))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.RAW_TRANSACTION_CONTROL, commitSql("SET AUTOCOMMIT=1"), false, true))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.STATEMENT_EXECUTE, commitSql("COMMIT"), false, true))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.STATEMENT_BATCH_IN_TRANSACTION, commitSql("COMMIT"), false, true))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        // 非提交类 SQL 在手动事务中才允许免门（效果延迟到 commit 门）
        assertThat(decide(CandidateGateRules.Api.STATEMENT_EXECUTE_IN_TRANSACTION, write("UPDATE t SET v=1"), false, true))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
    }

    @Test void noArgPreparedExecutionDependsOnRememberedPrepareSql() {
        assertThat(decide(CandidateGateRules.Api.PREPARED_EXECUTE_NO_ARGS, readOnly("SELECT 1"), true, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        assertThat(decide(CandidateGateRules.Api.PREPARED_EXECUTE_NO_ARGS, write("INSERT INTO t VALUES(1)"), true, false))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.PREPARED_EXECUTE_NO_ARGS, Unknown.SQL, true, false))
            .isEqualTo(CandidateGateRules.Decision.REFUSE_UNCLASSIFIED);
        assertThat(decide(CandidateGateRules.Api.PREPARED_BATCH_NO_ARGS, Unknown.SQL, true, false))
            .isEqualTo(CandidateGateRules.Decision.REFUSE_UNCLASSIFIED);
    }

    @Test void sqlClassifiedApisRefuseUnclassifiableInputsInsteadOfGateThenAllow() {
        var sqlClassified = List.of(
            CandidateGateRules.Api.STATEMENT_EXECUTE, CandidateGateRules.Api.STATEMENT_EXECUTE_QUERY,
            CandidateGateRules.Api.STATEMENT_EXECUTE_IN_TRANSACTION, CandidateGateRules.Api.STATEMENT_BATCH_AUTOCOMMIT,
            CandidateGateRules.Api.STATEMENT_BATCH_IN_TRANSACTION, CandidateGateRules.Api.PREPARED_EXECUTE_NO_ARGS,
            CandidateGateRules.Api.PREPARED_BATCH_NO_ARGS, CandidateGateRules.Api.RAW_TRANSACTION_CONTROL,
            CandidateGateRules.Api.UNCLASSIFIABLE);
        for (var api : sqlClassified) {
            assertThat(decide(api, Unknown.SQL, true, false))
                .as("api=%s：不可归类只能在委托前拒绝，不得出现“过门即可执行”的第二种结果", api)
                .isEqualTo(CandidateGateRules.Decision.REFUSE_UNCLASSIFIED);
        }
        // 非 SQL 分类的形态有固定语义（不依赖 SQL 文本，也不因此被拒绝）
        assertThat(decide(CandidateGateRules.Api.CONNECTION_COMMIT, Unknown.SQL, true, false))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.RESULTSET_MUTATOR, Unknown.SQL, true, false))
            .isEqualTo(CandidateGateRules.Decision.GATE);
        assertThat(decide(CandidateGateRules.Api.RESULTSET_READ, Unknown.SQL, true, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        assertThat(decide(CandidateGateRules.Api.ROLLBACK_OR_SAVEPOINT, Unknown.SQL, true, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
        assertThat(decide(CandidateGateRules.Api.CONNECTION_SET_AUTOCOMMIT_TRUE, Unknown.SQL, false, false))
            .isEqualTo(CandidateGateRules.Decision.NO_GATE);
    }

    // ------------------------------------------------------------------ 模型与辅助

    /** 不可归类标记（模型输入）。 */
    enum Unknown { NONE, SQL }

    private static CandidateGateRules.Sql readOnly(String text) { return new CandidateGateRules.Sql(text, true, false); }
    private static CandidateGateRules.Sql write(String text) { return new CandidateGateRules.Sql(text, false, false); }
    private static CandidateGateRules.Sql commitSql(String text) { return new CandidateGateRules.Sql(text, false, true); }

    private static CandidateGateRules.Decision decide(CandidateGateRules.Api api, Unknown unknown,
            boolean autoCommit, boolean transactionActive) {
        return CandidateGateRules.decide(new CandidateGateRules.Call(api, null, autoCommit, transactionActive));
    }

    private static CandidateGateRules.Decision decide(CandidateGateRules.Api api, CandidateGateRules.Sql sql,
            boolean autoCommit, boolean transactionActive) {
        return CandidateGateRules.decide(new CandidateGateRules.Call(api, sql, autoCommit, transactionActive));
    }

    /** 测试源码内的候选规则模型：只回答“该调用形态在委托前应得到哪种判定”。 */
    static final class CandidateGateRules {
        enum Api {
            CONNECTION_COMMIT, CONNECTION_SET_AUTOCOMMIT_TRUE, CONNECTION_SET_AUTOCOMMIT_FALSE,
            STATEMENT_EXECUTE, STATEMENT_EXECUTE_QUERY, STATEMENT_EXECUTE_IN_TRANSACTION,
            STATEMENT_BATCH_AUTOCOMMIT, STATEMENT_BATCH_IN_TRANSACTION,
            PREPARED_EXECUTE_NO_ARGS, PREPARED_BATCH_NO_ARGS,
            RAW_TRANSACTION_CONTROL, ROLLBACK_OR_SAVEPOINT, RESULTSET_MUTATOR, RESULTSET_READ,
            CLOSE_OR_ABORT, SPRING_SESSION_COMMIT, UNCLASSIFIABLE
        }

        enum Decision { NO_GATE, GATE, REFUSE_UNCLASSIFIED }

        /** {@code readOnly}/{@code commitClass} 为规则分类结果；{@code null} 表示不可归类。 */
        record Sql(String text, boolean readOnly, boolean commitClass) { }

        record Call(Api api, Sql sql, boolean autoCommit, boolean transactionActive) { }

        static final Set<String> READ_ONLY_RESULTSET_PREFIXES = Set.of("get", "is", "was", "next", "previous",
            "first", "last", "before", "after", "absolute", "relative", "findColumn", "close", "refreshRow");

        static Decision decide(Call call) {
            return switch (call.api()) {
                case CONNECTION_COMMIT, SPRING_SESSION_COMMIT -> Decision.GATE;
                case CONNECTION_SET_AUTOCOMMIT_TRUE ->
                    // 隐式提交仅在仍有未提交事务时发生；事务清理复位不设门
                    call.transactionActive() && !call.autoCommit() ? Decision.GATE : Decision.NO_GATE;
                case CONNECTION_SET_AUTOCOMMIT_FALSE, ROLLBACK_OR_SAVEPOINT, CLOSE_OR_ABORT, RESULTSET_READ ->
                    Decision.NO_GATE;
                case RESULTSET_MUTATOR -> Decision.GATE;
                case RAW_TRANSACTION_CONTROL -> sqlDecision(call.sql(), true);
                case STATEMENT_EXECUTE, STATEMENT_EXECUTE_QUERY ->
                    sqlDecision(call.sql(), false);
                case STATEMENT_EXECUTE_IN_TRANSACTION -> sqlInTransaction(call.sql());
                case STATEMENT_BATCH_AUTOCOMMIT -> sqlDecision(call.sql(), false);
                case STATEMENT_BATCH_IN_TRANSACTION -> sqlInTransaction(call.sql());
                case PREPARED_EXECUTE_NO_ARGS, PREPARED_BATCH_NO_ARGS -> sqlDecision(call.sql(), false);
                case UNCLASSIFIABLE -> Decision.REFUSE_UNCLASSIFIED;
            };
        }

        /**
         * 手动事务内（{@code autoCommit==false}）：提交类 SQL 优先过门；其余已归类 SQL 的效果延迟到
         * {@code Connection.commit()} 门，故不放门；**不可归类仍拒绝**（无法排除其中含原始提交）。
         */
        private static Decision sqlInTransaction(Sql sql) {
            if (sql == null) return Decision.REFUSE_UNCLASSIFIED;
            if (sql.commitClass()) return Decision.GATE;
            return Decision.NO_GATE;
        }

        private static Decision sqlDecision(Sql sql, boolean commitAlwaysGated) {
            if (sql == null) return Decision.REFUSE_UNCLASSIFIED;
            if (sql.commitClass()) return Decision.GATE;
            if (commitAlwaysGated && sql.readOnly()) return Decision.NO_GATE;
            return sql.readOnly() ? Decision.NO_GATE : Decision.GATE;
        }
    }

    private static RecoveryAdmittedDataSource adapter(DataSource source, RecoveryControlFixture fixture) {
        return new RecoveryAdmittedDataSource(source,
            new RecoveryControlLocation(fixture.root, fixture.installation, List.of(fixture.restoreRoot)), fixture.checkpoint);
    }
}
