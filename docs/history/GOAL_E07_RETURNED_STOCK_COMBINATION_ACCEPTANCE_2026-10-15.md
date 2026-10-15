# E07 更正账务、缺记账与退库候选组合开发验收（2026-10-15）

621 的真实 HTTP/MySQL 组合及独立 Python 消费通过。本批只收录实际覆盖的更正账务、两阶段撤回、缺记账补扣、分页和不可信候选拒绝，不计完整退库处置、E07 L2、D14/D15 整体、D18 L3 或总目标完成。合成初始出库、身份、物理凭据和开发签名不代表原 PDA、原身份或现场事实。

## 输入、写权与必跑映射

固定工作树 `D:/acrylic-pda-outbound`、分支 `codex/development`，运行 HEAD/远端均为 `7afaafca06002d79a23bb4d3a3ed734f9b5d3925`。清理聊天于 22:17 的实际只读回执再次确认 complete-released，heldProjectScopes/cleanupTasksInFlight 均为空，无监听、后台任务或待写文件；当前聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 为源码、证据、库、看板及索引唯一写者，无子代理。接续快照为 `.local/goal-20261015/e07-combo01/resume-20261015.json`、对应 status/patch/worktree 文件、`ownership621-resume.json` 及 `runtime-resume-20261015.json`。

