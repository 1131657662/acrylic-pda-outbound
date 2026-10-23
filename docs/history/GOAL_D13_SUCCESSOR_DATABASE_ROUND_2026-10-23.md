# D13 后继数据库追加绑定与每轮 NORMAL

已实现并验证后继数据库登记前置：真实非空源库已有不可变 binding/NORMAL，经实际 dump 恢复到新 schema 后，追加两轮目标绑定、投影及 NORMAL，原行和其他业务事实保留。新隔离 IT 1 项通过，相关主机回归 100 项通过。本片不开放后继运行时准入，不计真实 API/worker/P5 组合、D13 整体或最终候选通过。

## 输入与实现

- 固定树 `D:/acrylic-pda-outbound`，`codex/development`，输入 `7036f53a8b5263061842323b98087def3ca25e14`；总目标 active、未完成，版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。
- 证据根 `.local/d13-successor-database-20261023/run01/`。`decision.md` 记录已批准方向、本片退出标准和运行时集成仍待完成；`baseline/` 保全 34 条展开 dirty 原字节/SHA/patch 及目标原件。另一聊天的 Android 改动、继承源码和生产边界不属于本片。
- 增量迁移只新增空的 `recovery_successor_binding` 和 `recovery_successor_normal` 及不可变触发器/约束；不回填、不改旧迁移、不更新/删除原 binding/NORMAL/投影/映射，也不删除原触发器。
- `RecoveryDatabaseSource.inspect` 在已取得的 closed authority 事务中锁定 `server_state`，核完整外部链/数据库前缀、已登记 NORMAL、真实源 UUID/schema，给出后继命令需要的四个源/前驱 pin。该调用会使用既有库外事务见证，不是无状态脚本。
- 原 binding/NORMAL 的摘要采用全部原列的规范逻辑值，包含原 `registered_at` 六位微秒；二进制为小写十六进制，数字为整数，时间不靠 JVM 时区格式化。后继行摘要包含其完整协议正文，不含摘要字段自身。具体字段以 `RecoveryDatabaseHashes`、`RecoverySuccessorRows` 为准。
- `RecoveryClosedProjection` 的后继分支逐条比对数据库投影与已验证外部原件，重建全部数字代次/UUID 映射；每个新 QUIESCING 都核前驱 release/NORMAL intent、源 binding/NORMAL 摘要及目标真实 UUID/schema。只有原 NORMAL 状态的下一条后继记录可追加新绑定，其他阶段只能在已绑定的目标继续；不能只取最大行或忽略源库身份。
- `RecoverySuccessorNormalRegistration` 只在该轮准确 RELEASE_READY、完整投影及对应耐久原件一致后追加 NORMAL；初始 `normal-admission.json` 保持，更多原件按 revision/hash 从 `normal-history` 选取。登记、映射、投影及 `server_state` 均沿既有 closed 事务/状态锁原子执行；重试幂等，未知事务围栏仍适用。
- 所有新操作均无 bean/业务路由/自动初始化。结果能力始终为 `ordinaryWritesAllowed=false`、`localDeletionAllowed=false`。原运行时 `RecoveryAdmissionVerifier` 及单 SELECT 身份查询未改；测试明确断言后继 NORMAL 登记后仍不获运行时准入。

## 实际验证

| 证据 | 当次结果与范围 |
| --- | --- |
| `compile03/` | 独立 javac exit0，冻结 2713 项源/资源/运行器输入、110 项依赖；冻结及执行后核无漂移。非 Gradle 完整测试或 APK 构建。 |
| `unit01/` | 同一编译输入 100 项 found/started/succeeded，0 失败/跳过/中止，exit0；覆盖控制读取/路径/锁/意图/完整性、原 NORMAL、准入身份/比较、closed authority/connection 及 initial NORMAL 消费者。 |
| `it03/` | 全新隔离 allocation 1752，IT 1/0/0/0、runner exit0；真实 MySQL、非空迁移、实际 mysqldump/mysql 导入、新 schema 两轮追加登记。全量表/文件摘要与原件守恒、源库最终完全不变。 |
| `negative-*` | 四个独立合成控制分支分别错源 binding 摘要、源 NORMAL 摘要、目标 UUID、目标 schema；真实目标精确拒绝，七张控制/状态表无变化。相同正确后继命令在源 schema 精确拒绝，不将源库改造成目标。 |
| 同一 IT 的事务/原件反例 | 新 binding 的最后 INSERT 失败后，先前投影和状态全部回滚；每轮 NORMAL 的末状态 UPDATE 失败后，新 NORMAL 行回滚；两轮缺 intent 拒绝，投影/登记重放幂等，原及新表 UPDATE/DELETE 触发器继续拒绝。 |
| 原件与业务守恒 | 初始 binding/NORMAL 原行及时间、既有投影/映射保留；除明确的状态/控制追加表外，其他业务/账务/审计/任务表逐表核同，原文件核同；初始控制历史与 NORMAL 原字节保持。 |

