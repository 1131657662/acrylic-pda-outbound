# E07/D06-D07 Android、真实 HTTP、Chrome 与 Python 消费候选（run1606）

日期：2026-10-18。运行树为 `D:/acrylic-pda-outbound`，运行输入源码 HEAD 为
`93710ec5cff979a567654084c80abe089e9a73b7`，分支为 `codex/development`。本报告登记
run1604/1605 的失败原件、run1606 的新隔离候选，以及当前不能把合成输入提升为 D06/D07
L2 的原因。

## 范围与边界

本批验证的是 R07 故障订单撤回后由新设备独立作业的开发候选：真实隔离 MySQL、Spring
HTTP、Chrome、Android instrumentation 和只读 Python 消费者。输入中的人员、设备、仓库、
订单、签名和浏览器会话均为合成隔离身份；没有连接原库或现网，没有操作 USB PDA
`22D2910042`，没有使用正式签名，也没有恢复已取消的换机进度承接、多跳或接收者确认/激活链。

## 运行结果

| 运行 | 结果 | 实际事实 | 原件 |
| --- | --- | --- | --- |
| 1604 | 失败，runner 阶段 | fixture、Python 消费、服务端/Android 构建均完成；向模拟器推送输入时 PowerShell 将 `adb push` 的正常 stderr 当作异常，未进入 Android 方法 | `.local/goal-20261011/fault-restart-consumer-1604/`，输入 SHA-256 `EF6722853B8E4FAF44C33709AD334C1F8B15B0889EE94E0ABC7458085603986F` |
| 1605 | 失败，Android 首条方法 | fixture 与 Python 消费完成；首条 instrumentation 因应用绑定状态残留断言失败，失败点为 `FaultRestartDeviceTest.kt:28` | `.local/goal-20261011/fault-restart-consumer-1605/`，输入 SHA-256 `E5391A44D5BB601112C489B42DF7CC5B01FF7895C5BB019B5460B88CCCA3F966` |
| 1606 | 通过，隔离候选 | `FaultRestartConsumerFixtureIT` 为 `1/0/0/0`；四条 Android 方法均为 `OK (1 test)`，包含实际 `am force-stop` 后恢复、撤回消费和新进程恢复；Chrome 为 `expected=2 skipped=0 unexpected=0`；Python 只读消费者退出 0 | `.local/goal-20261011/fault-restart-consumer-1606/` |

run1606 的输入 SHA-256 为 `0331991E9173DF2F81B43B2190B5DDF2C9408E472F9A57645A0AB802C90E5726`，
Python 摘要确认 3 个消费者、2 个扫描项且输入读取前后未变化。Android 结果分别为：新设备结果
`SYNCED`、强停后新进程仍为 `SYNCED`、撤回结果 `WITHDRAWN/RECALLED`、新进程重放仍保持
撤回；应用结果保留 `localDeletionAllowed=false`。Chrome 记录两次刷新、撤回和重新发布命令。

构建产物仅作隔离开发证据：Debug APK SHA-256 为
`5197F24B2FF9C29DFC66286DC48EF805B7A101057A509DF6E894D8C22F2072F7`，AndroidTest APK
SHA-256 为 `4761A09233237D7AEBB98EA0346392DFA9CBCE383BC31DA7589F8F334BF7BD92`。最终隔离
数据库 dump 为 721892 字节，SHA-256 为
`E2D6C479396D6FD647790534B1FF423448BFCF8E344922EE48D7594A1589C244`；MySQL、HTTP、Vite
和模拟器均已认证停机，运行端口已释放。

## 判定

run1606 修复并证明了运行器 stderr 处理、应用数据清理和隔离组合的可执行性，但不满足
D06/D07 L2：输入、身份、签名、设备和浏览器均为合成隔离事实，仍缺原 PDA/原身份、现场回执、
原模拟器恢复及全范围组合。run1604、1605 的失败计数和原始日志继续保留；本报告不计 D06、D07、
D17、E06 L2、D18 L3 或总目标完成。

## 改动与验证索引

- 运行器修复：`tools/e07-d06-d07-emulator-run.ps1`；所有 adb 调用记录退出码，正常 stderr 不再
  触发 PowerShell `Stop`，模拟器使用隔离 D 盘 `-datadir`，安装后清理应用数据。
- 定向静态验证：PowerShell 语法、Python 语法和 `git diff --check` 通过。
- 直接证据：各运行目录的 `result.json`、`error*.txt`、Android instrumentation 日志、Chrome
  JSON、Python 摘要、输入、APK、数据库 dump、认证停机日志和前后环境快照。
