# PDA 正式签名身份（R06 起）

> **无状态文档**：本页只记录签名身份与操作约定，不含版本/进度事实；版本四元组见[看板](ORCHESTRATION_STATUS.md)的生成事实块。**本页不含任何口令或私钥内容**。

## 1. 这是什么

自 R06 起，正式 PDA APK（`production` 渠道，包名 `com.acrylic.outbound.pda`）使用**本机生成的独立签名身份**，不再依赖 Android 调试签名。当前是测试环境，旧版本可直接卸载重装，因此本次换身份不需要兼容旧证书链。

| 项 | 值 |
| --- | --- |
| 生成时间 | 2026-09-27（本机 `keytool`，JDK 21.0.11） |
| keystore | `D:/acrylic-pda-outbound/.local/pda-signing/pda-release.keystore`（PKCS12，**不入 Git**） |
| 口令文件 | `D:/acrylic-pda-outbound/.local/pda-signing/signing.properties`（**不入 Git**） |
| 别名 | `pda-release` |
| 算法 | RSA 4096，签名算法 SHA256withRSA |
| 有效期 | 10,950 天（2026-09-27 → **2056-09-19**） |
| 主题 | `CN=Acrylic Outbound PDA Release, OU=Outbound PDA, O=Acrylic Outbound, C=CN` |
| 证书 SHA-256 | `96:E9:DE:81:22:6F:A4:8A:04:C6:B6:BA:DA:90:AE:EF:FD:54:53:D0:B5:51:BE:D8:DE:04:EF:FB:C0:6A:95:B8` |
| 证书 SHA-256（无分隔） | `96e9de81226fa48a04c6b6bada90aeeffd5453d0b551bed8de04effbc06a95b8` |
| 证书 SHA-1 | `1f38e9279331b4d1f8505f265e4c34c12b02af54` |
| keystore 文件 SHA-256 | `B83287088E2C8BA483B7C73A15D4A327E18356325DD79615202D26DF8BDDD646` |
| properties 文件 SHA-256 | `C6024200BE2A65C48AFF0E3EEB5C5658742C250D1CE95F64685E4E1C11312489` |

证书摘要用于发布清单的 `certificateSha256` 字段与 `apksigner verify --print-certs` 的比对；它**不是**口令，可以公开。

## 2. 备份（两份，已放在不同物理盘）

| 位置 | 说明 |
| --- | --- |
| `D:\acrylic-snapshots\pda-signing-backup-20260927\` | 与工作副本同在磁盘 1（SSD），用于误删恢复 |
| `E:\acrylic-pda-signing-backup-20260927\` | **磁盘 0（HDD，另一块物理盘）**，用于磁盘故障恢复 |

每份包含 `pda-release.keystore`、`signing.properties`、`BACKUP_INFO.txt`（摘要与警告）。两份的 keystore 摘要均已复核等于上表值。

**仍未完成**：机器之外的副本（离线 U 盘 / 加密云盘 / 保险柜）。当前两份都在同一台机器上，整机损坏或失窃会同时丢失。请业主自行完成异地副本；本仓库不允许把 keystore 或口令提交进 Git，也不应粘贴到聊天、工单或截图中。

## 3. 构建如何使用

- `android/app/build.gradle.kts` 的 `signingConfigs` 从 `../.local/pda-signing/signing.properties` 读取 `storeFile / storePassword / keyAlias / keyPassword`；`release` buildType 使用该配置。
- **文件不存在时不创建 release 签名配置**：`debug` / `staging` 构建与全部单元测试在任何机器上都不受影响。
- 版本号来源仍是 `build.gradle.kts` 的默认值（可用 `-PpdaVersionCode` / `-PpdaVersionName` 覆盖），脚本不再硬编码版本。

```powershell
# 一键：跑单元测试 + lint + 打包 release + apksigner 校验 + 落 outputs/pda
powershell -NoProfile -ExecutionPolicy Bypass -File tools/build_pda.ps1 -BuildType release `
  -ServerUrl 'https://<host>/api/v1/' -EnvironmentName '测试环境'

# 等价的手工验证
.\gradlew.bat -p android :app:assembleRelease
& "$env:LOCALAPPDATA/Android/Sdk/build-tools/36.1.0/apksigner.bat" verify --print-certs `
  android\app\build\outputs\apk\release\app-release.apk
