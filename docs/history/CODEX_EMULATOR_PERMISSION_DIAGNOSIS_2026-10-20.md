# 本机 Codex 模拟器权限诊断

日期：2026-10-20。授权范围：检查本机 Codex 配置、execpolicy、Windows ACL，按最小权限原则修复并实际验证模拟器启动。仅本机，不重开 test 维护，不连接数据库或 test/生产，不计 D/O/U 或业务验收进度；`localDeletionAllowed=false`。

## 结论

**诊断完成，修复及启动验证未通过，阻断保留。** 用户新授权后，采用原失败的同一 PowerShell/Start-Process 启动机制复验一次，仍在宿主进程创建前返回 `rejected: blocked by policy`。没有创建 AVD，没有启动 emulator/qemu，没有执行应用操作。不能把可执行文件版本查询、虚拟化能力检查或写目录成功当作启动通过。

未调整全局或项目策略、未增加未证实有效的白名单、未改 ACL，也未关闭自动审查、另启 Codex、改用其他执行器或包装来尝试启动。未改产品代码、未部署。

## 诊断证据与判断

原拒绝：2026-10-20 17:22:21 北京时间；本轮复验：17:45:46 北京时间。原始记录及本轮记录、命令文本和 SHA 均保存在本机证据根，原维护报告保持历史结论。

| 检查层 | 实际观察 | 判断与限制 |
| --- | --- | --- |
| 会话实际权限 | 原失败时和本轮 `turn_context` 都为 `approval_policy=never`、`sandbox_policy=danger-full-access`；持久线程记录为 never / disabled | 这是任务开始前已有状态，不是本轮放宽。再写相同设置无修复依据 |
| 用户配置 | 用户配置无显式 approval_policy/sandbox_mode；`approvals_reviewer=user`、`windows.sandbox=elevated`；项目 trusted | 文件设置不能替代实际会话权限。没有把 elevated 字段误判为本轮运行了 Windows 沙箱 |
| 本地 execpolicy | 用户 default.rules 为 34 条 allow，0 条 prompt/forbidden；原完整 shell 命令及 emulator 直接参数检查都退出 0，`matchedRules=[]` | 未找到能解释拒绝的本地规则；无匹配也不代表自动审查已批准。不声称已定位某条禁止规则 |
| 已知配置位置 | 检查用户/项目及两个常见 ProgramData 路径，未发现对应 requirements/managed_config；本地无 auto_review 配置 | 只证明已检查路径未发现，不证明所有管理策略不存在 |
| Windows 身份与 ACL | 当前任务用户对项目和任务目录可写；保留写探针成功。SDK emulator 的版本和加速检查退出 0，WHPX 可用；读取并保全目录 ACL/SDDL | 原拒绝发生在 shell 创建前，没有 Windows Access Denied 证据；无依据扩大 CodexSandboxUsers 或目录权限。原 C 盘 AVD 的 SandboxUsers 只读状态保留 |
| 执行时审查 | 运行日志可见 guardian_approval 特性启用；工具路由层记录 CreateProcess Rejected。与本次拒绝对应的 guardian_review_feedback 无记录 | 已定位到执行请求的策略拒绝边界，尚不能证明具体由哪个 Guardian/托管/代理规则决定。返回内容没有规则 ID、详细理由或可调用的例外入口 |
| 实际启动 | 新授权后复验仍在 0 秒返回 blocked by policy；AVD/启动回执不存在，无 emulator/qemu 进程及对应端口 | 启动未通过，设备在线及应用最小操作未执行；不恢复真实业务演练 |

复验使用同一 shell 和启动命令结构，并非改包装。逐字比对发现路径归一化 `Replace` 字符串多一个反斜杠，已在 `command-comparison.json` 如实保存；因此不标为字节完全相同。两次均在命令执行前拒绝，未到该字符串处理步骤。

## 最小改动决定与后续条件

1. **配置改动为零**：实际会话沙箱已经禁用，本地规则无拒绝匹配，用户 reviewer 已为 user；没有可用证据支持扩大 approval、sandbox、writable_roots 或 ACL。
2. **未盲加白名单**：官方规则文档说明 allow 作用于匹配命令的审批路径，多个匹配规则以最严格决定为准；新规则通常需重启加载。此处没有证明本地白名单能改变返回拒绝的审查层，不能用禁用审查或宽泛 shell allow 代替排障。
3. **所需权限/信息**：需要此次执行请求的决策详情，以及执行时策略管理层提供的受支持、仅覆盖该本机模拟器命令的放行或例外机制。现有工具未暴露这两者；本机管理员/文件写权限不等于拥有该层管理权限，不假定用户能直接修改。
4. **恢复方法**：本轮没有修改任何 Codex 策略或 ACL，无需回滚配置；任务写探针和诊断证据保留。没有重启 Codex，不影响另一开发聊天。
5. 获得上述新依据后才重新验证：实际 emulator 启动、ADB online、完成一次应用最小操作；全部满足才可记客户端启动条件通过。业务演练、可信数据来源、同候选升级/还原/API-worker 闸门仍单独执行，不能由权限诊断替代。

## 证据与保全

本机证据根：`.local/codex-emulator-permissions-20261020/`；仅可脱敏结论进入 Git，用户配置原文、身份/设备枚举和会话日志不提交。

- `start.json`、`runtime-context-history.json`：授权边界、开始 SHA、配置哈希及历史实际权限。
- `exact-emulator-refusal.json`、`original-emulator-command.json`、`authorized-recheck-refusal.json`、`command-comparison.json`：原拒绝与复验拒绝、原命令及非完全同字节说明。
- `original-execpolicy-baseline.json`、`execpolicy-baseline.json`：本地规则检查真实 stdout/stderr/退出码。
- `rejection-log-window.json`、`recheck-policy-log.json`：本机日志窗口及路由拒绝；未臆造具体策略理由。
- `acl-*.stdout/stderr/receipt.json`、`acl-sddl.json`、`identity.*`、`write-probe.txt`：身份、目录 ACL 与写探针。
- `emulator-version.*`、`emulator-accel.*`、`adb-devices.*`：仅工具与枚举前置，不计启动成功。
- `diagnosis-result.json`、`before-document-snapshot.json`：阻断结论及其他会话改动保护基准。诊断脚本的解析失败另存原始错误，不隐去。
- `official-rules.*`、`official-config-advanced.*`、`official-config-reference.*`：本轮读取的官方文档缓存；来源为 `developers.openai.com/codex/rules`、`/codex/config-advanced/`、`/codex/config-reference/`。

其他会话的产品源码、测试、未提交文件与 Git 暂存不代修改/提交。全部旧备份、隔离还原库和失败证据保持；没有数据删除、test 维护或业务操作。本项只作本机执行环境诊断收口，不作功能、数据或部署放行。
