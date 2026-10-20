# Codex Windows 策略拒绝续查

日期：2026-10-20。接续[本机模拟器权限诊断](CODEX_EMULATOR_PERMISSION_DIAGNOSIS_2026-10-20.md)，按用户提出的 ACL → unelevated → 替代环境顺序核查。本项不计 D/O/U 或部署验收。

## 结论与验证标准

**完成只读核查及证据保全，修复和实际启动验证未完成；客户端启动条件仍阻断。** ACL 差异已证实，但不是已证实的拒绝根因。三个 Issue 也没有证明本机由 Windows 原生沙箱拒绝。不能把未执行写成“ACL 修复无效”“unelevated 仍失败”或“Windows 原生层已证实无法修复”。

修复通过必须实际启动模拟器、ADB online 并完成一次应用最小操作；版本查询、WHPX 检查和设备枚举不能替代。上轮 2026-10-20 17:45:46 北京时间的请求在宿主 shell 创建前返回 `rejected: blocked by policy`，原回执保持。本轮没有重试，不把旧拒绝冒充修改后复验。

用户已授权本机排障；但已拒绝操作不能靠扩大安全边界、降低隔离或更换执行路径强行通过。本轮没有修改源码、ACL、Codex 配置或设备数据，没有重启 Codex，没有访问 test、原库或生产。`localDeletionAllowed=false`，生产开关、正式签名、main、部署与 Release 继续关闭。

## 1. ACL 与实际权限

| 对象 | 实际观察 | 执行结果与限制 |
| --- | --- | --- |
| 实际会话 | `approval_policy=never`、`sandbox_policy=danger-full-access`，与上轮一致 | 任务开始前已有状态，本轮没有放宽；配置中的 elevated 不等于本次启用了沙箱 |
| 用户 config.toml | 无显式 approval_policy/sandbox_mode；`approvals_reviewer=user`、`[windows] sandbox="elevated"` | 只读；前后 SHA 核对见本机 final-verification.json |
| 工作区 | `icacls D:\acrylic-pda-outbound` 退出0；无显式 CodexSandboxUsers F；当前用户有F，另有 capability SID 项 | 无显式组项不等于实际令牌无权；未递归授权 |
| Android 用户目录 | `icacls %USERPROFILE%\.android` 退出0；CodexSandboxUsers 为 `(OI)(CI)(RX)`，用户/Administrators/SYSTEM 为F | RX 差异已记录，未改成F |
| 源 AVD / SDK | 源 AVD 无显式 SandboxUsers F；SDK emulator 用户/Administrators/SYSTEM 有F | 只读，不改源 AVD 或 SDK |
| Windows 令牌 | 中完整性、Administrators SID deny-only、管理员令牌检测false | 未进行 UAC/RunAs，不伪造提升权限 |

**授予 Full Control：未执行。** 拒绝在 shell 创建前，未取得 Windows 文件访问拒绝回执；实际文件系统沙箱已禁用。没有证据证明给沙箱账户加F能修复该决策，不能为突破同一拒绝扩大权限。“未执行”不等于“修复失败”。原 `Everyone:(CI)(DENY)(DC)` 保持；未重置ACL、接管所有权或删除。

## 2. unelevated 与重启

**修改 `[windows] sandbox="unelevated"`、重启 Codex、修改后启动：均未执行。**

实际读取的官方 Windows 文档将 unelevated 定义为组织策略允许时的遗留 fallback，使用受限 token/ACL；网络隔离较弱，不支持 denied read paths。它不是所有 `blocked by policy` 的通用修复。本次实际沙箱已禁用，也未证明切换实现会影响拒绝层，不能为突破同一拒绝降低隔离。

用户配置保持原字节，无配置回滚需要；没有重启或中断另一开发聊天。不把“未切换、未重试”写成“切换后仍拒绝”。

## 3. Issue 与替代环境

