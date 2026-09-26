# PDA test code22 发布记录（2026-09-23）

- 范围：`0.15.2-pda-orderflow-test` / code22 起支持后续应用内检查更新；旧版应用内升级不在本次范围。其他 PDA 使用 `https://outbound-test.nbsmzwl.cn/pda-download` 下载，通过系统安装器覆盖，不卸载、不清数据。
- test：后端切到 `/opt/acrylic-outbound-test/releases/pda-orderflow-20260923-01`，部署前备份 `/opt/backups/acrylic-outbound-test/20260923T073350Z`；部署时 API/Worker active，DB V34，未进行真实业务写入。公网清单 `r06-orderflow-22-20260923`，`serverCompatibility=0.30.0`，`firstInstallAllowed=true`，来源下限 code16。
- 公网核验：下载页、清单、APK 均 HTTP 200；清单 ECDSA 签名通过，SHA-256 `e0f1495560acba470d346aa30877f7d3a29c540f5f40abc4e50a2dbbff107556`。真实浏览器点击下载，APK 12,168,874 字节，SHA-256 `c711b225ac8079290a55904c9541e77307aa2e26042ee17e24d7935d0217d4bc`，与发布物一致。`/api/v1/auth/csrf` 返回 JSON/HTTP 200 和令牌。
- M71 `22D2910042`：安装前 code21 / Room18，六项未决扫码、上传、召回和续作阻断均为零；冻结作业已 `SYNCED`。稳定 DB/WAL/SHM 备份在 `.local/m71-preinstall-20260923/`。只执行 `adb install -r`，结果 Success；code22 可启动，首次安装时间仍为 `2026-09-18 11:45:04`，DB/偏好文件保留。应用内联网检查返回“当前已是最新版本；本次检查已完成”。本地 code16、21、22 APK 的 V2 签名一致。
- 限制：其他 PDA 尚待用户现场安装，code22→未来新版的实际下载/系统安装尚未现场验证；本次不等同完整验收。聚焦更新测试 8/0 和 `assembleStaging` 成功；全量 Android 测试此前 Robolectric JVM 崩溃，后端隔离 MySQL 集成测试未完成。
- 证据：忽略目录 `.local/pda-release-20260923/`、`.local/m71-preinstall-20260923/`；APK 和设备数据库不得提交。主树 `codex/development` @ `23ae8c492acf47e89875852d2573392fcf379f80`；本次未提交/推送/合 `main`。其他任务未提交代码及看板写权保持，不覆盖。
