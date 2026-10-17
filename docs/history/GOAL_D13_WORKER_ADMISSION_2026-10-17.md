# D13 后台调度恢复准入切片

状态：run1557 的生产调度恢复准入及独立消费通过；仅有界服务端 L1 开发备份，不计 D13 整体、L2 或 D18 L3。

输入 HEAD/实核远端 `4b98268204984f17e1b02df075789e09f8b9b687`，固定树与分支见看板；聊天 `01a147c5-0495-7703-ba98-2e7637cbc619` 唯一写者。推送后全输入 `.local/goal-20261017-resume/inputd15combination-postpush04.json` 共5533项，SHA `ea782dd87c76f5fb749671c7e847ff9b0cdc340121963d69c3febd6180c1f5d3`；同号 patch、未跟踪副本、写权、进程、监听及 C/D/E 余量已保留。版本事实仅见看板生成块。

## 默认与范围

问题：生产 `BackgroundWorkerProcess.poll/enqueueReviewReminderSweep` 没有恢复操作根。显式开启恢复准入后，primary datasource 拒绝这两个入口；已有事务与 HTTP 导出报告承认没有共用入口。

采用：在生产调度入口创建 WORKER_JOB 根，绑定当前调度线程，覆盖一次完整的领取、handler、同步 renderer 排空、发布/失败；提醒入队也是独立调度操作。统一由 try/finally 清绑定并关闭根。准入未开启时沿原调度行为执行。只在入口接线，不给 handler 另发权限、不改原数据库池/恢复权威、不开放任何生产决定能力。理由：沿现有操作注册表保持单一权威；只包 SQL 会在文件/子进程期间错误释放写者围栏。

不在此切片声称实现完整 retirement/quarantine 协议、跨进程 P5 全故障组合或 D13 全部文件入口。取消的换机承接/接收者激活不恢复；D16 标准、TEMP生产归档、七类能力与 localDeletionAllowed=false 保持。

## 验收标准与影响

| 路径/范围 | 影响层 | 必跑 | 复用依据 |
| --- | --- | --- | --- |
| `server/src/main/java/com/acrylic/outbound/jobs/BackgroundWorkerProcess.java` | 非web生产调度、恢复准入 | 默认关闭真实调度；开启后真实调度与提醒入队；执行期间阻断权威推进；完成/失败退根；旧权威后拒绝且无写入 | 既有 HTTP/Session、事务报告只能按路径/SHA引用；不能代替本次接线验收 |
| 本切片 Java IT/HTTP/合成输入 | 真实 HTTP/MySQL、同步 renderer/文件 | HTTP创建/同键/异文、无权/跨仓、实际调度生成与下载；根外连接拒绝；末SQL失败回滚；控制变更后HTTP及worker拒绝 | 原 TemporaryConfirmationFixture 仅用于明确合成事实准备，不当生产确认接口验收；运行输入逐路径SHA另存 |
| Python runner/独立消费 | 一次性隔离进程和文件 | 新目录/端口/datadir身份；非空迁移；XML新鲜度、文件哈希与独立回读；认证停机 | 复用 d15_combination_runtime 的实际字节按输入SHA绑定；旧库不复用 |

Android/Web产品源码及契约/迁移未改；本切片仅服务端入口L1。受影响Android/Chrome/恢复组合仍须按实际输入补齐后才能计L2。原模拟器、正式身份、原库、现网、生产审批均不触碰。作者与自审同一助手，独立工具不称独立人员。

## 实际执行与结果

最终有效输入为新隔离 run1557，数据库19336、HTTP19440。命令：

```text
.local/r09-control-venv/Scripts/python.exe -X utf8 docs/parallel/R09/tests/run_d13_admitted_worker.py --run 1557 --port 19336 --http 19440
```

