# RecoverySelectUnionSqlIT 真实 JDBC 有界验收

日期：2026-10-19。目标只覆盖 `RecoverySelectUnionSqlIT` 的隔离 MySQL/JDBC 行为，不改变产品 API、迁移或生产策略。

## 输入与执行

- 分支 `codex/development`，输入 HEAD `9c3dfc931535e32a4c3c6c2da82ead8645e6e109`；Gradle 退出码 0，约 50 秒完成。
- 输入清单含 2,484 个源码、测试、构建脚本与资源文件，SHA-256 `dad2475fa6833a5250e699e0008ebc90703851230f0ac7822d3e186f4db2fbdc`。测试后于 13:30 UTC 复核零漂移；清理前再对数据库、构建、tmp 与 project-cache 逐文件重哈希，四个根目录共 3,665 个文件零差异。
- 运行参数来自 `execution.json`：

```powershell
.\gradlew.bat -p server `
  -I "D:\acrylic-pda-outbound\.local\goal-20261019-resume\recovery-select-union-jdbc01\isolate.gradle" `
  integrationTest --tests com.acrylic.outbound.recovery.RecoverySelectUnionSqlIT `
  --offline --no-daemon --console=plain --max-workers=1 `
  --project-cache-dir "D:\acrylic-pda-outbound\.local\goal-20261019-resume\recovery-select-union-jdbc01\project-cache" `
  "-Pr08FontFile=D:\acrylic-pda-outbound\report-rendering\build\generated\resources\NotoSansCJKsc-Regular.otf"
```

- 隔离 MySQL 8.0 监听 `127.0.0.1:19681`，UUID `149997e9-cbbe-11f1-a541-d8bbc13a2b36`，schema `r09_union_20261019`。JDBC 与数据库为真实隔离实例；effect authority 明确是 `EffectRealFixture` 的 MODEL fixture，不是现场身份或可信恢复授权。

## 结果

- JUnit `10 tests, 0 failures, 0 errors, 0 skipped`。单条与 prepared `UNION ALL`、fence 后重复执行拒绝、嵌套 UNION、64 分支重复行、65 分支在 driver 前拒绝，以及五种不支持语法在 driver 前拒绝均通过。
- 结果断言覆盖行值与行数、fixture 表内容守恒、delegate 调用次数和 authority/fence 拒绝。10 个测试用例各建独立 fixture 表；合成表与行不是业务数据。
- 输入 source manifest SHA 为 `dad2475fa6833a5250e699e0008ebc90703851230f0ac7822d3e186f4db2fbdc`；`execution.json` SHA 为 `e60a206fb88314c52a728eb1dcfdbfa3bc0fc73cba8c618e6debf7ddacf4061f`。JUnit XML 为 4,148 字节，SHA `181487dcb422bb40252e22aff2e94b15f33a824c1e6867183182215f78e374c8`；独立保留副本在 `.local/goal-20261019-resume/recovery-select-union-jdbc01/junit-original/`。Gradle 日志 SHA `33850099c7a4a9052c0d4a4b13bb35f15be8397c6333f3abcebac0af846089c7`。
- 隔离数据库最终事实表 SHA `91497c1271ddcf8530007b8a25ffd41cebafc88ab7b48cff3f0ee06cdde6a74e`；身份查询 SHA `25ea0c3d20cf34682e86224e68af258b3e4b028f44ab8c4a735cab1474b2ab52`；一次性 SQL dump SHA `110ea6145adc55ceabc70d89fb668f8ea094dff57f13e72eaf52e2154af3ad90`。
- `mysqladmin` 认证停机退出 0，停机回执记录目标进程与监听清零。13:37 UTC 新鲜检查也未发现目标 MySQL 进程或 19681/19682 监听；系统 MySQL80 的 3306/33060 与共享 ADB 未操作。C/D/E 当时可用空间分别为 `74351943680 / 44464386048 / 195634053120` 字节。

## 限制与清理

- 这是解析器/执行门在真实隔离 JDBC driver 与 MySQL 上的有界 L1，不证明可信恢复授权、真实 HTTP/Android、D13/D14/D17、L2/L3 或第 8 节总闸门。模型权限 fixture 不替代现场原身份。旧失败证据未改写，本次未重跑旧用例。
- 已保留输入和清理前文件 SHA 清单 `closeout01/cleanup-before.json`（1,326,342 字节，SHA `db45b77fcdb56477b8c7bcea5b0116a8165a5e614472550656260a735f7a9971`），以及清理摘要 `closeout01/cleanup-summary.md`。隔离 datadir、一次性 dump、构建/tmp/project-cache 均是精确目标，fresh 进程/监听和路径/哈希检查通过。
- 对上述隔离数据库、任务构建输出、tmp、project-cache 和 dump 的递归清理命令于 13:38 UTC 被执行策略拒绝，返回 `blocked by policy`，在启动 shell 前被拒绝；没有退出码，也没有删除任何文件。拒绝后未改用其他删除入口；目录、dump 与缓存仍保留，D 盘空间未因清理变化。测试结果与原 XML/日志均保全。
- fail-closed 范围不变：不恢复换机进度承接、多跳、接收者确认/激活链；不启动旧 run03；不连接原库/现网；不使用正式签名；不部署、Release 或合并 `main`；`localDeletionAllowed=false`。
