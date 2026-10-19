# E07/D14 Android 与真实 HTTP 运行失败（run730）

run730 使用新的隔离目录 `.local/e2a-cross-20260925-730/`，Android `E2aRealHttpRecoveryTest` 与服务端 `E2aAndroidHttpIT` 均失败。Android 原始错误为 `原设备审计前缀存在缺口或内容不可核验`；服务端 XML 为受保护的 Android HTTP 日志断言失败（expected 0, actual 1）。这不是通过证据，也不重跑。

隔离身份为数据库 `acrylic_e2a_cross_20260925_730`、MySQL 端口 `19342`、输入 `private/android-http-input.json`。关键原件 SHA-256：`android-result.xml` `5FAD7CCA17264F53B58BBD0CB5BCB3617209340C2D372435445A730E889D29D1`、`integration-result.xml` `D0E1C935FAB201E80777C44CBA31BCB9FE54922AFF6524537D4B324AD317FC17`、`integration.log` `C1E4C26450D38E1B52A427877FF8AF393740BB3159B693EA3B72251DA72FD257`、输入 `BC466B20AB596EBAD91C1197F2A190BC79826D904BFE83A29C07559E1604DD91`、`private/android-http.log` `3EA4F3854D87B3F3B490E9603A80C4B16CC52191EE7C78C5CCD5857FFBE9D25C`、`private/server.err.log` `C607A1AA3B826FF322AA3C4750EE687ECB19B1A2DE755FC00E3093027861D2E5`。

MySQL 日志记录正常启动、认证停机和 `Shutdown complete`；隔离输入、datadir、日志和 XML 保留。业务成功/全表守恒未成立，不能计 E07、D14、D17、E06 L2、D18 L3 或第8节闸门。
