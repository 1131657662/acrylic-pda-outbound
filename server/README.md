# 正式工程本机运行

2026-09-20 编号修复：当前契约0.23.1 / Flyway V32，新正式单使用FH日期流水，旧号及原UUID不改；验证/启用/回退边界见[编号记录](../docs/ORDER_NUMBER_GUIDE.md)。未部署。

2026-09-20 R06-B服务端契约0.23.0 / Flyway V30：独立TEMP 48小时资格、固定仓库资料包、受控下载及激活声明回执已实现，32单元/102 MySQL集成及102路径契约通过。Android临时资料激活及临时建单尚未接入，未部署，详见[R06-B记录](../docs/R06_TEMPORARY_PREPARATION_GUIDE.md)。R06-A浏览器仍待审批；V29上不能直接运行旧版未列名回执INSERT，原[兼容边界](../docs/R06_TEMPORARY_ORDER_ACCESS_GUIDE.md)保持。

2026-09-19 R05当前超时待审：源码契约0.21.0 / Flyway V28，新增按审批权限和仓库筛选的当前待办游标查询，Web审批返回及中文历史提醒已本机验证，见[交付记录](../docs/R05_REVIEW_INBOX_GUIDE.md)；本轮未部署。此前审计段耐久凭据、跨恢复代次重建及召回前缀对账见[审计归档记录](../docs/R05_AUDIT_ARCHIVE_GUIDE.md)。

2026-09-18 R02已建立独立HTTPS测试后台。服务器目录、账号隔离、构建产物、备份恢复和实际真机验证见[R02记录](../docs/R02_TEST_DEPLOYMENT.md)。下文各开发阶段“尚未部署”等表述保留为当时的历史状态；测试部署不代表生产投用。

2026-09-17，当前正式流程：Web会话、仓库权限、库存/映射管理、订单草稿及发布、政策、历史，以及设备登记、PDA认证、订单独占领取、签名准备包、下载确认、正式订单首轮提交、短装审批及按政策出库记账、受控召回/恢复包/停止证明/主管零包作废和原机保留/卸货释放/隔离续作。Web设备管理已接既有登记、分页查询及启停接口，操作见[设备管理说明](../docs/WEB_DEVICE_GUIDE.md)。正式Android App运行说明见[android/README.md](../android/README.md)。完整业务范围与顺序见[开发进度](../docs/DEVELOPMENT_PROGRESS.md)。

## 环境

Java 21、Gradle 8.11.1、MySQL 8.0、Node ≥22.12（构建前端）。后端Spring Boot 3.5.16；具体前端版本和锁定依赖见`web/package.json`、`web/package-lock.json`。前端构建结果为静态文件，正式服务器无需升级Node或运行Vite。

Windows本机已安装MySQL时，从仓库根目录执行：

```powershell
python tools/start_development_db.py
powershell -NoProfile -ExecutionPolicy Bypass -File tools/development_server.ps1 -Task Verify
powershell -NoProfile -ExecutionPolicy Bypass -File tools/development_server.ps1 -Task Bootstrap
powershell -NoProfile -ExecutionPolicy Bypass -File tools/development_server.ps1 -Task Run
```

- 独立MySQL监听`127.0.0.1:13316`，数据存放`.local/mysql/data`；不使用或修改已有MySQL服务。
- `Verify`使用`acrylic_outbound_test`，验证真实MySQL迁移、约束、事务和接口；开发服务使用`acrylic_outbound_dev`。
- `Bootstrap`仅首次、空用户库运行；从本地配置创建管理员与主仓库，存在用户时拒绝执行，不重置密码。
- 开发数据库及管理员随机密码存`.local/development.json`，该目录已被Git忽略，不上传。可在该文件查看`adminUsername/adminPassword`登录本机Web。
- `Run`后端监听`127.0.0.1:8083`。新终端进入`web`执行`npm ci`、`npm run dev`，浏览器访问`http://127.0.0.1:5173`。
- `Run`使用`.local/server-runtime/`中的独立JAR副本，避免重新构建覆盖正在运行的文件。执行`Build`后需重启`Run`才能使用新版本。
- 开发脚本仅在`.local/pda-signing/`尚无密钥时生成一对3072位RSA签名密钥；已有密钥不替换，只有一半文件时拒绝运行。这是本机离线授权签名密钥，不是PDA设备私钥。生产密钥另行安全配置。

前端验证：

```powershell
cd web
npm run build
npm run check:contract
npm run test:e2e
```

浏览器测试使用本机Chrome和真实开发API，自动读取本地管理员凭据。现有5项场景覆盖库存、订单草稿、政策配置、发布丢失响应重试、政策固定、修订重发和作废。测试向**开发库**上传样本及合成库存，执行库存发布、映射修订、政策和订单操作；开发库当前库存及政策因此会切换为测试设置，这些记录保留，不连接生产。截图位于`.local/web-inventory.png`、`.local/web-stock.png`、`.local/web-publication.png`、`.local/web-orders.png`、`.local/web-order-detail.png`和`.local/web-order-published.png`。

