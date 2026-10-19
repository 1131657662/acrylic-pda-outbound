# E07/D14 Android 与真实 HTTP 运行失败（run732）

run732 使用新的隔离目录 `.local/e2a-cross-20260925-732/`，Android `E2aRealHttpRecoveryTest` 与服务端 `E2aAndroidHttpIT` 均失败。Android 原始错误为 `原设备审计前缀存在缺口或内容不可核验`；服务端 XML 为受保护的 Android HTTP 日志断言失败（expected 0, actual 1）。这不是通过证据，也不重跑。

隔离身份为数据库 `acrylic_e2a_cross_20260925_732`、MySQL 端口 `19342`、输入 `private/android-http-input.json`。关键原件 SHA-256：`android-result.xml` `87A98A3A69F65241E5E27B05F12320BF710E798D44C71157FBE1D0A85D1CC31A`、`integration-result.xml` `41979639F0F367A19E0971E3452F1E0392EDC7784956AFF2C6E00CB4F3D8CD8C`、`integration.log` `29C60BABB67059F28B1E3099465BF85BC60FC03C120D48342535C8432C88A059`、输入 `D7D80238AD514FF950799C9A641791D28ED85FDACDF4A97DBA0E36221CBC49A3`、`private/android-http.log` `844FBBE0BC9B80CF200998F48472B8B5C8357E97566A9416F08948E79E61AF8C`、`private/server.err.log` `566E1CF6C8908827ECDFB600A1A6916F34888017B82C307C375ABBFCED2DAFDE`。

MySQL 日志记录正常启动、认证停机和 `Shutdown complete`；隔离输入、datadir、日志和 XML 保留。业务成功/全表守恒未成立，不能计 E07、D14、D17、E06 L2、D18 L3 或第8节闸门。
