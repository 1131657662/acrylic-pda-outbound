# U04 保留明细四字号 tar 只读审计（2026-10-19）

本次在 `D:/acrylic-pda-outbound`、`codex/development`、输入 HEAD `622d38bdb018af8f4fdb3540a2e718476a08217d` 执行。四组保留合成资料的 Room 主库、WAL、SHM 内容相同，完整逻辑表也未变化；整份 tar 确实变化，原 `archiveUnchanged=false` 保留。本切片只补 retained-details 四字号只读页面的有界 L1，不计 U04/D17 整体、E06 L2、D18 L3、第8节或总目标完成。

## 接管与边界

- 新启动 OS 的实际进程、监听、服务、Git、全部 219 项脏文件哈希及写权保存在 `.local/goal-20261019-resume/restart-readonly-audit01/takeover.json`；当前聊天 `01a1520d-c412-7402-b77d-311a043b3d1e` 是唯一活动项目写者，未恢复旧聊天/旧 PID。
- 确认写权后顺序执行 `tools/version_facts.ps1 -Write` / `-Check`，均退出 0；原回执见同根 `facts-initial-results.json`。版本事实只见看板生成块。系统 MySQL80（父 4288 / 子 5188，3306/33060）只观察未连接；`auto.cnf` 读权限不足，数据库 UUID 未确认。
- 无 Java/Gradle、项目 MySQL、ADB server、模拟器或 Vite 在途；5040 属于 Windows svchost，不能当 ADB。USB PnP 只证明 Android/ADB 接口枚举，不证明设备会话、原身份或 Keystore 可用。本次未调用 ADB、未启动设备；正式 Keystore 只读哈希，未使用。
- 标准是固定四对 tar 的输入 SHA、逐成员内容/元数据差异、Room/WAL 逻辑消费、命令及输出哈希。不得重跑旧用例或改变原产物。

## 输入、命令与 SHA

原输入根为 `.local/goal-20261019-resume/u04-retained-details-run02/private/`，审计根为 `.local/goal-20261019-resume/restart-readonly-audit01/`。各 tar SHA（after 与下一档 before 原字节衔接）如下：

| 字号 | before SHA-256 | after SHA-256 |
| --- | --- | --- |
| 100% | `cdb8f4af1b6be700a78b79e89b21c332e9389d1d1010b1b57b61ba88c83675f4` | `a04495d8d4ca5d9f357b3c7bb31c47d8b7a775a7130fe4f03b281062cdddaae0` |
| 130% | `a04495d8d4ca5d9f357b3c7bb31c47d8b7a775a7130fe4f03b281062cdddaae0` | `b4471bd6265f5986b0c3b867c386919b4a328b7008c600687ddccdcd21a5b43f` |
| 150% | `b4471bd6265f5986b0c3b867c386919b4a328b7008c600687ddccdcd21a5b43f` | `aaf11db197d5435fa5c6729883f35d4b79efd6c6b5ef00f90eeb866b023ac7c7` |
| 200% | `aaf11db197d5435fa5c6729883f35d4b79efd6c6b5ef00f90eeb866b023ac7c7` | `d7295046b05365211db7a9e069d9a15c8f51eed59fce157460b69269240f089b` |

实际执行：

```powershell
C:\Python314\python.exe -B .local/goal-20261019-resume/restart-readonly-audit01/audit_retained.py .local/goal-20261019-resume/restart-readonly-audit01/compare01
C:\Python314\python.exe -B .local/goal-20261019-resume/restart-readonly-audit01/runtime_details.py
```

两条均退出 0。第一条用 `tarfile` 逐成员读取，把固定 DB/WAL/SHM 仅复制到新 scratch；SQLite 使用 `mode=ro`、`query_only=ON`、`trusted_schema=OFF`，保留 WAL 读取而不使用 `immutable`，逐表比较完整 schema、类型化行多重集，并执行完整性/外键检查。第二条用 Windows `tar -xOf` 独立重算 24 个 Room 成员并记录 WorkManager 逐列差异。八份 tar 的输入前后 SHA 在 `runtime-details01/inputs-before.json` / `inputs-after.json` 重核一致；原 APK、tar、日志和唯一证据未写入或删除。

