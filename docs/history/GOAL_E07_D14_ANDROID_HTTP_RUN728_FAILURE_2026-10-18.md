# E07/D14 Android 与真实 HTTP 运行失败（run728）

run728 使用新的隔离目录 `.local/e2a-cross-20260925-728/`，Android `E2aRealHttpRecoveryTest` 与服务端 `E2aAndroidHttpIT` 均失败。Android 原始错误为 `操作失败，请凭请求编号联系管理员`；服务端 XML 为受保护的 Android HTTP 日志断言失败（expected 0, actual 1）。这不是通过证据，也不重跑。

隔离身份为数据库 `acrylic_e2a_cross_20260925_728`、MySQL 端口 `19340`、输入 `private/android-http-input.json`。关键原件 SHA-256：`android-result.xml` `7207FEE956713B46CDD50673EF4EBE2215722ACDE6D5047185240917F6E442FA`、`integration-result.xml` `280FE1775E02EB81A51ABB654AC8F97A93A66A9BBAC52AD093875758F8D4920D`、`integration.log` `B606DA7D409DC40AE8E64FF25F8E81E4A3A3D7DEB48AF58EAA83B16E86CB28F1`、输入 `EE676ED129EE628C9895D015C14AA019BFDBE71B84D0051714E5BAAFE5D2176A`、`private/android-http.log` `F71ECAFFE822AD29644FC1FA5827C15A05148A5B6696C3D6FE7A0165B235CF85`、`private/server.err.log` `4EA45BA7218A1FF059B434495094EC1DB6AE143E7DE157A2F599E4B6719E8F30`。

MySQL 日志记录正常启动、认证停机和 `Shutdown complete`；隔离输入、datadir、日志和 XML 保留。业务成功/全表守恒未成立，不能计 E07、D14、D17、E06 L2、D18 L3 或第8节闸门。
