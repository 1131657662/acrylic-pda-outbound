# D13 连接终结 unchecked 失败：有界开发验证

本片沿已批准的 D13 最小设计补实际实现缺口。总目标保持 active、未完成；这里只证明本地 writer/root 协议与 JDBC MODEL 的关闭行为，不计 D13 整体、D18 或第8节通过。版本事实见看板事实块。

## 输入和写权

- 固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入 HEAD `ec81072be647cc059207144fabd97d631612b2b9`。fresh `ls-remote` 的开发分支为 `f3d6b2d1ae0d98c269ed7bde2a58fa51d9dbbba4`，main 为 `c6e9e0c238c657baecbf699ba6fdbada5d979b86`。
- 初始27项继承 dirty、handler 原字节及 SHA 保存在 `.local/d13-connection-completion-20261023/run01/baseline.json` 和 `originals/`；本轮接续含新增测试共28项，原件、diff、SHA、环境见同根 `verify01/resume-inventory.json`、`originals/`、`tracked.patch`。
- 本聊天只写 handler、新反例测试、此独立报告及本片 `.local` 证据。另一活动聊天拥有 Android、测试部署/真机及共享看板/相关索引；本片未修改共享看板、暂存、提交或推送，未代操作其他聊天。

## 问题、改动和自审

`RecoveryConnectionHandler.close()` 原本仅捕获 SQLException。驱动 rollback/close 抛 RuntimeException 或 Error 时，可能跳过共享 writer/effect 毒化；rollback unchecked 失败还会跳过物理 close。同一 SQLException 对象同时作为 rollback 和 close 失败时，原 combine 会触发 self-suppression 的 IllegalArgumentException，遮蔽原始失败。

仅修改 `server/src/main/java/com/acrylic/outbound/recovery/RecoveryConnectionHandler.java` 的 close/combine：

1. rollback、close 的 SQLException/RuntimeException/Error 均毒化已有 writer/effect gate。
2. rollback 失败后继续尝试物理 close；仍按已有协议保留未知 writer，root 不能成功退役或再借连接。
3. 保留首个异常对象，不包裹成不同身份；不同的第二个异常放入 suppressed，同一对象不自抑制。
4. 正常关闭、同步 monitor、已中毒连接的清理及子操作结束规则沿用原逻辑。未新增准入、效果执行或释放资格。

新增 `server/src/test/java/com/acrylic/outbound/recovery/RecoveryConnectionUncheckedCloseTest.java`，三类错误分别覆盖 close、rollback、双失败及同一异常对象；另验正常连续借用。实际本地控制目录、文件锁、writer/root 实现参与执行；DataSource/Connection/EffectTestAuthority 是明确的 MODEL 边界。

作者与自审为同一助手。自审核对共享 writer 在未知终结后不能借新连接、物理 close 调用、原异常身份/suppressed、重复 close 的终态、正常借用、原并发 monitor 测试及无生产配置/迁移改动。继承大文件不为行数阈值扩大重构。`effects.poison()` 的真实 P5 halt/通知异常不是此次 MODEL 故障覆盖，不能将本片外推到实际停机成功。

## 当次执行

Android Gradle 在途时没有启动第二个 Gradle。使用现有 JDK 的直接 javac/JUnit Platform 运行器，所有输出、TEMP/TMP 位于本片 D 盘目录；javac 和测试 JVM 各限制 256 MiB。没有执行 Gradle、服务器启动、数据库连接、旧 IT 或 run03。

运行器与命令在 `.local/d13-connection-completion-20261023/run01/verify01/`：

```powershell
& ./.local/d13-connection-completion-20261023/run01/verify01/run.ps1 -Phase red
& ./.local/d13-connection-completion-20261023/run01/verify01/run.ps1 -Phase green
& ./.local/d13-connection-completion-20261023/run01/verify01/run.ps1 -Phase regression
```

