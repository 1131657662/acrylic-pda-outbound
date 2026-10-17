# D13 库存文件入口 L1 开发备份

本切片补真实库存 multipart 文件入口在 default-off 与 P5 下的文件/事务/幂等守恒。固定树 D:/acrylic-pda-outbound，codex/development，基底 8b6aab3d0b08916c703bb7e660ec6fbdf64cd88f；唯一写者聊天01a147c5-0495-7703-ba98-2e7637cbc619。版本事实见看板生成块。只计服务端有界L1，不计D13整体、L2、D18 L3或总目标完成。

## 改动、输入与复用

实际调用：ImportController multipart→ImportService权限/状态/有界读取/GB18030 parser→ImportFiles内容寻址文件→ImportWriter事务/身份复核/幂等/行/审计→ImportQueries。文件持久化早于DB事务，未引用文件不代表可交付导入或库存发布。

产品仅修改 common/IdempotencyRequests.java：将两列通用queryForList/getObject改为显式getString与局部Stored record。SQL文本、参数顺序、键验证、摘要比较、UUID转换、事务和权限均不变，效果门不放宽。没有契约、schema、配置或生产开关改动。该共用类34个直接消费者逐路径SHA在证据idempotency-direct-consumers.json；其他入口未因此取得P5真实HTTP通过记录。

新增E2aAdmittedImportIT、RecoveryImportScenario/Faults、RecoveryImportInput和HTTP上传方法；运行器run_d13_import_file.py、原生writer探针、verify_d13_import_file.py及文件/dump独立消费者。原始合成文件由SkuFixture生成，不读取原sku/原身份，不发布库存。测试证据按content和facts分名、CREATE_NEW，避免重名覆盖。

本报告证据短路径位于.local/goal-20261017-resume/d13-import01/。commit-paths.json列17个拟提交路径；impact-map.json逐路径列影响层→必跑→复用路径+SHA；source-bindings.json共734路径。candidate-binding.json绑定run1569输入及consumer05；直接Java与运行输入零漂移，full-regression-input-binding.json核server/report-rendering源码至本轮普通完整回归零漂移。前序导出/Android/Web证据仅按未受影响路径复用，不把本次共用映射修复扩写为其他入口全通过。

## 当次验证

命令 python docs/parallel/R09/tests/run_d13_import_file.py --run 1569 --port 19348 --http 19454，session52350/exit0；原件.local/e2a-cross-20260925-1569/。真实非空旧基线迁移后启动应用，93普通定向+1真实IT零失败/错误/跳过。两个模式均实际登录Cookie/CSRF/multipart、查询预检与逐行、原文件SHA、原键重放、异文409、越仓404、viewer403及未登录401。

P5覆盖两独立会话同键并发只生一导入；原生warehouse锁与performance_schema确认实际等待，等待期间文件已持久、liveRoots>0、签名索引持根，独立Python进程取writers.lock被拒。原末审计trigger触发真实主键冲突，HTTP409/WRITE_CONFLICT，导入/行/幂等/审计事务回滚，再用原键恢复成功。原生app_user锁等待后递增auth_version，401/SESSION_REVOKED、零新导入。排空后探针FREE且锁字节哈希不变；P5索引为空、原进程记录保持，独立后继JVM启动。

命令 python docs/parallel/R09/tests/verify_d13_import_file.py --attempt .local/e2a-cross-20260925-1569 --output .local/goal-20261017-resume/d13-import01/consumer05/result.json，exit0。独立工具消费60条原HTTP元数据/正文SHA、5份118字节GB18030原文件、10行原顺序/字段/整数克、持久文件、原dump的导入/逐行/幂等/审计引用、P5 HMAC/两进程和原生BLOCKED/FREE探针。每份601500克，未发布库存；非空旧种子与本轮仓库/ID明确区分。dump SHA-256 7dd7754402f0744d2cbe6d7a595a12ae6cf6f67a29718ea7d67e0073d050a88e。

共用类影响普通完整回归：gradlew.bat -p server test -x :report-rendering:prepareFont --offline --no-daemon --console=plain，session91122/exit0。308套件4756项、零失败/错误、19条件跳过；原XML/逐套件SHA/全部skip在server-test01/result.json和xml/，日志../import-server-test01.log。普通测试不算IT；跳过不计执行。无新增Android/Chrome页面或协议行为，本切片未跑其业务组合，保留L2/L3未验。

## 失败、自审与保护

run1568/session24750 exit1：普通93项通过，IT1项失败；default-off已执行，P5首次导入成功后原键重放getObject触发CONTROL_EFFECT_UNCLASSIFIED/500。是产品失败，有产品写入，不套用无写入快修；XML/原HTTP/SQL/dump/gradle/签名原件及run1568-failure-summary.json保留。新库1569复验，未重启失败库、未放宽未知围栏。

consumer01启动被无关PDF依赖pypdf缺失阻断，移除不需要的跨消费者依赖后consumer02/03消费同原件通过。consumer04补强全库计数时误忽略非空旧种子失败；consumer05按本轮仓库及原ID精确核对后通过。所有失败log保留，未为消费者错误重跑产品。self-review.json为作者自审，Python是独立工具消费者，不声称独立人员审查。

完整输入/继承改动保护：inputd13import-failed01.json与同号patch/未跟踪副本/ownership/环境保全；后续运行、提交前和推送后快照逐次保存。handoff-final01、run03指定失败、原资料/身份/回执/唯一证据/最终APK及继承WIP保护；不还原、重排或覆盖无关文件。

## 环境、清理与下一步

1568/1569均实际身份前后核对、认证停机exit0，专属DB19347/19348与HTTP19453/19454无监听。完整普通回归也已结束，无项目Java重型任务。共享MySQL及其他项目仅观察，不连接/停止；原AVD/Keystore未改变。

按用户持续清理授权，在消费者完成、输入/命令/依赖SHA/结果/逐文件manifest/停机/进程均保全后，只清两轮可重建datadir/binlog：694文件、444713379逻辑bytes；D盘56101183488→56554676224，实际增加453492736bytes。C78866210816/E195634057216bytes。cleanup-before/after.json、run1568/1569-manifest-before-cleanup.json及../cleanup1568-1569.ps1保存回执；原XML/dump/HTTP/日志/签名/身份均保留，tmp未列清理目标。

提交推送按17明确路径，仅服务端L1开发备份；实际Git回执以看板和推送后快照为准。下一步核D13有效文件入口（submission/relay/source-evidence）的实际调用及既有P5证据，优先补有明确缺口的入口，再推进E06/E07/E08受影响组合。D16缺可信合法退出依据、统一最终候选和D18全链L3/第8节闸门仍未满足。七类能力/TEMP生产归档关闭，localDeletionAllowed=false；不接原库现网、不合main、不发布部署、不用正式签名，取消链不恢复。
