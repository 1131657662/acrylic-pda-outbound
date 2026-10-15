# E07 实物退回、独立退库审批与新周期出库组合开发验收（2026-10-15）

622 的真实 HTTP/MySQL、原签回执/引用独立 Python 消费、全表/dump/storage 保全及认证停机通过。本报告只收录退库 HTTP 组合；不计恢复截点缺更正围栏、E07 L2、D14/D15 整体、D18 L3 或总目标完成。初始出库、身份、实物凭据和开发签名为合成隔离输入，不代表原 PDA 或现场事实。

## 输入、写权与影响映射

固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`，运行 HEAD/远端均为 `50879f048466b540b0fa27c5f9a4d9ec1990a086`。运行前 `input622.json` 绑定 5,279 项 tracked/untracked 长度与 SHA256，清单 SHA256 `29a7b27d3686f4327bdd9df9f70fee73145a2b20049bd4f34b8555001d610467`。独立消费者运行时全部输入逐字节一致。本批未修改产品/测试 Java、Android、Web、契约、迁移或生产开关。

证据根为 `.local/goal-20261015/e07-combo01/`。接续复核清理聊天实际完成回执及 idle 状态，无其后台任务、待写文件或持有范围；本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 为唯一写者，无子代理。新增 `resume622-review.json` 及 status/patch/worktrees、`runtime622-review.json` 记录全部输入、写权和运行环境。替换当前区前保留 `board-before-622-report.md`。继承 Android/UI/文档/工具与私有请求未纳入本批提交。

| 路径 | 影响层与必跑/复用依据 |
| --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aReturnedStockIT.java` | 当次非空升级和真实 HTTP/MySQL；SHA256 `bad0d4f57ddc62e8de4ceb2e24f022cdb9da773a9eefacc8f6a8b16c87093b19` |
| `server/src/test/java/com/acrylic/outbound/inventory/ReturnedStockHttpChecks.java` | 实物退回后独立退库、同键并发、原键/异文和恢复覆盖；当次执行，SHA256 `06151123d5a26d443148da3e36b66a7e5f88eb1e14cc80c533eb82f365936708` |
| `server/src/test/java/com/acrylic/outbound/inventory/ReturnedStockRefusalChecks.java` | 权限/跨仓、自审批、漂移、坏证据、末 SQL 回滚；当次执行，SHA256 `69946537e823896cdab2f87202b58f4b2f0debe5f043c07c2b1c939cd1717a99` |
| `server/src/test/java/com/acrylic/outbound/inventory/ReturnedStockRedispatchHttp.java` | 新单发布/领取/资料/ACK/提交与旧周期拒绝；当次执行，SHA256 `66aa5ae24a400d1f87080b11ac31e68f2e9b4970bcda531b6449ee1e36004d8b` |
| `.local/goal-20261015/e07-combo01/consume622.py` | 只读独立字节/RSA/引用算法；SHA256 `915974aef4c2013ceaa4720835c44398cc452e415b7458b84f22ac4a129f160f` |
| 本报告、两个索引及看板 | 文档追溯；链接、差异、生成块和当前区上限；无产品行为变化 |

[621 账务/缺记账/候选组合](GOAL_E07_RETURNED_STOCK_COMBINATION_ACCEPTANCE_2026-10-15.md)、[620 身份组合](GOAL_E07_IDENTITY_COMBINATION_ACCEPTANCE_2026-10-15.md)及[618 更正五检查点](GOAL_E07_CORRECTION_CHECKPOINT_ACCEPTANCE_2026-10-15.md)按各报告路径与 SHA 复用，不机械重跑或扩大原结论。Android、Chrome、最终候选 L3 不在本批通过范围。

## 当次业务与独立消费

```text
powershell -NoProfile -File .local/goal-20261015/e07-combo01/run622.ps1
python .local/goal-20261015/e07-combo01/preserve622.py 622
python .local/goal-20261015/e07-combo01/consume622.py
```

