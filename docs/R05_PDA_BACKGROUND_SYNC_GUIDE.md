# R05 PDA 持续同步与失败调度

日期：2026-09-19。分支 `codex/development`，源码及本机验证完成，R05 整体仍未完成。本文只记录本轮 Android 调度；云端任务、真实归档和恢复演练分别见其专项记录。

## 行为与保护

- 应用启动登记唯一的15分钟周期任务；登录、退出登录及主动刷新重置当前调度状态，并排入30秒后的核对。Android 系统会根据网络、电量及后台限制安排执行，不承诺精确到点或在用户强停期间运行。
- `SyncWorker` 独立于提交上传的 `UploadWorker`，仅在当前网络为 Wi-Fi 且应用空闲时执行；每轮订单/条码各一页、审计恢复/补传/归档各有限批次。未完成分页30秒后续接，前台原有刷新上限保持。
- 网络错误、408、429、5xx 持久化重试，默认30、60、120秒递增，默认退避上限30分钟，并尊重服务端 `Retry-After`。不支持的接口保留原资料，完成当前分页后6小时再核对。永久错误暂停自动重试，主动刷新或新登录后重新核对。
- 周期唤醒复用已有后续任务，避免每15分钟在长退避链尾追加节点；正在运行的后续任务才追加自己的下一轮。后台之间、安装和清理互斥；新扫码可先取得业务资格，后台下载结果在提交前重新检查空闲与身份。
- 会话网络变更串行，Room 短事务内再次核对人员、设备、仓库、恢复代次和本次登录标识。退出登录、换仓/换人或任务取消后不提交旧响应。调度元数据只含身份范围摘要、次数、时间及固定提示，不保存令牌、凭据或审计正文。
- 不延长离线授权的48小时期限，不解除 `RECONCILING`，不删除原采集、扫码、作业、防重、提交或审计。设备设置展示上次自动同步结果和时间。

## 本次验证

最终执行：

```powershell
gradle.bat -p android :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --console=plain
```

192项主机测试通过，0失败/错误/跳过；Debug 和 AndroidTest APK 构建成功；lint 0错误、129项警告。新增调度相关17项主机用例覆盖持久退避、重登录/注销、永久错误、取消、分页恢复、审计原请求重放、扫码抢占、后台/安装/清理互斥，以及24次周期唤醒不累积重试。现有迁移、恢复、回执和作业测试同时通过。

使用现有 `Pixel_6` / `emulator-5586`（API37），独立包 `com.acrylic.outbound.pda.debug.r05background`。三个方法通过独立 instrumentation 调用运行，中间显式强停；它们构成有意保留检查点的三阶段演练，不作为可任意排序的普通测试集运行：

1. `seedDurableRetry`：本机回环合成 HTTP 对订单返回429/Retry-After 30，条码正常；收到审计请求后断开响应。真实 Worker 保存 attempt=1、剩余30秒、唯一后续任务及未确认的原审计正文。
2. `resumeDurableRetryInNewProcess`：PID由6168变为6229，原登录保持；WorkManager 恢复原任务ID，订单/条码同步游标保存。审计原正文SHA-256保持，确认点推进到1并清除待发送正文；合成后台对归档返回404，未签名归档仍保留。再等待原任务追加的下一轮实际运行，核对只保留一个6小时后的任务，避免把“入队”计成“已执行”。
3. `busyWorkerDefersThenLogoutStopsRequests`：取得业务资格期间 Worker 返回重试且不发请求；退出登录后再运行 Worker 不发额外请求。三个阶段均核对合成原事件、作业状态和原资料保留。

最后三阶段各1项通过。合成审计只有1条，订单是明确标注的测试资料，不能扫码执行；HTTP 服务由 AndroidTest 的 MockWebServer 在 `127.0.0.1:18991` 提供，随阶段关闭。它验证生产调度、Room、会话和网络客户端的集成，不代表真实后端、签名归档、多设备或公网验收。先前不含审计的三阶段及加入审计后的三阶段也分别通过，不与最终三项重复累计。

本轮只清理专用合成包的失败/重跑状态。原 `com.acrylic.outbound.pda.debug.r05archive` 的数据库、WAL/SHM、偏好及文件共22个文件前后SHA-256一致；未触碰 M71。结束时隔离包已强停、模拟器已停，AVD、合成库和检查点保留，原PDA恢复断点不变。

## 失败与修正

- 首轮主机测试中3个页面测试因 WorkManager 未初始化失败：应用采用 `Configuration.Provider` 并移除默认 initializer；Robolectric 使用专用测试 Application。
- 后续主机回归曾在 `robolectric-nativeruntime.dll` 的 SQLite 后台线程崩溃：测试 Application 改为同步执行器，并在页面测试结束关闭 WorkManager 测试库。前次190项及最终192项完整回归通过；崩溃日志保留在 ignored 证据目录。
- 首次原生阶段因模拟器 Wi-Fi 断开超时，连接 `AndroidWifi` 后进入真实同步逻辑；第二次因失败运行遗留的多个任务节点导致测试 `single` 断言失败。已让种子阶段取消专用包的旧调度，再要求本轮只有一个待执行节点，没有放宽生产判断。
- 复核发现周期任务可能向长退避链重复追加节点，修正周期/后续任务入队策略，2项针对性主机测试及最终全套/原生复测通过。

历史失败不是通过证据；最终日志和产物如下。

## 产物与证据

版本保持 `0.11.0-r05-sync` / versionCode15 / Room14；契约0.21/V28未改。以下是未发布的开发产物，与原始旧包15或公网code16不是同一包，不能按版本号混用。

