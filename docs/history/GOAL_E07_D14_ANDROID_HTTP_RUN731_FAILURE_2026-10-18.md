# E07/D14 Android 与真实 HTTP 运行失败（run731）

run731 使用新的隔离目录 `.local/e2a-cross-20260925-731/`，Android `E2aRealHttpRecoveryTest` 与服务端 `E2aAndroidHttpIT` 均失败。Android 原始错误为 `原设备审计前缀存在缺口或内容不可核验`；服务端 XML 为受保护的 Android HTTP 日志断言失败（expected 0, actual 1）。这不是通过证据，也不重跑。

隔离身份为数据库 `acrylic_e2a_cross_20260925_731`、MySQL 端口 `19342`、输入 `private/android-http-input.json`。关键原件 SHA-256：`android-result.xml` `640A74EB5A2FA94062755F3F28D5D0D5D6E59598B19A08B2DE0013849FCE7DC1`、`integration-result.xml` `B279D085A76B18CD9CC24C4C060EBEE5C4E92620F8050F1E6424E1DA2CAC49AB`、`integration.log` `07B37FD29DE150850F4F4C1082C909BAFCE46605430F1FBAF71E3106F1B86340`、输入 `7F43442481D87E43C952FDAEA02C2EA687EFB3A54C52C1DB9E026CB04E3BE527`、`private/android-http.log` `844FBBE0BC9B80CF200998F48472B8B5C8357E97566A9416F08948E79E61AF8C`、`private/server.err.log` `32478E4DD7580EFC5B82666E772A04FB4DAAEE3B8E6CCD78840E27AEBADC605F`。

MySQL 日志记录正常启动、认证停机和 `Shutdown complete`；隔离输入、datadir、日志和 XML 保留。业务成功/全表守恒未成立，不能计 E07、D14、D17、E06 L2、D18 L3 或第8节闸门。