上述三轮均已执行，各目录拒绝覆盖。每轮编译前冻结源码原字节及2394项输入 SHA，classpath 仅含110个已存在依赖 JAR，不消费 `server/build` 的已编译业务类。命令/参数、选定测试类、环境、工具哈希、依赖 SHA、编译/测试原日志及输出 SHA 在各轮 `commands.json`、`source-inputs.json`、`dependencies.json`、`result.json`；没有生成或借用旧 XML。

| 轮次 | 编译退出码 | 测试退出码 | 当次结果 |
| --- | --- | --- | --- |
| red，原 handler | 0 | 1 | 13项，4通过、9失败、0跳过/中止；确认反例可区分旧实现 |
| green，修复后 | 0 | 0 | 13项全部通过，0失败/跳过/中止 |
| regression，同一修复 | 0 | 0 | 9类73项全部通过，0失败/跳过/中止 |

red/green 的2394项输入仅 handler SHA 不同；依赖 manifest 相同，新测试原字节相同。三轮编译运行前后均无 source/dependency SHA 漂移。handler 原 SHA256 `80A340318E9B9FAF1372DB06858F1EB03531E2507083F21EFA0DCCBFBF53A2F8`，修复后 `354EFF35C924DD9D3C4A48773E533240A9B91DFBD978797F3E48A35E36375D7D`；新测试 SHA256 `675D8DEA0A549726EFABFBBA9BFC9FAD38FC90DF70C60052DF23EA530B36591F`。

回归按实际执行统计：RecoveryAdmittedDataSourceTest 17、RecoveryClosedConnectionAdmissionTest 3、RecoveryConnectionUncheckedCloseTest 13、RecoveryEffectConnectionTest 7、RecoveryLeasedTransactionTest 5、RecoveryOperationRetirementTest 6、RecoveryRequestAdmittedDataSourceGateTest 5、RecoveryRequestAdmittedDataSourceTest 11、RecoveryWriterLeaseTest 6。包含原并发 delegate/statement 与 close 的 monitor、事务回滚、效果门、root 保留/退役及缺失准入拒绝检查。

## 未验、断点和边界

- 未运行完整普通回归、Gradle 构建、真实 MySQL/HTTP/P5、Android/APK/模拟器/真机或部署；本地 JDBC MODEL 不能替代这些证据。也不是独立人员审查。
- fresh 观察系统 MySQL 监听3306/33060，内部身份未认证，本片没有连接或停库。ADB 只枚举设备，Debug Keystore 只核 SHA、未签名；环境/监听/磁盘/活动写者的最终快照在 `verify01/closeout/`。
- 继承代码、取消链、原资料/身份/Keystore、原回执/签名、失败日志和最终 APK 保留。共享看板及真机报告在本片执行期间被其实际写者更新，差异逐路径记录，不归作本片改动。
- 曾申请删除本片已结束 JVM 的可重建 class、TEMP 与合成控制夹具，但 exec_command 在创建进程前被自动审批拒绝，理由只有 `blocked by policy`。未删除、释放0字节；未换工具/包装或通道重试。保留路径/文件哈希、候选字节及磁盘采样见 `verify01/cleanup-status.json`。冻结源码、原始失败日志/结果、全部输入/命令/依赖及输出哈希均保留。
- 共享文件在另一聊天写权内，因此看板生成块和进度/历史索引的同步留待取得独占写权；接续先读本报告及 `verify01/closeout/`，核实际写者/进程/dirty，再定向链接本报告并运行 version_facts 的 Write/Check，不能覆盖另一聊天状态。
- 后续继续实际 Spring/HTTP/P5 入口与关闭未知结果的缺口映射和有界组合验证；已有只读入口矩阵、本片及早先数据库证据各按原范围复用，不重跑旧库/旧 IT。D13–D15整体、D16引用保护、D18最终候选和第8节仍未满足。

生产决定/归档开关、正式签名、原库/现网、main、生产部署和 Release 关闭，`localDeletionAllowed=false`。不恢复换机承接、多跳或接收者确认/激活链。开发修复及本片局部通过不改变总目标完成口径。
