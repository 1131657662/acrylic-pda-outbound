# U04 独立采集大字首屏原生 L1（2026-10-19）

## 范围与结论

本切片验证独立采集页在 100%、130%、150%、200% 字号下的原生输入法手输、候选确认、产品自动回顶、触屏保存和最近保存首屏。新隔离 AVD、真实本地 HTTP 与隔离 MySQL 上的 5 项 instrumentation 均退出 0；四档各新建一项合成任务并恰好保存一次。83 张 Room 表完整性/外键通过，所有原 Room 行保留，除已激活资料指针和设备序号外没有原行替换。

这只补 U04/D17 有界 Android L1，不计 U04、D17、O04、E06 L2、D18 L3 或总目标完成。输入是克隆身份上的合成资料、SKU、用户与车辆；不证明原身份、原资料、现场库存/放行、实体按键、TalkBack、真机或生产授权。`physicalKeysVerified=false`、`localDeletionAllowed=false` 保持。

## 输入、源码与构建

- 固定工作树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `3036aefd99fd5a385ca5ad88da45284ea073a8fb`。`takeover07.json` SHA-256 `a67583ee74b120b0e19efddd9c65fcf7a8804bc5a31fec3a5d66b0808958c12c` 记录 233 个 tracked/untracked 路径与 SHA；继承输入相对基线只有看板已存在变化，暂存区为空。
- `input05-manifest.json` SHA `729bd742441aae36caa407c9a337949f1333a19145d4129f1536a76af7cede37`，涵盖 3,459 个 Android、server 和报告渲染输入。后续逐文件 SHA 核对 0 漂移；结果 `input-audit05.json` SHA `ddaa6bc78e79fd7dcdf284651f3eb7f91cff122b5165c5fe9da10e43497db36d`。
- 受影响源码：`activity_independent_work.xml`、`IndependentWorkDisplay.kt`、`IndependentWorkPanels.kt`，以及原生 fixture 两处 ScrollView 定位；新增 `IndependentLargeFontNativeFixture.kt`、`IndependentLargeFontNativeTest.kt`、`IndependentLargeFontRenewalTest.kt` 和 `IndependentLargeFontScreenTest.kt`。布局为首屏拆分固定警告、计划/有效/剩余摘要、短最近保存、候选优先；手输接受后隐藏输入法并请求回到顶部。没有把原 Enter/实体键报告改写为已验。
- 执行 `gradlew.bat -p android -I .local/u04-independent-large-font-run01/isolate05.gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --offline --console=plain --no-daemon --max-workers=1 --project-cache-dir .local/u04-independent-large-font-run01/cache05`，退出 0。日志 SHA `45621f2a356a92cc2bb442384c8885873a2192cfcedda4de20d274e44256d5f9`；app APK SHA `e278ce29f79dc6b4a7d1f38d232ee5264e415ad4b2387c1c03c995f006c6e5ad`，test APK SHA `2144f0638faa9939437a9283d579f436ae6779ecd7c379906f59f261be324722`。构建产物放独立 `apk05/`，未覆盖其他 APK。
- 复用同一 Debug APK 的 host04 六套件 25 项 Robolectric 几何/显示测试通过，log SHA `324c470c8b0d30c057261a0123bf56f8b201c2ec815d85a61de600f722e848bc`。host01 不能区分四档真实几何、host02 200%确认按钮越界、host03 150%入口越界的原 XML/log 均保留；修正后的 host04 和原生消费者提供本报告范围内的后续证据。

## 隔离身份与原生消费者

源 `.local/u04-independent-native-input-run01/` 未写入；完整副本位于 `.local/u04-independent-large-font-run01/native01/`，副本 AVD 的配置和 backing image 均指向副本。数据库 UUID 为完整副本继承值，账号权限限于副本内单一 schema；未连接系统 MySQL 或原库。服务 JAR SHA `ea045c2f8642d9349d4be5fb78cba85afba7e63db65e686f85cf939d61795852`。设备保留原绑定 URL，只使用副本专属 ADB reverse。没有执行 `pm clear`，没有重跑续签旧用例或操作 M71。

