# E07 受影响组合验收（647）

本页记录 E07 恢复/外置副本范围的一项隔离组合开发验收。647 验证独立检查点恢复、真实 HTTP/MySQL、非空迁移、原件与存储守恒及只读消费者；结果不等同于 E07 L2、D18 L3、第 8 节闸门或总目标完成。

## 输入绑定与范围

- 工作区：`D:/acrylic-pda-outbound`，分支：`codex/development`。
- 执行时输入工作树 HEAD：`8156e99b4db709d81c77a63119b2206319172a07`；远端同 SHA。执行前已将旧看板结论、完整 Git 状态/diff/文件哈希、进程/监听和磁盘事实保全至 `.local/goal-20261016/e07-647-before-report-20261016-0434/`。
- 改动路径 → 影响层 → 必跑清单 → 复用依据：

  | 改动路径 | 影响层 | 必跑清单 | 复用依据 |
  | --- | --- | --- | --- |
  | `server/src/test/java/com/acrylic/outbound/e2averify/E2aIndependentCheckpointIT.java` | 独立检查点恢复、真实 HTTP/MySQL、迁移与身份 | 集成正向路径、权限/事务/幂等、原件恢复、HTTP 回读、非空迁移、认证停机 | SHA-256 `0756e01f505fd947bc246c332c895b4454c6f54c9a7f758ed5c89cb718f5d500` |
  | `server/src/test/java/com/acrylic/outbound/e2averify/E2aTestPorts.java`、`IndependentCheckpointSeed.java` | 隔离数据库/端口/输入种子 | 新库、新端口、输入绑定、停机复核 | SHA-256 分别为 `027788e4a0c69136c9f1f8fb93811de9df76b7924d70c3aef0a3298e4f587ee2`、`c93f2b4ec4dea81b0f676e381647d645a83a61c49a924a70e7dc7b1e8dd97700` |
  | `server/src/test/java/com/acrylic/outbound/inventory/IndependentCheckpointHttp.java`、`IndependentCheckpointScenario.java`、`IndependentCheckpointTarget.java` | HTTP 场景、目标身份和全表/文件事实 | 7 组 HTTP 正向回读、事实守恒、目标绑定 | SHA-256 分别为 `20b5bcf5c623d969f230f27bc1aa1fe6434ddc114fa24025903bbeb55d6b5b53`、`a44f24e43ad269b7b10a6465e9473dec1484952e28ca17a3221d2557827db3d5`、`cdfdea60521d0c12590bbac22d4aa426a3560ea71bee7d65befa065aa29017de` |
  | `tools/e2a-cross-source-verify.ps1` | 运行器、证据封存和隔离身份 | 命令、退出码、数据库/HTTP 停机、监听和磁盘记录 | SHA-256 `e598f52f8f171106b533ada1b1ea0981d941b856639023ccaa31469683c9893c` |

## 647 实际执行

执行命令和身份保存在 `.local/goal-20261016/e07-l2-combination-647/command.txt`：

- 新隔离数据库 `acrylic_e2a_cross_20260925_647`，DB 端口 `19353`，HTTP 端口 `19443`，辅助端口 `19444`，候选端口 `19445`；Java 临时目录在 D 盘 `.local/goal-20261016/e07-l2-combination-647-temp/`。
- `E2aIndependentCheckpointIT`：1 test、0 failures、0 errors、0 skipped，Gradle `integrationTest` 退出码 0；`integration-result.xml`、`integration.log`、`runner.log` 和 `exit.txt` 均保留。
- 真实 HTTP 结果：`association`、`attachment`、`full-candidates`、`initial-inventory`、`physical-inventory`、`physical`、`receive` 共 7 组，全部 `200/OK`。
- 最终与重建事实均为 179 张表、622 行；Flyway 记录 99。`original-binlog.bin` SHA-256 为 `a6c425e62baf996c0b161c50bb014b636bc499a22d6358ff10aa43b33f73aa76`。
- `connected-identity.tsv` 与 `shutdown-identity.tsv` 字节一致，均绑定 `acrylic_e2a_cross_20260925_647`、19353 和 `e2a_cross_20260925_647@127.0.0.1`；认证停机成功。

## 独立只读消费者

`.local/goal-20261016/e07-independent-consumer-649/consumer-result.json` 是最终消费者输出；早先 648 输出及源目录内重复副本均保留，本文只引用 649。

- 消费者 `passed=true`，读取 647 证据目录的 859 项输入；`manifestBefore` 与 `manifestAfter` 双次文件路径、长度和 SHA-256 相同，`inputUnchanged=true`。
- 最终/重建数据库事实为 179 表、622 行，消费者报告 `flywayRows=99`；消费者未连接数据库，也未打开网络连接。
- 消费结果与 647 的 HTTP、身份、数据库和文件守恒事实一致；报告中的 `failures` 为空。

## 失败保全

- 645 和 646 为运行器“Attempt already exists; preserve it and allocate a new run”预检失败，分别保存在 `.local/goal-20261016/e07-l2-combination-645/`、`.local/goal-20261016/e07-l2-combination-646/`（及 646-preflight）。两轮未启动数据库或服务，原命令、日志、退出码和预检 JSON 保留；它们不记作产品失败，也不记作通过。
- 647 的 19353、19443、19444、19445 在结束后均无监听；`runtime-after.json` 记录退出码 0。共享 `mysqld` PID 4632/5440 仅观察，未复用。
- 647 运行期间未覆盖既有源码、证据、数据库或未跟踪改动；D 盘证据和失败实例继续保全。执行后 D 盘可用空间记录在 `runtime-after.json`，C/D 盘余量在接续看板和接管快照中保留。

## 结论与边界

647 是一项通过的 E07 受影响组合开发验收单元。它没有外置介质/真实保管回取、Android/Chrome/原模拟器/恢复全链的组合事实，因此不收口 E07 L2、D14/D15 整体、D18 L3、任何第 8 节闸门或总目标。E06 网页多任务 L1、E05/O03 和既有 E07 资料继续按各自正式报告和输入 SHA 复用；没有新证据的历史失败、暂缓计数和原件继续保留。生产默认、七类生产决定、`localDeletionAllowed=false`、原库/现网、发布和正式签名边界保持关闭。下一步继续 E07 恢复/外置副本的独立组合，之后推进 E08/E09 及剩余 D/O/U 闸门，最终执行 D18 L3 全链。