登录后选择“装车作业 → 装车订单”，或访问`http://127.0.0.1:5173/orders`。创建订单前需发布库存，产品来自启用映射。发布前管理员在“出库政策”明确选择自动扣库存方式。`ORDER_CLERK`拥有订单读/编辑/作废/发布权限，`ORDER_VIEWER`只读；仓库管理员另有政策管理权限。用户角色分配界面尚待实现，所有请求仍需相应仓库授权。

## 后端独立构建与生产配置边界

根目录Gradle包装器可用于`gradlew.bat -p server test bootJar`；开发脚本优先使用本机已缓存的相同Gradle版本。正式后端工程独立于Android Demo的Gradle设置，不编译或覆盖Demo APK。

| 变量 | 含义 |
| --- | --- |
| OUTBOUND_DB_URL | 本项目独立库的JDBC地址；显式UTC，如`jdbc:mysql://127.0.0.1:3306/acrylic_outbound?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&rewriteBatchedStatements=true` |
| OUTBOUND_DB_USER / OUTBOUND_DB_PASSWORD | 通过部署环境注入；禁止使用共享生产root账号 |
| OUTBOUND_MIGRATE | 默认false；迁移使用独立DDL身份执行，运行账号仅需本项目DML |
| OUTBOUND_STORAGE | 私有原文件目录，不能挂到Nginx静态目录 |
| OUTBOUND_COOKIE_SECURE | 默认true；只在回环HTTP开发时设false |
| OUTBOUND_BIND / OUTBOUND_PORT | 默认127.0.0.1:8083，待部署时确认端口无冲突 |
| OUTBOUND_AUTH_SIGNING_PRIVATE_KEY | 离线授权签名RSA私钥文件路径，PKCS8 DER；运行账户只读，至少2048位，与公钥配对 |
| OUTBOUND_AUTH_SIGNING_PUBLIC_KEY | 对应X509 SubjectPublicKeyInfo DER公钥路径；需备份并保留历史验签所需公钥 |

当前已验证Windows/MySQL 8.0.46本机。正式服务器8.0.45、Linux目录耐久、Nginx/HTTPS/systemd限制、独立迁移账户、备份恢复和部署前复核仍按V2.3执行；本轮没有连接或修改生产服务器。

## 已实现与后续

当前上传为受限大小文件的同步预检：原文件先留存，再原子提交导入头、明细、幂等记录和审计。错误文件可查看；上传不改变有效库存指针。管理员另行核对差异和候选映射后原子发布；新增版本保留旧快照、扣账/冻结/周期事实。页面可维护映射版本、启停代码、查询库存及差异历史。接口和幂等边界见[已实现接口](../contracts/IMPLEMENTED_API.md)。

订单草稿支持一个订单多个产品、各产品正整数计划、固定库存/映射版本、不可变修订和审计。订单合计不设116包上限；车号可重复，订单号唯一。下发前检查仅核对状态、版本与候选数量，不发布、不领取、不预留库存。接口、响应示例和重试边界见[已实现接口](../contracts/IMPLEMENTED_API.md)。

0.3.0历史验证：Flyway V1～V9、24项单元测试及105项真实MySQL集成测试通过，覆盖库存、订单、政策、设备认证、领取竞争、保锁、准备包、下载确认、提交竞争、分块、审批隔离、记账及失败回滚。协议见[PDA接口](../contracts/PDA_API.md)，OpenAPI0.7.0共51个路径。设备管理当前提供API，尚无Web操作页；正式Android工程已接Keystore私钥、登录、订单资料准备和本地扫码界面。

订单领取固定发布计划/政策/库存及恢复代次，原子保存订单独占占用、48小时签名凭据、幂等记录、审计和outbox。下载未确认或授权过期不放锁，原设备原人员可重新登录查询和恢复原凭据；同机换人只返回占用信息，不能取得原人员token。V8持久化每授权唯一准备包，三个gzip文件及签名清单不可变；App完成校验和本地事务后发送download-ack，响应丢失可以原包重试。权限版本变更后重新登录仍不能使用旧离线凭据。

准备包中的可用状态仅为准备时截点，没有增量游标，不能用于宣称完整同步。通用续期及直接解锁接口尚未实现；当前版本支持正式提交/审批/记账、受控召回、原人原机保留及凭据减包续作与签名回执，但尚未完成完整系统和M71现场验收。V24/V25已提供MySQL持久任务表、短事务领取、租约恢复、fencing token和失败退避；`worker` profile可启动独立非Web Worker。计划单任务仍只查待生成/已替代状态，PDF/Excel处理器尚未接入；换机/更正、通用报表与导出继续按计划实现。Worker启动和边界见[持久任务交付记录](../docs/R05_BACKGROUND_JOBS_GUIDE.md)。本轮使用独立18083端口及测试库完成模拟器联调，未重启原开发应用、未启动公网Worker或部署生产；运行中的旧JAR需按上文Run方式重启才能使用新接口。

