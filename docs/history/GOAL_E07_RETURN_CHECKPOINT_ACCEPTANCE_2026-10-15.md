# E07 更正前恢复截点不退库与原 binlog 重建开发验收（2026-10-15）

624 真实 HTTP/MySQL 通过：只恢复更正前的非空前缀时，缺失物理退回/独立审批事实不能退库；从同一原 dump 重放原 ROW binlog 到终点后，五份原签回执逐字节重放且不重复处理。独立 Python、三库全表/dump/storage 保全与认证停机通过。仅补 D14 的这一开发缺口，不计完整 E07 L2、D14/D15 整体、D18 L3 或总目标完成。

初始 TEMP 确认、身份、物理凭据和开发签名为合成隔离输入；后续物理退回与独立退库实际走 HTTP。它们不能代替原 PDA、原身份或现场事实。

## 输入与改动映射

固定工作树 `D:/acrylic-pda-outbound`、分支 `codex/development`，运行 HEAD/远端均为 `39bfb924b3731986ff3decc1c7996ecb7bdfe74f`。唯一写者为本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b`；清理聊天实际 complete-released/idle、持有范围和在途任务为空，无子代理。源码、库、看板和索引写权单一。

证据根 `.local/goal-20261015/e07-return-checkpoint01/`，最小设计/必跑映射先记于 `design.md`。`input623.json`、`input624.json`、`review624.json` 及各自 status/patch/worktrees 绑定全部 tracked/untracked/hash/长度；624 输入共 5,283 项，独立消费时全部逐字节不变，清单 SHA256 `bb5ce269a7628443908e611c83567efc271cecc550762f1aacc0945fae4ffa9d`。继承 Android/UI/文档/工具及私有请求不纳入本次提交。替换当前区前保存 `board-before-624-report.md`；写权/精确进程/监听/磁盘另见 `runtime624-review.json`。

| 改动或复用路径 | 影响层 → 必跑清单与 SHA256 |
| --- | --- |
| 新增 `server/src/test/java/com/acrylic/outbound/e2averify/E2aReturnedStockCheckpointIT.java` | 非空升级/实际 dump 导入/原 binlog/HTTP → 当次完整 IT；`600377c0311f3d6d41ca857fc01624e09415cf0134c3e8f63fd0beb3f1d901cf` |
| 新增 `server/src/test/java/com/acrylic/outbound/inventory/ReturnedStockCheckpointScenario.java` | 物理退回/独立审批、前缀拒绝/原回执/default-off → 当次真实 HTTP；`a1d7d406ae7c15cb05c50785788aacd115f260dce510e44544718f5ef2c32cb8` |
| 新增 `server/src/test/java/com/acrylic/outbound/inventory/ReturnedStockCheckpointEvidence.java` | 每次拒绝/重放全表原件 → 除两列会话访问时间外全部精确不变；`a1fd3403640bb0d862e88f890d344e38be569909a12f3e75efae69ae21f64831` |
| 复用 `server/src/test/java/com/acrylic/outbound/e2averify/E2aPointInTimeCheckpoint.java` | 空目标守卫、dump/位置、精确终点 → 当次调用；`da5f256b21fba78c1e03e021bd609be5e6980355eaf9dd6cb759016be7fd5472` |
| 复用 `server/src/test/java/com/acrylic/outbound/e2averify/E2aPointInTimeCommands.java` | 实际 MySQL CLI/dump/CRC 重放/文件复制 → 当次调用；`126c299014369bafd9b96930f4101af3de5ed474211dbb1b4b579d150243d0d8` |
| 复用 `server/src/test/java/com/acrylic/outbound/inventory/CorrectionAccountingHttpFixture.java` | 已扣账合成初始事实 → 当次生成并明示限制；`239f26d6393db8688e9f4833e7a00885d8893c3ac9a33e8eb80f96f006dfbe27` |
| `.local/.../consume624.py`、`consume624_artifacts.py` | 独立原字节/RSA/引用、实际存储/角色/原失败保全消费 → 两者 PASS；源 SHA256 分别 `332b36e2c2d8836901ad9fb341808c6425a4c3c7298e0aaf5783f375888602c2`、`be59458dbadb2b6616b932ec8da7d29584068b59a6dc1796e7abe73a83c087c7` |
| 本报告、两个索引及看板 | 文档追溯 → 引用/差异/生成块及当前区上限检查 |

本批没有修改产品 Java、Android、Web、迁移、契约或开关。[622 完整退库 HTTP 组合](GOAL_E07_RETURNED_STOCK_HTTP_ACCEPTANCE_2026-10-15.md)的同键并发、异文、身份/跨仓、末 SQL 回滚及新周期出库按其输入 SHA 复用；[618 更正五检查点](GOAL_E07_CORRECTION_CHECKPOINT_ACCEPTANCE_2026-10-15.md)与 P07 原签还原按原报告范围复用。不能用这些局部证据免除最终候选 Android、Chrome、Python、非空迁移、原模拟器与恢复 L3。

## 实际业务、恢复与消费者

```text
gradlew.bat -p server compileTestJava --no-daemon --console=plain
powershell -NoProfile -File .local/goal-20261015/e07-return-checkpoint01/run623.ps1
python .local/goal-20261015/e07-return-checkpoint01/preserve623.py 623
powershell -NoProfile -File .local/goal-20261015/e07-return-checkpoint01/run624.ps1
python .local/goal-20261015/e07-return-checkpoint01/preserve624.py 624
python .local/goal-20261015/e07-return-checkpoint01/consume624.py
python .local/goal-20261015/e07-return-checkpoint01/consume624_artifacts.py
```

首个编译退出 0。623 全表比较失败后保全；修正夹具后 624 runner/test 均退出 0，原 JUnit `TEST-com.acrylic.outbound.e2averify.E2aReturnedStockCheckpointIT.xml` 为 1/0/0/0，Gradle 成功、业务测试 52.64 秒。原 XML/log/exit 和开始/完成 marker 均保留。

1. **源正向**：合成已扣账成功出库后，在更正前保存非空 dump/存储/开发钥副本和原 binlog 截点。真实 HTTP 物理退回 CREATE/APPROVE 后，库存仍 DEDUCTED、周期递增；独立退库 DISCOVER/SUBMIT/APPROVE 后才转 NOT_DEDUCTED。五份原命令/原签回执保存，申请人与主管不同。
2. **仅前缀**：核空 `_missing` schema 的库/用户/端口/datadir/log_bin 后，实际导入同一更正前 dump；全部表、schema、迁移历史与存储快照等于原前缀。实际 HTTP 退库 context 返回 409 `CORRECTION_LEDGER_CONFLICT`，后续物理批准/退库批准及原批准查询均 404 `CORRECTION_DOCUMENT_NOT_FOUND`。拒绝前后 179 业务表全列原件保存，库存保持 DEDUCTED、周期 1、退库释放 0，更正/重开周期/退库表为空。
3. **完整终点**：另一空 `_restore` schema 从同一 dump 重放原 ROW binlog，校验 dump/hash/CRC/位置；179 业务表、schema、迁移历史、触发器和私有文件精确达到源终点。五份原键 HTTP 回执逐字节等于原件，异文返回 409；库存 NOT_DEDUCTED、周期 2、独立退库释放 1。原 dispatch/库存账务不变，没有重复更正/退库。
4. **默认关闭**：在重建实例重启为 corrections/attachments default-off，具 SUPERVISOR 的原批准人及无 SUPERVISOR 的原申请人 POST 均 404，context 也 404；控制器 bean 不存在。原库存/退库/全表业务事实不变。角色差异由独立消费者从实际角色表核对。

请求仍更新既有 `SPRING_SESSION` 的 LAST_ACCESS_TIME/EXPIRY_TIME 两列；测试和 Python 只对这两列做显式比较豁免，所有会话行、身份和 `SPRING_SESSION_ATTRIBUTES` 均保持。原 before/after 仍完整保存，不把会话活动说成零物理写入，也不放宽业务守恒。

Python 独立消费 5,283 输入、三库 540 表原十六进制列/行数/排序哈希，五份数据库 RSA 更正回执及五份原命令、五份重放原字节、独立退库引用/主体分离、三组拒绝/重放全表原件和 HTTP 状态。原 ID/正文/回执与 dispatch/库存 ledger 保持；所有 capability（含 localDeletionAllowed）为 false。`consumer624-result.json` PASS，SHA256 `a1f8c0d8e777e32bfff6a385bb92fcb0e0856693ec005632eb411c544f5e3593`。

补充消费者逐件核对源、原前缀、终点副本和重建存储各 6 文件的实际长度/hash；开发钥副本与原件逐字节一致。应用仍使用同一隔离实例原开发签名路径，所以本批不另称独立历史钥还原正向；该范围沿 P07/618 原证据。独立工具算法由作者运行，不等于独立人员审查。

## 原失败与停机保全

623 首次快照取在登录后、首次业务 POST 请求 CSRF 之前；after 仅新增一个 `HttpSessionCsrfTokenRepository.CSRF_TOKEN` 属性及两列 session 时间，全部业务表未变。先完整保全源库和前缀库 360 表/dump/storage 并认证停机，再将每个登录客户端的 `/auth/csrf` 准备移至快照前；没有排除会话属性或改产品。该根因累计 1 次保留，`source623-before-csrf/`、原源码 hash、XML 失败指纹、before/after、诊断及全部失败库原件保留。624 实际复验通过，补充消费者确认 623 的 598 文件长度/hash 未变。

两个新编号均先核 33 个 worktree、目标路径、活动重型任务和 19330/19434 零监听，再统一分配；不复用旧库。624 schema `acrylic_e2a_cross_20260925_624`、MySQL 19330、HTTP 19434、UUID `038cd312-c8a8-11f1-9250-d8bbc13a2b36`；623 UUID `7e9c8b85-c8a7-11f1-b1cf-d8bbc13a2b36`。预计增长上限 4 GiB；624 运行前 C/D 可用 `83396263936` / `64470581248` bytes，收尾 `83183923200` / `64115523584` bytes。TEMP/TMP/Java 临时目录仅子进程设为 D 盘 `.local/t623`、`.local/t624`，凭据只在忽略目录。

624 三库 540 表 dump/storage/JDBC 守恒，809 文件长度/hash 保全，认证停机且 19330/19434 零监听；无项目重型任务，共享 MySQL 只观察。关键 SHA256：

- 原检查点 dump：`4e37a8328eb2653c5f638e0ee8fe0eaab2266e3632361f4378e600fc39764d59`。
- 原 ROW binlog：`22cb18200e95c9c6b86ce34b7f1eb91691ce147f577099a522f303f727cff73f`。
- 624 全库保全 dump：`ee3170a520f3963752c78269c05dc8c404524c12edeecb11787dd0638881fbd5`。
- 623 失败全库 dump：`ad4b2ef78e31e79d0afc4176333f36c9e74c07122b2e53b83513a6df3200d049`。

两实例 datadir/dump/binlog、原日志/XML/HTTP/命令/签名/私有身份继续保留；不接原库/现网、不合 main、不发布部署、不用正式签名。七类生产能力、TEMP 生产归档边界与 localDeletionAllowed=false 保持关闭。

下一步定位 O03 中转及 O04 独立来源跨截点恢复的直接未验组合，仅补受影响范围；其后继续 E07 其余组合、E08/E09 和剩余 D/O/U。外置介质/真实保管回取、设备/原身份/长时/生产事项按各自条件保留；开发缺口不转入现场项。总目标 active，第8节全部满足前不标 complete。
