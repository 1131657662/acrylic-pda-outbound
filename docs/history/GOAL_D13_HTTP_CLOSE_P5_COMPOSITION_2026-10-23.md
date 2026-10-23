# D13 HTTP关闭未知结果与实际P5：有界开发验证

总目标保持 active、未完成。本片接续已批准的 D13 最小设计，补足[连接终结修复](GOAL_D13_CONNECTION_UNCHECKED_COMPLETION_2026-10-23.md)只有本地 JDBC MODEL 的证据限制；不计 D13 整体、D18 或总目标第8节通过。版本事实见看板生成块。

## 输入、写权与实现范围

- 固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`。本片初始 HEAD `ec81072be647cc059207144fabd97d631612b2b9`、fresh 开发远端 `f3d6b2d1ae0d98c269ed7bde2a58fa51d9dbbba4`；30项 dirty 原字节、SHA、diff、进程/监听/设备/Keystore/余量保存在 `.local/d13-http-close-20261023/run01/baseline/`。
- 初始另一聊天实际写 Android、测试部署及共享看板，本片只写 D13 测试和独立证据。其收口提交 `8deba316b42cf4232b97f24bc48ab73557218dbe` 后状态 idle、无在途写入，本片才接管共享断点。五路径变更、原共享文件字节/SHA和接管依据见 `shared-before/`；主树和 fresh 开发远端已核同该提交。未回退、混入或冒领该聊天的改动。
- 新增 `CompletionFaultDataSource`、`RequestCloseFenceChild`、`HttpCloseFenceProcess`、`RecoveryRequestCloseFenceIT` 四个测试文件。沿用前片 handler 修复，没有进一步改生产实现、接口、契约、迁移或默认开关。
- 测试专用 loopback servlet 原样组合产品 `RecoveryRequestAdmissionFilter`、`RecoveryRequestRootListener`、operation registry、admitted DataSource、connection handler 和真实 `RecoveryFenceAuthority`。实际 Tomcat/HTTP、MySQL 驱动/事务、Windows 文件锁和独立子 JVM 参与执行。
- 故障只在测试 transport 边界注入：真实 driver rollback/close 已返回后，分别抛 SQLException、RuntimeException、Error。控制身份、初始NORMAL供应和 anchor 均为合成测试材料，私有配置仅在 `.local` ACL目录；不能替代原身份、可信恢复闭链或现场材料。

## 退出标准与实际结果

每个故障单独创建控制根、P5树、数据库表及独立 owner。先经产品请求入口执行两次普通 HTTP，实际提交记录1、2；两次请求根正常退役且P5 index清空。第三请求实际插入未提交记录3，父进程观察仍只有1、2，并独立探锁确认 writer 被持有、请求根处于 SERVING、一个 CONNECTION 子操作。

父进程放行关闭后，修复版必须在 catch/reborrow 继续代码前进入真实 F1–F5、owner实际 exit70；HTTP无成功响应。数据库仅保留1、2，后继独立进程以 `RECOVERY_LEASE_OUTSTANDING` 拒绝且不再借业务连接。

| 批次 | 输入与环境 | 当次结果 |
| --- | --- | --- |
| 有效 red：run1666 | 冻结原handler；全新19847隔离实例 | 6项，2通过、4失败，0跳过/中止；SQLException分支原本已围栏，4个unchecked分支命中反例 |
| green：run1667 | 冻结修复handler；全新19848隔离实例 | 6项全部通过，0失败/跳过/中止；每项实际halt70、后继exit1 |
| 离线原件消费者 | consume.py，第5次工具运行 | exit0；12组原件、源码/依赖/输出哈希、SQL/binlog和P5 HMAC核同 |

red 的4个unchecked请求捕获关闭异常后仍借出第四个连接，实际提交 canary记录4；最终数据库及binlog均为1、2、4。请求结束时原有root退役路径虽最终halt70，却来不及阻止这次继续执行。green六表均只有1、2，没有记录3或4；每个owner仅借3次连接，继续标记均不存在。每轮12次普通HTTP成功、6次故障HTTP没有成功响应；这不是业务路由验收。

父进程在后继扫描前已复制完整P5原件。真实后继依既有协议退役死owner的孤儿lease/index条目并仍拒绝准入，unknown审计原件和process记录保留。离线消费者分别核验退役前lease及前后index的HMAC、PID/operation关联和unknown原字节；不能把最终lease文件已退役误报为证据丢失，也不声称验证了所有后继/恢复策略。

真实authority会在 poison中立即halt，因此rollback故障的本轮证据不声称“halt后还继续调用physical.close”；前片“尝试close”断言只适用于返回的MODEL authority。实际OS终止、锁释放和数据库观察按本片原件记录。

## 命令、输入与失败保全

完整证据根：`.local/d13-http-close-20261023/run01/`。运行器拒绝覆盖已存在的compile/output/instance目录，所有任务TEMP/TMP在D盘。没有执行Gradle、旧IT、旧库或旧run03。

```powershell
& ./.local/d13-http-close-20261023/run01/compile.ps1 -Phase red -Attempt 03
python .local/d13-http-close-20261023/run01/run.py red
& ./.local/d13-http-close-20261023/run01/compile.ps1 -Phase green -Attempt 03
python .local/d13-http-close-20261023/run01/run.py green
python .local/d13-http-close-20261023/run01/consume.py
```

red/green各冻结2397项Java/构建配置输入、110个已存在依赖JAR；直接javac/JUnit Platform编译运行，不消费旧业务class或历史XML。两个compiled manifest只差handler：原SHA256 `80A340318E9B9FAF1372DB06858F1EB03531E2507083F21EFA0DCCBFBF53A2F8`，修复SHA256 `354EFF35C924DD9D3C4A48773E533240A9B91DFBD978797F3E48A35E36375D7D`。所有测试源码相同，依赖manifest相同，最终源码与green冻结输入核同。JDK/编译器SHA、完整命令/参数、原日志、输入及输出哈希分别在compile目录和各run目录。每个JVM限制256MiB。

以下失败均保留，不计有效red或通过，不机械重跑原库：

- 预选19839/19840已有历史目录，allocation guard在创建进程前拒绝；旧目录未操作，见 allocation-refusal.md。
- run1662/19843：server目录的既有嵌套`.local`导致环境守卫拒绝，6项均未进入场景；修运行目录及测试控制资料落点，未放宽守卫。
- run1664/19845：已到达实际HTTP/P5，但读取Windows子进程stderr时编码失败，6项失败；只固定输出编码。旧输入/原日志/实际继续标记均保留。
- 离线消费者前4次分别因Windows路径分隔符、dump CRLF、读取退役后的lease而非已存原件、canonical HMAC遗漏末尾LF失败；具体原脚本、诊断及修正见 consumer-attempt01–04.md。修正匹配现有字节契约，没有改原证据或重跑业务。consumer05.stdout/stderr与consumer-result.json记录最终exit0。

四个新实例均认证身份、dump、提取binlog并认证shutdown，进程退出0且端口无监听；原系统3306/33060只观察，内部身份仍未认证，未连接/停止。

## 自审、未验和接续

作者、自审及离线工具消费者均为同一助手，不是独立人员审查。新文件仅在test源集中，无扫描组件或生产注册；参数化写值、生成表名白名单、loopback动态端口、私有合成anchor及自有子进程边界已核。正常请求/退役与故障请求/后继分别留证。

前片73项相关回归是历史范围，handler字节和依赖未变，本片没有把它重跑或冒充完整普通回归。未运行完整Spring应用/Session、INITIALIZATION、闭链准入与Spring primary接线的组合、真实业务恢复、Android/APK/模拟器/真机或部署；本片既不是统一最终候选，也不是D13整体完成。

共享看板旧叙述先归档至 `ORCHESTRATION_STATUS_BEFORE_HTTP_CLOSE_2026-10-23.md`，原字节和映射见 `shared-before/`。清理4个本片已认证停机且用途结束的隔离datadir，752534347逻辑字节；D余量22247276544→23001108480，观察净增753831936字节（约0.70GiB）。逐文件SHA、路径/命令、源码/依赖/运行与SQL/binlog证据、前后余量见cleanup/。dump、原binlog、全部HTTP/P5原件/失败日志与冻结输入仍在；前片被策略拒绝的class/TEMP删除没有重试。最终dirty原件/SHA、环境及开发备份回执在closeout/，不以提交或整理计验收。

下一有界项继续实际Spring入口的INITIALIZATION、同一物理连接闭链检查与正常请求/Session组合，先核既有覆盖再选择新缺口，不重跑本片或旧库。D13–D15整体、D16引用保护、D18实际最终候选及第8节仍缺。

`localDeletionAllowed=false`，生产决定/归档开关、正式签名、原库/现网、main、生产部署和Release保持关闭；不恢复换机承接、多跳、接收者确认/激活链。原资料/身份/Keystore、原签回执、唯一原证据、最终APK及继承源码均保持。
