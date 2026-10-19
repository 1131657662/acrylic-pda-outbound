# E08/D17 run903 fixture 与消费边界审计（2026-10-18）

本审计只核对继承 run903 的输入是否能被 `PreparationEndToEndTest` 消费，以及当前隔离环境是否具备继续执行的条件。它不构成 D17、E06 L2、D18 L3 或总目标通过。

## 输入与身份

- 工作树 `D:/acrylic-pda-outbound`，分支与 HEAD/远端见看板事实块；唯一写者为本会话。
- `server/build/pda-emulator-fixture.json`：SHA-256 `4F4C12B0FDE30989856601271B297AF60F45E93D085A880B4987B6EE197D874A`，189 字节，字段为 `username/password/warehouseId/orderId`。该文件由 `PdaAndroidFixtureIT` 从隔离 MySQL 的 `IdentityFixture`/`PublicationDriver` 生成，属于合成隔离身份和订单，不是现场 PDA 或业务回执。
- run903 MySQL：`.local/e2a-cross-20260925-903/data`，回环 19330，`admin.cnf` 身份查询返回该 datadir、端口 19330、binlog=1；主 schema `acrylic_e2a_cross_20260925_903`，迁移行数 99，`app_user/outbound_order/product` 各 1 行。共享 3306 未连接或修改。
- `server/build/pda-temporary-android-body.json` 为旧 TEMP 输入（SHA-256 `9ACAF7E1C733E7781A2C617E059EFC9DAD2DB8EC51F535B79E0EA71E70745CF2`），不是本轮 `PreparationEndToEndTest` 的输入，不能混用。

## 字段映射与调用事实

| 来源 | 实际字段/行为 | 结论 |
| --- | --- | --- |
| `PdaAndroidFixtureIT` | 写入 `username/password/warehouseId/orderId`，不写 `baseUrl` | 只能作为服务端身份/订单输入，不能直接推送给 Android 测试 |
| `PreparationEndToEndTest` | 从设备 `filesDir/e2e-fixture.json` 读取并删除；必须读取上述四字段和 `baseUrl` | 需要单独生成绑定文件；不能伪造现场输入 |
| `SubmissionProxy` | 接收 Android 请求并转发到 `baseUrl` 上游；会故意丢一次提交响应 | Android 业务消费必须经过真实 HTTP 服务和隔离库 |
| `PreparationEndToEndTest.AdminApi` | 以 fixture 用户登录真实 HTTP、注册设备、领取订单、准备、审核提交 | 需要 HTTP 服务端口与 run903 schema 一致，且测试 APK/应用数据为同一次运行 |
| `PreparationEndToEndTest` evidence | 记录 `assignmentId/orderId/deviceId/rows/ackAt`、2 行扫描、601500g、丢响应恢复等 | 只有设备端实际 instrumentation 产出的 `e2e-evidence.json` 才能作为客户端事实 |

## 当前处置

- 保留 `pda-emulator-fixture.json` 和 run903 datadir；不把合成身份写成原 PDA/现场事实。
- 当前无绑定 run903 的 HTTP 服务；已有 5173/5175/8000、3306/33318 是其他项目/共享服务，只观察不连接。
- ADB 只列 USB M71 `22D2910042`；没有运行中的 emulator。已有 `Pixel_6` AVD 含历史 userdata/锁文件，未启动、未清空、未覆盖。若继续，必须在新证据目录使用独立 userdata，并记录其绝对路径和 boot/serial 事实。
- `PreparationEndToEndTest` 尚未执行；因此没有 Android 产品消费、真实 HTTP/客户端事务、Chrome/Python 消费或 D18 L3 证据。

## 直接验证与重新入队条件

下一轮只能在新证据目录和未占用 HTTP 端口启动绑定 run903 schema 的真实 Spring 服务，生成包含 `baseUrl` 的 `e2e-fixture.json`，并在隔离 userdata 的 emulator 上安装当次 APK、运行该测试、回读设备证据和数据库守恒。若任一身份、端口、datadir 或输入 SHA 不匹配，立即保全并停止，不复用旧 run03。

## 本轮实际消费结果

- 新证据目录 `.local/goal-20261018-resume/run903-preparation01/` 使用端口 19436 的真实 Spring JAR、run903 19330 MySQL 和独立 `Run903_Pixel6` AVD（ADB `emulator-5568`）。原 `Pixel_6` userdata 未启动或覆盖。
- `PreparationEndToEndTest#registeredDeviceLogsInClaimsDownloadsAndRecoversLocalActivation` 实际通过，instrumentation 为 `tests=1 failures=0 errors=0 skipped=0`。设备 evidence SHA-256 `2CF8CF130B43F418C2BD48B4E1A391A5D3B1B8E927F411FDE3486FABF9461088`，记录 2 行、601500g、Room 重开、签名验签和提交丢响应恢复；原始日志 SHA-256 `38582E829BE87D1AD98D0C958F42B3858697B63D1397EDBE31C60ED4BA50C426`。
- 数据库回读显示 `order_assignment=1`、`assignment_preparation=1`、`order_result_version=1`、`submission=1`、`submission_payload=1`、`submission_review=1`，提交状态 `SUCCEEDED` 且审核 `APPROVE`；`pda_audit_event` 为 0（该测试范围不产生该表事件）。数据库事实文件为 `db-after-targeted.tsv`/`db-state.tsv`，未连接共享库。
- 同一设备随后执行 `HighFrequencyOfflineDeviceTest#realHomeNavigationKeepsMaintenanceOutsideWorkPage`，实际失败于 `temporaryPreparation` 为 `GONE`。本轮只准备普通订单，没有临时资料，故该失败是输入/前置缺口；不改测试断言、不直接显示入口、不计 U04 或 D17 L1。失败日志 SHA-256 `1586CA1EF911D4A10102B2A49A4F7B2D5CD4358087C78FE453E2362A25F56FED`，保全后暂缓，待真实隔离临时准备输入重新入队。
- HTTP 服务、emulator 和 run903 MySQL 在收口前仍需按 PID/身份认证停机；在停机前不清理 userdata、服务日志或唯一 evidence。

## 收口与清理

- 服务 PID 18740、独立 emulator `emulator-5568` 和 run903 19330 已停止；收口后只剩共享 3306/33318 与 USB M71 `22D2910042`。最终监听、进程、ADB 和磁盘见 `run903-preparation01/post-stop-final.json`。
- 本轮新建 AVD userdata 目录已在无消费者、进程退出后清理。清理前清单/关键文件哈希见 `avd-cleanup-before.json`，清理命令及结果见 `avd-cleanup-after.json`；释放 `5,986,930,688` bytes。保留 instrumentation、服务日志、fixture、数据库状态、设备 evidence 和高频失败原文。
- D 盘清理不改变 `localDeletionAllowed=false`，不删除原资料、Keystore、继承源码或唯一历史证据。

