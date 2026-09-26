# PDA 订单刷新、领取与本机任务排查报告

日期：2026-09-23。范围：`codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80` 的源码、既有验收记录及连接中的 M71 只读检查。本报告是问题分析，不是修复或性能验收。

## 结论与核查边界

用户所报慢、DNS 失败、待准备卡片缺车号、本机任务混杂，都是**当前现场问题**。待准备显示和任务混杂有明确源码证据；慢的具体瓶颈、DNS 间歇失败根因还缺现场数据。既定路线要求现场阻断问题当步修复，不应笼统推到 R10。

当前 M71 `22D2910042` 安装 staging code20 / `0.15.0-r06-submission-test`。本次电脑与 M71 的单次域名解析均得到 `114.67.203.242`；这不能证明故障发生时 DNS 正常，也不证明 HTTPS 可达或快速。本次未触发真实领取、刷新、下载、上传；未读私有日志、执行 SQL `EXPLAIN` 或测规模性能。下文“已证实”仅指源码路径，不等于逐项在真机重现。

优先级：P1 表示影响当前领取、识别、状态或恢复；P2 表示已见到代码结构带来的体验/规模风险，尚待量化贡献。

## 问题、影响与修复

### PDA-01｜P1｜刷新订单被串行同步与补传拖住（结构已证实，耗时待测）

- **问题**：`refreshData()` 顺序运行订单同步、条码同步、审计归档/补传、逐本机订单召回检查、两个订单列表请求和提交归档核对。订单与条码同步各可连续最多 20 批。列表请求排在后面，前面的异常可直接阻断它。
- **导致**：按钮长时间转圈；条码或审计延迟/失败也会让订单列表保持旧值，操作员无从知道具体卡在哪一步。
- **修复**：记录各阶段耗时、请求数、响应码和脱敏关联 ID；把查看列表与耗时同步的状态/反馈分开，显示最后成功时间和部分失败。优化调度时不得跳过身份、恢复、召回及作业安全屏障，再据数据调整批量与后台执行。
- **依据**：`android/app/src/main/java/com/acrylic/outbound/pda/ui/PreparationViewModel.kt:71`；`android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncRepository.kt:14`；`android/app/src/main/java/com/acrylic/outbound/pda/sync/BarcodeRepository.kt:14`；`android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncRun.kt:10`。

### PDA-02｜P1｜领取/准备成功，后续刷新失败却报“操作未完成”（已证实代码路径）

- **问题**：领取持久化原意图并请求后台成功后立即执行整套刷新；准备本机激活、确认后也执行整套刷新。写操作和后续查询处于同一个外层 `try/catch`，刷新失败只展示最后异常，原待领取列表可能仍旧。
- **导致**：后台其实已经占用订单，但操作员看到旧“领取订单”卡片或失败提示；再次点击可能生成新意图并收到 `ORDER_ASSIGNED`。准备完成也可能被误报失败。后台唯一占用保护仍在，不能声称已发生双领或丢资料。
- **修复**：分开“领取提交结果”“本机准备结果”“列表同步结果”；确认成功立即显示已领取/已准备卡片，后续失败明确提示“领取成功，列表更新失败”。旧待领取行不可继续作为新请求入口；未知写结果只恢复原幂等请求并对账。补 POST 成功、其后 DNS/条码/列表失败的回归测试。
- **依据**：`android/app/src/main/java/com/acrylic/outbound/pda/ui/PreparationViewModel.kt:61`、`:65`、`:119`；`android/app/src/main/java/com/acrylic/outbound/pda/preparation/PreparationRepository.kt:45`、`:63`、`:75`。

### PDA-03｜P1｜已领取待准备卡片缺车号和业务订单号（已证实）

- **问题**：待领取接口提供 `vehicleNo/orderNo`，但本机活动 assignment DTO 不含这两个字段；待准备卡片只显示内部 `orderId`、人员与期限。下载成功后才从验签计划展示车号、订单号。
- **导致**：下载前无法核对车辆，内部长标识易被误认成业务订单号，增加错单风险。`OB...` 是保留的旧**业务号**，不同于内部 UUID，不得伪造为 FH 新号。
- **修复**：在授权范围内，从领取时固定发布版本提供可信车号、订单号和计划摘要；领取确认结果可立即显示已核实信息，离线缓存标来源与时效，下载后以验签计划为准。未决领取仍明确标“内部标识”，不能冒充业务号；M71 320dp/放大字体核长号换行。
- **依据**：`server/src/main/java/com/acrylic/outbound/pda/PdaOrdersController.java:18`、`:26`；`server/src/main/java/com/acrylic/outbound/pda/AssignmentStore.java:121`；`android/app/src/main/java/com/acrylic/outbound/pda/ui/OrderCards.kt:43`；`docs/ORDER_NUMBER_GUIDE.md:23`。

