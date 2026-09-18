# PDA 应用检查更新与安全升级交付记录

日期：2026-09-19。分支：`codex/development`。本专项已完成源码、staging 更新渠道和一台 M71 的 App 内覆盖升级验证；不表示 R05 或整个系统已经验收，也不表示生产已部署。

## 已实现范围

- 设置页和首页提供“检查应用更新”，显示当前版本、staging 渠道和更新说明。
- 更新清单只从当前服务地址的 HTTPS 渠道读取；客户端验证 ECDSA 清单签名、包名、渠道、`versionCode`、最低 Android API、可升级来源范围、APK 大小/SHA-256 和签名证书摘要。
- APK 下载到 App 私有 `files/updates/` 目录，使用临时文件、Range/ETag 续传和校验后原子改名；响应 ETag 在下载开始时持久化，完整但摘要错误的临时包会清理，未校验文件不会交给系统安装器。`FileProvider` 只暴露更新目录，安装由 Android 系统安装器确认。
- 扫码输入、待保存确认、上传结果未知、未处理输入、召回/恢复和待确认续作会阻止安装，并显示下一步。安装取消或系统未完成安装时保留旧版本、原资料和可重试的下载记录。
- 未卸载、未清空数据、未重新配对或重置设备身份。Keystore、绑定、订单、Room 资料、待上传文件、原请求和审计仍由原应用数据目录保存。
- `tools/publish_pda_update.py` 生成不可变发布目录和签名清单；test Nginx 已增加 `/updates/` 静态路径。签名私钥只在本机 `.local/pda-update-signing/` 和服务器受保护目录，不进入 Git。

## staging 渠道

- 清单：<https://outbound-test.nbsmzwl.cn/updates/staging/manifest.json>
- APK：<https://outbound-test.nbsmzwl.cn/updates/staging/r05-update-02.apk>
- 包名：`com.acrylic.outbound.pda.staging`
- 版本：`0.10.0-r05-update2-test` / `versionCode 11`
- APK SHA-256：`03a8ddc0ee839a464b51bf7a26cef91c181a2bc2381ff82d27e6de3290eb2d9b`
- APK 大小：10,992,083 字节
- staging 证书 SHA-256：`2437447bae29492167a90d7dd2adaf696b226b05438f995475d4fff523e51196`

远程清单和 APK 已通过 HTTPS 返回 200、与本机发布目录字节一致、摘要一致。发布 ID 为 `r05-update-02`，同一发布目录不覆盖；后续包须使用新的发布 ID 和更高 `versionCode`。

## M71 实际验证

设备：`22D2910042`（Android 9，480×800）。

1. 已安装首版 `0.9.0-r05-update-test` / `versionCode 10`。
2. 在 App 内更新页检查 HTTPS 清单，验证签名和兼容范围，下载真实 APK，并在安装前显示“安装包已校验”。
3. 通过 `FileProvider` 调起 Android 系统安装器；系统显示“是否要为这一现有应用安装更新？您现有的数据不会丢失”。手工确认后显示 `InstallSuccess`。
4. 安装后实际版本为 `0.10.0-r05-update2-test` / `versionCode 11`。`firstInstallTime` 保持 `2026-09-18 11:45:04`，`lastUpdateTime` 更新为 `2026-09-18 23:53:58`，数据目录仍为 `/data/user/0/com.acrylic.outbound.pda.staging`。
5. 更新页重新进入时，`INSTALL_PENDING` 会读取本地清单并以实际已安装 `versionCode` 显示“更新完成”；未完成或取消则恢复“可重试安装”。本次新增 `PdaUpdateDeviceTest#installPendingIsResolvedAfterInstalledVersionIsObserved` 在 M71 上通过（1 test）。

安装后只读核对（无秘密正文）：

| 项目 | 结果 |
| --- | --- |
| `no_backup/device-binding.json` | 存在，241 字节；安装前后 SHA-256 均为 `51a0b5344f89fa3fec5e89e948ffb22d352076ddcae53ddc0afbad8d113ed123` |
| `no_backup/pairing.enc` | 存在，1,255 字节；安装前后 SHA-256 均为 `3d09b20a11fba46033981a0f07be734f1d24117ceeb1b15bcd89808f7ad6ef04` |
| `no_backup/session.enc` | 存在且可继续使用；加密会话在重新登录后会更新，不能要求字节不变 |
| Room 数据库 | `outbound-pda.db` 存在，schema 9；`prepared_order=1`、`prepared_bale=6`、`work_order=1`、`work_line=1`、`work_capture=1`、`work_scan=1`、`work_event=6`、`local_submission=1`、`claim_intent=1`，其余召回/续作/同步阻断表当前为 0 |
| 更新状态 | `files/updates/state.json` 和已校验 APK 保留；当前记录为 `r05-update-02` / `INSTALL_PENDING`，重新进入更新页按已安装版本完成核对 |

设备证据文件保存在本机 ignored 目录：`.local/r04-device-before-hashes.txt`、`.local/r04-device-installed-hashes.txt`、`.local/m71-update-data-fingerprint-after.json`、`.local/m71-update-files-updates-state.json`。代表页截图为`.local/pda-update-channel/update-screen.png`；截图、数据库和加密文件不提交 Git。

## 本轮验证结果

- `:app:compileStagingKotlin`：通过。
- `:app:testStagingUnitTest`：通过。
- `:app:assembleStaging`：通过。
- `:app:lintStaging`：通过，0 错误；保留已有非阻断提示。
- `:app:assembleAndroidTest` 及 staging instrumentation APK：通过构建。
- staging M71 instrumentation：单独运行 `installPendingIsResolvedAfterInstalledVersionIsObserved`，1 test 通过。此前同一 M71 已在首版阶段通过真实清单/APK下载校验及系统安装器入口测试。
- `python -m py_compile tools/publish_pda_update.py`：通过；`git diff --check` 在提交前执行。

曾记录的失败也保留：Gradle wrapper 首次下载因网络连接重置失败，改用本机缓存的 Gradle 8.11.1；安装完成后重跑整类三个 instrumentation 测试时，旧的“安装器入口”用例要求安装按钮可见，而 `INSTALL_PENDING` 已被实际安装结果解析为完成，因此该整类重跑未计为通过；没有清除数据或伪造状态，单独的安装后核对用例通过。

## 未完成事项与边界

- 第二台 PDA 只能异地联网，未在本专项安装第二版 APK；异地设备、长时任务、断电恢复和含大量扫码事实的真机升级仍待现场安排。
- 仅验证 staging 开发签名，未启用 production 更新源、正式签名或正式发布；test 域名不是生产环境。
- `r05-update-02` 是不可变测试发布；远端包基于本轮最后一次修正前的构建，首版已完成真实校验和覆盖安装。后续包含新源码的包必须生成新的 release ID 和更高版本号。
- R05 的条码可用状态流、通用审计补传、持久任务/重试、提醒、安全留存清理及其他路线任务继续保留；本专项不把这些未完成项写成已验收。
