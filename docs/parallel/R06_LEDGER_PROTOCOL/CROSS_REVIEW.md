# C1b 固定交叉只读复审

复审日期：2026-09-21。复审只读核对 W1 固定生产 `04890362cd8ebeaaf18c7782f6e145447d0ff92d`（生产父版本 `05ed1d3c4f121d9462d0077bd607c130f64cf562`）与 W2 最终验收 `b3f426546ef5555375213c212ffe578613ce575c`；没有启动数据库、构建或测试，也没有修改生产、测试或管理文件。

## 结论

固定 SHA、W1 生产护栏与 W2 50 项不同 IT 的证据可以进入下一组合准备，未发现阻断当前组合的范围或环境问题。W2 19327 已按精确 datadir/库/账号护栏正常停止，`stop-proof.json` 记录 Flyway V34 成功 34/34、回执 72、停止后无 mysqld。

W2 的锁后第二查询由 `LedgerSecondLookupIT` 动态证明：首调用首次未命中，竞争调用先提交，随后把当前 `decision_version` 推到过时值，首调用仍返回竞争者的原始响应字节；对应 XML 在 `ledger-second-expected-stale-results`，1/1/0/0。并发、身份竞态、四 GET 权限、故障回滚、损坏关系、历史读取和合法 RSA 错语义重签均有最终 XML/JSON；首次矩阵 40 项中的 3 个夹具失败及修后复跑结果保留分列。

## 必须保留的限制

W2 `LedgerFaultIT` 动态覆盖 request/version/receipt/event/audit/signature 六个注入点，证明失败后 request、receipt、event、audit、decisionVersion 和既有原事实计数恢复为失败前值；对应 `fault-*.json` 全部保留。现有证据没有把“唯一键异常回滚完成后，事务结束，再由另一个只读支路动态读取既有原记录”作为同一场景执行。`LedgerConcurrencyIT` 的同键竞争与 `LedgerSecondLookupIT` 的锁后回放分别覆盖相关边界，但不能合并声称覆盖该组合场景。该限制不阻断当前进入新组合，但后续若声称完整 C1b 事务/回放闭环，应补独立动态证据。

W2 首矩阵的 `resource_id` 缺失、非法 `RECOVERING` 值和 fraction canonical 过早拒绝均是夹具/输入构造问题，原失败 XML 与修后最终 XML 均保留；两次准备 IT 失败（首次未启迁移、r2 快照旧 private-files 路径）同样不计入 50 项通过。首 complete 的保留基线从 2026-09-21 13:24 检查点建立，不能解释为种子前快照。

W1 作者 17 单元、9+2 IT、JAR 和 OpenAPI 0.30/120 路径仅作固定生产交付的作者证据；W3 的 68 项协议测试仅证明 codec/shape/签名与独立 facts 消费者。两者均不替代 W2 数据库、真实 HTTP、设备、E2a、决定或放行验证。生产 handler/POST、业务决定及全部能力仍关闭。

## 证据定位

- W1：`docs/parallel/R06_LEDGER_RUNTIME/VALIDATION.md`，提交 `04890362cd8ebeaaf18c7782f6e145447d0ff92d`。
- W2：`docs/parallel/R06_LEDGER_VERIFY/HANDOFF.md`，提交 `b3f426546ef5555375213c212ffe578613ce575c`。
- W2 ignored 证据：`.local/evidence-r2/ledger-matrix-{first,second,third}-results/`、`ledger-second-expected-stale-results/`、`fault-*.json`、`stop-proof.json`。