### PDA-04｜P1｜本机任务缺真实状态分类（已证实）

- **问题**：界面依次展示全部 `prepared_order`、当前人员全部领取意图及尚未准备的活动授权。卡片“资料已就绪”只讲授权/下载，不查看作业、冻结、待上传、待审或成功回执。成功提交的本机原证据按保护规则仍在数据库。
- **导致**：已完成和未完成混杂，历史越来越长；“资料已就绪”可能被误会为仍要扫码。真正的待领取在另一分段；本机页所谓“没领取”更可能是未决或被拒意图，需现场卡片/截图进一步对应。
- **修复**：组合本机作业、提交回执、召回和授权事实，分“需立即处理／作业中／待上传或待审核／待准备／历史已完成／领取异常”，提供筛选、数量、来源和单一下一动作；冲突时显示待核对。不得清空成功/失败/原始证据来让界面整洁；测试跨人、离线、恢复及全部状态。
- **依据**：`android/app/src/main/java/com/acrylic/outbound/pda/data/PreparationDao.kt:7`、`:17`；`android/app/src/main/java/com/acrylic/outbound/pda/ui/PreparationViewModel.kt:95`；`android/app/src/main/java/com/acrylic/outbound/pda/ui/OrderCards.kt:15`、`:35`、`:43`；`android/app/src/main/java/com/acrylic/outbound/pda/ui/SubmissionActivity.kt:69`；`docs/pda-page-template.md:77`。

### PDA-05｜P1｜被拒绝的领取意图阻断同单新版本再次领取（额外发现，已证实代码路径）

- **问题**：`claim_intent` 以 `orderId` 为主键。版本冲突、已占用、状态冲突、恢复代次或库存基线变化会把原意图标记为 `REJECTED`。同单后来重新发布为 READY，`claim()` 仍取旧意图，`retryClaim()` 又立即拒绝 `REJECTED`；刷新并不转换该行。换操作员后，同一旧意图也可能触发原人员限制。
- **导致**：即便服务器现已允许领取，本机仍被旧拒绝记录卡住；“刷新订单状态”不会解决，容易误认为服务器或网络故障。这是由代码推得的场景，尚未在真实订单上执行破坏性复现。
- **修复**：严格区分“恢复原未知请求”和“后台已确认新 READY/新版本后的新领取”。保留旧拒绝意图作为历史证据，在核实订单版本、恢复代次和当前人员设备后建立新意图/新幂等键；调整单 `orderId` 主键模型或另存历史。测试旧版冲突→修订再发布→安全新领、跨人员切换和原未知请求恢复，不绕过服务器互斥。
- **依据**：`android/app/src/main/java/com/acrylic/outbound/pda/data/ClaimIntent.kt:7`；`android/app/src/main/java/com/acrylic/outbound/pda/data/PreparationDao.kt:16`；`android/app/src/main/java/com/acrylic/outbound/pda/preparation/PreparationRepository.kt:45`、`:53`、`:66`；`android/app/src/main/java/com/acrylic/outbound/pda/ui/OrderCards.kt:35`。

### PDA-06｜P1｜偶发 DNS 错误及原始英文异常直接展示（现象已报告，根因待定位）

- **问题**：现场收到 `Unable to resolve host ... No address associated with hostname`，表示当次域名解析未得到可用地址；本次单次解析成功不能排除间歇性设备网络、DNS 或网络切换。网络层未把这一类异常转换成清楚的中文状态，外层直接显示异常 `message`。
- **导致**：刷新中断；在领取请求前/后发生时，仅凭英文报错无法判断后台是否处理了原请求，易反复点击。尚不能断言域名永久配置错、API 宕机或特定运营商故障。
- **修复**：按同一时间戳记录 M71 所连网络、DNS/解析、HTTPS 阶段并对照网关/nginx/API 日志；第二台设备及异地网络复测。读取失败可提示上次成功时间及安全重试；写结果未知必须保留原幂等意图并查询状态。不得写死 IP、关闭 TLS 校验或绕过恢复保护。
- **依据**：用户现场报错、本次 DNS 快照；`android/app/src/main/java/com/acrylic/outbound/pda/network/PdaHttp.kt:23`、`:29`；`android/app/src/main/java/com/acrylic/outbound/pda/ui/PreparationViewModel.kt:129`；`docs/R05_PENDING_ACCEPTANCE.md:18`、`:19`。

### PDA-07｜P2｜HTTP 客户端重建与大库存准备包风险（额外发现，贡献待测）