runner/test 均退出 0，原 JUnit `TEST-com.acrylic.outbound.e2averify.E2aReturnedStockIT.xml` 为 1/0/0/0，Gradle 成功；原 marker/log/XML 保留。非空初始资料实际迁移后执行 `ReturnedStockHttpChecks.verify` 全部场景，未调整产品限流。

正向实际完成物理退回及独立退库发现/提交/主管批准；申请人和批准人不同，附件实际下载与原件逐字节核对。同键并发取得同一原签回执，查询原键逐字节返回，异文返回 409；退库前后的原出库、结果、账务数量/重量与周期保持，库存由 DEDUCTED 到 NOT_DEDUCTED 并递增 stockVersion，独立 `returned_stock_release` 引用有效。新单实际 HTTP 建单、发布、领取、preparation/content/ACK 与提交 SUCCEEDED，重复原提交不重复记账；新周期一次 dispatch/一次扣账。另一新单使用旧周期提交得到 REVIEW_REQUIRED / DISPATCH_CYCLE_CONFLICT，零 dispatch。

反例覆盖库存隔离/上游缺失、审批前版本漂移、无审批权限及申请人即使补有主管角色仍拒自审批、跨仓、撤销主管角色、坏附件摘要、旧通用入口绕过及末条 `returned_stock_release` SQL 故障。末条故障返回 500、相关状态回滚且原键零回执，去除隔离故障后原命令成功。退库后再撤回更正拒绝并保持状态。HTTP 日志为 400×1、409×7、403×3、404×1、500×1；本批不声称覆盖实际锁等待后撤权或 default-off 全矩阵。

Python 独立消费 180 表原 typed bytes/排序/行数/哈希、32 份数据库 RSA 更正回执、32 份原回执文件、15 份原命令，核对 3 项退库 release 对原 dispatch、cycle reopening 和独立批准的引用。独立验证新周期 PDA 成功回执 RSA、原提交 ID 与一次扣账，旧周期无出库；全部 capability（含 localDeletionAllowed）为 false。消费者为作者自审下的独立算法，不等于独立人员审查。`consumer622-result.json` 为 PASS，SHA256 `3eb40ac79a04619b4e54b110155a26c5bd87707852e5e64c89fc7a3805927ebe`。

## 实例保全、失败与边界

分配前路径未占用、端口无监听，`allocation622.json` 记录 C/D 可用 `83405008896` / `65017974784` bytes、预计增长 3 GiB。实例 `.local/e2a-cross-20260925-622`，schema `acrylic_e2a_cross_20260925_622`，MySQL 19330、HTTP 19434，UUID `54d7394d-c8a5-11f1-8ddd-d8bbc13a2b36`。TEMP/TMP/Java 临时目录只对子进程设为 `.local/t622`；凭据仅本机忽略目录。

11 个预分配 schema 中主 schema 有 180 表，其余为空；JDBC 全表、dump 与 storage 守恒。dump SHA256 `34906972d42a0dbfe24f811eefb414f39d71ffe196e41682e35eb0e749c65fd5`，507 个实例文件长度/哈希保全。认证停机成功，接续重核 19330/19434 零监听、无项目 Java/Gradle/模拟器重型任务；共享 MySQL 仅观察。报告前 C/D 可用 `83407642624` / `64739217408` bytes，精确进程/监听见 `runtime622-review.json`。

622 无业务/消费者失败；原先失败库、CLI、消费者误判及累计计数继续保留。数据库/datadir/dump/binlog、原日志/XML/HTTP/命令/签名与私有身份不删除、不复用。七类生产能力、TEMP 生产归档与 localDeletionAllowed=false 边界保持，不接原库/现网、不合 main、不发布部署或使用正式签名。

下一步在新隔离编号实际验证更正前非空恢复截点拒绝退库、全量原 binlog 重建后原批准回执可重放且库存状态正确；之后继续 E07 其余组合、E08/E09 和剩余 D/O/U。总目标保持 active。