GitHub API 三项请求均 HTTP403 rate-limit，失败回执保留；随后三个 Issue 网页均 HTTP200，原文和哈希保存在本机。

| 引用 | 原文限定 | 对本机的含义 |
| --- | --- | --- |
| openai/codex #48669 | ACL 是报告者判断；缺旧ACL快照、变更时间及受控前后验证，不能建立因果 | 相似症状，不证明本机ACL是根因 |
| openai/codex #50979 | 创建前拒绝、本地规则无匹配；对Auto-review确定归因已撤回，决策路径未确认 | 不能断言一定是Windows native sandbox或特定Guardian规则 |
| openai/codex #44839 | 本地规则检查不覆盖全部运行时策略，也不构成再次执行许可 | 没有可直接用于本机放行的通用修复 |

- `wsl --list --verbose` 退出0：已有 Ubuntu 与 docker-desktop，均 Stopped、WSL2。未启动发行版、安装工具或迁移演练。
- `adb devices -l` 退出0：已有 M71 为 device。序列号仅保存在本机；未打开应用、安装APK、清数据、读取订单或连接test。
- M71枚举成功不代表真实身份、应用或业务流程已验证。WSL2/M71仅盘点，不能作为突破现有执行时拒绝的替代执行器。

**判定：执行时拒绝仍未解除，具体规则/根因未定位。** 用户设定的修改后复验条件未完成，不能归因为“Windows原生层已证实无法修复”。需要受支持的策略管理途径提供具体决策及相应处理路径，再按实际启动标准验证；不能以迁移环境代替放行。

## 证据与断点

起始主树 `D:/acrylic-pda-outbound`、`codex/development`、HEAD `3aa0a78ac812564263f1c62ffc1a9e6070fb9d8d`。产品版本事实见看板生成事实块。部署固定候选仍 `8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd`；未改/重建候选，不拼主开发分支通过。

证据根 `.local/codex-windows-policy-followup-20261020/`；配置原文、身份/SID、设备序列号及本机原件不提交。Git仅保存本报告与共享文档的脱敏增量。

- `start.json`、`effective-turn-context.json`：SHA/分支/远端、全部脏路径、空暂存、会话权限和配置摘要。
- `*-icacls.json`、`acl-sddl.json`、`identity.json`、`current-token.json`：ACL和非管理员令牌原回执。
- `issue-verification.json`：API403；`issue-web-verification.json`、`issue-*.html/.txt`：网页原文/哈希；`official-windows.html/.txt`、`official-doc-receipt.json`：实际读取的官方页面。
- `wsl-enumeration.*`、`adb-enumeration.*`：时间、命令、退出码、stdout/stderr原字节；WSL按UTF-16LE解码。
- `inherited-files-before.json`、`shared-docs-before.zip`：其他聊天WIP哈希及共享文档写前原件；`final-verification.json`：配置/规则/ACL守恒、继承文件检查、进程/监听/C-D余量。
- `diagnosis-result.json`、`evidence-manifest.json`：三步真实状态和证据哈希。旧拒绝沿 `.local/codex-emulator-permissions-20261020/authorized-recheck-refusal.json`，不复制成新失败。

下一步需要执行时策略层可暴露的决策详情和受支持的处理机制；本轮未取得规则ID或例外入口，管理员ACL权限不等于该层管理权限。

[test维护报告](DEPLOY_TEST_BUSINESS_REHEARSAL_2026-10-20.md)恢复结论保持历史范围；本轮未重新核验test，未重开维护/导出，也未把维护前备份当演练输入。真实业务演练及固定候选的真实升级/还原/API-worker链继续阻断。以后重开窗口仍须重核消费者/PDA03快照并新备份，不用旧截点覆盖新事实。

只产生小型证据和文档，无产物/数据库清理，释放0字节。另一开发聊天持有的源码/测试/规则WIP不代提交；看板/索引/HEAD按明确交接短写窗处理。
