# D13 孤儿 owner 生命周期测试修复（2026-10-20）

输入 HEAD `dec98b2d260ba26eb5c0115b1cd4469e60debe6d`，叠加本轮四条测试/构建 WIP。运行结束后主树因另一聊天的三份预检文档提交前进至 `64eaf4919d10c03d2f611c72cfc51ff0a314f209`；业务输入未因此变化。证据根 `.local/goal-20261020-resume/d13-orphan-lifecycle-test01/`，原测试、生产输入和构建字节见 `input-manifest.json`、`edit-inputs.zip`。

## 实际失败与根因

[独立部署预检](DEPLOY_PREFLIGHT_2026-10-20.md)的三处租约断言在主树原测试中复现：`RecoveryLeasePublicationTest` 7 项/2 失败，`RecoveryLeaseVisibilityTest` 4 项/1 失败，共 **11/3/0/0**、Gradle exit 1，见 `gradle-red01.log/.exit`、`xml-red01/` 和 `test-result-red01.json`。

旧输入执行 `registerOperation(); retire();` 后即假定 owner 死亡。但生产 `RecoveryFenceAuthority.retire()` 在存在活动 operation 时只请求延迟退休，并继续持有生命周期锁，直到最后 operation 退休；此时它仍是活 owner。旧测试把正常排空请求当成进程退出，与已有生命周期规则冲突。未据此修改产品、删除孤儿拒绝或放宽围栏。

## 修复与逐项自审

- 新 `RecoveryOrphanOwnerProcess` 在真实独立子 JVM 中读取 `FenceTestSupply` 合成配置，实际调用产品 authority、验证启动并发布租约，保存 operation UUID 后正常退出；刻意不调用 operation 退休，操作系统在进程退出时释放 owner 锁。没有 mock 产品 authority 或通过篡改时间模拟死亡。
- 父测试等待自己创建的子 JVM，限制等待时间，超时只终止该子进程；命令、stdout/stderr 和 pid/exit 回执写在该项合成 JUnit 临时树。该树由测试框架管理，原始运行结果以本轮 XML/日志封存。收口另核无本轮 Java/Gradle/owner 残留，不凭旧 PID 推定。
- 仅替换 publication 的两个孤儿输入和 visibility 的一个死亡输入。保留孤儿首次扫描拒绝、record/index 清理、process 原证据保留、同 JVM 后续启动仍拒绝、只读计数不改变目录及记录等全部原断言；未删除任何测试。
- `server/build.gradle` 使普通 `test` 依赖既有 `recoveryControlClasspath` 任务，子 JVM 使用真实测试运行时 classpath；没有新增运行服务、数据库或发布任务。
- 同跑原 `RecoveryLeaseRetirementTest` 8 项与 `RecoveryEffectGateTest` 2 项，包含最后 operation 才允许生命周期退休、退休失败围栏和效果门。生产 authority/process/scan 三文件逐 SHA 不变；31 条非看板继承改动逐 SHA 保持。

## 实际命令与结果

```text
gradlew.bat -p server test --tests com.acrylic.outbound.recovery.RecoveryLeasePublicationTest --tests com.acrylic.outbound.recovery.RecoveryLeaseVisibilityTest --tests com.acrylic.outbound.recovery.RecoveryLeaseRetirementTest --tests com.acrylic.outbound.recovery.RecoveryEffectGateTest --offline --no-daemon --console=plain -Pr08FontFile=D:/acrylic-pda-outbound/report-rendering/build/generated/resources/NotoSansCJKsc-Regular.otf
```

TEMP/TMP 与 Java 临时目录只在本轮子进程指向证据根 D 盘 `tmp`；使用既有固定字体，不跳过字体任务。`gradle-final02.log/.exit` 记录 exit 0；本轮 XML 已在后续构建前原字节复制为 `xml-final02/`，计数/哈希见 `test-result-final02.json`：

| 类 | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: |
| RecoveryLeasePublicationTest | 7 | 0 | 0 | 0 |
| RecoveryLeaseVisibilityTest | 4 | 0 | 0 | 0 |
| RecoveryLeaseRetirementTest | 8 | 0 | 0 | 0 |
| RecoveryEffectGateTest | 2 | 0 | 0 | 0 |
| 合计 | 21 | 0 | 0 | 0 |

这是作者与自审的主机文件树/子 JVM 定向验证，仅修复错误死亡输入。没有真实数据库、HTTP、恢复业务/设备全链或最终候选完整回归；不计 D13、D18、第 8 节整体完成。与 [D04 审计测试修复](GOAL_D04_AUDIT_STREAMING_TEST_REPAIR_2026-10-20.md) 合看，仅解释原预检四处断言，不能把定向通过改写成原完整回归通过。

## 保全、开发备份与接续

本次明确提交四条测试/构建路径、本报告及两个索引，均标“开发备份”。另一聊天预检提交及隔离整改按原归属保留，不代整合其代码。收取证据前后 WIP 原字节、全清单和环境保存在 `resume-*`；收口命令、输出哈希、Git/远端和环境见 `closeout.md`、`final-manifest.json`。未形成可清理隔离库或大产物，本轮清理释放 0 字节；历史清理不重复计增。

下一步须在实际冻结候选上重新执行可解释的服务端完整回归及对应组合；独立预检隔离树正在处理包工具链，避免同机重型构建冲突，不将其未提交改动并入主树。O02 CANTOPEN 无新假设不重跑，原身份/可信事实/现场和各有界未验仍沿看板保留。

总目标 active、未完成。生产决定、正式签名、原库/现网、main、部署和 Release 持续关闭，`localDeletionAllowed=false`。不恢复换机承接、多跳、接收者确认/激活链，不启动旧 run03；原资料、身份/Keystore、回执签名、唯一证据、最终 APK 和继承源码保持。
