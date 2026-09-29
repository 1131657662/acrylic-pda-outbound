package com.acrylic.outbound.recovery;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G0-K1-1 <b>真实 JDBC 驱动特征验证</b>（真实 MySQL 隔离实例）。
 *
 * <p>本类只回答“Connector/J + MySQL 8.0 实际怎么表现”，用于补齐设计 §4.2 / S19 / S20 / S20-u 中
 * 之前只有模型证据的部分。它<b>不</b>验证生产效果门、HTTP/Session、P5 或锁协议——那些属 B1/B9。
 *
 * <p>报告口径（必须分开）：
 * <ul>
 *   <li>{@code DRIVER|...} ＝ 直接真实驱动行为（真实连接、真实提交、独立观察连接核对）；</li>
 *   <li>{@code RULE|...}   ＝ 测试侧候选规则（{@link G0K1JdbcBoundaryTest.CandidateGate} 有限样本分类器）
 *       对同一批真实 SQL 形态给出的判定，<b>不是</b>产品实现。</li>
 * </ul>
 *
 * <p>环境：必须指向本轮隔离实例（精确 URL/账号），否则 {@link #requireExactEnvironment()} 直接失败；
 * 系统 3306、原库与现网不可达（本类不含任何 3306 端口）。
 */
class G0K1RealJdbcBoundaryIT {

    /** Explicit environment contract instead of a literal pin: any isolated instance in the reserved band. */
    private static final java.util.regex.Pattern URL_PATTERN = java.util.regex.Pattern.compile(
        "jdbc:mysql://127\\.0\\.0\\.1:(19[4-9][0-9]{2})/([a-z0-9_]+)\\?([^\\s]+)");
    private static final java.util.regex.Pattern EVIDENCE_NAME = java.util.regex.Pattern.compile("run[0-9]+-[a-z0-9-]+");
    static int PORT;
    static String DATABASE;
    static String USER;
    static String BASE;
    static String URL;
    /** 同一实例、同一 schema，仅额外允许一次提交多条语句，用于多语句形态。 */
    static String MULTI_URL;

    private static String password;
    private static Path recordFile;

    @BeforeAll
    static void requireExactEnvironment() throws Exception {
        String url = System.getenv("OUTBOUND_DB_URL");
        USER = System.getenv("OUTBOUND_DB_USER");
        password = System.getenv("OUTBOUND_DB_PASSWORD");
        if (url == null || USER == null || password == null || password.isEmpty()) {
            throw new IllegalStateException(
                "G0K1RealJdbcBoundaryIT requires OUTBOUND_DB_URL/USER/PASSWORD of the isolated instance");
        }
        Path candidate = Path.of("").toRealPath();
        while (candidate != null && !Files.isDirectory(candidate.resolve(".local"))) candidate = candidate.getParent();
        if (candidate == null) throw new IllegalStateException("refusing to run: repository root with .local not reachable");
        Path local = candidate.resolve(".local").toRealPath();
        var matched = URL_PATTERN.matcher(url);
        if (!matched.matches()) {
            throw new IllegalStateException("refusing to run: OUTBOUND_DB_URL must be a loopback isolated URL on "
                + "port 19400-19999, got " + url);
        }
        PORT = Integer.parseInt(matched.group(1));
        DATABASE = matched.group(2);
        if (PORT == 3306) throw new IllegalStateException("refusing to run against system 3306");
        if (!USER.equals(DATABASE)) {
            throw new IllegalStateException("refusing to run: the schema and the account must be the same isolated identity");
        }
        String parameters = matched.group(3);
        if (!parameters.contains("useSSL=false") || !parameters.contains("allowPublicKeyRetrieval=true")
            || !parameters.contains("serverTimezone=UTC")) {
            throw new IllegalStateException("refusing to run: URL parameters are part of the contract");
        }
        BASE = "jdbc:mysql://127.0.0.1:" + PORT + "/" + DATABASE;
        URL = url;
        MULTI_URL = URL + "&allowMultiQueries=true";
        String evidence = System.getenv("G0K1_EVIDENCE_DIR");
        if (evidence != null && !evidence.isBlank()) {
            Path directory = Path.of(evidence);
            if (!Files.isDirectory(directory) || !directory.toRealPath().startsWith(local)
                || !EVIDENCE_NAME.matcher(directory.getFileName().toString()).matches()) {
                throw new IllegalStateException("refusing to run: evidence must be a run<N>-<slug> directory inside "
                    + "the repository .local tree");
            }
            recordFile = directory.resolve("it-records.tsv");
            Files.writeString(recordFile, "kind\titem\tcall\tsql\tparams\ttx_before\ttx_after\tobservation\n");
        }
        // 记录实例身份（不含凭据）
        try (Connection c = open(URL); Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT @@port, @@version, @@datadir, CURRENT_USER(), @@transaction_isolation, @@autocommit");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).as("the server must be the instance the contract names").isEqualTo(PORT);
            Path datadir = Path.of(rs.getString(3).replace('\\', '/')).toRealPath();
            assertThat(datadir).as("the server datadir must be an isolated .local instance").startsWith(local);
            record("DRIVER", "instance-identity", "SELECT @@port,@@version,@@datadir,CURRENT_USER()", "-",
                "url=" + BASE.replace("jdbc:mysql://127.0.0.1:" + PORT + "/", "…/"), "-", "-",
                "port=" + rs.getInt(1) + " version=" + rs.getString(2) + " datadir=" + rs.getString(3)
                    + " user=" + rs.getString(4) + " iso=" + rs.getString(5) + " autocommit=" + rs.getInt(6));
        }
    }

    // ------------------------------------------------------------------ S19 / 手动事务与隐式提交

    @Test void manualTransactionCommitIsVisibleOnlyAfterCommitToTheObservationConnection() throws Exception {
        reset("t_manual");
        try (Connection writer = open(URL)) {
            writer.setAutoCommit(false);
            String txBefore = txState(writer);
            try (PreparedStatement ps = writer.prepareStatement("INSERT INTO t_manual(id, val) VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "manual");
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
            int beforeCommit = observeCount("t_manual");
            writer.commit();
            int afterCommit = observeCount("t_manual");
            record("DRIVER", "manual-transaction-commit", "Connection.commit()", "INSERT INTO t_manual",
                "url=" + DATABASE, txBefore, txState(writer),
                "observation_before_commit=" + beforeCommit + " observation_after_commit=" + afterCommit);
            assertThat(beforeCommit).as("未提交时独立观察连接看不到该行").isEqualTo(0);
            assertThat(afterCommit).as("提交后独立观察连接看到该行").isEqualTo(1);
        }
    }

    @Test void setAutoCommitTrueImplicitlyCommitsAPendingTransaction() throws Exception {
        reset("t_implicit");
        try (Connection writer = open(URL)) {
            writer.setAutoCommit(false);
            String txBefore = txState(writer);
            execute(writer, "INSERT INTO t_implicit(id, val) VALUES (1, 'implicit')");
            assertThat(observeCount("t_implicit")).as("setAutoCommit(true) 之前未提交 ⇒ 不可见").isEqualTo(0);

            writer.setAutoCommit(true);   // 隐式提交发生的真实位置（驱动/服务端），不是代理委托次数
            int after = observeCount("t_implicit");
            record("DRIVER", "implicit-commit-setAutoCommit(true)", "Connection.setAutoCommit(true)",
                "INSERT INTO t_implicit", "url=" + DATABASE, txBefore, txState(writer),
                "observation_after_setAutoCommit_true=" + after);
            assertThat(after).as("setAutoCommit(true) 触发真实隐式提交 ⇒ 独立观察连接看到该行").isEqualTo(1);
        }
    }

    @Test void closingAManualTransactionWithoutCommitLosesTheRow() throws Exception {
        reset("t_rollback");
        try (Connection writer = open(URL)) {
            writer.setAutoCommit(false);
            execute(writer, "INSERT INTO t_rollback(id, val) VALUES (1, 'uncommitted')");
            writer.rollback();
        }
        int after = observeCount("t_rollback");
        record("DRIVER", "manual-transaction-rollback", "Connection.rollback()", "INSERT INTO t_rollback",
            "url=" + DATABASE, "autoCommit=false", "closed", "observation_after_rollback=" + after);
        assertThat(after).as("回滚后不可见（作为隐式提交证据的负控）").isEqualTo(0);
    }

    // ------------------------------------------------------------------ S20 / 原始提交类 SQL

    @Test void statementRawCommitSqlCommitsAPendingTransaction() throws Exception {
        reset("t_raw_commit");
        try (Connection writer = open(URL)) {
            writer.setAutoCommit(false);
            String txBefore = txState(writer);
            execute(writer, "INSERT INTO t_raw_commit(id, val) VALUES (1, 'raw')");
            Statement s = writer.createStatement();
            boolean result = s.execute("COMMIT");     // 原始提交类 SQL（门必须按提交类处理）
            int after = observeCount("t_raw_commit");
            record("DRIVER", "raw-commit-sql", "Statement.execute(\"COMMIT\")", "COMMIT", "url=" + DATABASE,
                txBefore, txState(writer), "execute_returned=" + result + " observation_after=" + after
                    + " driver_autocommit_flag=" + writer.getAutoCommit());
            assertThat(after).as("原始 COMMIT 已使写入对独立观察连接可见").isEqualTo(1);
            assertThat(writer.getAutoCommit()).as("驱动侧 autoCommit 标志未被该 SQL 改变（不能作为提交证据）")
                .isFalse();
        }
    }

    @Test void statementSetAutoCommitSqlCommitsAPendingTransactionAndChangesServerState() throws Exception {
        reset("t_set_autocommit");
        try (Connection writer = open(URL)) {
            writer.setAutoCommit(false);
            String txBefore = txState(writer);
            execute(writer, "INSERT INTO t_set_autocommit(id, val) VALUES (1, 'setac')");
            Statement s = writer.createStatement();
            s.execute("SET AUTOCOMMIT=1");
            int after = observeCount("t_set_autocommit");
            String serverFlag = scalar("SELECT @@autocommit");
            record("DRIVER", "raw-set-autocommit-1", "Statement.execute(\"SET AUTOCOMMIT=1\")", "SET AUTOCOMMIT=1",
                "url=" + DATABASE, txBefore, txState(writer),
                "observation_after=" + after + " server_autocommit=" + serverFlag
                    + " driver_autocommit_flag=" + writer.getAutoCommit());
            assertThat(after).isEqualTo(1);
            assertThat(serverFlag).as("服务端 autocommit 已变 1").isEqualTo("1");
            assertThat(writer.getAutoCommit()).as("驱动侧标志仍为 false ⇒ 再次说明不能以 getAutoCommit() 作提交证据")
                .isFalse();
        }
    }

    // ------------------------------------------------------------------ S20 / 无参执行（prepare 记忆）

    @Test void preparedStatementNoArgExecutionStillWritesWithRealDriver() throws Exception {
        reset("t_prepared");
        try (Connection writer = open(URL)) {
            String txBefore = txState(writer);
            try (PreparedStatement ps = writer.prepareStatement("INSERT INTO t_prepared(id, val) VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "prepared");
                assertThat(ps.executeUpdate()).as("无参 executeUpdate() 是本次调用看不到 SQL 的形态").isEqualTo(1);
            }
            int after = observeCount("t_prepared");
            record("DRIVER", "prepared-no-arg-execute", "PreparedStatement.executeUpdate()",
                "INSERT INTO t_prepared (SQL 仅在 prepare 期可见)", "url=" + DATABASE + " autocommit=true",
                txBefore, txState(writer), "observation_after=" + after);
            assertThat(after).isEqualTo(1);
        }
    }

    @Test void callableStatementNoArgExecutionWritesThroughAStoredProcedure() throws Exception {
        reset("t_call");
        try (Connection writer = open(URL); Statement ddl = writer.createStatement()) {
            ddl.execute("DROP PROCEDURE IF EXISTS g0k1_noarg");
            ddl.execute("CREATE PROCEDURE g0k1_noarg() BEGIN INSERT INTO t_call(id, val) VALUES (1, 'call-noarg'); END");
            String txBefore = txState(writer);
            try (CallableStatement cs = writer.prepareCall("{call g0k1_noarg()}")) {
                boolean returned = cs.execute();     // 无参调用：调用体只在 prepareCall 期可见
                record("DRIVER", "callable-no-arg-execute", "CallableStatement.execute()", "{call g0k1_noarg()}",
                    "url=" + DATABASE, txBefore, txState(writer), "execute_returned=" + returned);
            }
            assertThat(observeCount("t_call")).as("过程内的写入经真实驱动提交").isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ S20 / 批处理与 clearBatch

    @Test void statementBatchAndLargeBatchAndClearBatchRealBehaviour() throws Exception {
        reset("t_batch");
        try (Connection writer = open(URL)) {
            String txBefore = txState(writer);
            try (Statement s = writer.createStatement()) {
                s.addBatch("INSERT INTO t_batch(id, val) VALUES (1, 'b1')");
                s.addBatch("INSERT INTO t_batch(id, val) VALUES (2, 'b2')");
                int[] counts = s.executeBatch();
                record("DRIVER", "statement-batch-execute", "Statement.executeBatch()",
                    "addBatch(sql) x2（autoCommit=true）", "url=" + DATABASE, txBefore, txState(writer),
                    "batch_counts=" + java.util.Arrays.toString(counts) + " observation=" + observeCount("t_batch"));
                assertThat(observeCount("t_batch")).isEqualTo(2);
            }
            execute(writer, "INSERT INTO t_batch(id, val) VALUES (3, 'cleared')");
            try (Statement s = writer.createStatement()) {
                s.addBatch("INSERT INTO t_batch(id, val) VALUES (4, 'after-clear')");
                s.clearBatch();
                long[] large = s.executeLargeBatch();
                record("DRIVER", "statement-large-batch-after-clearBatch",
                    "Statement.executeLargeBatch()", "clearBatch() 后执行", "url=" + DATABASE, txBefore,
                    txState(writer), "large_counts=" + java.util.Arrays.toString(large)
                        + " observation=" + observeCount("t_batch"));
                assertThat(observeCount("t_batch")).as("clearBatch 后批内容不再存在（行数仍为 3 行）").isEqualTo(3);
            }
        }
    }

    @Test void preparedStatementNoArgBatchWritesWithRealDriver() throws Exception {
        reset("t_prepared_batch");
        try (Connection writer = open(URL)) {
            try (PreparedStatement ps = writer.prepareStatement("INSERT INTO t_prepared_batch(id, val) VALUES (?, ?)")) {
                for (int i = 1; i <= 3; i++) {
                    ps.setInt(1, i);
                    ps.setString(2, "pb" + i);
                    ps.addBatch();      // 无参 addBatch：SQL 来自 prepare 期
                }
                long[] counts = ps.executeLargeBatch();
                record("DRIVER", "prepared-no-arg-large-batch", "PreparedStatement.executeLargeBatch()",
                    "addBatch() x3（SQL 来自 prepare 期）", "url=" + DATABASE, txState(writer), txState(writer),
                    "large_counts=" + java.util.Arrays.toString(counts)
                        + " observation=" + observeCount("t_prepared_batch"));
            }
            assertThat(observeCount("t_prepared_batch")).isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------ S20 / 可更新 ResultSet

    @Test void updatableResultSetInsertUpdateDeleteAreCommittedByTheRealDriver() throws Exception {
        reset("t_updatable");
        try (Connection seed = open(URL)) {
            execute(seed, "INSERT INTO t_updatable(id, val) VALUES (1, 'seed')");
        }
        try (Connection writer = open(URL);
             Statement s = writer.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            String txBefore = txState(writer);
            try (ResultSet rs = s.executeQuery("SELECT id, val FROM t_updatable")) {
                rs.moveToInsertRow();
                rs.updateInt("id", 2);
                rs.updateString("val", "inserted");
                rs.insertRow();
                rs.moveToCurrentRow();
                int afterInsert = observeCount("t_updatable");

                rs.absolute(1);
                rs.updateString("val", "updated");
                rs.updateRow();
                String updatedVal = scalar("SELECT val FROM t_updatable WHERE id=1");

                rs.absolute(2);
                rs.deleteRow();
                int afterDelete = observeCount("t_updatable");

                record("DRIVER", "updatable-resultset-mutators",
                    "ResultSet.insertRow/updateRow/deleteRow", "CONCUR_UPDATABLE + SELECT id,val FROM t_updatable",
                    "url=" + DATABASE, txBefore, txState(writer),
                    "observation_after_insert=" + afterInsert + " val_after_updateRow=" + updatedVal
                        + " observation_after_delete=" + afterDelete);
                assertThat(afterInsert).as("insertRow() 立即对独立观察连接可见").isEqualTo(2);
                assertThat(updatedVal).isEqualTo("updated");
                assertThat(afterDelete).isEqualTo(1);
            }
        }
    }

    // ------------------------------------------------------------------ S20-u / 未知与特殊形态

    @Test void unclassifiedFormsRealDriverBehaviourIsRecordedPerForm() throws Exception {
        reset("t_unknown");
        // ① 注释引导语句：驱动接受并执行
        try (Connection writer = open(URL)) {
            execute(writer, "/* g0k1 hint */ INSERT INTO t_unknown(id, val) VALUES (1, 'comment')");
            record("DRIVER", "form-comment-led", "Statement.execute(sql)", "/* g0k1 hint */ INSERT …",
                "url=" + DATABASE, txState(writer), txState(writer), "observation=" + observeCount("t_unknown"));
            assertThat(observeCount("t_unknown")).isEqualTo(1);
        }
        // ② 多语句：默认连接被驱动拒绝
        try (Connection writer = open(URL); Statement s = writer.createStatement()) {
            assertThatThrownBy(() -> s.execute("INSERT INTO t_unknown(id, val) VALUES (2,'a'); INSERT INTO t_unknown(id, val) VALUES (3,'b')"))
                .isInstanceOf(SQLException.class);
            record("DRIVER", "form-multi-statement-default-url", "Statement.execute(sql)", "INSERT …; INSERT …",
                "url=" + DATABASE + " (no allowMultiQueries)", txState(writer), txState(writer),
                "driver_rejected=true observation=" + observeCount("t_unknown"));
        }
        // ③ 多语句：仅当连接显式允许时驱动才执行
        try (Connection multi = open(MULTI_URL); Statement s = multi.createStatement()) {
            s.execute("INSERT INTO t_unknown(id, val) VALUES (4,'m1'); INSERT INTO t_unknown(id, val) VALUES (5,'m2')");
            record("DRIVER", "form-multi-statement-allowMultiQueries", "Statement.execute(sql)", "INSERT …; INSERT …",
                "url=" + DATABASE + " allowMultiQueries=true", txState(multi), txState(multi),
                "observation=" + observeCount("t_unknown"));
            assertThat(observeCount("t_unknown")).isEqualTo(3);
        }
        // ④ 会话级 SET（无数据效果）与厂商语法（被拒绝）：单列，不计入已覆盖
        try (Connection writer = open(URL); Statement s = writer.createStatement()) {
            s.execute("SET SESSION group_concat_max_len=1024");
            boolean vendorRejected;
            try {
                s.execute("SOME VENDOR SYNTAX");
                vendorRejected = false;
            } catch (SQLException expected) {
                vendorRejected = true;
            }
            record("DRIVER", "form-session-set-and-vendor-syntax", "Statement.execute(sql)",
                "SET SESSION … / SOME VENDOR SYNTAX", "url=" + DATABASE, txState(writer), txState(writer),
                "session_set_ok=true vendor_syntax_rejected=" + vendorRejected);
            assertThat(vendorRejected).isTrue();
        }
    }

    @Test void ddlImpliesACommitOfThePendingTransactionInTheRealServer() throws Exception {
        reset("t_ddl");
        try (Connection writer = open(URL)) {
            writer.setAutoCommit(false);
            String txBefore = txState(writer);
            execute(writer, "INSERT INTO t_ddl(id, val) VALUES (1, 'before-ddl')");
            assertThat(observeCount("t_ddl")).as("DDL 之前未提交 ⇒ 不可见").isEqualTo(0);
            execute(writer, "CREATE TABLE IF NOT EXISTS t_ddl_marker (id INT PRIMARY KEY)");
            int after = observeCount("t_ddl");
            writer.rollback();
            int afterRollback = observeCount("t_ddl");
            record("DRIVER", "ddl-implicit-commit", "Statement.execute(\"CREATE TABLE …\")",
                "CREATE TABLE IF NOT EXISTS t_ddl_marker", "url=" + DATABASE, txBefore, txState(writer),
                "observation_after_ddl=" + after + " observation_after_later_rollback=" + afterRollback);
            assertThat(after).as("MySQL 的 DDL 会隐式提交未完成事务 ⇒ 写入可见").isEqualTo(1);
            assertThat(afterRollback).as("随后的 rollback 无法撤销已被 DDL 隐式提交的写入").isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ RULE / 候选规则 vs 真实形态

    @Test void candidateRulesAreEvaluatedAgainstTheRealSqlFormsAndStayConservative() throws Exception {
        var state = new G0K1JdbcBoundaryTest.CandidateGate.ConnState(true, false);
        var manual = new G0K1JdbcBoundaryTest.CandidateGate.ConnState(false, true);
        var model = new G0K1JdbcBoundaryTest.CandidateGate.StatementModel();
        String[][] forms = {
            {"execute", "SELECT id FROM t_unknown", "READ_ONLY_EXPECTED"},
            {"execute", "INSERT INTO t_unknown(id, val) VALUES (9,'x')", "WRITE_EXPECTED"},
            {"execute", "COMMIT", "COMMIT_CLASS_EXPECTED"},
            {"execute", "SET AUTOCOMMIT=1", "COMMIT_CLASS_EXPECTED"},
            {"execute", "/* g0k1 hint */ INSERT INTO t_unknown(id, val) VALUES (9,'x')", "UNKNOWN_EXPECTED"},
            {"execute", "INSERT …; INSERT …", "UNKNOWN_EXPECTED"},
            {"callableExecute", "{call g0k1_noarg()}", "UNKNOWN_EXPECTED"},
            {"execute", "CREATE TABLE t_rule_marker (id INT)", "WRITE_EXPECTED"},
            {"execute", "SET SESSION group_concat_max_len=1024", "UNKNOWN_EXPECTED"},
            {"execute", "SOME VENDOR SYNTAX", "UNKNOWN_EXPECTED"},
        };
        for (String[] form : forms) {
            var decision = G0K1JdbcBoundaryTest.CandidateGate.gate(form[0], form[1], model, state, false, null);
            record("RULE", "candidate-decision", form[0], form[1], "autocommit=true transactionActive=false", "-", "-",
                "decision=" + decision + " expected_shape=" + form[2]);
        }
        // 手动事务：非提交类 SQL 延迟到 commit 门；未知仍拒绝
        assertThat(G0K1JdbcBoundaryTest.CandidateGate.gate("execute", "INSERT INTO t_unknown(id, val) VALUES (9,'x')",
            model, manual, false, null)).isEqualTo(G0K1JdbcBoundaryTest.CandidateGate.Decision.NO_GATE);
        assertThat(G0K1JdbcBoundaryTest.CandidateGate.gate("execute", "COMMIT", model, manual, false, null))
            .isEqualTo(G0K1JdbcBoundaryTest.CandidateGate.Decision.GATE);
        assertThat(G0K1JdbcBoundaryTest.CandidateGate.gate("execute", "SOME VENDOR SYNTAX", model, state, false, null))
            .as("未知形态仍一律委托前拒绝").isEqualTo(G0K1JdbcBoundaryTest.CandidateGate.Decision.REFUSE_UNCLASSIFIED);
        // 有限样本分类器的边界：真实驱动执行成功的“注释引导语句”仍被判 UNKNOWN ⇒ 保守拒绝
        assertThat(G0K1JdbcBoundaryTest.CandidateGate.classifySql("/* g0k1 hint */ INSERT INTO t_unknown(id, val) VALUES (9,'x')"))
            .isEqualTo(G0K1JdbcBoundaryTest.CandidateGate.SqlKind.UNKNOWN);
        assertThat(G0K1JdbcBoundaryTest.CandidateGate.classifySql("{call g0k1_noarg()}"))
            .isEqualTo(G0K1JdbcBoundaryTest.CandidateGate.SqlKind.UNKNOWN);
        assertThat(G0K1JdbcBoundaryTest.CandidateGate.classifySql("INSERT …; INSERT …"))
            .isEqualTo(G0K1JdbcBoundaryTest.CandidateGate.SqlKind.UNKNOWN);
    }

    // ------------------------------------------------------------------ 辅助

    private static Connection open(String url) throws SQLException {
        return DriverManager.getConnection(url, USER, password);
    }

    /** 独立观察连接：自己的连接、自动提交、只读查询，用于断言“已提交效果”。 */
    private static int observeCount(String table) throws SQLException {
        try (Connection observer = open(URL); Statement s = observer.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertThat(rs.next()).isTrue();
            return rs.getInt(1);
        }
    }

    private static String scalar(String sql) throws SQLException {
        try (Connection observer = open(URL); Statement s = observer.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }

    private static String txState(Connection c) throws SQLException {
        return "autoCommit=" + c.getAutoCommit() + " isolation=" + c.getTransactionIsolation();
    }

    private static void execute(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    /** 每轮测试独立表；重建保证可重复运行。 */
    private static void reset(String table) throws SQLException {
        try (Connection c = open(URL); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS " + table);
            s.execute("CREATE TABLE " + table + " (id INT PRIMARY KEY, val VARCHAR(50)) ENGINE=InnoDB");
        }
    }

    /** 结构化记录：DRIVER＝真实驱动行为，RULE＝测试侧候选规则；不含凭据。 */
    private static void record(String kind, String item, String call, String sql, String params,
            String txBefore, String txAfter, String observation) {
        String line = String.join("\t", kind, item, call, sql, params, txBefore, txAfter, observation);
        System.out.println("G0K1REAL|" + line);
        if (recordFile != null) {
            try {
                Files.writeString(recordFile, line + System.lineSeparator(), StandardOpenOption.APPEND);
            } catch (Exception e) {
                throw new IllegalStateException("failed to append evidence record", e);
            }
        }
    }

    /** 供报告使用的形态清单（如驱动不支持则单列，不计为已覆盖）。 */
    static List<String> coveredForms() {
        return new ArrayList<>(List.of("commit", "setAutoCommit(true)", "raw COMMIT", "SET AUTOCOMMIT=1",
            "prepared no-arg execute", "callable no-arg execute", "statement batch", "executeLargeBatch",
            "clearBatch", "prepared no-arg batch", "updatable ResultSet insertRow/updateRow/deleteRow",
            "comment-led statement", "multi-statement (allowMultiQueries)", "DDL implicit commit"));
    }

    static List<String> unsupportedForms() {
        return new ArrayList<>(List.of("multi-statement on a connection without allowMultiQueries (driver rejects)",
            "vendor-specific syntax (server rejects)"));
    }

    static String localeIndependent(String value) { return value.toLowerCase(Locale.ROOT); }
}
