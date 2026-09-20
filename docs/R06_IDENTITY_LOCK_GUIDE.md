# R06 L1 日常身份写锁闭合

日期：2026-09-20。固定基线 `6852da9fe5a02833eca22735a0d193cbfc64b346`，工作树 `D:/acrylic-pda-worktrees/r06-identity-locks`，短分支 `codex/r06-identity-locks`。本批只闭合既有身份写路径的 G/W/U 并发反序，不开放 TEMP 决定。依据 [锁图](parallel/R06_M2B/LOCK_GRAPH.md) 和 [交接](parallel/R06_M2B/HANDOFF.md)。

## 范围与调用链

新 `IdentityWriteGuard` 必须加入调用者已有事务；先 E（server_state 共享锁）再 G（user_management_guard 排他锁），在首次仓库/设备锁及身份关联写入前获取。业务入口继续 NORMAL/epoch 校验；认证入口仅取得 E/G，保留恢复期间认证行为。没有把 G 放进深层 PdaAuthLocks 或全部 ServerState 调用。

| 接入点 | 覆盖入口 / 锁后校验 |
| --- | --- |
| UserAccess | 人员新增、修改及改密；原 G 保持，复用前缀 |
| MutationAccess | 设备、正式单、库存/映射/政策发布、PC及配对审批；新增锁后完整 Web 鉴权 |
| SubmissionAccess | 正式/TEMP整包、分块、finish/abort、归档及正式 Web 审批；保留来源与锁后身份核对 |
| RecallAccess | 正式召回、恢复、续作；后续 ContinuationAuthorization 的 W/D/U 均在 G 之后 |
| PdaAccess / TemporaryAccess | 正式领取、准备、续期、条码同步及 TEMP 准备/确认；正式条码分支锁后重读身份 |
| PdaAuthentication / PdaChallenges | login、refresh、logout、挑战签发；E/G 后 W/D、会话或挑战写入；恢复模式行为保持 |
| PdaAuditService / AuditArchiveService | 同原设备 FORMAL/TEMP 连续审计及归档，不拆流 |
| ImportWriter | 文件解析外置，保存事务显式 RC；E/G 后重验导入权限与原 actor，再 W |
| SyncService | 订单同步虽是 GET，但会创建流/基线；E/G 后重验 pda:assign |
| PairingChallenges | CREATE 与消费；消费先提示读 epoch，E/G 后重锁原挑战并核对有效期、绑定及签名，原事务一次消费 |

R08 导出 RR 特殊次序、Job/Worker、恢复控制、普通只读查询、schema、API、Android 均不改。精确 U/H 集合排序、PC 原人集合变化 C04 属于 L2；不能据此关闭全部 M2b 写阶段阻断。共同 G 会增加跨仓排队，本批记录锁等待和事务耗时，不承诺吞吐达标。

## 验证环境与恢复断点

新合成库 `acrylic_outbound_l1_20260920`、独立账号、签名及私有文件全部在本任务 `.local`；创建前核对 127.0.0.1:13316 的 datadir，拒绝既有同名库。原 R05/R06 库、恢复断点、签名、R08 预览、AVD/M71 和现网未操作。

测试使用真实 UserService 更新 U 后、grants 前暂停，再让 PC/续作/PDA竞争；performance_schema 的 data_locks/data_lock_waits 保存连接号、真实等待资源和两端持锁集合。九组取证的两个事务均为 READ-COMMITTED、autocommit=0，等待行是 G 的 X,REC_NOT_GAP；竞争者尚未取得 W/D 行锁。没有用“线程未结束”推断锁，也没有 mock 数据库或事务。注入只暂停真实 SQL/鉴权边界。

`PublicationConcurrencyIT` 的原手工 W 先锁夹具改为合法 E/G/W，保留“等待者不能覆盖已提交新基线”原测试意图，两个出版并发测试通过。R08 生产入口和 RR 时序完全未改；其测试夹具只追加精确 L1 URL/账号白名单，原库未使用。新增 L1 夹具在 Spring 初始化前核对精确专库 URL/账号，进入测试再检查实际 DATABASE。

## 本次实际结果

