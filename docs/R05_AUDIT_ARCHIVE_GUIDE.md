# R05 审计连续前缀归档与恢复

日期：2026-09-19。分支 `codex/development`。本子项实现连续审计段的可恢复证明，以及恢复代次变化后的原事实重建和召回对账；R05整体仍在开发。

最新已完成[真实PDA跨进程/跨代次恢复](R05_ARCHIVE_RECOVERY_E2E_GUIDE.md)：Pixel_6原身份在代次2恢复15条代次1原审计，缺文件/丢响应/跨进程续接及持久RECONCILING屏障6阶段通过，首轮1次测试断言错误另记。不是完整灾后整库恢复或断电验收。

后续已完成[本机V28/PDA端到端验证](R05_ARCHIVE_PDA_E2E_GUIDE.md)：Pixel_6真实产生15条连续审计并补传、归档，丢弃成功响应后重试同一请求取得相同签名证明，Room重开与服务端文件核对通过。该次未改变恢复代次，不作为实际跨代次恢复或断电验收；下文336/173项仍是原子项历史回归。

当前契约为0.20.0，服务端Flyway V28，Android Room14。新增 `POST /api/v1/pda/audit/archives` 固定当前代次的1至200条连续段，及 `POST /api/v1/pda/audit/archives/restore` 从服务端耐久文件核验旧代次段并在当前代次重新签发证明。证明绑定设备、仓库、来源代次、序号范围、规范正文SHA-256、字节数、至少5年留存和 `localDeletionAllowed=false`。

服务端恢复不信任旧数据库检查点或旧归档索引：先验签，再读取内容寻址文件并核对摘要、字节数、事件结构、来源授权、操作员、仓库和连续序号；已存在事实只接受相同摘要，冲突整笔回滚。当前代次必须按段首尾连续，缺段不能跳过。召回正文可提交最多1024段已核验归档前缀，之后只提交原设备连续审计尾部并以 `RECALL_STOPPED` 结束；每单 `events/captures/scans` 仍必须完整。

PDA 在Room14保存待发送和已验签段，响应丢失或重启继续原请求；恢复每次刷新最多20段，未恢复完整前不推进通用审计补传。恢复只推进已验签前缀的本地检查点，不清除 `RECONCILING` 屏障，也不删除本地事实。超过1024段或单段正文超过512KiB停止并保留资料，等待运维处理。

本轮新增服务端集成覆盖签名段、缺口/重叠、摘要和文件损坏、跨代次重建、冲突回滚、恢复屏障、设备隔离、并发重试及召回前缀。Android新增Room迁移、跨语言证明验签、待请求重试、恢复代次和召回正文验证测试。

验证命令：

```powershell
.\tools\development_server.ps1 -Task Verify
.\gradlew.bat -p android :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin --console=plain
cd web
npm run check:contract
```

实际结果：服务端32项单元、336项真实MySQL集成和`bootJar`通过；Android Debug全套173项主机测试通过，均0失败/错误/跳过。debug APK、APK签名及原生测试源码编译通过；lint为0错误/126项警告；OpenAPI 3.1校验通过95条路径。APK路径为`android/app/build/outputs/apk/debug/app-debug.apk`，版本`0.11.0-r05-sync`/versionCode15，11,301,656字节，SHA-256为`9d1867977fd70dca0da4c067ae30754795e2a7847170a7ccb8fd55bdb49649fa`。最终日志为`.local/r05-audit-archive-server-verified.log`、`.local/r05-audit-archive-android-final.log`、`.local/r05-audit-archive-contract-final.log`及`.local/r05-audit-archive-apk-signature.log`；计数与产物汇总保存在`.local/r05-audit-archive-verification.json`，此前分项日志仍保留。

首次服务端完整回归为332项中1项失败，失败用例为`restoredEpochRebuildsOriginalEventsFromFilesWithoutOldDatabaseIndex`，日志`.local/r05-audit-archive-server.log`保留；最终336项完整回归包含该用例并全部通过。此前文档中的Android152项是新增审计归档测试前的记录；本次最终173项包含21项新增归档测试，不与旧Staging报告或定向重跑次数叠加。

源码和自动化验证不等于异地备份恢复、第二台PDA、M71现场、生产签名或长时断电验收。当前仍未实现整单业务事实90天清理；该清理须在R09完整备份恢复和引用核对后另行实施。本轮未安装设备、未部署公网、未提交或推送。

下一具体动作：按已确认的应用更新方案第9节接入“下载 PDA 应用”固定页面及登录页/设备管理页入口，复用当前环境可信发布清单；原同步/任务现场验证及R09恢复、留存待办继续保留。