第一项 instrumentation 经本地真实 HTTP 登录并为该克隆 boot 签发新凭据，断言克隆设备公钥/设备 ID 不变、旧凭据条目逐项不变且恰加一项；使用常规可信时间与本地密码核验，没有注入 session、时钟或 token。随后四档各使用一项新合成 SKU、新任务、新条码，真实系统 IME 输入，触屏校验后自动收起，产品 `scrollY=0` 时直接触屏确认。待确认时任务计数为 0、Room processing 为 `WAITING_CONFIRM`；确认后计数为 1、processing 为 `COMMITTED`、最近保存可见。每条仅一次，记录重量 15,501 克，无 VOID。4 个条码为 `003A0000011` 至 `003A0000014`。

测试总回执 `native-result.json` 为 5 项/退出 0；逐档 Android 命令与日志均在 `native01/private/font-1..4-result.json`、`font-1..4.log`。日志 SHA：font-1 `4551e32107cccd644ef930a138247ea9d9af87ae3ee599b97303bacfaf8a720b`，font-2 `31bdc9f98c1468fb872b41f099c4900c09b800b13db4e3c49d12d37b36596ad8`，font-3 `0e3014db66a5db5d60af3b632dd64f711e0fab6a6d6aa3dbaae036b6b260a609`，font-4 `3ea30fc684a263c19f62ce7d6cd1ca6235c9d6c9ac3381d6ca40f59a2d33f1c4`。

## Room 与文件审计

`independent_device_state` 的唯一设备键行被原地推进到新序号高水位；保留的是同一设备键及单行基数，不是旧数值。

只读审计器 `audit_native01.py` 对 8 份 tar 逐成员计算 SHA 并以只读 SQLite 消费完整 Room/WAL；各阶段 integrity 均为 `ok`、foreign-key 错误为 0。原始输出 `audit-native01.json` SHA `0f5ebd1e4de812ceb1ca6c9ea98586309c4c9bd25e8522a99a80aae02918f910`。独立语义消费者 `semantic-audit01.json` SHA `a764a9fd852f34dd247b0d9505e1d1a66cf8ba7a7f69d791861663e9ed9dbe8b`：83 表同名守恒；原 Room 行均保留，新增合计 60 行；四个任务/capture/capture_state/guard/scan/task_state/task_line/task_request 每表各新增 4 行，task_event 新增 24 行；SKU chunk/decision/import/original 各新增 1 行。`sku_active` 是唯一业务资料指针替换，`device_state` 的 `nextCaptureSeq` 从 5 到 9，原高水位记录仍只有一行。

从 `before-install` 到最终 tar 共 34 个成员变化：3 个 Room DB/WAL/SHM 成员、30 个新增 app 输入/结果/截图/viewport 文件、1 个 `files/profileInstalled` 运行元数据成员。旧 shared preferences 和其余旧 app 文件不变。八份 tar SHA-256：

| 阶段 | SHA-256 |
| --- | --- |
| before-install | `169eb3ff4f76f1740bdc6afe81556fbc4492b800e7eca9bed8ef572f0fd5f938` |
| after-install | 同 before-install，字节相同 |
| after-renewal | `f5619357b8b374224743cf15d2ab928d7ed82a6998bdf205bc029c404f4912ae` |
| font-1 | `5692a7aa99ebd4973c834434bb72b1a1d7878a656e4726752f51b06d13a547ce` |
| font-2 | `ae6f3cb7d3be2946c34da3d68c368cd5ff32a9eeca61dd194da7ab858e0acd8` |
| font-3 | `f9e2fa243ae031e5b83f1c7c7879698c98280e7214be7a1abe4111837a87247a` |
| font-4 / final | `180a28344dc04e6764731b0e3d1d4ac9323f7a4e170b930e1bc5fa4cdcf25d30` |