- **问题**：`PdaSession.network()` 每次创建 `PdaNetwork`，构造器再创建独立 `OkHttpClient`，连续请求不能共享该客户端的连接池。请求总超时 90 秒、读取超时 45 秒；准备时服务端首次读取整个固定库存快照并打包三部分，PDA 顺序下载、解压、验签、激活。尚无耗时、压缩字节数及服务端 SQL 执行计划证据。
- **导致**：重复建连和大包准备可能增加等待，首次准备可能明显慢于再次下载，也可能碰到超时或单部分 16MiB 限制。不能断言这是此次慢的主因，更不能擅自缩减防重所需库存范围。
- **修复**：测 DNS/TCP/TLS、首次准备事务、打包、各部分下载、校验、Room 落盘耗时/体积；隔离副本用真实规模查看 `EXPLAIN`。复用受控 HTTP 客户端/连接池并隔离环境、身份和恢复代次，按数据决定缓存、传输和超时策略；下载失败仍保留占用和原包身份。
- **依据**：`android/app/src/main/java/com/acrylic/outbound/pda/network/PdaSession.kt:14`；`android/app/src/main/java/com/acrylic/outbound/pda/network/PdaHttp.kt:23`、`:46`；`android/app/src/main/java/com/acrylic/outbound/pda/preparation/PreparationRepository.kt:92`；`server/src/main/java/com/acrylic/outbound/pda/PreparationService.java:19`；`server/src/main/java/com/acrylic/outbound/pda/PreparationPayloads.java:11`。

### PDA-08｜P2｜本机全量列表在状态变化时反复重建（额外规模风险，贡献待测）

- **问题**：`orders()` 无本机分页/状态过滤；界面每次状态更新先 `removeAllViews()`，再解析每单 JSON、重建全部卡片。忙碌和提示变化也引起重渲染。远端待领取每批 20 条，但本机历史没有窗口限制。
- **导致**：本机资料积累后，打开首页、刷新进度、返回首页可能卡顿或扰动滚动位置；当前卡片数量与帧耗时尚未测量。
- **修复**：按状态/时间分段查询 Room，使用稳定 ID 的可复用列表与窗口分页，避免无关状态更新时全量重建；保留当前滚动位置和未处理入口。M71 实际历史量、320dp 与字体放大下测帧耗时/内存。
- **依据**：`android/app/src/main/java/com/acrylic/outbound/pda/data/PreparationDao.kt:7`；`android/app/src/main/java/com/acrylic/outbound/pda/ui/MainActivity.kt:52`、`:74`；`android/app/src/main/java/com/acrylic/outbound/pda/ui/OrderCards.kt:12`；`docs/pda-page-template.md:79`。

### PDA-09｜P1｜修复版的 App 内升级通道另有已知阻断（已有记录，非此次卡顿根因）

- **问题**：当前旧包接受的服务端兼容值硬编码到 `0.20.0`，而 test 后端契约已为 `0.30.0`；项目记录明确旧 code15/16 无法诚实地通过 App 内检查升级到 R06 候选包，现场 code20 是手动覆盖安装。
- **导致**：即使上述问题修复并生成新 APK，也不能未经验证就承诺在旧设备上点“检查更新”自动获得修复。手动安装也须继续校验身份、签名、Room 与未上传资料保留。
- **修复**：沿现有 R05-OPS-01 单独修兼容声明机制并完成可信 HTTPS/安装验证；正式发布前按原版本和数据状态测试升级路径，不改假版本号骗过检查。此项不影响本文的优先修复分类，但直接影响交付方式。
- **依据**：`android/app/src/main/java/com/acrylic/outbound/pda/update/UpdateManifestVerifier.kt:31`；`docs/R05_PENDING_ACCEPTANCE.md:9`；`docs/ORCHESTRATION_STATUS.md` 顶部 PDA 升级状态。

## 实施顺序与验收

