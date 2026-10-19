# D13 导出重试效果门兼容切片

状态：run1567真实HTTP/MySQL及独立消费通过，仅本切片服务端L1开发备份，不计D13整体/L2/D18L3。基底bcbff6a54df5ece2b4b0c57bf82bacef8e710bc1，固定树D:/acrylic-pda-outbound/codex/development，当前聊天单写者。依总目标第3节自主授权执行有界修复，版本事实见看板事实块。

## 默认与完成标准

现行ExportRetry在有权事务中调用无类型queryForList锁app_user，内部getObject被P5效果门拒绝；同类根因已有run1562/1563明确调用事实。采用与创建/下载相同的显式byte[] ID读取，保持原SQL、锁、参数、权限复核、事务和幂等语义；不放宽getter、不开放新权限。无须改变已定业务政策。

| 改动路径 | 影响层 | 必跑标准 | 复用依据 |
| --- | --- | --- | --- |
| server/src/main/java/com/acrylic/outbound/exports/ExportRetry.java | 生产重试事务锁读取 | 默认关闭及P5准入后真实HTTP重试、原键重放、同键并发、异目标冲突、401/越仓拒绝、末SQL失败全回滚、锁等待后撤权；生产调度与下载保留同一快照 | 原创建/下载/worker/效果门路径按source-bindings精确SHA复用；受影响路径本次实跑 |
| 新IT/必要HTTP夹具与运行器/消费者 | 新隔离合成候选 | 新库非空迁移、真实会话/CSRF与HTTP、XLSX/PDF原快照/顺序/克/无公式消费、P5排空及认证停机 | run1564原生围栏/普通回归仅在未改路径SHA一致时复用；不是当次全回归 |

本切片仅补服务端入口L1，不能计D13整体/L2/D18L3。Android/Chrome最终组合继续单列；合成身份/签名不冒充原设备。失败计数沿真实根因记录，不重用旧run03或失败数据库。产品没有该次写入且明确仅夹具错误时才用快速修复通道。

七类能力、TEMP生产归档、localDeletionAllowed=false及D16标准不变；不合main、不部署发布、不用正式签名、不连原库现网。最终路径SHA、结果、失败、清理与提交回执沿实际证据追加。

## run1565 失败与新证据

run1565（隔离DB19344/HTTP19450）默认关闭/P5正向文件及同键并发留下原件后，在末审计trigger的SQLSTATE45000注入触发未知结果围栏，真实JVM halt70；Gradle exit1，IT未生成完整XML，不能计通过。已核`RecoveryConnectionHandler.knownStatementFailure`只认23/42类，原生45000并非本切片预期的已知语句失败。该保守围栏不放宽，不将本次归为无产品写入的快速修复。原binary/output.bin及可读转存/哈希在`.local/goal-20261017-resume/d13-retry01/run1565-failure/`，原HTTP/SQL/UNKNOWN标记保留；MySQL已认证exit0且无专属监听。

新验收输入改用隔离InnoDB表真实主键重复，由末审计trigger执行冲突INSERT，实际驱动返回完整性约束错误；核整事务任务/审计回滚后同原键成功。保留未知结果拒绝语义。运行器另补缺XML时保全退出码和二进制结果；新库编号复验，不覆盖run1565。

run1566（DB19345/HTTP19451）真实主键冲突按既有ApiErrors契约返回409/WRITE_CONFLICT，夹具误预期500使IT失败（1项/1失败），不是产品权限或回滚判通过；后续回滚/撤权尚未执行。普通定向原XML、HTTP和test-result均留在该attempt，认证停机exit0/无专属监听。修正夹具期望409并核WRITE_CONFLICT，消费者按原响应requestId独立核冲突，不改产品错误契约；新run1567继续完整验收。两轮原因不同，不将未知围栏和错误状态预期合并清零，也不称无产品写入快修。

## run1567 实际验收与独立消费

命令`python docs/parallel/R09/tests/run_d13_export_retry.py --run 1567 --port 19346 --http 19452`，runner/Gradle均exit0；82项普通定向、1项IT，零失败/错误/跳过。attempt为`.local/e2a-cross-20260925-1567/`，完整输入/patch、原XML/HTTP/初始化和Gradle命令、dump、身份及认证停机回执保留。实际从旧非空基线迁移到候选；隔离DB19346、HTTP19452已停机，无本轮监听。