| 路径（未修改的入口按输入复用） | 影响层 | 必跑或复用依据 |
| --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aReturnedStockCombinationIT.java` | 非空迁移、真实 HTTP/MySQL 组合 | 当次 1/0/0/0；SHA256 `621a9104532142be4653f80f1e827f4eb95ab9215038847372a1c522d12be66d` |
| `server/src/test/java/com/acrylic/outbound/inventory/CorrectionAccountingHttpChecks.java` | 更正/撤回、账务及周期 | 当次组合执行；SHA256 `931c38c7d48b5ee4c1600ce11d663a94d44d184ae97e7e836ca4070bb1fd8506` |
| `server/src/test/java/com/acrylic/outbound/inventory/ReconciliationMissingPostingChecks.java` | 缺记账正向、独立审批、原键并发与反向贡献 | 当次真实 HTTP；SHA256 `0fd142d24bbdd14898e3ff0e921f2a2db59a06ffde3612deb5ec5f093e987ba7` |
| `server/src/test/java/com/acrylic/outbound/inventory/ReconciliationMissingPostingRefusals.java` | 坏证据、状态漂移、末条 SQL 回滚 | 当次组合执行；SHA256 `ce294f3a79625b6083e114d7dce87605743cc11178e4e176d654473773dd4b05` |
| `server/src/test/java/com/acrylic/outbound/inventory/ReconciliationMissingPostingBoundaries.java` | 仓库/角色、撤权/身份失效、异文、库存变化 | 当次组合执行；SHA256 `c5c295f2cf30df8f547b93d331c54292fb1a6c15887a76070cdee05b8c032466` |
| `server/src/test/java/com/acrylic/outbound/inventory/ReturnedStockPagingChecks.java` | 有界候选分页与不可信候选拒绝 | 当次真实 HTTP；SHA256 `9b77bfbec0758ba2745547406c8eae6392612f265b5d6f075f246ce3c3379086` |
| `.local/goal-20261015/e07-combo01/consume621.py` | 只读独立算法 | 全输入、原字节/RSA/引用图、全表/文件/dump/hash/停机消费；源 SHA256 `7a720853818a22680fe2f24e8f1ac393ca56968a91be25c6ecf0d252d5712200` |
| 本报告、两个索引及看板 | 文档追溯 | 链接、生成块、当前区上限、明确路径暂存审查；不更改产品输入 |

运行前 `input621.json` 绑定 5,278 项 tracked/untracked 的长度与 SHA256，清单 SHA256 `45c547f9861e96f68642650a2cad568e274b87039abf1b829927b8387936ac74`。接续独立消费时，5,277 项仍与运行输入逐字节一致；唯一变化为看板生成块，运行前看板 `board-before-621.md` 与该项长度/哈希精确相符（SHA256 `5fa0263cde336b436911d5662be1cf3a82fbf21639bb67515117ef59cc85e22f`）。消费者明确记录这一报告漂移，不将 live 看板误记为未变。替换本批看板前另留 `board-before-621-report.md`；全部继承 Android/UI/工具/文档和 `server/physical-disposition-last-request.json` 继续保留。

历史[更正身份组合](GOAL_E07_IDENTITY_COMBINATION_ACCEPTANCE_2026-10-15.md)、[更正五检查点](GOAL_E07_CORRECTION_CHECKPOINT_ACCEPTANCE_2026-10-15.md)和[撤回五检查点](GOAL_E07_WITHDRAWAL_CHECKPOINT_ACCEPTANCE_2026-10-15.md)按各报告路径及输入 SHA 复用；621 不重跑或扩大其原范围。

## 实际运行与独立消费

分配前确认 621 路径未占用、目标端口零监听；`allocation621.json` 记录 D 盘可用 `65323651072` bytes。任务临时目录 `.local/t621` 先创建，TEMP/TMP/Java 临时目录仅对子进程设置。库身份为 schema `acrylic_e2a_cross_20260925_621`、端口 19330、datadir `.local/e2a-cross-20260925-621/data`、server UUID `c52d24af-c89f-11f1-89d4-d8bbc13a2b36`；HTTP 为 19434。凭据只存本机忽略目录。

```text
powershell -NoProfile -File .local/goal-20261015/e07-combo01/run621.ps1
python .local/goal-20261015/e07-combo01/preserve621.py 621
python .local/goal-20261015/e07-combo01/consume621.py
```

runner/test 退出码均为 0；JUnit `TEST-com.acrylic.outbound.e2averify.E2aReturnedStockCombinationIT.xml` 为 1 test / 0 failures / 0 errors / 0 skipped，完成 marker 明示实际 HTTP bounded paging、untrusted candidate rejection 及 accounting/withdrawal/missing-posting combination。非空初始资料实际升级后进入业务场景。更正链包含有/无原扣账、重量增减/归零、多次两阶段撤回、原出库事实不变和冲正；取消/实物退回后的新周期及撤回占用还原另有实际断言。

缺记账正向实际上传/读取受控凭据、发现/提交、主管同键并发批准，按当时有效重量补扣；随后重量差额与完整反向贡献归零。申请人审批、跨仓、角色撤销/会话失效、坏附件/类型、旧命令与异文、结果及上游/隔离/占用/周期漂移均拒绝。末条 `reconciliation_posting` 插入故障返回 500、事务状态保持后，原命令重试通过。拒绝、并发与回滚事实由当次集成断言和 HTTP 日志证明；本批不声称完成实际锁等待后撤权或 default-off 全矩阵。

分页实际读取 50+2 条、不重不漏，第二页无 next cursor；合成克隆行仅用于分页边界，选中不可信克隆后 context 返回 `CORRECTION_RECEIPT_INTEGRITY_FAILURE`。保留首屏 `paging.json` 和两次 HTTP 响应哈希；第二屏原响应正文未单独保存，因此 Python 只独立核对首屏 50 条/顺序/游标/数据库引用及两次读取/409 日志，50+2 完整集合仍以当次 JUnit 断言为依据，不冒充独立原正文消费。

Python 实际消费全部 180 表 before/after 原十六进制列、行数和排序行哈希，103 份数据库 RSA 原回执（含 iss/aud/claims.receipt）、104 份原回执文件、23 份原命令、全部二进制 response/command SHA256、3 项 reconciliation_posting 的有效 ledger/approval 引用和 13 条反向冲正。正向缺记账对应 dispatch 的最终账务重量和为零。全部 capabilities（包括 localDeletionAllowed）为 false。消费者为作者自审下的独立算法，不等于独立人员审查。结果 `consumer621-result.json` 为 PASS，SHA256 `0b778fdb1bc7835702fd7c99a27082b7970fc1c65fd715aada40aa701e440b89`；复用只读消费基础文件 `consume620.py` SHA256 `17c91c37f8bb7a4fd5093a15e55aaab3814f062cfcaa01625147112633559885`。

11 个预分配 schema 中主 schema 有 180 表，其余为空；JDBC 全表、dump 与 storage 守恒。dump SHA256 `312b113912f093340f5bcabc6ffb97327424b8beec9558ba1189ee4f8da340f5`，674 个实例文件长度/哈希逐项复核。认证停机成功；接续重查 19330/19434 零监听，无项目 Java/Gradle/模拟器重型任务；共享 MySQL 仅观察。22:22 的 C/D 可用分别为 `83409453056`/`65021898752` bytes，运行/写权/全输入和提交收尾证据均保存在本批目录。原库、datadir、dump/binlog、日志/XML、签名/HTTP 原件和私有身份保留。

## 原失败、边界与下一步

消费者首次错误要求每条 WITHDRAWAL_RESTORE 与被恢复记录属于同一 correction line，多轮撤回的代表贡献链不满足该假设。只读原件显示应沿引用图核对原贡献行；按实际账务规则修复后补验通过，未改产品或数据库。保留 `consume621-before-graph-fix.py`、`consumer621.log` 和 `consumer621-graph-fix.log`；旧 CLI、运行器、失败库和累计计数不清零。没有因此重跑已通过的 HTTP。

本批未修改产品 Java、Android、Web、迁移、契约或生产开关。七类生产决定能力、TEMP 生产归档和 `localDeletionAllowed=false` 边界保持；不接原库/现网、不合 main、不发布部署、不使用正式签名。下一步执行完整 `ReturnedStockHttpChecks` 正反例，并定位恢复检查点后“缺更正不放库存”的直接缺口；外置介质/实际保管回取、E07 其余受影响组合和 Android/Chrome/原模拟器最终候选 L3 继续。总目标保持 active。
