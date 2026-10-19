# E08/D17 Android 真实 HTTP 候选 run1600（2026-10-18）

本报告登记全新隔离 run1600 的 R09 临时来源证据消费。Android 侧为 Robolectric 主机测试，不是真实设备；未恢复已取消的换机承接、确认或激活链。

## 输入与运行

- 工作树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `93710ec5cff979a567654084c80abe089e9a73b7`。
- 命令：`powershell -NoProfile -File tools/e2a-cross-source-verify.ps1 -Run 1600 -Android -DbPort 19360 -HttpPort 19460`；attempt `.local/e2a-cross-20260925-1600/`。
- 隔离库 `acrylic_e2a_cross_20260925_1600`，账号 `e2a_cross_20260925_1600@127.0.0.1`；`connected-identity.tsv` 与 `shutdown-identity.tsv` 内容和 SHA-256 一致，binlog 开启。
- 输入为 `.local/e2a-cross-20260925-1600/private/android-http-input.json`，仅记录合成 TEMP/R09 夹具；凭据、token、私钥和签名正文不在本报告展开。

## 结果

- `integration-result.xml`：`E2aAndroidHttpIT`，`1/0/0/0`，退出 0；`test-completed.marker` 存在。
- `android-result.xml`：`E2aRealHttpRecoveryTest`，`1/0/0/0`；`android-result.json` 为 `PARTIAL/REVIEW_REQUIRED`，包含 OPEN/RAW/PAGE/STOP/VERIFY 回执、原请求和五次丢回执恢复输入。
- Flyway 在非空 V34 种子上完成 V34→V36；日志记录完整迁移校验和真实 Spring HTTP 端口 `19460`。
- MySQL 由隔离账号执行认证 shutdown，日志记录正常关闭；端口 `19360/19460` 在收口后无本项目监听。
- 本批只计 R09/E06 的 Android 主机 + 真实 HTTP/MySQL + 非空迁移局部候选；不计 D06/D07、D17 整体、E06 L2、D18 L3、第8节闸门或总目标。它不包含真实 Android 设备、Chrome、外部 Python、原模拟器恢复全链、现场身份、外置介质或原 PDA。

## 证据哈希

| 路径 | SHA-256 |
| --- | --- |
| `.local/e2a-cross-20260925-1600/private/android-http-input.json` | `1A509A69B35A15766C09E4A00895EB36D569FF5F57E67C74D672CABAC46621B3` |
| `.local/e2a-cross-20260925-1600/integration-result.xml` | `37C180C65BF4A48F4FAB466260E4303E6E5CCA8718C997B5D385F053361FBCDC` |
| `.local/e2a-cross-20260925-1600/android-result.xml` | `76F2A779339A83BF6BA8574291810DCEBBB884D90BD558393A3445B5A4A44C8B` |
| `.local/e2a-cross-20260925-1600/android-result.json` | `6A50FFDE20071E75F041C84C73BE819D8783AFB8D34D5674B7924FF84D4C2363` |
| `.local/e2a-cross-20260925-1600/private/android-http.log` | `E8BB776D1D5D18881E06EF800E9E4665CD4A2B1CCA291B742D8DF666E6C6D37A` |
| `.local/e2a-cross-20260925-1600/connected-identity.tsv` | `3856085388B4DC1C595C565229437C9088913D41BF94D0EDF27CD54A8258AD4F` |
| `.local/e2a-cross-20260925-1600/shutdown-identity.tsv` | `3856085388B4DC1C595C565229437C9088913D41BF94D0EDF27CD54A8258AD4F` |

失败的旧 Android/HTTP attempts、run735 真设备候选及其原件继续保留；下一步是另建新编号的实际 emulator D06/D07 组合，不复用 run03 或 run1600 的主机证据。
