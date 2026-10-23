# 测试环境领取与 M71 主页布局验证

## 范围与输入

- 用户授权部署测试环境、接口验证及 M71 覆盖安装和现场领取验证；生产、原库、main、正式签名和 Release 不在本次范围内。
- 主树为 `D:/acrylic-pda-outbound`，开发分支为 `codex/development`。接续输入 HEAD 为 `ec81072be647cc059207144fabd97d631612b2b9`；继承改动保持，未作为测试包输入。
- 测试候选以 `571c34aa9eb8d08f0fb643f36542259a575c7702` 固定源码为基础。Android、契约和数据库版本定义见[看板事实块](../ORCHESTRATION_STATUS.md#输入写权和保存)；构建参数与实际 APK 元数据以证据文件为准。
- 证据根：`.local/test-claim-20261023/run01/`；布局追加证据：该目录的 `layout-top-controls/`。

## 已完成的测试部署与领取链路

- 测试站点：`https://outbound-test.nbsmzwl.cn`；后台部署目录为 `/opt/acrylic-outbound-test/releases/claim-571c34aa-20261023-01`。
- `deployment-evidence/activate.stdout.txt` 保存激活结果；后台、worker 和备份定时器均为 active。仅部署后台，Web 和 APK 在线发布入口未改变。
- 首次候选 APK：`build-android-02/AcrylicOutboundPda_app/outputs/apk/staging/app-staging.apk`，SHA-256 为 `36e6c646b6b21234bf1eed744ef1a0b25d5a1231bfbbc0ce7a532dc2e37e55ad`。
- M71（序列号 `22D2910042`）通过 `adb install -r` 覆盖安装；应用包为 `com.acrylic.outbound.pda.staging`，未卸载或清除数据。
- M71 经公网 HTTPS 刷新后显示待领取测试订单；核对页显示订单号、车号、仓库、计划和产品摘要；确认后取得本机独占授权。订单标识及业务字段只保存在本机证据中，不随源码提交。
- 下载并校验资料后显示“资料已就绪 · 已确认下载”；再次刷新，领取的订单从待领取列表移除，本机作业仍保留。
- 原始证据：`device-m71/claim-preview-window.xml`、`claim-result-window.xml`、`now.xml`、`postclaim-available-window.xml`、`refresh-window.xml`。这些为本助手现场操作和读取，非独立人员验收。

## 布局决定与改动

- 现有页面将订单卡片放在刷新与分区控制之前。M71 首屏被两张本机订单占满时，刷新入口不可见；`layout-top-controls/device-local-before.xml` 保存该状态。
- 按既有授权自主采用小范围布局调整：将“联网刷新订单与资格”放在作业区最前，其后为本机/待领取分区及临时作业入口；卡片列表接在控制区后，分页仍放在列表底部。
- 仅调整 `android/app/src/main/res/layout/activity_main.xml` 中现有控件顺序，沿用全部 ID、样式和可见性规则。页面仍整体滚动，顶部控制区并非悬浮固定栏。
- 不修改接口、领取请求、授权、资料下载或本地数据库逻辑。异常分区仍由“数据”页进入。
- 在既有固定源码副本上只叠加此布局改动；重建时校验完整源码清单，构建结束恢复副本原布局。主树的相同布局改动保留。

## 追加验证

- 固定源码清单 6,055 文件逐字节核同后，仅叠加主页布局。`source-input.json` 保存输入和覆盖文件哈希，`candidate-layout.before.xml` / `candidate-layout.after.xml` 保存原件。
- 布局自审：36 个有 ID 的控件及其全部属性保持，候选布局与主树布局规范化换行后相同；`layout-self-review.json` 保存检查结果。
- 定向主机 UI 测试：`ClaimPreviewDialogTest` 4 项、`OrderCardsTest` 6 项，失败/错误/跳过均为 0。这些为 Robolectric UI 用例，不是实际 HTTP、真机领取或完整 Android 回归。
- Gradle 测试任务所需的服务端构建输入文件由主树既有构建目录复制并记哈希；本次上述 UI 用例使用各自的合成数据，不以这些输入文件证明服务端签名或契约验收。
- lint 首轮阻断为 `PropertyEscape`：固定源码副本中未纳入 Git/源码清单的 `android/local.properties` 使用未转义的 SDK 盘符。原报告为 1 个 error、561 个 warning，原日志、结果和三类 lint 报告保存在 `attempt01/`，未覆盖或删除。
- 仅将该本机 SDK 配置的盘符改为 properties 要求的转义形式，不添加 lint baseline、不降低检查级别。第二次调用复用了旧 lint 分析，38 项 up-to-date，仅失败入口任务重跑；该缓存结果不算新分析，日志在 `attempt02/`。
- 确认无本轮构建在途后，将旧主应用 lint 分析原件及哈希保存在 `attempt02/`，使其输出缺失后执行新的分析。第三次构建退出 0，`BUILD SUCCESSFUL in 3m 1s`；54 项任务中 18 项执行、36 项经 Gradle 校验复用。新 lint 报告为 0 个 error、561 个 warning，原报告和上述 10 项 UI 测试 XML 保存在 `attempt03/`。

## 布局 APK 与 M71 复验

- 最终测试包：`outputs/pda/Acrylic-PDA-test-claim-top-controls-20261023.apk`，14,483,226 字节；SHA-256 为 `a25800eec215ee7b913ac6f0a2843a590982500e59f965ab704de476585d923c`。仅包含固定候选源码及本次布局覆盖，不包含开发分支继承 WIP。
- 签名证书与首次候选一致，`adb install -r` 返回 Success；设备已安装 `base.apk` 的 SHA-256 与最终包一致，首次安装时间保持，未卸载或清空应用数据。原 Debug Keystore 哈希保持，未使用正式签名。元数据、签名、安装前后包信息及核验分别保存在 `apk-receipt.json`、`apksigner.txt`、`install.txt` 和 `final-build-and-upgrade-check.json`。
- M71 首屏实测：刷新控件范围为 `[18,167][462,239]`，本机分区为 `[18,239][462,311]`，待领取分区为 `[18,311][462,383]`，均在卡片列表之前；实际 UI dump 为 `device-local-after.xml`。分页仍在列表末端，异常入口仍在数据页。
- 布局包再次联网刷新后，诊断显示本机任务已读取、本机授权已更新、待领取订单已更新及召回已核对；待领取列表为空，先前已下载就绪订单仍在本机作业。`refresh-result.txt`、`device-refresh-diagnostics.xml` 和 `device-available-after.xml` 保存结果。
- 首次刷新验证器在切换分区后才读取诊断，状态被正常分区操作覆盖而退出 1；该次 UI 原件及失败说明保存在 `ui-attempt01/`，应用同步日志同时证明请求成功。修正为刷新后立即读取诊断再切换分区，复验退出 0；未因此修改应用代码。
- 另逐张核对覆盖安装前的两张本机订单，滚动读取后两张均保留；最终回到作业页顶部，证据为 `device-local-upper-retained.xml`、`device-local-lower-retained.xml` 和 `device-final-work-retained.xml`。真实业务标识仅保存在 `.local/` 原件，不写入提交文档。
- 首次候选已完成真实 HTTPS 领取闭环；布局包仅进行了覆盖安装、位置、联网刷新和本机订单保留回归，未重新领取新订单，未运行完整 Android 回归或全流程生产验收。

## 收口与保存

- 构建失败和验证器失败均保留，不能以成功轮次覆盖；最终 APK 保留在上述交付路径，原候选 APK、固定源码 ZIP/清单、原身份及设备原件继续保留。
- 本轮可重建构建目录清理和最终 Git/写权/环境快照见 `layout-top-controls/cleanup-receipt.json`、`closeout/`；清理结果与字节数见[看板](../ORCHESTRATION_STATUS.md)。只清理本轮构建输出，不清理共享依赖或其他聊天产物。
- 清理前已校验最终 APK、五份保留报告及完整输入/依赖哈希，并保存构建输出逐文件清单；移除 4,795 个可重建文件、逻辑字节 105,417,704。D 盘余量从 22,984,560,640 增至 23,099,539,456 字节（观察净增 114,978,816，包含文件分配及同期活动影响）。首次清理前置检查因报告重名、第二次因旧 PowerShell 长路径限制退出，均未删除；收窄最终报告路径并使用已安装 PowerShell 7 后成功，失败说明保留。

## 继承范围与限制

- 本次仅解决测试部署、已知领取链路和主页控制区位置，不代表总目标或全流程生产验收完成。
- 继承 D13 跨实例只读准入证据及失败记录继续有效；D13–D15 整体、D16 和 D18 等剩余范围仍按看板及各自报告接续，本次局部验证不能覆盖它们。
- 不改变 `localDeletionAllowed=false`、七类生产决定关闭边界、TEMP 生产归档边界或原资料/身份/Keystore/签回执保留要求。
