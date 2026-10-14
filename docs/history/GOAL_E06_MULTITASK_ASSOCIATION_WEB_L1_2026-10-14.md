# E06多任务人工关联网页L1开发备份

输入`91fe25d8bb83fccfd0b42f428bd734f825750f51`，固定主树`D:/acrylic-pda-outbound`、`codex/development`、单助手单写者。采用[最小设计](GOAL_E06_MULTITASK_ASSOCIATION_WEB_DESIGN_2026-10-14.md)：在原主管人工关联入口显式载入额外任务，逐任务核原件、全部候选、实物及适用冲突回执，合计有效包及原短装，主管明确批准目标差额。未知结果保留完整成员、正文、key及epoch；主原件和每个明确成员均可回读同一关联。仅登记待对账依据，不记账、不放行、不产生正式作业或删除资格。版本事实见看板生成块。

影响映射：`AssociationForm/AssociationMembers/association-{api,contracts,shortfall,members-contracts,selection,selection-api}`及两个定向测试 → 网页多任务输入、数量/产品/防重约束、原键持久化与回执消费 → 类型/构建/契约、实际HTTP消费者及Chrome正向/丢响应/刷新/双页/成员GET、精确全表增量。服务端和迁移未改，2418项输入及运行JAR与[服务L1](GOAL_E06_MULTITASK_ASSOCIATION_L1_2026-10-14.md)逐字相同，故其权限/并发/异文/末SQL/锁后撤权/default-off/非空迁移按该页及输入SHA复用；Android原件生成不受网页改动影响，复用该输入对应的[实物验收](GOAL_E06_PHYSICAL_ACCEPTANCE_2026-10-14.md)与[独立结束验收](GOAL_E06_CLOSURE_ACCEPTANCE_2026-10-14.md)。这些复用不替代后续E06 L2或D18 L3。

本机证据：`A=.local/goal-20261013/e06-conflict-audit-1538`；`B=.local/goal-20261014`；`R=B/e06-multitask-web-evidence-review02`。完整命令和退出状态由原会话执行记录核对，原日志保持；本次为同作者接续复核，不称独立人员审查。

| 范围 | 实际结果与原证据 |
| --- | --- |
| 输入准备 | `B/e06-multitask-web-input01.log`退出零，`A/web-prepare01`在尚无主/成员关联的真实非空输入记录全表及Chrome输入 |
| 类型/契约/构建 | `B/e06-multitask-web-typecheck02.log`、`contract01.log`、`build02.log`实际退出零；构建输出`B/e06-multitask-web-build02`，已有chunk体积提示保留 |
| 多任务消费者 | `B/e06-multitask-web-consumers03.log`13项零失败/跳过；核实际回执的成员完整性、摘要/原上下文/实物绑定、原目标短装、重复/异车/自审/超计划拒绝与原键恢复。`R/current-consumers01.log`在接管输入上再次实际消费同一HTTP证据，13项通过、退出零 |
| 旧单任务消费者 | `B/e06-multitask-web-old-consumers02.log`16项零失败/跳过、退出零；原单任务、短装与冲突证据来自保全的1534成功响应，不重造旧事实 |
| Chrome产品入口 | `B/e06-multitask-web-chrome01.log`1场景通过、退出零；显式载入两任务、候选分页每页1、明确批准、真实成功HTTP后丢响应、刷新/双页原key恢复、成员页及清空本地命令后主/成员GET。三次POST均200且key/body/epoch相同，原响应及命令在`A/browser01` |
| 全表事实 | `B/e06-multitask-web-finish01.log`退出零；`A/web-finish01`逐值守恒并核精确新增一主关联、一成员、一幂等、登录与关联两条审计。`R/facts-review.json`不连库重读180张表原hex证据，旧业务行全保留，其余业务表零变化，三响应与原命令逐值一致 |
| 输入/自审 | `A/browser01/input-manifest.json`206项；接管时205项不变，唯一差异为测试末增两条截图调用。`R/chrome-input-reconstructed.spec.ts`去掉两行后SHA256与当次Chrome脚本完全相同。2418服务输入和JAR相同；详见`R/result.json`、`facts-review.json`。作者复核持久化先于发送、身份/仓库/原件变化停止、完整成员/目标/实物核对、回执边界与历史兼容 |

已目视390px选择页底部，原/目标短装批准和保存动作可见且无横溢。成员页截图仅保留当时视口，成功回读由当次Chrome断言及HTTP证据证明；后加两条panel截图调用尚未执行，不冒称已有截图。产品输入无变化，不为仅截图改动重开已停机数据库。

失败保留：`consumers01`因Node解析器把type-only依赖引向无扩展名`api`而失败，修为明确类型导入后`consumers02/03`通过；`old-consumers01`漏冲突证据环境变量，补齐实际保全输入后`old-consumers02`通过。typecheck01/build01已有成功日志，后续源码修正后以02为准。当前复核首轮用正反斜杠不同的路径字符串比较而拒绝，解析实际路径后重构脚本哈希匹配；未发HTTP或连库，原工具输出保留。旧失败不倒推通过，1522/1523 CLI及UI/native/恢复暂缓计数均保留。

1538保全`B/preserve-1538-e06-multitask-web-l1-01`：dump与认证停机退出零、对应端口零监听，347个文件长度/SHA保持、220个适用文件NTFS压缩；原datadir/dump/binlog和日志零删除。dump SHA256 `8a652d08ac17a8bbad5f530cb495fa79ec688e0a255c59eecfcec6899fd65aec`；本次对全部347文件及dump重新核验通过。原保全D盘增加206950400字节，收尾C/D余量47109197824/16737067008字节；接管及后续重型动作分别复测。预览1494与实物1519保持；1538及旧失败库禁止复用。

接管全脏原字节、长度/SHA、diff/暂存、远端、工作树、进程/监听和磁盘在`B/resume-e06-web-20261014-02`，写权`B/ownership-resume-e06-web-02.json`。继承Android/UI/文档/工具及`server/physical-disposition-last-request.json`保留且不混入本范围提交；私有原件、密钥、数据库、APK和产物不进Git，不称已异地备份。

此页仅收多任务网页L1开发备份；E06受影响L2、E07恢复/外置副本、E08留存/UI、E09最终候选及全部剩余D/O/U闸门继续，总目标active未完成。主管/第二任务/库存/实物观察和签名明确合成，不冒充原PDA现场事实。七类生产决定、TEMP边界、`localDeletionAllowed=false`及D16标准保持。