runner exit0；IT 1项、0失败/错误/跳过。默认关闭时真实HTTP→非web生产调度→XLSX→下载通过；开启准入后真实HTTP/Session→生产调度→真实独立JDK renderer→XLSX/PDF→下载通过。API与worker是同一测试JVM内独立Spring上下文，不称独立API/worker进程。身份、TEMP已确认输入均为合成fixture，不替代生产确认POST、原PDA或现场事实。

401、跨仓404 WAREHOUSE_NOT_FOUND、同键同任务、异文409均实际覆盖。合成probe handler不提供root：它观察生产调度生成的WORKER_JOB；持根期间独立Python权威推进返回CONTROL_WRITERS_ACTIVE，根外JDBC拒绝。completion真实事务最后一条SQL故意重复主键，前条INSERT回滚，任务FAILED、probe表0行。正常排空后root RELEASED、无子操作、registry无泄漏。排空scheduler后独立权威推进成功，旧worker两入口拒绝且任务事实不变；旧HTTP返回503 CONTROL_INSTANCE_STALE。七类CommandOperation为空，无normal admission。

最终证据根为 `.local/e2a-cross-20260925-1557/`；输入SHA、命令/依赖锁、原XML、HTTP字节、子进程及监听、业务快照、dump、停机回执均保留。直接/复用源码绑定见 `.local/goal-20261017-resume/d13-worker-closeout02/source-bindings.json`；本次明确纳入既有E2aCrossSourceHttpIT两处可配置端口改动作为必要设施依赖，该文件运行前后字节未改。其余继承源码保护见同目录inherited-protection.json。

独立工具消费 `d13-worker-closeout02/consumer/result.json` 为PASS：36条导出HTTP原文及1条stale HTTP核SHA；两份XLSX各7表、原顺序、前导零、整数克、无公式；PDF两页、原顺序/重量、无批注；三份业务orders完全一致；独立核Python控制回执、最终SQL三导出任务/一FAILED probe及probe零行。PDF两页PNG均实际查看，无裁切、重叠、缺字。dump SHA256 `05fe0f779ae187cbce5ff5b42f4b38566caf73e7f2f64be3e1c240a5f755ad7c`。作者及自审为同一助手，独立消费工具不称独立人员。

## 失败、自审修正与清理

- run1555将既有跨仓404错预期为403，尚未进入导出；原XML/HTTP/dump及failure-diagnosis.json保留，未记通过。
- run1556业务及消费者通过，但自审发现提醒检查可能消费默认关闭阶段旧行；新1557将该阶段提醒延后，准入前断言提醒0行，再由实际调度新增，完整复验通过。1556→1557产品源码未变；未复用旧数据库。
- run1555/1556认证停机、无消费者后清理合成datadir/binlog，逻辑445076580 bytes；D卷观测增加453824512 bytes，见closeout01 cleanup前后记录。
- run1557清理先因消费者PID22272拒绝；本次重新核该PID已退出、批次端口无监听、mysql-stopped.json认证停机且无消费者，单独运行cleanup1557.ps1成功。逻辑222637362 bytes；D余量57108062208→57335005184 bytes，卷观测增加226942976 bytes；C78778314752/E195634057216 bytes。逐文件SHA、输入/命令/结果、原SQL/HTTP/XML/日志/开发DER及源码保留，见closeout02的manifest及cleanup前后记录。

## 尚未覆盖与接续

本次只证明正常同步renderer排空和普通失败退根。RecoveryOperationLease明示完整retirement/quarantine/halt未实现；ExportRendererProcess的EXPORT_PROCESS_DRAIN_INCOMPLETE若被worker转为失败，入口finally仍可能关闭root，未确认子进程排空时是否过早释放围栏尚未验证，列为下一切片。不得据本报告声称完整文件/进程围栏或D13通过。

未改Android/Web产品、契约、迁移或任何生产开关；未运行Android、Chrome、完整L2或最终L3。D16合法退出资格仍缺可信依据；七类生产能力、TEMP生产归档、localDeletionAllowed=false及原库/现网/正式签名边界保持。

## 提交前逐路径影响与验证清单