原 run02 结果均为 `passed=true / exit=0 / archiveUnchanged=false`，instrumentation 日志均含 `OK (1 test)`；本次没有重跑它们。Debug APK SHA 为 `86e513aa57eb98319b06d783b6f1fb2768d3ab43e47d39deaef6e3efe00d0693`，test APK SHA 为 `873d6e3289309e5c2b4c4712fb23f1d7deb32336aaa3f2ea95c28adc68a9fd4d`。

## 逐成员结果

完整逐成员清单在 `compare01/{1-00,1-30,1-50,2-00}-members.json`，包含路径、类别、状态、大小、内容 SHA、tar 元数据及变化字段。没有删除成员；既有成员相对顺序未变。

| 字号 | 成员 before→after | Room 内容 | 应用资料 | instrumentation 新增 | runtime 内容 |
| --- | --- | --- | --- | --- | --- |
| 100% | 227→243 | 0；SHM 仅 mtime | 153 不变，2 目录仅 mtime | 16 | profileInstalled；WorkManager WAL/SHM |
| 130% | 243→259 | 0；SHM 仅 mtime | 154 不变，1 目录仅 mtime | 16 | WorkManager WAL/SHM |
| 150% | 259→275 | 0；SHM 仅 mtime | 153 不变，2 目录仅 mtime | 16 | profileInstalled；WorkManager WAL/SHM |
| 200% | 275→291 | 0；SHM 仅 mtime | 154 不变，1 目录仅 mtime | 16 | WorkManager DB/WAL/SHM |

Room 三成员在八份 tar 中始终相同：主库 1,175,552 bytes / SHA `89ed42df6b12a12009c014d81c49d6d16e2fed7fe23800b6c3b130c58e0677dc`，WAL 524,288 bytes / SHA `e3cea766bbbef6527050382fe7d688848b3343e429df425abd9a4a1bc0d0fe77`，SHM 32,768 bytes / SHA `e4e4a5c120629f73d4c7b930b08bab3926094712556adf827eac157999be1a8c`。每档 83 张表 / 257 行、schema、逐表行多重集均不变，integrity 为 `ok`、foreign-key 结果为空。冻结 submission、27 条 VOID 和会话相关业务资料均在完整比较范围内。

应用资料的变化仅为 `files` / `files/retained-details` 目录 mtime；`shared_prefs/background-sync.xml` 不变。每档新增 `files/retained-details/u04r2-<scale>/` 目录及 15 个 retained-details 输出文件；两个 database JSON 原字节相同，SHA `1966cf7082221d5318c5806fb3d62bac210c8b451469da5e74031fd3440594bf`。`files/profileInstalled` 为 24 bytes，仅 100%/150%内容和 mtime 改变。

WorkManager 四档 schema、行集合身份和行数不变；每档只改一条 `Preference.long_value` 与一条 `WorkSpec.schedule_requested_at`，其他表/列不变，integrity 均 `ok`、foreign-key 为空。逐列值和行键摘要在 `runtime-details01/result.json`；没有把运行时调度元数据误报成 Room 业务变更。

## 结论、限制与后续

独立 tar/SQLite 消费支持“Room 物理与逻辑守恒、tar 差异由测试输出和运行时元数据构成”的有界结论。它不证明原身份/Keystore、可信时效、外置保管、真实 HTTP/MySQL、真机硬件或 U04/D17 全部标准；`originalIdentityOrCustodyProven=false` 保持。没有产品源码改动，`localDeletionAllowed=false`、取消的换机/确认/激活链、旧 run03、原库/现网、正式签名、部署/Release/main 边界均保持。

核心输出 SHA-256：`compare01/result.json` = `fa5028bec2b07bf9aa7cb8d8953f7967c6f197005ffb7147288c8c6db66f68fa`；`compare01/outputs-manifest.json` = `dca3da57591d8ad2a874cd156eba448cb9bad7b1d72826140614e1d0540d9554`；`runtime-details01/result.json` = `0635e83e72a8141179fffef64548ee0cc78071613c9d463cc2d6e4734b4562ff`。完整哈希与收口状态见同根 `final-manifest.json`、`execution-observation.json`、`closeout.json`。

下一步从有效 U/O 或 D 剩余项选择独立缺口，绑定直接产品入口与影响层后推进；不重复本已验 retained-details 用例，也不因本审计宣布总目标完成。
