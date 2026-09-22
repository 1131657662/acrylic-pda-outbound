# V35 结构负向矩阵

`E2aV35NegativeChecks` 面向已经完成 V34 synthetic intake seed 的隔离数据库。构造器从真实 `source_evidence_intake_receipt` 及其 object/set/verification 关系读取基线 ID；它不生成签名、HTTP 回执或可信业务事实。

`verify(evidenceRoot)` 先验证 OPEN、PAGE、STOP、VERIFY 四个合法形状可插入，再逐案回滚执行以下数据库约束：route/status/version、请求摘要格式、字节边界、操作引用形状、scope/submission/round 复合外键、自然键重复，以及 immutable UPDATE/DELETE trigger。每案使用独立 Spring JDBC 事务，失败结果记录 SQLState 和 vendor code；意外成功会使验证失败。总表及逐案 JSON 使用 `CREATE_NEW` 写入，并核对 intake 行数在验证前后不变。

该 helper 只证明 V35 DDL 结构约束。它不替代 writer、真实 HTTP、签名验证、PAGE/STOP/VERIFY 业务流程或生产回执验收；需要在 V35 动态迁移和 W1 最新 seed 合入后由 W2 在 `127.0.0.1:19329` 隔离实例执行。
