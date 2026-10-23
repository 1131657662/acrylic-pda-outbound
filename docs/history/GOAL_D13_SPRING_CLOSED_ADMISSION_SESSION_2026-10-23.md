# D13 实际 Spring 闭链准入、初始化与 JDBC Session

沿用户批准的 D13 最小设计补有界入口组合，并修复真实拒绝响应缺口。总目标 active、未完成；本片不是 D13 整体、完整恢复或第8节通过。版本事实见看板事实块。

## 输入、写权与边界

- 固定树 `D:/acrylic-pda-outbound`，开发分支 `codex/development`；输入 HEAD 与 fresh 开发远端核同为 `3fa42b8d87cd6e25c253a739bf12a093876d8148`，main 为 `c6e9e0c238c657baecbf699ba6fdbada5d979b86`，未操作 main。
- 证据根 `.local/d13-spring-admission-20261023/run01/`。`baseline/` 保存初始23条展开 dirty 的原字节、SHA、patch 与环境；22条无关继承路径逐字节保留，看板允许按本片断点更新。接续时 fresh 核到28条路径、空暂存；已新核分支/远端、进程/监听、ADB、Debug Keystore 与 C/D/E 余量。
- 其他本项目/清理聊天 fresh 为 idle，本助手是本片唯一写者；没有创建子代理、发送聊天消息或借用旧写权。系统 MySQL 只观察3306/33060，内部身份未认证，未连接或停机。
- 新库仅 loopback、合成闭链登记及合成控制文件。使用实际 `OutboundApplication`、原准入配置、primary DataSource、初始化窗口、Security、JDBC Session、filter/listener 与 `/api/v1/auth/csrf`。没有测试 HTTP 路由、替代 primary 或安全绕过。仅隔离 HTTP 的 cookie secure 参数关闭；不改产品默认。
- P5/effect 开关保持既有默认。本片不与上一片真实 P5 关闭故障结果拼成同输入组合；MODEL 控制登记不代表完整恢复供应、原身份或可信权威。

## 问题与修复

生产接线要求显式 baseline；原 startup 和 Session registration 正向测试夹具未供应该值。先冻结原输入执行 startup red，3项中2过1失败，缺口为 `CONTROL_BASELINE_REQUIRED`；补正夹具并增加缺 baseline 拒绝测试。修后 startup/config 两类实际9项全过。

实际完整 Spring/Session 的有效产品 red 为 `run1675-spring-spring04`：正常 CSRF 已持久创建并复用一个 Session，其他178表守恒；将本片自有合成 `server_state` 改为既有 `RECOVERY_PENDING` 后，Session 借连接抛闭链拒绝，HTTP 却返回 HTML 500。原 cause 链指向 `SessionRepositoryFilter` 与 `RECOVERY_DATABASE_ADMISSION_REFUSED:SERVER_MODE`。

只作以下生产修复：

1. `RecoveryDatabaseAdmissionException` 是专用 `SQLNonTransientConnectionException` 子类；保持原消息前缀、具体 gap 及 SQL state。仅闭链检查失败创建此类型。
2. 请求 filter 在原根内识别该类型的 cause 链，对未提交响应清除缓冲/旧长度，用既有 `writeWithoutState` 返回 JSON 503；不查询 `ServerState`。finally 的解绑/FINALIZING 和 listener 退役机制保留。
3. 优先级既有的 recovery MVC advice 增加此专用异常映射。普通 SQL 即使消息/SQL state 相同、仅 suppressed 中有拒绝、或响应已提交，都保留原失败；不泛化吞掉任意数据库错误。

未改变物理准入条件、writer/P5 围栏、事务、触发器、迁移、契约、生产开关或业务权限。作者、自审和离线工具消费者是同一助手，不冒称独立人员。

## 执行与失败保全

每轮先冻结全部服务端 main/test、报表 main、资源及构建定义；用现有 JDK 直接 javac/JUnit Platform，runtime 只含该轮新 class 与110个既有依赖 JAR。编译、测试 JVM 限384 MiB，TEMP/TMP 在本片 D 盘；未跑 Gradle或旧 IT。首四轮2527输入，增加专用类型/反例后2529输入，输入和依赖前后无漂移。

| 输入/运行 | 实际结果与定性 |
| --- | --- |
| `unit-red/` | startup 原夹具3项，2过1失败；原输入保留 |
| `spring01-unit/` | 修后 startup/config 9项全过，0跳过/中止 |
| `run1670-spring-spring01`，19849 | 完整应用启动后，表摘要缺 Java 时间模块失败；未到 HTTP 验证 |
| `run1671-spring-spring02`，19850 | 正常 HTTP/Session 成功；Windows 表名大小写造成摘要排除错误，仅两张 Session 表变化 |
| `run1674-spring-spring03`，19853 | 正常请求/178表守恒通过；UUID 故障注入被产品不可变绑定触发器正确拒绝 |
| `run1675-spring-spring04`，19854 | 有效产品 red：预期503，实际500；正常 Session 与表守恒已通过 |
| `response-red/` | 7项，3过4失败；三种 filter 包装及 MVC 映射反例有效 |
| `response-green/` | 9类52项全部通过，0失败/跳过/中止 |
| `run1676-spring-spring05`，19855 | 实际组合1项全部通过，0失败/跳过/中止；运行器exit0 |
| `consumer01/` | 离线原件核验exit0；命令/输入脚本及stdout/stderr另存 `consumer-attempt01/` |