以下每行的SHA是本次实际运行字节；复用完整路径/SHA见上述source-bindings.json（未改的依赖不重复纳入提交）。

| 改动路径 | 影响层 | 必跑及实际结果 | 运行字节SHA256 |
| --- | --- | --- | --- |
| `server/src/main/java/com/acrylic/outbound/jobs/BackgroundWorkerProcess.java` | 调度/恢复准入 | run1557默认关闭、准入后调度/持根/回滚/stale通过 | `7dc9a776681774ac4dcf8c41c3f1ef68ea55fa4cc6d553473522b174925ef1ac` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aAdmittedWorkerExportIT.java` | IT/必要夹具 | run1557真实HTTP/MySQL/调度/子进程通过 | `5eb6613c96deb0e4dbde17bd6b42e29e5331c2ce87bde0d1fe554a8f9ac9d145` |
| `server/src/test/java/com/acrylic/outbound/e2averify/RecoveryWorkerHttp.java` | IT/必要夹具 | run1557真实HTTP/MySQL/调度/子进程通过 | `84aa1a4464e3cca7dad3371c6c32e287b67a9d8529886849dd9afcdb6951a4b7` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aCrossSourceHttpIT.java` | IT/必要夹具 | run1557真实HTTP/MySQL/调度/子进程通过 | `c57a244b3f96976c1538c18e9db6ab12f8c0a3ea4e57718185505e29e17a6f1c` |
| `server/src/test/java/com/acrylic/outbound/inventory/RecoveryExportInput.java` | IT/必要夹具 | run1557真实HTTP/MySQL/调度/子进程通过 | `d3adc7b26d51e82c82ad6b23abec281fde1db4e6183924f1ae163368dd43abda` |
| `server/src/test/java/com/acrylic/outbound/recovery/RecoveryAdmissionEntryFixture.java` | IT/必要夹具 | run1557真实HTTP/MySQL/调度/子进程通过 | `a87a820a4fcb0ab5ee80866a70ee128c5740fa25961317335219ec5c01c898f4` |
| `server/src/test/java/com/acrylic/outbound/recovery/RecoveryWorkerProbe.java` | IT/必要夹具 | run1557真实HTTP/MySQL/调度/子进程通过 | `ebb2e58bea74871bce01c8b81cece4940760fc46bcacc207da02b694128bb261` |
| `server/src/test/java/com/acrylic/outbound/recovery/RecoveryWorkerChecks.java` | IT/必要夹具 | run1557真实HTTP/MySQL/调度/子进程通过 | `687a092958eb6aaee4863401cb63e0bb195b1c0ea2a639147e681381cac86fb4` |
| `docs/parallel/R09/tests/run_d13_admitted_worker.py` | 隔离运行/独立消费 | 新隔离run1557及实际文件/SQL回读通过 | `9dd0c5cc42302766256e7f6b505a831ba0e44e2539ed15ff4d9b278c0d90fc15` |
| `docs/parallel/R09/tests/d13_export_file_consume.py` | 隔离运行/独立消费 | 新隔离run1557及实际文件/SQL回读通过 | `6eb03cedb1326d0e9ac7e0e7993573fb1d8851ef4b410a6d069589fa40b6d7e2` |
| `docs/parallel/R09/tests/verify_d13_admitted_worker.py` | 隔离运行/独立消费 | 新隔离run1557及实际文件/SQL回读通过 | `77aceef01c57c198e4f2b37a2d6799579184e9f6e32e8229befe9deb287838e2` |

本报告、D01矩阵、DEVELOPMENT_PROGRESS.md、history/README.md及ORCHESTRATION_STATUS.md只写实际验收入口与断点；旧看板归档ORCHESTRATION_STATE_before_d13_worker_20261017.md。文档必跑为引用/差异检查与version_facts -Write/-Check；不代替业务测试。最终全量工作树、patch、未跟踪副本与环境见inputd13worker-prepush06.json及推送后inputd13worker-postpush07.json的同号附件。
