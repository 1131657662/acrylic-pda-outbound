# E07/D06-D07 Android 与真实 HTTP 候选（run735）

本批在全新隔离 run735 上补做 Android/真实 HTTP 组合。服务端消费者夹具 `FaultRestartConsumerFixtureIT` 为 `1/0/0/0`；Spring HTTP 使用 `19435`，模拟器通过 `adb reverse tcp:19434 tcp:19435` 消费产品固定地址。数据库是 `127.0.0.1:19345/acrylic_e2a_cross_20260925_735`，所有进程在收口时已停止。

## 输入与边界

- 分支 `codex/development`，HEAD `93710ec5cff979a567654084c80abe089e9a73b7`；消费者输入 `.local/goal-20261011/fault-restart-consumer-735.json`，SHA-256 `99BF5AAFF320DFA216EDDDC794508B83D5D020B414960D4BAC587A7D8C136859`。
- 设备仅为隔离 `Pixel_6`、`emulator-5568`；实体 `22D2910042` 未下发命令。Debug APK SHA-256 `5197F24B2FF9C29DFC66286DC48EF805B7A101057A509DF6E894D8C22F2072F7`，AndroidTest APK SHA-256 `4761A09233237D7AEBB98EA0346392DFA9CBCE383BC31DA7589F8F334BF7BD92`。
- HTTP 的 `outbound.recall.fault-withdrawal-enabled` 仅在隔离进程环境中显式设为 `true`；产品默认关闭值和生产边界未改。首次默认关闭请求失败原文保留在 `fault-restart-01-new-device-run735.log`。

## Android 结果

四个方法均由显式 `adb -s emulator-5568 shell am instrument` 单独执行，输出均为 `OK (1 test)`，无跳过或错误：

1. 新设备登记、领取、下载、扫码、冻结和成功上传。
2. `am force-stop com.acrylic.outbound.pda.debug` 后恢复已验证成功结果和原冻结字节。
3. 原机消费签名撤回，保留本地订单为 `WITHDRAWN/RECALLED`，冻结被拒绝。
4. 新产品进程再次读取撤回状态，原准备资料和撤回状态保持。

直接 instrumentation 汇总 XML、四份原始输出、logcat、设备属性和应用持久文件位于 `.local/goal-20261018-resume/e07-d06-d07-run734-android/run735-evidence/`。应用结果文件确认 `SYNCED`、`localDeletionAllowed=false`；数据库快照为 1 条 `SUCCEEDED` submission、2 条 capture、8 条 event、2 条 dispatch、3 条 `WITHDRAWN` change request。`database-final.sql` SHA-256 为 `26C55A0FC344EFFD52E83D6E43714C2A39DB5A279F19DFFE00DF000EB9E22BB0`。

## 停机与未验

首次以隔离账号停机因脚本未授予 `SHUTDOWN` 失败，原始错误保留；随后只在隔离库中由本地 root 授予该账号 `SHUTDOWN`，再以隔离账号 `mysqladmin` 成功停机，事实见 `.local/e2a-cross-20260925-735/private/shutdown-retry2-fact.json`。HTTP、MySQL、模拟器收口后无本项目监听。

本批证明 Android 与真实 HTTP/MySQL 的局部候选，不包含 Chrome、外部 Python 消费、原 PDA/现场身份、外置介质、原模拟器恢复全链或完整非空迁移组合；不计 D06/D07 L2、D17、E06 L2、D18 L3、第8节闸门或总目标。Gradle connected 误枚举实体 `m71` 的失败原件另保留，不再复用该运行器。
