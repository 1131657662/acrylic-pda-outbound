# code15 → code16 隔离升级验证

日期：2026-09-19。分支 `codex/development`。本轮完成浏览器下载后的**系统覆盖安装、取消重试、Room11→14和资料保留**；未完成 App 内检查/下载/安装的完整链路，未放开公网旧版本升级范围。M71及此前两台首次安装模拟器保留。

## 实际安装与资料保留

使用新的 `PdaUpgradeVerify` / `emulator-5584`（API37）。先以ADB安装原始已发布code15作为基线，再通过该模拟器Chrome145下载公开code16，由用户开启Chrome安装来源权限。第一次系统“Update this app?”选择取消，原生检查确认旧版和全部基线资料保持；重新打开同一APK并点击Update，系统显示“App updated”。**code16未通过ADB安装。**

| 项目 | 实际结果 |
| --- | --- |
| 升级前APK | `0.11.0-r05-sync-test` / code15，Room11；SHA-256 `2c302a14cc24c9c0e7f00c64da9316e72ed73ed6f16f38972816eb56f934baf0` |
| 升级后APK | `0.11.1-r05-download-test` / code16，Room14；Chrome下载及实际已装APK均为11,173,106字节，SHA-256 `9bc281b2c9a91972e17843004f08208c172b862cb745a3c5baeda4e275df796c` |
| Android安装来源 | 系统安装器 `com.google.android.packageinstaller`；来源Chrome，升级前后firstInstallTime保持，实际包名为staging，唯一签名证书摘要 `2437447bae29492167a90d7dd2adaf696b226b05438f995475d4fff523e51196` |
| 真实身份和登录 | code15使用测试公网短码登记、原密钥确认及人员签名登录；升级后设备绑定、pairing.enc、session.enc摘要保持，原私钥挑战签名验证通过，原会话可调用设备身份接口及订单/条码同步 |
| 本机作业资料 | 使用服务端集成测试生成的签名夹具，经原版激活、采集、作废及冻结代码生成：1订单、2库存包、3采集、2扫码明细（含作废）、12事件、1有效防重记录、1未上传冻结批次；升级前所有原表的逐行摘要和行数保持，冻结正文与原SHA一致 |
| 取消重试 | 首次取消后code15/Room11、原身份及全部原表摘要保持；随后同一浏览器文件可再次交给系统安装器并成功覆盖 |
| 迁移完整性 | Room14打开、quick_check和foreign_key_check通过；更新后的实际APK元信息、证书及文件摘要核对通过 |

夹具资料属于隔离模拟器的合成作业，使用集成测试身份/固定时间边界，与真实公网登记身份分离；未领取真实订单、未上传合成扫码、审计或冻结批次。不能据此宣称含真实作业的M71、完整线上提交/归档、断电恢复或异地验收通过。

## App内升级的未完成范围

公开16版清单仍为`minSupportedVersionCode=16`、`firstInstallAllowed=true`、后台兼容0.18.0，摘要仍为 `bdede672609f486a74267b3efdfc786872c21a3a5d0e65ee9b49d73a0d1d1ab7`。本轮没有重写该清单、替换已发布APK或放开旧设备升级。

原始15版运行时兼容检查实测：0.17.0、0.18.0通过，0.19.0、0.20.0拒绝。不能用当前工作区代码或Git HEAD猜测已发布15版行为；本轮阻断旧机公开更新的是最低可升级版本范围。

为独立验证App内升级，曾准备仅验证用的签名清单`r05-upgrade-validation-16`及完全相同的16版APK，首次安装关闭、起始15。它使用独立目录 `/opt/acrylic-outbound-test/upgrade-validation-20260919`，从未切换到公开渠道；初始0.17.0标记是旧协议测试输入，不是后台实际部署版本。曾添加仅回环HTTPS监听及保留端到端TLS的本机隧道/CONNECT代理。自动审批两次拒绝带代理启动隔离模拟器，即使用户明确同意后仍仅返回`blocked by policy`，因此没有通过其他方式设置同一代理。默认网络独立HTTPS端口尝试无法连通：首次Nginx监听地址热切换绑定失败，改用另一端口后服务器监听正常但外部连接超时。

上述临时Nginx监听已停用，配置归档为独立验证目录的`nginx-disabled.conf`；本机隧道/代理进程已停止。测试公网原下载页、清单、APK及业务服务保持。最终覆盖升级走默认网络Chrome和系统安装器；**不计为App内下载、INSTALL_PENDING恢复或App安装来源授权验证**。

## 测试及证据

本轮7项原生检查分阶段通过：旧版配对登录、原版兼容检查、补完夹具基线、取消后保留、升级后保留、原会话同步及升级后当前版本UI。UI复用现有只读测试方法，方法名虽含firstInstalled，本次明确用于覆盖升级后的code16，不将其记为首次安装。测试辅助APK构建通过，仅新增三份隔离测试源码，未改变应用业务逻辑或重建已发布APK。

首次夹具基线失败：复用了同一个`WorkInput`的采集ID，触发主键重复。修正为新采集ID，并在原有2条采集/1条作废记录上核对后补完剩余步骤，没有清库或重新登记。失败日志保留，不能写成首次全通过。产物收集脚本曾因默认GBK读取中文JSON失败，改为UTF-8；APK本体摘要和证书核验已先完成，原生同步测试未受影响。

证据目录 `.local/pda-upgrade-stage/`（不提交，可能包含设备测试标识）：

- `before.json`、`after.json`、`external-cancel-verified.json`：原表/身份摘要、版本、首次安装时间和取消/迁移验证。
- `paired.json`、`original-client-compatibility.json`、`sync-after.json`：旧版登记登录、实际兼容范围及升级后原会话同步。
- `before-installed-package.txt`、`after-installed-package.txt`、`installed-apk-verification.json`：PackageManager来源及实际安装包核验。
- 各测试方法同名`.log`、`native-build*.log`、`checkpoint.json`：分阶段成功、首轮失败及恢复记录。
- `public-manifest-unchanged.json`：公网16版清单保持的原始证据。

## 下一具体动作

1. 保留当前模拟器资料，先解决可用的独立HTTPS验证渠道或自动审批限制，再补App内检查、可靠下载、取消恢复及系统安装的完整链路；代理已有用户授权，不重复索要同一授权。
2. 若要开放公网旧版本范围，使用新ID和更高版本，不重新签发同一公开16版。用原始15及当前16分别验证拟发布APK，并核对实际后台兼容后才启用已验证范围。
3. 按用户决定后补Android9/M71、空白真机及异地，继续R05同步/任务和本机0.20/V28对公网0.18/V23的联调待办。

本轮未提交、推送、合并、打标签、GitHub发布或生产部署；未部署本机0.20/V28、未重启业务API。上述系统升级通过不代表R05整体验收。
