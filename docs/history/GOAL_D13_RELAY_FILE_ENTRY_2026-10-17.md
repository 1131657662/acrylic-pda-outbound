# D13 原件中转收存文件入口 L1 开发备份

本切片仅验证RelayController application/zip→RelayIntake事务/权限/幂等→RelayArchive有界临时校验→RelayFiles原字节持久→信任匹配/记录/末幂等SQL。基底390274a55282f4ee8b26cd1c141dd48316fd2a10，固定树D:/acrylic-pda-outbound/codex/development，本聊天唯一写者。版本事实见看板生成块。只收存合成签名ZIP，没有冻结正文；不业务处理、记账或补造提交，不计D13整体/O03整体/L2/D18L3或总目标完成。

## 改动与输入

没有产品源码/契约/schema/配置改动。新增E2aAdmittedRelayIT、RecoveryRelayScenario/Faults、RecoveryRelayInput、run_d13_relay_file.py及verify_d13_relay_file.py/d13_relay_file_consume.py，RecoveryWorkerHttp增加application/zip与原件下载。commit-paths.json列13明确路径，impact-map逐路径说明影响层→必跑→复用路径+SHA，source-bindings共795项，直接Java与run1571输入零漂移。证据短路径在.local/goal-20261017-resume/d13-relay01/。

使用新隔离库、生成EC P-256密钥/登记设备/合成原包，内含12个固定ZIP成员，明确UNCONFIRMED_EXPORT_TIME。不读原身份或正式Keystore。原件中转默认false，隔离context才启用。七类生产能力/TEMP生产归档和localDeletionAllowed=false不变。

## 实际验证与独立消费

python docs/parallel/R09/tests/run_d13_relay_file.py --run 1570 --port 19349 --http 19455，session33064/exit0，89普通定向+1真实IT零失败/错误/跳过。该首轮覆盖收存/重放/原字节下载/同键并发/末SQL回滚/锁后撤权与P5根、原生writer探针和后继；自审发现关闭态无权及未知公钥输入尚未实际覆盖后，补测试而不改变产品，并使用新库1571复验，未复用1570数据库。

python docs/parallel/R09/tests/run_d13_relay_file.py --run 1571 --port 19350 --http 19456，session95849/exit0，同样89普通+1真实IT零失败/错误/跳过。功能关闭态主管和无权用户均409/ORIGINAL_RELAY_DISABLED。恢复控制default-off/P5下真实登录/CSRF/ZIP、原字节查询/下载、原键重放、异文409、越仓404、viewer403、未登录401；不同生成公钥只收存为UNVERIFIED，未冻结process409/ORIGINAL_NOT_FROZEN，查询EVIDENCE_ONLY；截断ZIP400。

P5两独立会话同键并发仅一记录。原生device行锁/performance_schema确认实际等待；此时文件已持久、应用根在途、签名索引持根，独立Python取writers.lock返回BLOCKED。原末api_idempotency_request trigger真实主键冲突→409/WRITE_CONFLICT→relay/幂等/审计整事务回滚→原键恢复。原生app_user锁等待后递增auth_version→401/SESSION_REVOKED、零新relay。排空后探针FREE且锁字节SHA不变，签名索引为空、进程记录保留、独立后继JVM启动。

.local/r09-control-venv/Scripts/python.exe docs/parallel/R09/tests/verify_d13_relay_file.py --attempt .local/e2a-cross-20260925-1571 --output .local/goal-20261017-resume/d13-relay01/consumer01/result.json，exit0。独立工具消费75条原HTTP元数据/正文SHA、6份ZIP/原下载/持久文件、逐成员长度/摘要/CRC、实际ECDSA签名、已登记公钥匹配及UNVERIFIED不匹配、原dump六条relay/幂等/审计、P5 HMAC/两进程/原生探针。submission保留4条非空种子，dispatch/ledger均零；未新增业务事实。dump SHA-256 0cc29fe68d63cb8f17b565b381c0bc7ec4e0c518570038b74cb2c7d13e38a874。

普通完整回归按ordinary-regression-reuse.json复用前切片server-test01：1140个server/main及report-rendering/src路径SHA相同，4756项零失败/错误、19条件跳过。没有本轮全量重跑声明；新增测试实际定向+IT执行。无Android/Chrome产品页面或协议改动，本切片不跑其业务组合；L2/L3仍未完成。

## 自审、保全与环境

self-review.json为作者自审，Python独立工具消费者不等于独立人员。run1570/1571均通过，无本轮产品失败；前序1568及其消费者失败、run03指定原件继续保留。inputd13relay-running01/02完整输入/patch/全部未跟踪副本/ownership/环境/远端保全；提交前/推送后另存。继承WIP、原资料/身份/回执/唯一证据/最终APK无覆盖、还原或重排。

两轮前后实际身份核对、认证停机exit0，DB19349/19350和HTTP19455/19456无监听，无项目Java重型任务；共享MySQL及其他项目仅观察、不连接/停止，原AVD/Keystore未变。完成消费者与逐文件manifest/命令/依赖SHA/结果/进程核对后，仅清可重建datadir/binlog：694文件、444809851逻辑bytes，D56084119552→56537739264，实增453619712bytes；C78865981440/E195634057216bytes。cleanup-before/after.json、run1570/1571-manifest-before-cleanup.json及../cleanup1570-1571.ps1保留回执。原ZIP/签名/HTTP/dump/XML/日志/配置/身份和全部继承改动保留。

本切片以13明确路径提交推送开发备份；实际Git回执见看板与git-receipt.json。不合main、不发布部署、不接原库现网、不用正式签名、不降低D16标准、不恢复取消链。下一步审计submission分块及source-evidence有效文件入口，补明确缺口，再推进E06/E07/E08受影响组合；D16可信合法退出依据、统一最终候选和第8节闸门仍未满足。