首三次失败分别是不同夹具/消费方法缺口，不是重复同根因重跑。UUID 绑定原本不可变，因此没有删除/停用触发器来造反例；技术决定追加在 `decision.md`，改用已有恢复模式状态。实际跨实例 UUID 反例仍按[独立历史报告](GOAL_D13_CROSS_INSTANCE_DATABASE_IDENTITY_2026-10-23.md)的输入和范围继承，本片不称新增 UUID Spring 通过。

关键命令原文及结果在 `compile.ps1`、`focused.ps1`、`run.py`、各编译/执行目录：

```powershell
python .local/d13-spring-admission-20261023/run01/run.py spring01 unit
& ./.local/d13-spring-admission-20261023/run01/focused.ps1 -Label response-red
& ./.local/d13-spring-admission-20261023/run01/focused.ps1 -Label response-green -Regression
& ./.local/d13-spring-admission-20261023/run01/compile.ps1 -Label spring05
python .local/d13-spring-admission-20261023/run01/run.py spring05
python .local/d13-spring-admission-20261023/run01/consume.py spring05 consumer01
```

这些都是已执行命令，不是重跑指令；运行器拒绝覆盖已有目录。52项回归包括响应、startup/config、物理闭链、请求 dispatch/registration、admitted DataSource/gate 和 root 退役。

## green 原件证明

- 初始化 bean 在 `INITIALIZATION` 根内通过实际 primary 借连接，记录物理 connection ID；ready 后窗口关闭、无根借连接拒绝，真实请求完成后 live roots 归零。
- 两次正常 CSRF 请求持久创建/复用同一 Session；180表完整摘要中仅两张 Session 表变化，178张其他表守恒。
- 合成库进入恢复待核后，实际 Session 路径得到 JSON 503，code 为 `RECOVERY_DATABASE_ADMISSION_REFUSED`，epoch 为 unknown、mode 为 UNAVAILABLE；180表均不变。恢复该自有模式后原 client 再访问成功。
- 同一合成库以恢复待核状态冷启动，初始化的 primary 借用被 `SERVER_MODE` 精确拒绝，180表不变；15份控制原件 SHA 守恒。
- MySQL general log 证明初始化查询与8条 Session 读写 SQL 均在同一物理连接上先出现闭链 SELECT。两段恢复待核区间均有闭链读取且没有 DML；逐行号关联在 `consumer01/proof.json`。不外推成所有借用/worker/文件入口均覆盖。
- 离线消费者核同2529输入、110依赖、51份运行输出；general log、原 dump/binlog 的单独 SHA 在 `consumer01/retained-originals.json`。general log 含合成凭据/Session 字节，只保留私有目录，不打印或提交原文。

## 保存、清理与接续

五个新实例均先后认证端口/datadir/schema/账号/server UUID，导出完整 SQL dump、保留 binlog 与 general log，再认证 shutdown、进程exit0、监听消失。清理前逐文件保留1725份 datadir 输入 SHA/字节、命令、依赖/源码、结果及保留证据 SHA；只删除这五个 disposable datadir。

逻辑删除1,109,080,635字节；D余量21,610,569,728→22,741,049,344字节，观察净增1,130,479,616字节（约1.05 GiB，不等同逻辑字节）。当时 C=74,921,979,904、E=195,634,053,120字节。原清理汇总在 ordered dictionary 上求和返回 null，未覆写，按已保存逐文件字节生成 `cleanup/accounting-correction.json` 更正；没有重复删除。所有失败日志、原 HTTP、dump/binlog/general log、冻结输入与最终 APK 保留。

原看板/进度/历史索引完整字节及 SHA 先存 `documents-before/`，看板旧叙述归档[Spring组合前断点](ORCHESTRATION_STATUS_BEFORE_SPRING_ADMISSION_2026-10-23.md)，生成事实不在历史复写。完整dirty、继承守恒、Git回执及fresh环境按本片 `closeout/` 和后续提交收口快照读取。

未验完整普通回归、实际业务恢复供应与全部入口/P5合并、真实OS拒绝自halt、Android/APK/模拟器/真机、本片部署或最终候选。下一有界项先核实际恢复供应对闭链接线和独立 worker/Session/文件入口的覆盖，复用本片准确输入，不机械重跑旧IT/旧库。D13整体、D16/D18及第8节仍缺；生产开关/TEMP归档、正式签名、原库/现网、main、生产部署/Release继续关闭，`localDeletionAllowed=false`，取消链和旧run03不恢复。
