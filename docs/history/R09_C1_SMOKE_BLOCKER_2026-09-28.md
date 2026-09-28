# R09 C1 复验前冒烟：环境准备失败，按边界停止

## 结论与授权

**冒烟未通过，失败发生在环境准备阶段，Spring应用尚未启动。C1仍为部分完成、真实HTTP未验收。** 不是探针重复注册复现，也没有证据将其定性为R09产品缺陷。

用户认可两批代码，授权先冒烟后真HTTP/真MySQL复验，并明确“冒烟失败 → 停在冒烟，报告（不直接跑IT）”。因此本次在准备失败后停止，未重试启动、未运行C1 IT、未分配559及后续attempt，也未开工C2。后续继续冒烟交用户决定。

输入`6c74f79`，`D:/acrylic-pda-outbound`、`codex/development`，开工时主树干净且与origin一致。单助手；旧验证树、系统3306、557/558失败库均未动。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

## 新增冒烟入口及实际执行

测试提交`0bcf24c`只新增[C1StartupSmoke](../../server/src/test/java/com/acrylic/c1probe/C1StartupSmoke.java)，未改任何既有生产/测试文件。它是独立main入口，非JUnit IT：

- 核对主树、专用datadir/schema/账号/端口、空库和一次性marker。
- 预定在显式隔离许可下迁移冒烟库，再以真实`OutboundApplication`及修正后的`C1HttpProbe`启动完整Spring Boot/Tomcat。
- 预定断言上下文active、HTTP端口已启动、一个C1 controller/两个映射、登记表仍为零，并正常关闭应用。
- 不执行C1登记、不发测试HTTP请求；应用内迁移、Worker、Bootstrap保持关闭，不改变生产默认值。

上述运行断言**本次全部未执行**。唯一已通过的入口验证为`gradlew.bat -p server recoveryControlClasspath --console=plain`编译及classpath生成；没有新的单测或IT XML，历史78项单测不冒充当次结果。

本机编排脚本`.local/run-r09-c1-smoke-20260928.ps1`使用独立目录`.local/r09-c1-smoke-20260928-1/`及端口19330，未占用IT编号。MySQL初始化成功，日志记录其在loopback就绪；但后续readiness连接失败，尚未创建专用业务schema/账号、未执行应用迁移、未调用Java入口。

## 原样错误与分类

`readiness.log`原样：

```text
ERROR 2005 (HY000): Unknown MySQL server host '127' (11001)
```

脚本停止异常：

```text
Smoke MySQL not ready; see retained logs
```

脚本finally中的自动关闭也使用了同一错误参数形式，`shutdown.log`记录：

```text
mysqladmin: connect to server at '127' failed
error: 'Unknown MySQL server host '127' (11001)'
```

**分类：助手新增的本机冒烟编排错误。** PowerShell对未加引号的`-h127.0.0.1`发生参数误解析，MySQL收到主机`127`；同一脚本的readiness和自动关闭均受影响。MySQL服务端本身已启动、监听127.0.0.1:19330。随后仅为清理已启动实例，核对PID/datadir身份后，以显式字符串`'--host=127.0.0.1'`执行mysqladmin shutdown成功，未借此重试冒烟或执行IT。

`runner-result.txt`中的`javaExit=1`是脚本预设的失败占位值，**不是实际JVM退出码**；Java从未被调用。不存在`application.log`、`smoke-started.marker`、`smoke-started-ok.txt`或`smoke-completed.marker`。不能据此声称“Spring有启动异常”或“重复mapping已消除”。

## 证据与收尾

失败目录内保留`initialize.log`、`initialize.stdout.log`、`mysqld.log`、`readiness.log`、`shutdown.log`、`runner-result.txt`、失败datadir，以及后续清理的`shutdown-confirmed.log`/`teardown-confirmed.txt`。编译日志为`.local/r09-c1-smoke-compile-20260928.log`。原脚本保留，不覆盖失败原件；上述本机文件均不提交。

关键文件SHA-256：

| 文件 | SHA-256 |
| --- | --- |
| 本机冒烟脚本 | `4EC309CA8CC45D59719968C81CE8B71A20076CF9CBFC8A9F08B6CBDBF44C2C97` |
| readiness.log | `C7F147868AB12D856B8BC9DC705A174DCF0C2DB8C2E673F33246DD31B307247A` |
| shutdown.log | `57D87E68C1FB790BC9BC4B8E65EF05EE48B6376E84C795D6C7EE73A4501A74E7` |

隔离实例已正常关闭；19330/19437无监听，后续收尾检查见看板。559/560目录不存在；实际IT号仍止于558，接续前仍须重新查重。未删marker、未放宽任何超时或守卫。历史557/558及探针单测红证据保留。

## 未验与下一步

本次未验证Spring上下文、探针唯一注册、C1真实HTTP、双侧一致/缺侧拒绝、fixture原身份/映射/请求守恒、真实UNKNOWN与重启、独立NORMAL路径、attempt105连接层回归。两批实现与历史单测证据见[第二批记录](R09_C1_INITIAL_REGISTRATION_BATCH2_2026-09-28.md)，不改变其未验收结论。

建议后续只纠正冒烟脚本的原生参数传递，保留失败目录与日志，受控安排新的冒烟执行；完整应用启动成功后，才分配559+做已批准的C1验收。此次按用户失败即停要求不继续修正/重跑。

C1验收后，C2仍按[入口评估](R09_C2_ENTRY_ASSESSMENT_2026-09-28.md)逐批授权：G0租约/旧操作收尾→HTTP/Session→PDA→ImportFiles→SubmissionFiles→来源页/对象文件→Worker/提醒→ExportFiles→渲染进程→Flyway→Bootstrap→全入口组合。此为开发顺序，运行时初始化许可必须先于业务入口。现场原资料、跨进程/断电/Linux持久性、独立审查和全入口组合继续未验；A3发布前清单、526专项、R07抬头撤销不扩做。
