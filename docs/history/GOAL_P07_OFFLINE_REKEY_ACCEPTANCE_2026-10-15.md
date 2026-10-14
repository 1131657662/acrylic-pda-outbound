# P07 外部秘密供应与停机重锚 L1（2026-10-15）

本切片完成可隔离的外部秘密供应器、产品 CLI 和独立回读入口，修复成功/失败时删除唯一旧签记录副本的问题。仅计工具产品入口 L1；不是 D13 全部、E07 L2、D18 L3 或总目标完成。真实停机/外部介质/生产运行秘密仍另验。

## 输入与可验证标准

基线为 `codex/development` 的 `c0c728366f074a3a2197170744e422e7bafd8084`，实际远端同 SHA。开发输入为该基线加本页列出的 P07 路径，源码 SHA-256/长度绑定在 `.local/goal-20261015/p07-ops01/source-input.json`、`p07-ops02/source-input.json`、`p07-ops03/source-input.json`；不是只凭 HEAD 验收。提交前 index/tree 及提交后远端回执保存到 `p07-ops03/`。

退出标准是实际 Python→产品 JVM→独立 Python 消费链成功、新当前钥可启动 P5 权威、原记录守恒；活动写者/非空租约、旧请求/异身份、错钥/历史钥不可取回、并发、末替换失败和输出目录覆盖拒绝有原件证据。沿总目标 P07 采用显式供应、不自动发现/修复/启停的默认，理由和操作契约见[运维入口](../RECOVERY_REKEY_RUNBOOK.md)。

| 改动路径 | 影响层 → 必跑清单 | 复用依据 |
| --- | --- | --- |
| `server/.../recovery/RecoveryLeaseRekey.java` | 离线重签/逐文件替换/回滚/保留 → 原 4 项＋新 CLI 正反例 | 旧报告 `GOAL_E07_P07_REKEY_ACCEPTANCE_2026-10-14.md` 的 `72f7e3f4c3ca1a522810a227610f2023811fd88c` 仅作历史，受影响 4 项已重跑 |
| `server/.../recovery/RecoveryRekeyRequest.java`、`RecoveryRekeySecrets.java`、`RecoveryRekeyCli.java` | 固定身份、外部钥、二进制管道、规范回执 → CLI 17 项及 Python IT | 不开放 Spring/HTTP 接线 |
| `server/build.gradle` | 主运行依赖清单 → `recoveryRekeyClasspath`、独立 JVM、`bootJar` | 无数据库配置/迁移改动 |
| `tools/recovery_rekey.py`、`recovery_rekey_verify.py` | 实际供应、未知结果、独立 HMAC/原字段回读 → Python IT 4 项，包装修改后再跑这 4 项 | 无 Python 旧消费者改动，旧 O02/O03 按已有报告及 SHA 保留 |
| `server/.../RecoveryLeaseRekeyTest.java`、`RecoveryRekeyCliFixture.java`、`RecoveryRekeyCliTest.java`、`RecoveryRekeyPythonIT.java` | 合成输入和原字节断言 → 专项、锁/配置受影响回归 | 不代替原设备/现场身份 |

省略路径前缀为 `server/src/main/java/com/acrylic/outbound`；测试位于 `server/src/test/java/com/acrylic/outbound/recovery`。继承 79 项改动及原字节保全见 `p07-ops01/dirty-hashes.json`、`dirty-files/`；补验后 79/79 未变，见 `p07-ops03/inherited-check.json`。Android、既有 UI/恢复、文档、私有请求和其他工具未混入。

## 实际执行

| 运行 | 当次结果与覆盖 | 原证据 |
| --- | --- | --- |
| `p07-ops01` | 17 项，1 失败；独立 JVM 重锚成功后的规范回执解析失败 | `gradle.log`、`test-results/`、`result.json`、短临时目录原文件 |
| `p07-ops02` | 40/40、0 failure/error/skip：Rekey 4、CLI 17、配置 7、协调锁 8、Python IT 4 | `command.json`、`gradle.log`、`test/`、`integrationTest/` |
| `p07-ops03` | 包装工具内存擦除/职责整理后 Python IT 4/4、`bootJar` 通过 | 同目录命令、日志、XML、`result.json`、`source-check.json` |