- default-off与P5各自经真实Cookie/CSRF/HTTP创建、重试、原键重放和XLSX/PDF下载；两模式均401/越仓404/创建重试键冲突409。
- P5两个独立登录会话同键并发只建一任务；异目标同键409。末审计trigger真实主键冲突返回409/WRITE_CONFLICT，任务零行且审计计数不变，移除注入后同原键成功并下载。
- 原生连接锁app_user，performance_schema实际采到等待，再在持锁连接增加auth_version并提交；已等待请求401/SESSION_REVOKED且不建任务。仅隔离测试用户授予performance_schema SELECT，不改产品权限。
- P5排空后索引为空，原两个应用进程记录保留，独立后继JVM正常启动并增加第三条记录。SQL45000仍按原未知边界拒绝，没有放宽效果门。

独立Python消费命令使用已安装的bundled Python执行`verify_d13_export_retry.py --attempt .local/e2a-cross-20260925-1567 --output .local/goal-20261017-resume/d13-retry01/consumer02/result.json`，exit0。首次系统Python缺pypdf而未开始消费，原`consumer01.log`保留，不记产品失败，也未重跑产品。consumer02核80条原HTTP状态/正文SHA、10份导出（6 XLSX/4 PDF）、固定原快照、逐行顺序/整数克/无公式、并发/末SQL回滚/撤权、dump任务数和P5 HMAC/原进程记录；dump SHA256 `0296c442eda587f4723609203589f713bfb932dbb6b0b77a45b9e2062ddeabf2`。独立工具由同作者编写，不冒称独立人员审查。

四份PDF共8页全部用Poppler渲染，8张PNG只有3种不同SHA；三种实际看图，另五张按逐字哈希相同复用，无裁切/重叠/中文缺字。命令、原PDF绑定、raster-hashes及visual-review在`d13-retry01/pdf-preview/`。原件与重试PDF元数据虽各自有任务标识，页面仍一致，文件SHA分别核对。

## 输入、自审、复用与清理

`d13-retry01/source-bindings.json`和`candidate-binding.json`绑定7直接路径及依赖，逐路径与run1567 inputs一致；`impact-map.json`给出改动路径→影响层→必跑→具体复用路径/SHA。生产改动只有ExportRetry一行显式byte[]读取；SQL/参数/行锁/事务/权限/幂等保持。新IT/夹具/运行器和消费者均实际消费本轮输入，不以静态检查代验收。

`reused-server-regression.json`核前轮完整普通回归输入的2458个服务端/renderer源码文件，只有ExportRetry和HTTP测试夹具两个明确改动；新增本切片夹具由本轮编译和真实IT覆盖。未受影响路径按前轮`d13-retirement01/server-test02/result.json` SHA `06d903f7a259edabece1a92e67146fd0496544a47d365f332c2047e04de1386f`复用，不能写成本轮完整普通回归。`inherited-protection.json`对5578项基底核无缺失或意外改动；提交前完整快照另记看板。

run1565/1566/1567已认证停机，核进程/监听及无消费者，清理前保存三轮逐文件SHA、输入/patch、初始化/运行/认证停机命令与结果及dump/失败摘要；只清可重建合成datadir/binlog。共1041文件/667992570逻辑bytes，D盘56271433728→56952586240 bytes，实增681152512。C78732627968/E195634057216 bytes；`cleanup-before.json`/`cleanup-after.json`存精确路径/文件数/进程及前后磁盘。原XML/HTTP/dump/日志/UNKNOWN签名/身份、所有继承WIP、handoff-final01、run03指定失败及最终APK保持。

本轮无Android/Chrome产品变化；它们及跨入口恢复组合并未执行，不因服务端L1通过免除D13整体/L2/D18L3。七类生产能力、TEMP生产归档、localDeletionAllowed=false、D16可信资格标准、原库/现网/main/发布边界保持。下一步沿逐路径范围审计核D13有效文件入口及E06/E07/E08剩余受影响组合，不恢复取消链；总目标保持active。

14明确路径开发备份已提交推送`8b6aab3d0b08916c703bb7e660ec6fbdf64cd88f`，实际远端一致；提交前完整`inputd13retry-precommit06.json`共5579项/SHA `6f4a48fcabe8a40e81537cbe1977cda9a5097ae3546f8f7c3682d9e43b1d8b4c`。104项继承未提交状态保持；提交后文档断点另存，不把后补回执混作已提交内容。