1. **先保留现场事实**：仅收集脱敏的每阶段耗时、请求/服务端关联 ID、DNS/Wi-Fi、设备版本和包大小；不在真机上为诊断随意重领、提交、清库、重置或覆盖证据。建立刷新/领取/准备的同条件基线，区分设备、网络、服务器、数据库与本机 UI 时间。
2. **先修操作结果真实性**：PDA-02、03、04、05、06 的结果、业务标签、列表状态及原请求恢复优先。成功写入后查询失败不再显示旧的可领取入口；拒绝的旧意图不锁死新版本，未知意图不被改成新的 POST。服务端仍负责唯一占用、权限、签名、恢复和幂等。
3. **再按证据优化耗时**：PDA-01、07、08 按实测拆分前台列表与后台同步、复用连接与增量渲染。服务端 READY 列表已有 `(warehouse_id,status,created_at,id)` 索引；活动授权列表索引以 `(device_id,status,id)` 开头，而查询用 `active_marker`，是否实际慢须在隔离库看 `EXPLAIN`、行数和耗时后决定，不能直接改生产索引。准备包完整性、防重、审计与恢复保护不能牺牲。
4. **覆盖失败矩阵**：主机测试 POST 成功而 DNS/订单列表/条码/审计失败，POST 前与响应丢失、下载已激活但确认失败、同单旧拒绝后新版本可领、跨操作员、历史 OB 号、所有本机任务状态及大列表；隔离后端测试 DTO/权限与幂等；M71 及第二台 PDA 在正常、弱网、断网回网和实际库存规模下验证反馈与耗时。刷新/领取/准备 p50/p95 目标在取得基线并与现场确认后设定，不捏造现有合格阈值。
5. **发布核对**：明确后端、APK、数据库迁移和签名组合，核旧机升级/覆盖后的本机身份、会话、待上传、历史记录仍在。主机、模拟器、真实 HTTP、真机、异地和部署证据分别记录；不以测试环境通过替代生产放行。

既定 `docs/DEVELOPMENT_ROADMAP.md:133` 的 R03 覆盖高频订单页，`:135` 的 R05 覆盖同步，`:140` 的 R10 负责规模及长时综合验收，`:145` 明确现场阻断问题当步修复。本报告识别的现行缺陷应在当前可用线收口；R10 不是延期修复理由。

## 2026-09-23 修复进展

在用户确认方案后，`codex/development` 主树已实施 PDA 订单流代码修改。此次写权仅覆盖本机 `android/app`、相应服务端 DTO/只读接口/测试及本文；未切换 R06 E2a 树，未更改 V35、隔离迁移器、E2a 端口/数据库/证据。主看板由并发任务维护，本线不覆盖其状态区；原 `docs/out.xlsx` 保持未跟踪且未修改。

- 授权契约：返回业务订单号、固定 revision 车号与计划量；增加仓库权限和 READY 状态控制的只读 claim preview，服务端无 Flyway 迁移。
- 原请求保留：Room 17→18 活动领取意图按订单/人员/设备隔离；UNKNOWN 原键正文不替换；REJECTED 新版本替换前归档旧事实，异常页仅对当前人员设备显示拒绝原因和时间；Room 18 导出 schema 与迁移的历史分页索引一致。
- 界面与反馈：已确认领取与后续列表刷新分开；待准备显示订单号/车号/计划量；已验签完成单进入历史，待提交、审核、结果未知、冻结、召回等仍在本机任务并单独标注；本机列表分页，不删业务证据。
- 网络与时延：同一服务地址复用客户端；DNS/传输错误中文化，不盲重试 POST；前台只读列表先呈现，受控后台继续分批同步订单、条码、审计和归档，召回核对继续前台执行。后台归档异常传递给持久重试策略、分批结果标记继续，缺失接口标记不支持；前台仍显示保留原记录的提示。日志仅记方法、状态/阶段耗时及下载字节数，不记 URL、订单号、认证头、签名、密钥或正文。
- 验证：Android staging 主机全量单测 **283/0失败/0错误/0跳过**，覆盖合成 Room17→18、UNKNOWN 保留、拒绝归档人员隔离、本机历史/召回筛选、窄屏/大字体卡片、跨人员禁用、DNS 中文提示、POST 断开仅发送一次、后台归档网络失败/忙碌/不支持分流；`lintStaging` 与服务端 `compileTestJava` 通过。均为主机静态/模拟数据验证，不能代替真实 HTTP、APK 或设备验收。
- 阻断：`server integrationTest --tests com.acrylic.outbound.inventory.PdaAssignmentIT` 7 项均在 Spring 上下文启动时失败（必填环境未配置，最深为 `IllegalArgumentException`）。当前 `OUTBOUND_DB_URL/USER/PASSWORD/MIGRATE` 均未配置；未复用 E2a MySQL 绕过阻断，集成断言没有执行。
- 待验：隔离真实 HTTP、真机/第二台 PDA、现场 DNS 根因、弱网/耗时基线、M71 历史规模与字体放大帧耗时、准备包查询/压缩/传输及 EXPLAIN、APK/升级路径、部署均未验。未生成 APK、未操作真实订单、未提交/推送/部署；旧 code20 真机 Room 升级与 App 内升级阻断仍需专项验证。

客户端中文提示不等于修复外部 DNS。性能优化没有现场对照数据，不能称已达目标；日志与后续现场测量要分别记录。当前 SHA、工作树和并发写权以看板顶部入口为准；本线未修改该文件。