原 8 份 tar 仅含 `files`、`databases`、`shared_prefs`，不包含 `no_backup`。完成续签和四档后，另只读封存最终 `no_backup` tar，6 个成员含加密凭据库，tar SHA `684cc59fb1846968910aab192bc8040915c16a029fe25974356ce8c5ea7eefbb`。没有续签前的 no_backup 快照；旧凭据逐项保留仅由续签时运行断言证明，不能声称两份 vault tar 对比。

## 视觉与边界

实际打开并检查全部 8 张 Activity 内容截图；均为 480×800，viewport JSON 中 `scrollY=0`。待确认 8 个/保存 7 个关键控件矩形均在 Activity 可用区域内；200%待确认确认按钮下沿 677px，四档完整警告可见，150%/200%警告按预期换行。图像、SHA、矩形汇总在 `visual-review01.json`，SHA `cfe9504e9dd55b85ebe7cd0169b1c72efd30b7de25c26a38762804b6ffd6e53e`。截图不包含系统状态栏，不能据此宣称系统栏实拍验收。

重启后现场状态均重新核对。新 HTTP、MySQL、AVD 已停止；最终 `stop-observation03.json` 显示目标进程/监听为空，系统 MySQL 3306/33060 和共享 ADB server 保持运行，M71 `22D2910042` 只枚举未操作，Debug Keystore SHA 保持 `0539b3d0b80ab4619a08db639d433ec2f3ac0a8d7b2fbdc32865d5cfc6b0b6cb`。HTTP 与 MySQL 通过核实后的 PID 停止；账号没有全局 `SHUTDOWN` 权限，不称 SQL 认证优雅停机。失败和启动/审计诊断原件均保留。

用户已授权清理已停机测试数据。副本 datadir 的 361 文件/235,683,218 字节 SHA 清单 `database-data-manifest01.json` 证明源 348 个文件零漂移，清理前没有目标 mysqld/监听；但执行精确 `Remove-Item -LiteralPath .../native01/database/data -Recurse -Force` 前被执行工具策略拒绝，未删除，也未改用其他接口。副本仍保留；本轮 D 盘余量未取得清理后净增。唯一原始 APK、tar、日志、no_backup 封存、源数据库/AVD/Keystore和失败证据均保留。

## 失败、限制与下一步

- 启动器最初 root TCP 空密码认证返回 MySQL 1045，记录于 `startup01-failure.json`；未改用户或权限。后来使用既有作用域限于副本 schema 的账号启动隔离服务。
- 首次 app cnf 曾以字面 `\n` 写入导致 CLI 认证失败，错误配置保留；修正配置后用只读查询验证连接。首次审计 scratch 共用目标触发 `FileExistsError`，改为按阶段分目录后完成；首次图片抽取目标目录不存在，tar 未变，随后成功提取并核图。
- 首个停机 JSON 因序列化 CIM 对象深度不足而截断，且把执行中的 PowerShell 自身误列为剩余进程；原回执保留，独立 `stop-observation03.json` 以简单字段重核后确认目标进程/监听均清零。
- 旧 host01几何不能区分字号、host02/03真实布局失败仍保留；本轮没有验证实体键、TalkBack、M71、原身份、原资料、外置保管、长时、真实 Chrome/Python业务消费、非空迁移或统一候选 L2/L3。

后续继续 U04/D17 与 O04/U03 独立缺口；不恢复换机承接、多跳、接收者确认/激活链，不启动旧 run03，不连接原库/现网，不使用正式签名，不部署、Release 或合并 main。七类生产决定和 TEMP 生产归档保持 fail-closed，`localDeletionAllowed=false`。总目标继续 active，仅第8节全部闸门在实际最终候选满足后才能 complete。
