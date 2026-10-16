# D13 恢复 worker、文件与导出 L1 开发备份

## 范围与输入

本单元补 D13 恢复跨入口的 worker、文件落盘和 XLSX 导出方向。它只证明一批新的隔离输入从真实 HTTP 创建 PENDING，经 SQL/文件恢复、独立 worker JVM、renderer 和 HTTP 下载完成；不证明 D13 整体、错根/错库/水位组合、D18 L3、生产恢复或原 PDA 现场事实。固定树为 `D:/acrylic-pda-outbound`，分支为 `codex/development`。接手输入快照为 `.local/goal-20261017-resume/input52.json`，SHA-256 `9fe9fa3930daca5646156a4986509faa52434f206cb812009df2d64b5468de1e`。

本批直接影响路径及 SHA-256：

| 路径 | 影响层与必跑 | SHA-256 |
| --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRestoredWorkerConfig.java` | 恢复 worker 的显式 run/attempt/schema/用户/端口绑定 | `a612ee84520e1aa71dc25f5c4a696ce88273db75c1ac95eb130dee7f1ea86a87` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRestoredWorkerProcess.java` | 独立 JVM worker 启动、退出与 HTTP/文件边界 | `9f1240e42a52d87cb4e67f95e7022ff02b823bba6010df3498492aa27176d6ac` |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aRestoredWorkerRestore.java` | 新隔离实例恢复 schema、权限和旧 definer 兼容夹具 | `1666f0d2ba63a3e4e4936f4a508683748056cc45bc9b4c8590f5a563e7463070` |
| `tools/r09-restored-worker-launch.py` | 记录 worker 命令与退出结果的独立启动器 | `22def84757447301ee6d3e45488bab131a1550a7a1efee0c811a0fcebb4e` |
| `tools/e2a-cross-source-verify.ps1` | 新 attempt、端口、datadir 和报告绑定 | `f764cb320c2cf843f913031428c1333a986b0f6fd437d45e808f4253dae10171` |

### 设计决定

- 新批次的 run、attempt、schema、数据库用户、MySQL/HTTP/辅助端口和 datadir 全部由环境显式绑定；默认值继续兼容旧的只读补核。
- 旧 667 只作为封存 dump/storage 输入，未连接或复用旧数据库。恢复测试在新实例创建 `_restore` schema、`SET_USER_ID` 权限并锁定旧 definer 用户，生产默认开关和权限不变。
- 浏览器/renderer 类路径使用主树 `report-rendering/build/browsers`；证据、失败库、datadir、日志、XLSX 均保存在 `.local/goal-20261017-resume`。

## 定向验证与结果

1. `server:testClasses` 通过。日志 `.local/goal-20261017-resume/d13-workerfileexport01-build/testClasses.log`，SHA-256 `04a3c0046df42588a15bfb2d1fc844431a0487ec4a1b41857b3615dad27e180d`。
2. 新隔离 run 684 使用 MySQL `19384`、HTTP `19471`、辅助/候选端口 `19472/19473`，schema `acrylic_d13_worker_20261017_684`，用户 `d13_worker_684`。真实 HTTP 创建 PENDING；SQL 与文件恢复后由独立 worker JVM 执行，再经 HTTP 下载 XLSX。
3. `consumer684-result.json` 报告 179 张表中 175 张未变；允许变化仅 `background_job`、`operation_event`、`spring_session`、`spring_session_attributes`，行数 `229 -> 235`，created attempts `0`，replayed attempts `1`，source manifest 未变，连接/停机身份一致，监听已停止，`passed=true`。证据 SHA-256 为 `b18b8eaf0ab0c91644dca7e13ef256f98b286f493fc91d41d8ca53d90cc30b43`。
4. XLSX 有 7 个 sheet、2 条原始明细、601500 克、0 公式，保留前导零代码 `003`；文件 `.local/goal-20261017-resume/d13-workerfileexport04/restored-worker.xlsx` 13694 bytes，SHA-256 `12c96188d5a12d1d1be352601691f1974146750accea94d6b299117c2bb3ab14`。
5. 独立 Python 消费者退出码为 0；Java `--verify-recorded` 只读取已记录文件，退出码为 0，明确没有数据库连接或业务重跑。`recorded-java-result.json` SHA-256 `442f00ea66bb61cbe048ca2e0e4206783ff4757ed09c1b769edda291f4b4f61`。
6. `source-manifest-before.json` 与 `source-manifest-after.json` 字节相同，均 SHA-256 `96be5feaaa5ef4a548452776f63ec791f335d1e7a1c709f182905e9ee3e256ec`；`shutdown-identity.tsv` SHA-256 `1cee416c601ec9156f1f9455d7042431475af255f8b5360440a79251159f5d2c`，19384/19471–19473 停机后零监听。

## 失败轮与保全

- run 681 (`d13-workerfileexport01`) 在外层预创建 `attempt/tmp` 后触发 `Attempt already exists` 守卫；未启动业务数据库或监听。
- run 682 (`d13-workerfileexport02`) 启动器未创建 `attempt/tmp`，Spring Boot 无法创建 Tomcat 临时目录；业务路径未执行，MySQL 已停机。
- run 683 (`d13-workerfileexport03`) 在恢复后 worker claim 阶段遇 MySQL 1449，原因是旧 view/trigger definer `e2a_cross_20260925_667@127.0.0.1` 不存在；业务 PENDING/恢复已发生，XLSX 未完成。随后加入锁定 definer 用户与 grants，run 684 才通过。

三轮的 `runner-guard-failure.json`、`runner-tempdir-failure.json`、`definer-failure.json`、日志、datadir、dump 和停机身份原件均保留，不复用失败数据库，不清零失败计数。

## 结论与未覆盖

本单元按 L1 记为“worker/file/export 有界开发备份”：直接受影响的编译、真实 HTTP、隔离 MySQL、SQL/文件恢复、独立 JVM、renderer/XLSX、Python 消费和只读记录补核均有当次证据。它不计 D13 L2、D13 整体、D18 L3，也不替代 Android、Chrome、非空迁移、原模拟器、真实身份、错根/错库/水位、并发反例或生产恢复。生产默认关闭，`localDeletionAllowed=false`，原库/现网、发布和部署边界不变。

下一步执行 D13 受影响组合（含错根/错库/水位和适用并发/回滚反例），再推进 D14–D18、E06–E09、O/U 及最终候选 L3；既有失败和现场缺口继续单列。