| 验证 | 结果及界限 |
| --- | --- |
| 新增身份锁 26 例 | 全通过；C01 的 PC review/resume 与续作原人三例；C02 的库存发布/正式发布/PC审批×禁用/角色/仓库撤权九例；C03 login/refresh/logout 三例；恢复期认证与无事务拒绝两例；C05 TEMP准备/上传/取消×设备停用/临时准入撤除六例；C06 跨仓设备写/导入/配对确认三例 |
| 原功能组合及补充 | 按 classname+testcase name 去重，**404 项集成用例通过（含26新增）**，最终失败0；另1项 `TemporaryAndroidSubmissionIT` 缺实际Android冻结输入跳过，不计通过。覆盖人员、设备、库存/政策/订单发布、配对、PDA身份/领取/准备/续期、PC、召回/续作、正式/TEMP提交/审计、归档、订单/条码同步、R08撤权 |
| 单元与构建 | 35项后端单元通过，compileJava/compileTestJava及bootJar成功；没有APK构建、HTTP服务器、设备、模拟器或部署 |
| 独立只读审查 | W3核对14生产文件、9测试文件指纹、入口顺序、三批XML及9份原始P_S证据，无阻断；审查未连接数据库或重跑测试。源码指纹在 `.local/evidence/l1-reviewed-source.json`，SHA256 `b27bdab2ee5f2ff09f148d1ec98e436bd03790d37bc5f5cd565187c06f271ab2`；专项文档不在该源码指纹内 |

C01失败业务操作保持原作业状态/占用且零dispatch；C03禁用提交后login/refresh拒绝且未消耗原挑战，logout允许安全重复撤销、不新发会话；C05原资格签名、manifest、来源、提交及占用计数保持。C02撤权由真实人员管理服务提交，角色/仓库变化同时递增版本，旧Web会话被锁后校验拒绝。已提交的合法历史不会被撤权回写抹掉。

本机证据目录均在本工作树 `.local/evidence`，不进Git：`regression-first/test`保存35单元；`regression-first/integrationTest`保存364项首轮（357通过、6护栏失败、1跳过）；`regression-supplement`保存47项全通过（含原6项R08及41项追加回归）；`final-identity`保存最终26项全通过；`final-test-summary.json`按用例去重。多轮重复运行不累计为新用例。

## 首轮失败与修正

1. 首次构建在报表字体下载时 Connection reset，尚未执行测试。改用已有同版本本机字体，通过项目固定SHA256校验；`compile-initial.log`保留。生产Java编译已通过，但不能把该轮整体写成成功。
2. 字体重试后35单元中34通过、1项缺被忽略的 `docs/sku.txt`。安全复制主树已有获授权样本，摘要一致，后续35通过。初轮XML在 `initial-unit-failure`。
3. 新锁测试首轮14项中10项失败：Mockito桩直接作用于带MANDATORY的事务代理，在设置桩时触发无事务错误，随后unfinished stubbing。改为桩真实目标，调用仍经生产事务代理。第二轮20项中19通过、1项恢复夹具误用未来 `RECOVERING` 字符串而违反现有V1 CHECK；改用现行 `RECOVERY_PENDING`，没有修改schema或业务白名单。失败分别在 `identity-first-failure`、`identity-second-failure`，第三轮20通过留于 `identity-third-pass`。
4. 组合首轮6项R08测试在业务执行前被原专库硬护栏拒绝。只增加本次精确L1 URL/账号组合并配置已有真实报表运行目录，补跑6项通过；没有连接原R08预览库、修改导出生产代码或放宽为任意库前缀。

## 锁成本与剩余范围

九个人为短屏障场景、18个事务样本记录 E/G 前缀调用至返回的获取耗时（包含 E/G 请求及数据库往返，不是纯 G 等待）和返回至事务提交/回滚的持有时长。最近秩p95：前缀获取 **107.4763 ms**，持有 **229.1919 ms**。慢样本为登录密码核验；该工作在G内，会串行拖延其他仓库的身份关联写入。`lock-timing-summary.json`的 `WaitMs` 表示上述前缀获取耗时，并包含每场景原证据文件与时长；原JSON包含两连接号和持锁集合。此统计含测试暂停和观察成本，**不是压测、吞吐结论或SLA**；文件解析仍在导入事务外，导入批量写、库存发布和大提交的生产负载尚未测。

未在旧实现上重放真实死锁，因此本批只能说原静态G/W/U反序按已覆盖入口闭合、上述并发场景通过，不能写“复现并修复所有死锁”。L2的精确U/H集合、PC原人变化C04仍未实现；新TEMP命令/决定、关联/拒绝处置、共享记账/成功签名/Android保存与归档，及R09恢复能力/Job fence均保持待办。真实HTTP/设备/现场和长时负载未验。L1是M2b写阶段的一个前置检查点，不关闭整个写阶段阻断。

版本边界：契约0.28/Flyway V33/Android code20/Room17不变；没有迁移、API字段或产品行为开放。新专库及签名/文件/证据保留，测试进程全部退出，共享MySQL保持运行。仅交付短分支源码；合并、main、推送与部署状态以总调度实际操作记录为准。