```

发布到更新渠道时，`certificate-sha256` 必须填上表的证书摘要（`tools/publish_pda_update.py --certificate-sha256 ...`）。

## 4. 首次验证的产物（2026-09-27）

| 项 | 值 |
| --- | --- |
| 打包产物 | `outputs/pda/Acrylic-PDA-0.16.0-r06-release.apk`（9,998,436 字节，`outputs/` 不入 Git） |
| 包名 | `com.acrylic.outbound.pda`（production 渠道，无后缀） |
| versionCode / versionName | `21` / `0.16.0-r06-release` |
| 签名校验 | `apksigner verify` → `Verifies`，v2 方案通过（minSdk 28，无需 v1） |
| 签名者摘要 | 与上表证书 SHA-256 一致（`96e9de81…95b8`） |
| APK SHA-256 | `fc0b523a499bd1717a140d83c1ca08adb4c7e487910da7a4979ee9a78506dedf` |

注意：`-ServerUrl` 与 `-EnvironmentName` 会写进 `BuildConfig`，**改变它们就会改变 APK 字节与摘要**。每次发布都要按实际注入值重新记录摘要（上图为本机 `-EnvironmentName unset`、未配置服务地址时的值）。

## 5. 与"更新通道密钥"的区别（勿混）

| | APK 签名身份 | 更新清单签名密钥 |
| --- | --- | --- |
| 用途 | 决定"这个包是不是本应用的后续版本"（`ApkVerifier` 比对证书摘要） | 决定"这份更新清单是否由管理员签发"（`UpdateManifestVerifier` 验 ECDSA） |
| 材料 | `.local/pda-signing/pda-release.keystore`（RSA 4096） | `.local/pda-update-signing/update-ec-private.pem`（EC） |
| 客户端需要 | 安装包证书 = 已安装应用证书 | 仅**公钥**，经 `-PpdaUpdatePublicKey` 注入为 `BuildConfig.PDA_UPDATE_PUBLIC_KEY` |

客户端还会校验清单里的 `serverCompatibility` 是否在白名单内（`UpdateManifestVerifier.kt` 的 `UpdateCompatibility`）。**后端契约版本升级后必须把新版本号加进该白名单**，否则所有更新都会被判为"服务端兼容范围尚未验证"。本批已补入 `0.33.0`。

## 6. 安全与轮换约定

- keystore 与口令**永不入库**：`.local/` 整体被忽略，另有 `signing.properties` / `keystore.properties` / `*.keystore` / `*.jks` / `*.p12` / `*.pem` 等显式规则。
- 备份与 keystore 同等敏感：备份内含口令，持有者即可签发本应用的更新。建议把口令另存入密码管理器，构建机只保留 properties 文件。
- **丢失本身份的后果**：该应用永远无法再原地升级，只能让每台设备卸载重装新身份的包（测试环境可接受，现场不可接受）。
- **泄露本身份的后果**：持有者可签发能被现有设备接受的恶意更新。处置方式是启用新身份并接受全体重装，因此本 keystore 不应离开受控机器与备份介质。
- 轮换：仅在泄露或口令不可恢复时轮换；轮换必须同时更新本页、发布清单的 `certificate-sha256`，并在现场走一遍"卸载 → 安装新身份包"流程。

## 7. 变更记录

- 2026-09-27 建立：生成 PKCS12/RSA4096/30 年 keystore，接入 `signingConfigs`，versionCode 20→21、versionName → `0.16.0-r06-release`，`build_pda.ps1` 支持 `-BuildType` 并注入更新公钥，更新通道白名单补 `0.33.0`，两份备份落地（D: 与 E:）。