Flyway脚本一经运行保留校验和；后续结构变化新增迁移，不修改已运行版本。生产迁移前须备份并验证恢复，当前不提供清库或破坏性回滚脚本。

短装审批页面测试使用`SubmissionBrowserFixtureIT`生成隔离待审批订单（build目录的凭据禁止提交或打包）。可用`OUTBOUND_API_PROXY=http://127.0.0.1:18083`启动独立Vite端口5174，再设`OUTBOUND_WEB_URL=http://127.0.0.1:5174`运行`npm run test:e2e -- --grep "short shipment approval"`。测试会批准该隔离订单，每次重跑先重新生成fixture，不复用已成功批次。


召回迁移为Flyway V10，扩展包占用归属到独立恢复包；不删除旧提交或已出库防重。Web需 `order:recall`，主管零包作废另需 `order:cancel`，PDA证据接收/停止需 `pda:recover`。V10为默认管理员和PDA操作员补相应权限。接口与字段见 [召回协议](../contracts/PDA_API.md#受控召回与原机停止040)。V1～V9保持原迁移校验和。

召回浏览器测试使用 `server/build/recall-browser-fixture.json` 中的隔离测试身份，运行 `npm run test:e2e -- --grep 'controlled recall'`，设置 `OUTBOUND_WEB_URL` 指向代理隔离测试后端的Vite服务。该fixture含测试凭据，只在本机ignored build中使用，不放入App或提交仓库。


受控续作使用 Flyway V11（申请、票据、保留清单及占用归属）和 V12（每来源每轮唯一冻结批次）；V1～V10 保持原校验和。`order:resume` 专项权限由 V11 授予默认仓库管理员。申请/审批沿用 change-requests，原 PDA 通过 `/pda/continuations/{id}/activate` 取得固定新资格。只有完整恢复证据、原机停止、原批次 REJECTED/ABORTED、实物核验和处置集合一致时才能批准；V13扩展独立卸货/隔离凭据。

S2 分块控制头先锁定唯一在途身份，完整正文核验后才在同一事务选择 current 和转移占用；失败不放锁。新资格期限从批准签发起固定48小时，重试及激活不延长。源数据缺失/损坏、换机、无凭据减包、计划修改或恢复代次变化不能通过此入口绕过核验。详见 [续作设计](../docs/PDA_CONTINUATION_DESIGN.md) 和 [接口](../contracts/continuation-paths.yaml)。

实物处置续作新增Flyway V13：独立主管凭据及 `order:dispose` 权限、全局QUARANTINED占用外键。V1～V12均不改写。减少行只有在完整替换批次的同一事务内执行释放或隔离；任何冲突回滚，批准/激活/过期/停用不自动放锁。正式部署前按现有数据库备份及迁移流程验证，不可将旧迁移文件修改后重新执行。

本轮验证24项单元、162项MySQL集成（完整统计保存在`.local/disposition-server-results.json`），相关生命周期与并发测试为DispositionApiIT/DispositionLifecycleIT。Web和原生fixture只保存于ignored build中。操作和受测APK见[实物处置指南](../docs/PDA_DISPOSITION_GUIDE.md)。原开发8083/5173未重启；新代码不会自动出现在旧运行进程。

## PDA短码配对（0.7.0）

新增Flyway V14，保留V1～V13校验和及原设备/订单数据。未登记PDA仅能访问配对挑战、申请、原申请状态/确认/取消；每次须申请凭据和原公钥的签名证明，短码不是认证凭据。管理员仍需Web会话、device:manage、仓库权限，写操作包含CSRF、幂等键和恢复代次。最终PDA确认时重新核验批准人员权限，只在同事务成功后登记设备；原完成回执持续可恢复。

本次全套24单元/182真实MySQL集成通过，无失败或跳过；包括19项配对行为、隔离、限速及并发检查和1项fixture生成。测试范围、阈值、保留边界及APK见[配对交付记录](../docs/PDA_PAIRING_REGISTRATION_GUIDE.md)，协议见[配对API](../contracts/PDA_API.md#短码配对登记070--契约0110)。

浏览器/模拟器联调仅用`acrylic_outbound_test`、18083/5174和隔离账号。`PairingBrowserFixtureIT`生成`server/build/pairing-browser-fixture.json`，含测试凭据，禁止提交或打包。运行Web测试可设`OUTBOUND_WEB_URL=http://127.0.0.1:5174`，执行`npm run test:e2e -- tests/pairing.spec.ts tests/devices.spec.ts`。正式HTTPS、代理后客户端IP及多实例限速、M71和异地网络仍待实际部署验证；本轮未连接生产，未重启原8083/5173。
