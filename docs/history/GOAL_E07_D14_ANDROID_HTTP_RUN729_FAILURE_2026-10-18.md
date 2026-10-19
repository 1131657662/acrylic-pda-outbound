# E07/D14 Android 与真实 HTTP 运行失败（run729）

run729 使用新的隔离目录 `.local/e2a-cross-20260925-729/`，Android `E2aRealHttpRecoveryTest` 与服务端 `E2aAndroidHttpIT` 均失败。Android 原始错误为 `操作失败，请凭请求编号联系管理员`；服务端 XML 为受保护的 Android HTTP 日志断言失败（expected 0, actual 1）。这不是通过证据，也不重跑。

隔离身份为数据库 `acrylic_e2a_cross_20260925_729`、MySQL 端口 `19341`、输入 `private/android-http-input.json`。关键原件 SHA-256：`android-result.xml` `82C80CAC062E84DA6743F689BB39A23F50904B67BA2FAA157F550FB71CCD8149`、`integration-result.xml` `FD53867F076C50206F4A871B6D0C9BA3AF2D1F9775421E9507811DB2CC1F5254`、`integration.log` `8DBBAD7BC044E7D2A7BA942BBC03442630D3D5CF1896849AC6BEA96AB6FEFE8B`、输入 `36C9B263ACEC20A00861B672C506844782AB3D6EE06DB2604ED1FA9256AE3AB1`、`private/android-http.log` `F71ECAFFE822AD29644FC1FA5827C15A05148A5B6696C3D6FE7A0165B235CF85`、`private/server.err.log` `8149AFA2F6465805F20DA49E3EBF8191A6D5E0E72D4B2B5FA68BD322CB4FA480`。

MySQL 日志记录正常启动、认证停机和 `Shutdown complete`；隔离输入、datadir、日志和 XML 保留。业务成功/全表守恒未成立，不能计 E07、D14、D17、E06 L2、D18 L3 或第8节闸门。