实际命令和 cwd/Java 参数见 `compile03/command.json`、`javac.args`、`it03/command.json`、`unit01/command.json`；隔离初始化/身份/导出/导入/认证停库的 argv 与退出码见 `.local/e2a-cross-20260925-1752/`。所有进程已结束，隔离端口无监听。系统 MySQL 身份未知，仅观察，未连接或停止。ADB 仅枚举两真机，无设备操作、模拟器或签名/APK 构建。

关键哈希：

- IT 结果 `68daac89bf050a4ad26ca4b2f60ee245f1ef8e7f8f7c048ca544089c98c514aa`。
- 100 项主机结果 `6a576416d40ba51211580ca649c759dc765ef29b57d1ac248048205574cad25b`。
- 目标完整事实/文件快照 `93b10dfd273e216908091a4af795a5b1ec89c8951573edd7227d00bd7e9c0957`。

作者、自审、Python 原件写者及 Java/MySQL 消费者由同一助手操作，不冒充独立人员；control/closed authority、业务数据及签名均为明确合成隔离输入。跨 schema 正向不等于实际跨实例正向；随机错误 UUID 反例也不等于实际第二实例验证。

## 失败、清理与开发备份

- `it01` / allocation 1750：夹具缺合成签名文件，在导入前失败；补既有签名夹具。`it02` / allocation 1751：实际导入后，测试快照的 LocalDateTime 序列化失败；改逐列取值/二进制十六进制后 `it03` 成功。两次均非业务通过，原 stdout/stderr、冻结输入及停库回执保留。
- 三个新隔离 attempt 均认证停库、端口与消费者复核后，按持续清理授权清理 datadir、binlog/index 和可重建 dump，共 15 条明确路径。删除前保留逐文件 SHA/长度、命令/依赖/结果，原始控制链、intent、合成签名身份、全量事实摘要与关键恢复行原文继续保留；见 `cleanup01/retained-recovery-rows.sql.txt`。
- 逻辑删除 738810669 字节；清理窗口 D 盘空闲从 25268326400 增至 26028728320 字节，实测净增 760401920 字节（约 0.71 GiB）。磁盘净差与逻辑长度分列，不伪称相等。原脚本 `Measure-Object` 未解析 OrderedDictionary 键导致汇总 null，原记录不改；`cleanup01/correction.json` 从已保存逐文件长度重算，未重复删除。
- 未清原资料/身份/Keystore/回执/唯一原证据/最终 APK、继承源码或其他聊天缓存。`before-cleanup/`、`before-docs/` 及最终断点保留原字节/哈希；旧看板叙述另[归档](ORCHESTRATION_STATUS_BEFORE_SUCCESSOR_DATABASE_2026-10-23.md)，不恢复旧状态或写权。
- 本片按明确路径开发备份到开发分支；实际暂存/提交/推送及远端核验见 `closeout-submit/`，最终 dirty 与环境见 `closeout-final/`。提交不记整体验收，不改变总目标 active/未完成。

## 下一步

后继数据库原件已有正向，但运行时准入仍故意拒绝。下一有界任务应把完整 successor lineage、实际目标身份和指定轮次 NORMAL 原件接入同一只读数据库观察，再在新隔离已有 binding/NORMAL 的恢复输入验证真实 API、独立 worker、P5 和文件；不能复用本片已清测试库，也不能把早期无旧 binding 的组合结果拼成后继正向。

实际跨实例正向、进程死亡/pending 边界与完整恢复组合仍须执行；D13 整体、其余入口、OS 拒绝自 halt、D16、D18 实际最终候选及总目标第8节未满足。生产开关、正式签名、原库/现网、main、部署/Release 保持关闭；取消链、多跳、接收者确认/激活和旧 run03 不恢复。