初次失败是 CLI 在已有末 LF 后又 `println`，测试也错误 `strip`，导致 `CONTROL_NONCANONICAL_JSON`。修复为精确 UTF-8 字节输出和原字节解析；不降低规范、不忽略错误。由于产品已重锚，采用完整保全而不是假定运行器无写入；全部 JVM/供应器原目录保存在 `.local/rtmp-p07ops01/02/03`，521 个文件 SHA-256/长度登记在 `p07-ops03/retained-runtime-hashes.json`。

`p07-ops02` 的 490 个相关输入前后未变。`p07-ops03` 仅包装工具有影响性修改，4 项真实 Python 入口已补跑；Java/锁/配置输入未变，复用该轮 36 项有明确路径哈希依据，不机械重跑。JAR 原件保存 `p07-ops03/server.jar`，280,746,825 bytes，SHA-256 `F7A6C628D3D7BE2504A791B676CEA9AAD15ACE982BAEFB64E2F93C70EE584FA9`。

业务正向使用真实文件系统和独立 JVM；旧签进程正文及旧 index/marker 保留于 `.rekey-backup-*`，独立 Python 验证旧/新 HMAC、marker 绑定和文件集合，新当前钥实际 `validateStartup/checkEffect/retire`。拒绝和回滚断言比较 P3/P5/业务目录原字节，覆盖跨 JVM 活动写者、非空租约、旧请求重放、两 JVM 并发仅一次成功、错身份/钥、外部钥路径、不能取回历史钥和尾替换失败。配置 7 项包括 default-off、不供应锚/错误 marker/缺围栏拒绝；未写 MySQL或启 API。

## 自审和运行事实

作者自审及实际 Python 独立消费者分别记录；没有独立人员审查。Java 输入字段严格，复用绝对路径/链接/本机文件系统检查；秘密不在命令行/环境/回执，旧/新及管道副本使用后清零。失败逐目标回滚并永久保留原副本；输出失败/超时为未知结果，无自动重试/反向覆盖/自动重启。

主机于 07:15:24 重启，历史 PID/AVD 运行状态已失效；本聊天未重启模拟器或操作物理 PDA。实测共享 MySQL 3306/33060 为 PID 5412（服务父 PID 4372）；无 Java/Python/ADB/emulator/qemu 在途，也无 API/Vite/目标隔离端口监听，见 `p07-ops03/processes-after.json`、`netstat-after.txt`。

另一聊天受用户授权清理磁盘；只读核对其回执确认 source edits=0、shared board edited=false、看板写者仍为本聊天，没有发送跨聊天消息。范围/写权证据保存在 `p07-ownership-checkpoint01/ownership-resolved.json` 和外部清理回执副本。它无损压缩 12 个冷 AVD 文件并删除已核对可重提取的 57 个 DLL，释放约 8.4 GiB；原源码/数据库/独特证据未删除。本轮 JAR 构建后 C/D 可用 78,881,837,056 / 19,867,713,536 bytes，后续复测见 `disks-after.json`。

## 未验与下一动作

没有执行真实 MySQL/HTTP、Chrome、Android、非空迁移、生产秘密/异地介质、Linux、多机停机或 D18 最终组合；本切片无数据模型/迁移/HTTP/客户端改动，旧链按输入 SHA 复用不能免除后续 L2/L3。历史钥的真实 ACL/外置防覆盖/长期可取回和原签名物料仍保留外部边界；工具只用只读句柄核验指定材料，不替环境配置保管权。

突发中止的部分替换/历史原件人工恢复，以及 D13–D15 其余跨截点、历史钥验证/引用保护、限额/监控/回退的必做开发仍需逐项核对，不能用本报告替代。下一项从该实际源码及已保存证据核对这些独立缺口；E06 Chrome 审批、原设备阻断和所有原失败/暂缓不清零。总目标保持 active，生产默认关闭、`localDeletionAllowed=false`、main/Release/部署边界不变。
