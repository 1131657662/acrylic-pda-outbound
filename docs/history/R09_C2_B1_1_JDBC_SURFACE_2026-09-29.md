# B1-1 对抗性复核：JDBC 完整标准接口表（2026-09-29）

主结论与驱动依据见[复核记录](R09_C2_B1_1_ADVERSARIAL_2026-09-29.md)。本表从 run9-final-unit 的 JUnit system-out 逐签名生成；600 个签名各 3 次，无未分配签名。**这是 MODEL 委托边界证据，不是 1800 次真库测试**。真实驱动/独立连接证据另见主记录清单与 run11-final-it。

不透明取得、隐式执行元数据、未知调用直接拒绝；效果调用的测试输入为 FENCED。允许的本地/取值方法应结合主记录 §4 的驱动事实，不能用此 mock 表独立推导无数据库副作用。RowId 在驱动上不可达，getMetaData 在 Prepared/Callable 上拒绝、在 ResultSet 上仅描述既有列。

## Statement（56 个签名）

| 精确方法签名 | 唯一判定 | 单次委托数 | 重复 | 效果依据 |
| --- | --- | --- | --- | --- |
| `public abstract <T> T java.sql.Wrapper.unwrap(java.lang.Class<T>) throws java.sql.SQLException` | PROXY_ONLY | 0 | 3/3 | 仅同一代理兼容接口；真实 unwrap 跨接口/回取测试 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String,int) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String,int[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String,java.lang.String[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.getMoreResults() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.getMoreResults(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.isCloseOnCompletion() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.isClosed() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.isPoolable() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Wrapper.isWrapperFor(java.lang.Class<?>) throws java.sql.SQLException` | PROXY_ONLY | 0 | 3/3 | 仅同一代理兼容接口；真实 unwrap 跨接口/回取测试 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String,int) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String,int[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String,java.lang.String[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.getFetchDirection() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getFetchSize() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getMaxFieldSize() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getMaxRows() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getQueryTimeout() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getResultSetConcurrency() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getResultSetHoldability() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getResultSetType() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getUpdateCount() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int[] java.sql.Statement.executeBatch() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract java.sql.Connection java.sql.Statement.getConnection() throws java.sql.SQLException` | BOUND_HANDLE | 1 | 3/3 | 返回 owner 绑定代理；身份、SQL 记忆和 FENCED 回取测试 |
| `public abstract java.sql.ResultSet java.sql.Statement.executeQuery(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract java.sql.ResultSet java.sql.Statement.getGeneratedKeys() throws java.sql.SQLException` | BOUND_HANDLE | 1 | 3/3 | 返回 owner 绑定代理；身份、SQL 记忆和 FENCED 回取测试 |
| `public abstract java.sql.ResultSet java.sql.Statement.getResultSet() throws java.sql.SQLException` | BOUND_HANDLE | 1 | 3/3 | 返回 owner 绑定代理；身份、SQL 记忆和 FENCED 回取测试 |
| `public abstract java.sql.SQLWarning java.sql.Statement.getWarnings() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.addBatch(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract void java.sql.Statement.cancel() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.clearBatch() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.clearWarnings() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.close() throws java.sql.SQLException` | CLEANUP | 1 | 3/3 | 关闭清理；事务回滚和真实池回收测试 |
| `public abstract void java.sql.Statement.closeOnCompletion() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setCursorName(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setEscapeProcessing(boolean) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setFetchDirection(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setFetchSize(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setMaxFieldSize(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setMaxRows(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setPoolable(boolean) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setQueryTimeout(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default boolean java.sql.Statement.isSimpleIdentifier(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default java.lang.String java.sql.Statement.enquoteIdentifier(java.lang.String,boolean) throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public default java.lang.String java.sql.Statement.enquoteLiteral(java.lang.String) throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public default java.lang.String java.sql.Statement.enquoteNCharLiteral(java.lang.String) throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String,int) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String,int[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String,java.lang.String[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.getLargeMaxRows() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default long java.sql.Statement.getLargeUpdateCount() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default long[] java.sql.Statement.executeLargeBatch() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default void java.sql.Statement.setLargeMaxRows(long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |

## PreparedStatement（114 个签名）

| 精确方法签名 | 唯一判定 | 单次委托数 | 重复 | 效果依据 |
| --- | --- | --- | --- | --- |
| `public abstract <T> T java.sql.Wrapper.unwrap(java.lang.Class<T>) throws java.sql.SQLException` | PROXY_ONLY | 0 | 3/3 | 仅同一代理兼容接口；真实 unwrap 跨接口/回取测试 |
| `public abstract boolean java.sql.PreparedStatement.execute() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String,int) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String,int[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String,java.lang.String[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.getMoreResults() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.getMoreResults(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.isCloseOnCompletion() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.isClosed() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.isPoolable() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Wrapper.isWrapperFor(java.lang.Class<?>) throws java.sql.SQLException` | PROXY_ONLY | 0 | 3/3 | 仅同一代理兼容接口；真实 unwrap 跨接口/回取测试 |
| `public abstract int java.sql.PreparedStatement.executeUpdate() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String,int) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String,int[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String,java.lang.String[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.getFetchDirection() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getFetchSize() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getMaxFieldSize() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getMaxRows() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getQueryTimeout() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getResultSetConcurrency() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getResultSetHoldability() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getResultSetType() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getUpdateCount() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int[] java.sql.Statement.executeBatch() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract java.sql.Connection java.sql.Statement.getConnection() throws java.sql.SQLException` | BOUND_HANDLE | 1 | 3/3 | 返回 owner 绑定代理；身份、SQL 记忆和 FENCED 回取测试 |
| `public abstract java.sql.ParameterMetaData java.sql.PreparedStatement.getParameterMetaData() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.ResultSet java.sql.PreparedStatement.executeQuery() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract java.sql.ResultSet java.sql.Statement.executeQuery(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract java.sql.ResultSet java.sql.Statement.getGeneratedKeys() throws java.sql.SQLException` | BOUND_HANDLE | 1 | 3/3 | 返回 owner 绑定代理；身份、SQL 记忆和 FENCED 回取测试 |
| `public abstract java.sql.ResultSet java.sql.Statement.getResultSet() throws java.sql.SQLException` | BOUND_HANDLE | 1 | 3/3 | 返回 owner 绑定代理；身份、SQL 记忆和 FENCED 回取测试 |
| `public abstract java.sql.ResultSetMetaData java.sql.PreparedStatement.getMetaData() throws java.sql.SQLException` | DENY_METADATA_EXECUTION | 0 | 3/3 | 取得处拒绝；真实隐式执行反例 |
| `public abstract java.sql.SQLWarning java.sql.Statement.getWarnings() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.addBatch() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract void java.sql.PreparedStatement.clearParameters() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setArray(int,java.sql.Array) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setAsciiStream(int,java.io.InputStream) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setAsciiStream(int,java.io.InputStream,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setAsciiStream(int,java.io.InputStream,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBigDecimal(int,java.math.BigDecimal) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBinaryStream(int,java.io.InputStream) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBinaryStream(int,java.io.InputStream,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBinaryStream(int,java.io.InputStream,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBlob(int,java.io.InputStream) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBlob(int,java.io.InputStream,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBlob(int,java.sql.Blob) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBoolean(int,boolean) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setByte(int,byte) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBytes(int,byte[]) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setCharacterStream(int,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setCharacterStream(int,java.io.Reader,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setCharacterStream(int,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setClob(int,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setClob(int,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setClob(int,java.sql.Clob) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setDate(int,java.sql.Date) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setDate(int,java.sql.Date,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setDouble(int,double) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setFloat(int,float) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setInt(int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setLong(int,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNCharacterStream(int,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNCharacterStream(int,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNClob(int,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNClob(int,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNClob(int,java.sql.NClob) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNString(int,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNull(int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNull(int,int,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setObject(int,java.lang.Object) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setObject(int,java.lang.Object,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setObject(int,java.lang.Object,int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setRef(int,java.sql.Ref) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setRowId(int,java.sql.RowId) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setSQLXML(int,java.sql.SQLXML) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setShort(int,short) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setString(int,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setTime(int,java.sql.Time) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setTime(int,java.sql.Time,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setTimestamp(int,java.sql.Timestamp) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setTimestamp(int,java.sql.Timestamp,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setURL(int,java.net.URL) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setUnicodeStream(int,java.io.InputStream,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.addBatch(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract void java.sql.Statement.cancel() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.clearBatch() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.clearWarnings() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.close() throws java.sql.SQLException` | CLEANUP | 1 | 3/3 | 关闭清理；事务回滚和真实池回收测试 |
| `public abstract void java.sql.Statement.closeOnCompletion() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setCursorName(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setEscapeProcessing(boolean) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setFetchDirection(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setFetchSize(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setMaxFieldSize(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setMaxRows(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setPoolable(boolean) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setQueryTimeout(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default boolean java.sql.Statement.isSimpleIdentifier(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default java.lang.String java.sql.Statement.enquoteIdentifier(java.lang.String,boolean) throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public default java.lang.String java.sql.Statement.enquoteLiteral(java.lang.String) throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public default java.lang.String java.sql.Statement.enquoteNCharLiteral(java.lang.String) throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public default long java.sql.PreparedStatement.executeLargeUpdate() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String,int) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String,int[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String,java.lang.String[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.getLargeMaxRows() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default long java.sql.Statement.getLargeUpdateCount() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default long[] java.sql.Statement.executeLargeBatch() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default void java.sql.PreparedStatement.setObject(int,java.lang.Object,java.sql.SQLType) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.PreparedStatement.setObject(int,java.lang.Object,java.sql.SQLType,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.Statement.setLargeMaxRows(long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |

## CallableStatement（235 个签名）

| 精确方法签名 | 唯一判定 | 单次委托数 | 重复 | 效果依据 |
| --- | --- | --- | --- | --- |
| `public abstract <T> T java.sql.CallableStatement.getObject(int,java.lang.Class<T>) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract <T> T java.sql.CallableStatement.getObject(java.lang.String,java.lang.Class<T>) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract <T> T java.sql.Wrapper.unwrap(java.lang.Class<T>) throws java.sql.SQLException` | PROXY_ONLY | 0 | 3/3 | 仅同一代理兼容接口；真实 unwrap 跨接口/回取测试 |
| `public abstract boolean java.sql.CallableStatement.getBoolean(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.CallableStatement.getBoolean(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.CallableStatement.wasNull() throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public abstract boolean java.sql.PreparedStatement.execute() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String,int) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String,int[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.execute(java.lang.String,java.lang.String[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract boolean java.sql.Statement.getMoreResults() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.getMoreResults(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.isCloseOnCompletion() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.isClosed() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Statement.isPoolable() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract boolean java.sql.Wrapper.isWrapperFor(java.lang.Class<?>) throws java.sql.SQLException` | PROXY_ONLY | 0 | 3/3 | 仅同一代理兼容接口；真实 unwrap 跨接口/回取测试 |
| `public abstract byte java.sql.CallableStatement.getByte(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract byte java.sql.CallableStatement.getByte(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract byte[] java.sql.CallableStatement.getBytes(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract byte[] java.sql.CallableStatement.getBytes(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract double java.sql.CallableStatement.getDouble(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract double java.sql.CallableStatement.getDouble(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract float java.sql.CallableStatement.getFloat(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract float java.sql.CallableStatement.getFloat(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.CallableStatement.getInt(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.CallableStatement.getInt(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.PreparedStatement.executeUpdate() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String,int) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String,int[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.executeUpdate(java.lang.String,java.lang.String[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract int java.sql.Statement.getFetchDirection() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getFetchSize() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getMaxFieldSize() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getMaxRows() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getQueryTimeout() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getResultSetConcurrency() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getResultSetHoldability() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getResultSetType() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int java.sql.Statement.getUpdateCount() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract int[] java.sql.Statement.executeBatch() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract java.io.Reader java.sql.CallableStatement.getCharacterStream(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.io.Reader java.sql.CallableStatement.getCharacterStream(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.io.Reader java.sql.CallableStatement.getNCharacterStream(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.io.Reader java.sql.CallableStatement.getNCharacterStream(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.lang.Object java.sql.CallableStatement.getObject(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.lang.Object java.sql.CallableStatement.getObject(int,java.util.Map<java.lang.String, java.lang.Class<?>>) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.lang.Object java.sql.CallableStatement.getObject(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.lang.Object java.sql.CallableStatement.getObject(java.lang.String,java.util.Map<java.lang.String, java.lang.Class<?>>) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.lang.String java.sql.CallableStatement.getNString(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.lang.String java.sql.CallableStatement.getNString(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.lang.String java.sql.CallableStatement.getString(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.lang.String java.sql.CallableStatement.getString(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.math.BigDecimal java.sql.CallableStatement.getBigDecimal(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.math.BigDecimal java.sql.CallableStatement.getBigDecimal(int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.math.BigDecimal java.sql.CallableStatement.getBigDecimal(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.net.URL java.sql.CallableStatement.getURL(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.net.URL java.sql.CallableStatement.getURL(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Array java.sql.CallableStatement.getArray(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Array java.sql.CallableStatement.getArray(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Blob java.sql.CallableStatement.getBlob(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Blob java.sql.CallableStatement.getBlob(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Clob java.sql.CallableStatement.getClob(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Clob java.sql.CallableStatement.getClob(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Connection java.sql.Statement.getConnection() throws java.sql.SQLException` | BOUND_HANDLE | 1 | 3/3 | 返回 owner 绑定代理；身份、SQL 记忆和 FENCED 回取测试 |
| `public abstract java.sql.Date java.sql.CallableStatement.getDate(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Date java.sql.CallableStatement.getDate(int,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Date java.sql.CallableStatement.getDate(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Date java.sql.CallableStatement.getDate(java.lang.String,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.NClob java.sql.CallableStatement.getNClob(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.NClob java.sql.CallableStatement.getNClob(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.ParameterMetaData java.sql.PreparedStatement.getParameterMetaData() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Ref java.sql.CallableStatement.getRef(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Ref java.sql.CallableStatement.getRef(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.ResultSet java.sql.PreparedStatement.executeQuery() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract java.sql.ResultSet java.sql.Statement.executeQuery(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract java.sql.ResultSet java.sql.Statement.getGeneratedKeys() throws java.sql.SQLException` | BOUND_HANDLE | 1 | 3/3 | 返回 owner 绑定代理；身份、SQL 记忆和 FENCED 回取测试 |
| `public abstract java.sql.ResultSet java.sql.Statement.getResultSet() throws java.sql.SQLException` | BOUND_HANDLE | 1 | 3/3 | 返回 owner 绑定代理；身份、SQL 记忆和 FENCED 回取测试 |
| `public abstract java.sql.ResultSetMetaData java.sql.PreparedStatement.getMetaData() throws java.sql.SQLException` | DENY_METADATA_EXECUTION | 0 | 3/3 | 取得处拒绝；真实隐式执行反例 |
| `public abstract java.sql.RowId java.sql.CallableStatement.getRowId(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.RowId java.sql.CallableStatement.getRowId(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.SQLWarning java.sql.Statement.getWarnings() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.SQLXML java.sql.CallableStatement.getSQLXML(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.SQLXML java.sql.CallableStatement.getSQLXML(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Time java.sql.CallableStatement.getTime(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Time java.sql.CallableStatement.getTime(int,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Time java.sql.CallableStatement.getTime(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Time java.sql.CallableStatement.getTime(java.lang.String,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Timestamp java.sql.CallableStatement.getTimestamp(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Timestamp java.sql.CallableStatement.getTimestamp(int,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Timestamp java.sql.CallableStatement.getTimestamp(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract java.sql.Timestamp java.sql.CallableStatement.getTimestamp(java.lang.String,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract long java.sql.CallableStatement.getLong(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract long java.sql.CallableStatement.getLong(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract short java.sql.CallableStatement.getShort(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract short java.sql.CallableStatement.getShort(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.registerOutParameter(int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.registerOutParameter(int,int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.registerOutParameter(int,int,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.registerOutParameter(java.lang.String,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.registerOutParameter(java.lang.String,int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.registerOutParameter(java.lang.String,int,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setAsciiStream(java.lang.String,java.io.InputStream) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setAsciiStream(java.lang.String,java.io.InputStream,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setAsciiStream(java.lang.String,java.io.InputStream,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setBigDecimal(java.lang.String,java.math.BigDecimal) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setBinaryStream(java.lang.String,java.io.InputStream) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setBinaryStream(java.lang.String,java.io.InputStream,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setBinaryStream(java.lang.String,java.io.InputStream,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setBlob(java.lang.String,java.io.InputStream) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setBlob(java.lang.String,java.io.InputStream,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setBlob(java.lang.String,java.sql.Blob) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setBoolean(java.lang.String,boolean) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setByte(java.lang.String,byte) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setBytes(java.lang.String,byte[]) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setCharacterStream(java.lang.String,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setCharacterStream(java.lang.String,java.io.Reader,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setCharacterStream(java.lang.String,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setClob(java.lang.String,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setClob(java.lang.String,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setClob(java.lang.String,java.sql.Clob) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setDate(java.lang.String,java.sql.Date) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setDate(java.lang.String,java.sql.Date,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setDouble(java.lang.String,double) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setFloat(java.lang.String,float) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setInt(java.lang.String,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setLong(java.lang.String,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setNCharacterStream(java.lang.String,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setNCharacterStream(java.lang.String,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setNClob(java.lang.String,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setNClob(java.lang.String,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setNClob(java.lang.String,java.sql.NClob) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setNString(java.lang.String,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setNull(java.lang.String,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setNull(java.lang.String,int,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setObject(java.lang.String,java.lang.Object) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setObject(java.lang.String,java.lang.Object,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setObject(java.lang.String,java.lang.Object,int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setRowId(java.lang.String,java.sql.RowId) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setSQLXML(java.lang.String,java.sql.SQLXML) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setShort(java.lang.String,short) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setString(java.lang.String,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setTime(java.lang.String,java.sql.Time) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setTime(java.lang.String,java.sql.Time,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setTimestamp(java.lang.String,java.sql.Timestamp) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setTimestamp(java.lang.String,java.sql.Timestamp,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.CallableStatement.setURL(java.lang.String,java.net.URL) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.addBatch() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract void java.sql.PreparedStatement.clearParameters() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setArray(int,java.sql.Array) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setAsciiStream(int,java.io.InputStream) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setAsciiStream(int,java.io.InputStream,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setAsciiStream(int,java.io.InputStream,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBigDecimal(int,java.math.BigDecimal) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBinaryStream(int,java.io.InputStream) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBinaryStream(int,java.io.InputStream,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBinaryStream(int,java.io.InputStream,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBlob(int,java.io.InputStream) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBlob(int,java.io.InputStream,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBlob(int,java.sql.Blob) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBoolean(int,boolean) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setByte(int,byte) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setBytes(int,byte[]) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setCharacterStream(int,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setCharacterStream(int,java.io.Reader,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setCharacterStream(int,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setClob(int,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setClob(int,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setClob(int,java.sql.Clob) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setDate(int,java.sql.Date) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setDate(int,java.sql.Date,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setDouble(int,double) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setFloat(int,float) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setInt(int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setLong(int,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNCharacterStream(int,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNCharacterStream(int,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNClob(int,java.io.Reader) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNClob(int,java.io.Reader,long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNClob(int,java.sql.NClob) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNString(int,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNull(int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setNull(int,int,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setObject(int,java.lang.Object) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setObject(int,java.lang.Object,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setObject(int,java.lang.Object,int,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setRef(int,java.sql.Ref) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setRowId(int,java.sql.RowId) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setSQLXML(int,java.sql.SQLXML) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setShort(int,short) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setString(int,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setTime(int,java.sql.Time) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setTime(int,java.sql.Time,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setTimestamp(int,java.sql.Timestamp) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setTimestamp(int,java.sql.Timestamp,java.util.Calendar) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setURL(int,java.net.URL) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.PreparedStatement.setUnicodeStream(int,java.io.InputStream,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.addBatch(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public abstract void java.sql.Statement.cancel() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.clearBatch() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.clearWarnings() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.close() throws java.sql.SQLException` | CLEANUP | 1 | 3/3 | 关闭清理；事务回滚和真实池回收测试 |
| `public abstract void java.sql.Statement.closeOnCompletion() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setCursorName(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setEscapeProcessing(boolean) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setFetchDirection(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setFetchSize(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setMaxFieldSize(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setMaxRows(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setPoolable(boolean) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public abstract void java.sql.Statement.setQueryTimeout(int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default boolean java.sql.Statement.isSimpleIdentifier(java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default java.lang.String java.sql.Statement.enquoteIdentifier(java.lang.String,boolean) throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public default java.lang.String java.sql.Statement.enquoteLiteral(java.lang.String) throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public default java.lang.String java.sql.Statement.enquoteNCharLiteral(java.lang.String) throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public default long java.sql.PreparedStatement.executeLargeUpdate() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String,int) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String,int[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.executeLargeUpdate(java.lang.String,java.lang.String[]) throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default long java.sql.Statement.getLargeMaxRows() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default long java.sql.Statement.getLargeUpdateCount() throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default long[] java.sql.Statement.executeLargeBatch() throws java.sql.SQLException` | SQL_OR_BATCH | 0 | 3/3 | 以已知 INSERT 测门；真实 SQL/批边界另表 |
| `public default void java.sql.CallableStatement.registerOutParameter(int,java.sql.SQLType) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.CallableStatement.registerOutParameter(int,java.sql.SQLType,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.CallableStatement.registerOutParameter(int,java.sql.SQLType,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.CallableStatement.registerOutParameter(java.lang.String,java.sql.SQLType) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.CallableStatement.registerOutParameter(java.lang.String,java.sql.SQLType,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.CallableStatement.registerOutParameter(java.lang.String,java.sql.SQLType,java.lang.String) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.CallableStatement.setObject(java.lang.String,java.lang.Object,java.sql.SQLType) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.CallableStatement.setObject(java.lang.String,java.lang.Object,java.sql.SQLType,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.PreparedStatement.setObject(int,java.lang.Object,java.sql.SQLType) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.PreparedStatement.setObject(int,java.lang.Object,java.sql.SQLType,int) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |
| `public default void java.sql.Statement.setLargeMaxRows(long) throws java.sql.SQLException` | LOCAL_OR_VALUE | 1 | 3/3 | 参数/选项/本地值；驱动审计及真实清批/OUT 控制 |

## ResultSet（195 个签名）

| 精确方法签名 | 唯一判定 | 单次委托数 | 重复 | 效果依据 |
| --- | --- | --- | --- | --- |
| `public abstract <T> T java.sql.ResultSet.getObject(int,java.lang.Class<T>) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract <T> T java.sql.ResultSet.getObject(java.lang.String,java.lang.Class<T>) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract <T> T java.sql.Wrapper.unwrap(java.lang.Class<T>) throws java.sql.SQLException` | PROXY_ONLY | 0 | 3/3 | 仅同一代理兼容接口；真实 unwrap 跨接口/回取测试 |
| `public abstract boolean java.sql.ResultSet.absolute(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.first() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.getBoolean(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.getBoolean(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.isAfterLast() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.isBeforeFirst() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.isClosed() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.isFirst() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.isLast() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.last() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.next() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.previous() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.relative(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.rowDeleted() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.rowInserted() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.rowUpdated() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.ResultSet.wasNull() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract boolean java.sql.Wrapper.isWrapperFor(java.lang.Class<?>) throws java.sql.SQLException` | PROXY_ONLY | 0 | 3/3 | 仅同一代理兼容接口；真实 unwrap 跨接口/回取测试 |
| `public abstract byte java.sql.ResultSet.getByte(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract byte java.sql.ResultSet.getByte(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract byte[] java.sql.ResultSet.getBytes(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract byte[] java.sql.ResultSet.getBytes(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract double java.sql.ResultSet.getDouble(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract double java.sql.ResultSet.getDouble(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract float java.sql.ResultSet.getFloat(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract float java.sql.ResultSet.getFloat(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract int java.sql.ResultSet.findColumn(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract int java.sql.ResultSet.getConcurrency() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract int java.sql.ResultSet.getFetchDirection() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract int java.sql.ResultSet.getFetchSize() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract int java.sql.ResultSet.getHoldability() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract int java.sql.ResultSet.getInt(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract int java.sql.ResultSet.getInt(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract int java.sql.ResultSet.getRow() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract int java.sql.ResultSet.getType() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.io.InputStream java.sql.ResultSet.getAsciiStream(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.io.InputStream java.sql.ResultSet.getAsciiStream(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.io.InputStream java.sql.ResultSet.getBinaryStream(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.io.InputStream java.sql.ResultSet.getBinaryStream(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.io.InputStream java.sql.ResultSet.getUnicodeStream(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.io.InputStream java.sql.ResultSet.getUnicodeStream(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.io.Reader java.sql.ResultSet.getCharacterStream(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.io.Reader java.sql.ResultSet.getCharacterStream(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.io.Reader java.sql.ResultSet.getNCharacterStream(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.io.Reader java.sql.ResultSet.getNCharacterStream(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.lang.Object java.sql.ResultSet.getObject(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.lang.Object java.sql.ResultSet.getObject(int,java.util.Map<java.lang.String, java.lang.Class<?>>) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.lang.Object java.sql.ResultSet.getObject(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.lang.Object java.sql.ResultSet.getObject(java.lang.String,java.util.Map<java.lang.String, java.lang.Class<?>>) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.lang.String java.sql.ResultSet.getCursorName() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.lang.String java.sql.ResultSet.getNString(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.lang.String java.sql.ResultSet.getNString(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.lang.String java.sql.ResultSet.getString(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.lang.String java.sql.ResultSet.getString(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.math.BigDecimal java.sql.ResultSet.getBigDecimal(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.math.BigDecimal java.sql.ResultSet.getBigDecimal(int,int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.math.BigDecimal java.sql.ResultSet.getBigDecimal(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.math.BigDecimal java.sql.ResultSet.getBigDecimal(java.lang.String,int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.net.URL java.sql.ResultSet.getURL(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.net.URL java.sql.ResultSet.getURL(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Array java.sql.ResultSet.getArray(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Array java.sql.ResultSet.getArray(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Blob java.sql.ResultSet.getBlob(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Blob java.sql.ResultSet.getBlob(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Clob java.sql.ResultSet.getClob(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Clob java.sql.ResultSet.getClob(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Date java.sql.ResultSet.getDate(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Date java.sql.ResultSet.getDate(int,java.util.Calendar) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Date java.sql.ResultSet.getDate(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Date java.sql.ResultSet.getDate(java.lang.String,java.util.Calendar) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.NClob java.sql.ResultSet.getNClob(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.NClob java.sql.ResultSet.getNClob(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Ref java.sql.ResultSet.getRef(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Ref java.sql.ResultSet.getRef(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.ResultSetMetaData java.sql.ResultSet.getMetaData() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.RowId java.sql.ResultSet.getRowId(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.RowId java.sql.ResultSet.getRowId(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.SQLWarning java.sql.ResultSet.getWarnings() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.SQLXML java.sql.ResultSet.getSQLXML(int) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.SQLXML java.sql.ResultSet.getSQLXML(java.lang.String) throws java.sql.SQLException` | DENY_OPAQUE | 0 | 3/3 | 取得处拒绝；真实 locator/OUT 与独立观察 |
| `public abstract java.sql.Statement java.sql.ResultSet.getStatement() throws java.sql.SQLException` | BOUND_HANDLE | 1 | 3/3 | 返回 owner 绑定代理；身份、SQL 记忆和 FENCED 回取测试 |
| `public abstract java.sql.Time java.sql.ResultSet.getTime(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Time java.sql.ResultSet.getTime(int,java.util.Calendar) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Time java.sql.ResultSet.getTime(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Time java.sql.ResultSet.getTime(java.lang.String,java.util.Calendar) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Timestamp java.sql.ResultSet.getTimestamp(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Timestamp java.sql.ResultSet.getTimestamp(int,java.util.Calendar) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Timestamp java.sql.ResultSet.getTimestamp(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract java.sql.Timestamp java.sql.ResultSet.getTimestamp(java.lang.String,java.util.Calendar) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract long java.sql.ResultSet.getLong(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract long java.sql.ResultSet.getLong(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract short java.sql.ResultSet.getShort(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract short java.sql.ResultSet.getShort(java.lang.String) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract void java.sql.ResultSet.afterLast() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract void java.sql.ResultSet.beforeFirst() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract void java.sql.ResultSet.cancelRowUpdates() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract void java.sql.ResultSet.clearWarnings() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract void java.sql.ResultSet.close() throws java.sql.SQLException` | CLEANUP | 1 | 3/3 | 关闭清理；事务回滚和真实池回收测试 |
| `public abstract void java.sql.ResultSet.deleteRow() throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.insertRow() throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.moveToCurrentRow() throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract void java.sql.ResultSet.moveToInsertRow() throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.refreshRow() throws java.sql.SQLException` | DENY_UNKNOWN | 0 | 3/3 | 未分类拒绝，零委托 |
| `public abstract void java.sql.ResultSet.setFetchDirection(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract void java.sql.ResultSet.setFetchSize(int) throws java.sql.SQLException` | READ_LOCAL | 1 | 3/3 | 既有行/列/状态读取；驱动审计无新业务执行 |
| `public abstract void java.sql.ResultSet.updateArray(int,java.sql.Array) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateArray(java.lang.String,java.sql.Array) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateAsciiStream(int,java.io.InputStream) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateAsciiStream(int,java.io.InputStream,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateAsciiStream(int,java.io.InputStream,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateAsciiStream(java.lang.String,java.io.InputStream) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateAsciiStream(java.lang.String,java.io.InputStream,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateAsciiStream(java.lang.String,java.io.InputStream,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBigDecimal(int,java.math.BigDecimal) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBigDecimal(java.lang.String,java.math.BigDecimal) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBinaryStream(int,java.io.InputStream) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBinaryStream(int,java.io.InputStream,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBinaryStream(int,java.io.InputStream,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBinaryStream(java.lang.String,java.io.InputStream) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBinaryStream(java.lang.String,java.io.InputStream,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBinaryStream(java.lang.String,java.io.InputStream,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBlob(int,java.io.InputStream) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBlob(int,java.io.InputStream,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBlob(int,java.sql.Blob) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBlob(java.lang.String,java.io.InputStream) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBlob(java.lang.String,java.io.InputStream,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBlob(java.lang.String,java.sql.Blob) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBoolean(int,boolean) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBoolean(java.lang.String,boolean) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateByte(int,byte) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateByte(java.lang.String,byte) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBytes(int,byte[]) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateBytes(java.lang.String,byte[]) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateCharacterStream(int,java.io.Reader) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateCharacterStream(int,java.io.Reader,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateCharacterStream(int,java.io.Reader,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateCharacterStream(java.lang.String,java.io.Reader) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateCharacterStream(java.lang.String,java.io.Reader,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateCharacterStream(java.lang.String,java.io.Reader,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateClob(int,java.io.Reader) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateClob(int,java.io.Reader,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateClob(int,java.sql.Clob) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateClob(java.lang.String,java.io.Reader) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateClob(java.lang.String,java.io.Reader,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateClob(java.lang.String,java.sql.Clob) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateDate(int,java.sql.Date) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateDate(java.lang.String,java.sql.Date) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateDouble(int,double) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateDouble(java.lang.String,double) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateFloat(int,float) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateFloat(java.lang.String,float) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateInt(int,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateInt(java.lang.String,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateLong(int,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateLong(java.lang.String,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNCharacterStream(int,java.io.Reader) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNCharacterStream(int,java.io.Reader,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNCharacterStream(java.lang.String,java.io.Reader) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNCharacterStream(java.lang.String,java.io.Reader,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNClob(int,java.io.Reader) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNClob(int,java.io.Reader,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNClob(int,java.sql.NClob) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNClob(java.lang.String,java.io.Reader) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNClob(java.lang.String,java.io.Reader,long) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNClob(java.lang.String,java.sql.NClob) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNString(int,java.lang.String) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNString(java.lang.String,java.lang.String) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNull(int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateNull(java.lang.String) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateObject(int,java.lang.Object) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateObject(int,java.lang.Object,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateObject(java.lang.String,java.lang.Object) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateObject(java.lang.String,java.lang.Object,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateRef(int,java.sql.Ref) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateRef(java.lang.String,java.sql.Ref) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateRow() throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateRowId(int,java.sql.RowId) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateRowId(java.lang.String,java.sql.RowId) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateSQLXML(int,java.sql.SQLXML) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateSQLXML(java.lang.String,java.sql.SQLXML) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateShort(int,short) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateShort(java.lang.String,short) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateString(int,java.lang.String) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateString(java.lang.String,java.lang.String) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateTime(int,java.sql.Time) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateTime(java.lang.String,java.sql.Time) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateTimestamp(int,java.sql.Timestamp) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public abstract void java.sql.ResultSet.updateTimestamp(java.lang.String,java.sql.Timestamp) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public default void java.sql.ResultSet.updateObject(int,java.lang.Object,java.sql.SQLType) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public default void java.sql.ResultSet.updateObject(int,java.lang.Object,java.sql.SQLType,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public default void java.sql.ResultSet.updateObject(java.lang.String,java.lang.Object,java.sql.SQLType) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
| `public default void java.sql.ResultSet.updateObject(java.lang.String,java.lang.Object,java.sql.SQLType,int) throws java.sql.SQLException` | CHECK_EFFECT | 0 | 3/3 | FENCED 效果拒绝；行变更双向真实测试 |