| 本机产物 | SHA-256 |
| --- | --- |
| `.local/r05-background/app-default-final.apk` | `f4a03b0d41286480984ab467f6addee8bb7d64608d8fe5eb99c282a872dd01c6` |
| `.local/r05-background/test-default-final.apk` | `efda068c3bb4ab51290615518a4e6aea6aedb65f311b863133cfefa0796600ee` |
| `.local/r05-background/app-isolated.apk` | `34e5b037070f4b066b3b165098899f720237d39bb581b5effaf9d9ee0cbaecfe` |
| `.local/r05-background/test-isolated.apk` | `32508008f070c116e680e70c385bf44cf4761a68118471dd38de4cbfa7961683` |

本机证据目录 `.local/r05-background/`：`final-coalesced-host-verification.log`、`scheduler-coalesce.log`、`isolated-build-final.log`、三个 `native-final-*.log`、`final-checkpoint.json` 及 `archive-before.txt` / `archive-after.txt`。原检查点审计正文摘要为 `dbf3de8147a88ce9f1e464e9352cd7e46729e6aa1f2cf41a239ce7e38ced5229`。证据、APK和合成数据不提交到Git。

## 接续边界

初始下一动作已在同轮继续完成，见下一节。2026-09-20沿用真实API夹具完成断网/跨进程、周期到期后网络恢复及4种显示检查，修正大字号返回按钮，见[生命周期记录](R05_PDA_SYNC_LIFECYCLE_GUIDE.md)。后续稳定Wi-Fi无进程观察也通过：原周期ID `10c51f25-b64e-43da-9902-e2bc270903d4`保持，系统自然启动PID4574，次数2→3、WorkManager SUCCESS，较原到期晚152932ms完成；被动存证后1项原生保护核对通过，云端20表/私有文件及原归档包22文件保持。本次未修改产品源码或重跑192项主机回归。API/模拟器已停，资料保留，不重新执行一次性登记/领取或周期种子。下一步按[R05剩余验收](R05_ACCEPTANCE_STATUS.md)续接受控续期真实API场景；Android9/M71、第二台/异地及长时断电仍需独立证据。

整单事实90天清理仍依赖R09实际恢复与引用核对；Linux143启停和App内独立HTTPS升级渠道仍保留既有自动审批阻断，不重复执行或询问已批准的代理授权。公网保持0.18/V23/code16。本轮没有提交、推送、合并、打标签、发布或部署。

## 同轮接续：真实V28 API、后台状态和签名归档通过

新建本机库 `acrylic_outbound_r05_background_20260919`、只限本库的账号和新开发签名密钥；API仅监听 `127.0.0.1:18091`，复用已验证的0.21/V28候选，JAR SHA-256 `3131233665c7e624cc60498907334e61180e593ccbf091ce327c9537c2d3ea32`。使用独立包 `com.acrylic.outbound.pda.debug.r05backgroundapi`，未重置前面合成调度包或原归档包。

既有 `PdaAndroidFixtureIT` 在全新库执行1项通过；沿用 `PreparationEndToEndTest`，以 `verifyBackground=true` 启用新调度阶段，原生完整流程1项通过，137.081秒。真实登记/登录/领取/资料准备后，调用生产 `SyncScheduler.resume` 等待原30秒任务执行，确认后台缓存为ASSIGNED。完成扫码、提交丢响应恢复、主管批准和回执验签后，再等真实调度及其分页续接完成，核对：

- 订单缓存变为COMPLETED；两个已扫码条码均为DISPATCHED，订单/条码游标均保存。
- 15条原审计由后台Worker补传，连续确认点15；真实后端签名归档1段，经客户端验签。
- Room和MySQL归档完整正文一致；实际私有文件SHA-256、字节数与证明相同，`localDeletionAllowed=false`。
- 4条采集、2条扫码、15条事件及原提交正文保留；后端2条逐包出库、1张订单、601500克，没有重试重复出库。
- 实际 `DeviceSettingsActivity` 显示持久的“订单、条码和操作记录已同步”；Room完整性ok。这里只核对正常尺寸文案，不替代大字号/低高度检查。

该接续只新增测试与可选测试调用，生产代码保持前面192项回归的版本；未再次计为192项重跑。两种隔离APK构建及针对新测试的lint通过，0错误/129警告。首次准备时本机MySQL未启动导致环境核对失败，使用已有workspace启动脚本启动后成功，未改变业务条件；实际夹具和原生流程首轮通过。

产物和证据位于 `.local/r05-background-api/`：`fixture.log`、`android-build.log`、`native-e2e.log`、`lint.log`、`background-api-evidence.json`、`verification.json`、`cleanup.json`。应用APK SHA-256 `7ca96df37fba559c603e4e86610e6d20bdce9fc8727e8bbb5bc53feb57f3e7cc`；测试APK `f9df613980c7fc4f56b0f7ed81c81308dfb52060e21814795a5677471126a48e`。同为未发布开发code15/Room14，不能冒充公开旧包。

原归档包22文件前后摘要再次一致，M71未操作；结束时API18091和模拟器已停，独立库、应用身份/会话、私有文件及证据保留。测试结束已关闭其临时 `SubmissionProxy`，后续须把该独立包服务地址恢复为本机API直连（`http://10.0.2.2:18091/api/v1/`）或显式重启原端口的本机测试转发，不能直接将关闭的随机端口当成API故障；不要清库或重新登记原设备。本机HTTP/API37不代表公网HTTPS、Android9或完整R05验收。
