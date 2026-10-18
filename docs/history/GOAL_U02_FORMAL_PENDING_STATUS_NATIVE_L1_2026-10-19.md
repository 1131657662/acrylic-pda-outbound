# U02 正式作业台待确认与保存状态原生 L1（2026-10-19）

正式作业台的继承状态修复已在实际 Android 客户端通过：真实 HTTP 创建、发布、领取、准备新隔离订单，手输校验后显示“待确认输入 1”且已保存为零，点击产品确认按钮后才保存一包，Activity 重建保持。只计该状态子项 L1 开发备份，U02/D17 整体、E06 L2、D18 L3 和总目标继续未完成。

## 输入与逐路径影响

固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`，输入 HEAD/远端 `2bfcf239b984996db5dc4c72bf44b79ae11f8c45`。唯一写者为本聊天 `01a147c5-0495-7703-ba98-2e7637cbc619`，无子代理。证据目录统一前缀 `.local/goal-20261019-resume/u02-formal-native-retained914-`，含 run01–run05；最终结果在 run05，下列未注明的证据均在 run05。`run01/dirty-input01.json/.zip` 和 `tracked-input01.patch` 是切片全量输入，`inherited-protection01.json` 证明 201 项原改动字节保持；唯一新增代码是本测试。

| 路径 | 影响层 | 必跑与结果 | 复用依据（路径＋SHA） |
| --- | --- | --- | --- |
| `android/app/src/main/java/com/acrylic/outbound/pda/ui/WorkRenderer.kt` | 正式作业台待确认/待落盘/阻断文字 | 实际 WorkActivity 待确认→产品确认→保存→重建通过；lint/构建通过 | 继承 WIP 原字节 `1884df75484945227bf03ed3efbb323d0e745e14fd8702a44ab66015043b66c4`；与前序 U02/O01 绑定相同，本轮不改写，按本范围纳入开发备份 |
| `android/app/src/androidTest/java/com/acrylic/outbound/pda/FormalPendingStatusDeviceTest.kt` | 隔离客户端状态消费者 | run05 `OK (1 test)`；产品正常校时、先前 INVALID 正常确认、新库存和订单真实 HTTP、正式页面手输与重建 | 新测试 SHA `953777f1af962b01ca09663f98bf1ec208f2c085e265ff7f52e220b502543a06`，源码输入全集见 `source-bindings-final.json`；仅 DEBUG/ranchu/固定隔离地址和显式参数，attempt 不覆盖 |
| 本报告、进度索引、看板与旧看板归档 | 证据与断点 | 引用/范围及 version_facts Write/Check | `board-before.md` 保存旧字节；版本事实只看生成块 |

产品 APK SHA 保持 `c42ca36bd7f418fa92f00772dfedb9ef3047b1db7e41a843570f1c8d1e395992`。最终测试 APK `apk/formal-u02-test02.apk` SHA `8bbfacb8a7eb0f84f47ab09097600436990b9836c8c161c96bbd20c33075aa9e`。`build02-command.json`、`private/build02.log/.exit` 记录 AndroidTest 构建/lint 零退出；前几次构建也保全。主机旧状态报告只按源码未漂移路径复用，不把 actionable tasks 当作测试用例数。

未改产品模型/数据库/契约/迁移、Web 或 Python 产品消费。真实 HTTP 用于本项 Android 新订单输入，Chrome、非空迁移、恢复、权限/事务/并发/末 SQL 等其他链未作为本轮新增验收，不计完整组合通过。TEMP 状态专项见 `GOAL_U02_TEMP_PENDING_STATUS_NATIVE_L1_2026-10-19.md`，不能把两个局部 L1 拼成完整 U02。

## 实测与独立消费

保留 run914 `Filter914_Pixel6` 原 userdata、Keystore 和原登录身份，经实际“检查后台连接”重校可信时间，原 device/user/warehouse/localSessionId、公钥和 Room 保持；未 wipe、重绑或重新登记。这是合成隔离身份，不代表原 PDA/现场事实。

run05 先在产品页面正常确认 run04 的重复包拒绝，原 capture 仅增加 failureAckAt，未删除原输入或释放防重；随后经真实 HTTP 发布包含原两条库存和一条新有效条码的完整快照，再新建/发布/领取/准备新订单。实际 WorkActivity 手输、确认及重建通过，没有冻结/上传/放行。`run05-instrumentation01-command.json`、`private/run05-instrumentation01.log`、`result01.json`、前后及最终设备 tar 保留。

`consumer02.py` 实际消费前后/最终 Room＋WAL：83 表完整性/FK正常，无旧行删除；新 capture/scan 各一条，capture 为产品正式持久态 COMMITTED；未生成 submission，旧 submission 逐行相同，全部 TEMP 表逐行相同。九表有预期变化：prepared_bale/order、work_capture/device/event/guard/line/order/scan；旧 capture 唯一变化是上述确认时间，设备序号正常前进。其余原行保持，最终设备包与成功包全部表一致（`consumer02-result.json`）。这只证明本机输入关系，不声称已对 MySQL 全表执行独立守恒检查；工具消费者也不是独立人员复核。

## 失败与环境偏差保全

- run01：新进程读取 TEMP source 前漏 store.load，失败在 HTTP 新订单前；`failure-consumer01-result.json` 证明 83 表守恒。run02 补 load 后发现原两个条码都已有本机 guard，无可用条码，仍在创建正式订单前停止；两个错误分开保留。
- run03：生成条码长度不合规范，部分库存上传后无可发布有效记录；产品拒绝发布，staging 留存。没有清理库、改库存状态或伪造成功。run04 直接尝试既有 571 条码，产生真实新订单及 INVALID capture；完整 Room/WAL 显示原因是本机重复包防重，不是计划映射。此前 commentary 的映射推断已经纠正，失败不改为通过。
- run05 才完成实际正向；没有沿快速修复通道忽略 run04 已写业务输入。run04 原数据、后续产品正常确认和本轮最终 dump 全部保留。
- 启动器曾误把已由本写者启动的 DB 端口判成 AVD 占用、早期 ADB offline，缺失测试 APK、原生 stderr/编码及只读脚本的表名/编码错误均保留原文件/回执；这些不计产品通过。
- run02 服务/AVD 在 run03 测试构建期间仍运行，是调度偏差，不能称全程重型任务串行合规。后来 restart 正确拒绝占用，按实际 PID/命令行认证停止后再启动。run05 最终 `closeout.ps1` 动态核实唯一目标实例、端口和数据库身份后关闭，未碰共享服务。
- 第一次独立消费者误以为正式保存态是 SAVED，实际为 COMMITTED；`consumer.py` 和失败文件保留，`consumer02.py` 按产品语义重新消费。一次诊断在 C 盘创建未带 WAL 的临时 SQLite 副本，不能作为完整库证据；后续 D 盘完整消费者纠正，临时副本按哈希保全后清理。没有依据该不完整副本删除/改变 guard。

失败指纹、不同新依据和未通过轮次见 `failures-summary.json`；无同根因无依据机械重跑，无失败计数清零。本项没有修改产品原件来满足测试。

## 收口与接续

`stop-result.json`、`processes-final-stopped.json`、`listeners-final-stopped.json` 记录本轮 HTTP19781、认证 MySQL19780 和 AVD5622/5623 已关闭，目标 PID/端口为空；数据库身份、唯一最终 dump、最终设备包、原身份/签名、失败日志和最终 APK 保留。共享 MySQL/ADB/USB/其他项目只观察，不接原库或现网。

切片清理仅一份可重建 C 盘不完整诊断库副本及正常 Kotlin daemon 日志，输入/命令/依赖/结果/原哈希摘要见 `cleanup-review.json`。逻辑删除 1,292,574 bytes，C/D/E 前后余量见 `cleanup-result.json` 和 `disks-final-cleaned.json`；卷变化与文件长度分别记录。前序策略拒绝的 datadir/AVD RAM 目标未改用其他方式删除；原资料、身份、回执、唯一证据及源码未清。

全量最终改动/patch/zip、索引和 Git 回执见 `dirty-candidate01.*`、`tracked-candidate01.patch`、`evidence-index-final01.json`、`git-postcommit.json`。提交仅明确范围，开发备份不合 main、不发布/部署/Release，不用正式签名。七类生产决定与 TEMP 生产归档保持关闭，`localDeletionAllowed=false`，D16 标准不降低，取消的换机接续链不恢复。

下一可执行项：核对 U02 原结果未知/保全主动作和 U04 辅助页当前候选缺口，按已有路径＋SHA复用定向证据，只补直接受影响产品入口；统一最终候选及 D18 L3 仍另行执行。
