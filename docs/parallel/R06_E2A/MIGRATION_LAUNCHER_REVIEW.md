# R06 E2a migration launcher 独立只读复审

复审对象：`codex/r06-e2a-migration` 固定提交 `c5462e9924c9db18700976f5f259d1eb649fb576`。

复审范围仅包括 `E2aMigrationRunner`、`E2aMysqlLauncher`、`E2aMigrationEnvironment` 的初始化、启动、监听归属、停机、路径/密钥和阶段证据处理。未运行构建、数据库、迁移或 seed；本文不是动态通过结论。

## 动态启动前必须修复

1. **P0：异常停机路径可跳过安全清理，留下本次实例或无法形成阻断证据。**
   `E2aMigrationRunner.java:36-41` 的 `finally` 先调用 `requireOwnedListener(server)`，再调用 `shutdown`。当服务器存活但监听归属探针超时、输出不匹配或监听被其他进程占用时，归属检查直接抛异常，后续清理、`stopped` 标记和端口复核均不会执行。触发条件是启动后任何监听归属异常；后果是本次创建的实例可能继续占用 19329，且没有可靠的清理证据。应把异常保留、清理失败记录和 marker 写入放入嵌套结构；只有已证明监听 PID、命令行和 datadir 属于本次 `Process` 时，才允许认证 `mysqladmin shutdown`。若监听非本次 PID 或归属未证明，必须阻断并明确报告，不得按端口触碰外部实例。

2. **P1：阶段失败证据可能被二次 I/O 异常覆盖，且不能保证写入。**
   `E2aMigrationRunner.java:33-35` 直接向 marker 追加失败类型；若 marker 不可写，原始迁移/连接异常会被 `Files.writeString` 的异常替换。`E2aMigrationRunner.java:98-103` 使用 `CREATE_NEW`，残留 started marker 会在真正启动前直接失败，因此不会产生本轮失败阶段记录。`E2aMigrationRunner.java:39` 只有 shutdown 成功返回时才写 `stopped`。触发条件是残留 marker、权限/磁盘错误或停机失败；后果是阶段失败证据不完整或误导。应保留原始异常、以独立 best-effort 方式记录失败/停止阶段，并把 marker 生命周期按 run 唯一 ID 隔离。

3. **P1：V35 资源校验没有验证实际 Flyway classpath 或内容 hash。**
   `E2aMigrationRunner.java:18` 和 `81-96` 只扫描硬编码工作树 `server/src/main/resources/db/migration`，接受任意非空 `V35__*.sql`。`runMigration` 的 `Flyway.configure()`（`67-77`）未显式绑定迁移位置、版本资源或预期 SHA。触发条件是工作树资源与 test runtime classpath 不一致、存在同名替代文件或 V35 内容漂移；后果是前置检查通过但 Flyway 实际加载的资源不是审查对象。应从实际运行 classpath 解析唯一 V35 资源，并校验固定内容 hash/版本后再允许动态启动。当前固定树没有可供本文替代的动态验证证据。

## 已解决/已具备的护栏

- `E2aMigrationEnvironment.requireExact`（`26-55`）在 SQL、Flyway 或进程启动前校验固定工作树、19329、schema/account、迁移 profile、禁止 reset/repair，以及 datadir/storage/signing 路径。
- `E2aMysqlLauncher.start`（`18-39`）拒绝旧 datadir，固定 `mysqld` 工具、回环地址、19329、禁用 MySQLX，并记录初始化日志；`startServer`（`42-52`）要求初始化产物存在。
- `E2aMysqlLauncher.verifyProcessDataDir`（`88-107`）核对 PID、datadir、mysqld 命令行和端口；`E2aMigrationRunner.requireOwnedListener`（`56-65`）核对 19329 监听归属。
- `E2aMysqlLauncher.shutdown`（`55-79`）使用临时私有凭据文件执行认证停机，并等待进程退出和端口释放；`privateArtifact`（`127-158`）拒绝越界/链接工件并限制 ACL。
- `E2aMigrationEnvironment.verifyConnected`（`74-82`）核对 datadir、schema、端口和 `CURRENT_USER()`，降低连错实例风险。

## 其他边界

- 本复审不重复 V35 迁移资源/API 未整合的既有结论；上述 classpath/hash 项仍是允许动态启动前的独立缺口。
- 未执行 MySQL、Flyway、负向矩阵、seed、HTTP、部署或生产放行；没有“迁移通过”结论。
- foundation fixture 生成的少量 `e2a.csv` 仅作为合成导入输入并保存真实 SHA-256；它不属于来源证据文件保留断言范围。
