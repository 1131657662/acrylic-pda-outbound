# D13 导出生命周期状态列表有界验证（2026-10-20）

本报告记录导出完成后的列表查询补证：生产调度器完成 XLSX/PDF 导出后，按 `status=SUCCEEDED` 查询同一订单的导出列表，列表中的任务 ID、状态、可下载标志和 SHA-256 必须与下载视图一致。该断言只补在既有 `E2aAdmittedWorkerExportIT` 测试中；生产代码、HTTP 契约、迁移、权限和默认开关未改，已取消的换机承接、多跳或接收者确认/激活链未恢复。结果仅是 D13 有界 L1 测试证据，不代表 D13 整体、D18、最终候选或第 8 节完成。

## 输入、改动与命令

- 固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`；输入 HEAD 与 `origin/codex/development` 均为 `2320adf3e9f11b8bfe308b1926c909a58c8dc1df`。输入清单为 `.local/goal-20261020-resume/d13-export-lifecycle01/input-manifest.json`，SHA-256 `8CE63176E3E75801046F1C53ED5BF178D2D18E10C01C4D59750941FAB75F1E19`。
- 唯一源码改动是 `server/src/test/java/com/acrylic/outbound/e2averify/E2aAdmittedWorkerExportIT.java`，当前 SHA-256 `00A90E76BAEE6F3FA88A8AAF89D17C3FAC363E586FEA2352E56CBAF488D24315`。新增断言在下载成功后执行 `GET .../exports?status=SUCCEEDED`，确认同一任务 ID、`SUCCEEDED`、`downloadable=true` 及下载视图 SHA 一致；未新增生产入口。
- 实跑输入以 run 目录的 `inputs.json`（SHA `C416CEAFA39CE2D9E94B23611B0BC84E30E6076202BAD8FA07C9BC1A8B951F7E`）、`tracked.patch`（SHA `9ADF3A5CBACF0DB1182D980F8DA5F9F78F958965EF5F35481B7B23656A7C86BB`）、`status.txt`（SHA `3BEE9FF84C7EF3067BD3FEA6407F1DB93F8090ED88B966AC52F43949FE8D4F25`）及 `git.json` 为准。文档更新前5903个输入文件逐SHA均一致，35条非本批继承脏路径逐SHA保持；编辑前清单中拟新增的另一个测试类未创建。
- 运行器命令为 `python docs/parallel/R09/tests/run_d13_admitted_worker.py --run 918 --port 19348 --http 19458`，命令 JSON SHA `45C706DFF923AAFD079A5C6CF0F9B474E64B198A352603075C3E8D99EC2518A8`。测试内部 Gradle 命令和退出回执保存在 `.local/e2a-cross-20260925-918/gradle-command.json`、`gradle.stdout`、`shutdown-command.json`、`mysql-stopped.json`。
- renderer/Chromium 只为测试输入准备：`gradlew.bat -p report-rendering installDist installChromium --offline --no-daemon --console=plain`，结果为 305 个文件、逻辑长度 820144740 bytes；命令、结果、日志 SHA 分别为 `D9F68A0A242D180DB94511912A88E71F4A3093FBBCC86DC46B042FBA83D5A1D6`、`9D6E5B2BDCE5D87DD0E492AA6C198DC3CC5310F96E28A08272624590846711CD`、`E9E14C4E47E26868CFDD02C6F0D4DF4BEF44E6DFAEB59E86C44072DA3ED4EC84`。

## 实际结果

- 隔离 MySQL 为 `127.0.0.1:19348`，datadir `.local/e2a-cross-20260925-918/data/`，schema `acrylic_e2a_cross_20260925_918`，账号 `e2a_cross_20260925_918@127.0.0.1`；HTTP 端口为 `19458`。运行结束后 `mysqladmin shutdown` exit 0，MySQL 进程退出且 19348/19458 均无监听；未连接系统 3306 或共享 13316。
- `E2aAdmittedWorkerExportIT` 实际执行 1 项，0 failure、0 error、0 skipped，Gradle exit 0。JUnit XML SHA-256 `E93C386A4E06D07DF6637B695186081EAADE047CAAA1A7C744FA14403C9CF903`，`test-result.json` SHA-256 `93F401209E5D0FA306DDE320120FDFDD6A6C2C5A6266DDA18C2B561F3128B5AA`；运行器结果 SHA-256 `6292547971EE3AB06B0C051F2785B756841D480BC84E9478871DA4723AD78A9F`。
- XLSX 下载 14006 bytes，SHA-256 `F67CD565DC38D4FE8215507099BA6185ECAC4AEE9A4F5F59B2D14BD76F550706`；PDF 下载 354536 bytes，SHA-256 `CE196D306903C3E0893371C756D20D42D197306F749A9E498F26301E45AE4F04`。P5 阶段的 XLSX/PDF 与默认关闭样本也保留在 run 目录；它们是合成隔离输入，不是业务交付 APK 或真实资料。
- 以 `status=SUCCEEDED` 查询后，列表返回刚下载的任务 ID，状态为 `SUCCEEDED`、`downloadable=true`，列表 SHA 与下载视图 SHA 相同。测试同时保留 worker admission、authority 变化拒绝、默认关闭和 P5 相关断言；没有把下载成功扩展为生产放行或删除授权。
- 同助手的 Python 离线消费者核对66组原始 HTTP 元数据/正文SHA，五组完成任务分别来自默认关闭XLSX、admitted XLSX/PDF及P5 XLSX/PDF；五个成功列表均精确包含对应下载任务。消费者SHA `d3452157d58806269bc81cfdcd391c45b9a2e81b239f231609f592496eaad471`。脱敏结构审计 `http-receipts-offline-audit.json` SHA `c691accefc700fd4138e8292ba6620ad0ba6c3ca3c1ffacc1fc5cea796742416`：GET 46次，POST登录5次、导出请求15次（10个200含原键重放、5个409）。新增断言仅GET；整次IT仍含合成确认、登录、任务创建和生成，不声称全程只读。原CSRF/登录正文保留在私有证据目录，审计摘要不复制token或身份值。
- Web与worker为同一测试JVM中分别启动的Spring应用上下文；renderer另有子进程，P5后继另由既有夹具检查。不能将本轮写成独立部署的API/worker双进程验收，也没有新增版面、XLSX逐格或PDF可见布局验收。
- 历史2026-10-03报告也使用过编号918并指向同名目录。此次运行器源码以 `assert not attempt.exists()` 和新建独占文件拒绝覆盖已有目录；本次文件和XML绑定2026-10-20输入，只引用上述哈希，不用编号合并历史结论。旧918原件迁移/清理去向本次未查明，旧报告保留，本次不宣称其原件仍在该路径。

## 保全、清理与限制

- 原始 HTTP JSON、快照、下载文件、JUnit XML、测试日志、输入清单和身份/停机回执保留在 `.local/e2a-cross-20260925-918/`；逐文件输入 SHA、清理前路径/SHA、进程和监听快照见 `.local/goal-20261020-resume/d13-export-lifecycle01/cleanup-before.json` 与 `run918-file-hashes.txt`。
- 实际清理655文件、逻辑长度1043113269 bytes：仅停机 run 的 `data/`、两份binlog文件、`tmp/`、任务 `tmp-it/` / `tmp-renderer/`，以及 `report-rendering/build/browsers/` / `install/`。保存初步候选清单后再收窄范围；最终执行计划SHA `C0687F2CE7F2F3A722017CB060E2DC8CD72534D81F2F158BB84D83150C1913C8`，命令SHA `F5683EEEA419E9FE7CA0EB6A2BCF7E8212AEA748BDD53241B2AA262AD43458E6`，成功回执 `cleanup-after.json` SHA `827CC7A30310628EB8FFE5C16B5E1531669427E434E50256088387D75EACEF2C`。D可用空间31051177984→32098521088 bytes，卷观测增加1047343104 bytes（约0.98 GiB）；逻辑长度与卷增量分别记录，exit 0，目标均已移除。
- 保留已校验字体及其生成资源、依赖锁、所有原始HTTP/XML/下载/签名/身份和逻辑dump；合成私钥留在受限本地证据目录，不入Git。renderer安装副本/浏览器可由已存命令、源码和依赖重建，浏览器重取需要网络；没有为证明可重建而重新下载。两项Java后台进程为Gradle/Kotlin daemon，命令行未指向清理目标，未停共享daemon；用户Chrome、ADB及共享/系统MySQL未操作。原资料、Keystore、唯一证据、最终APK、未提交源码及继承改动继续保留。
- 本轮只验证导出生命周期列表的测试断言；没有验证真实人员/设备、原库/现网、非合成输入、真实部署、Release、正式签名、Android UI 或第 8 节组合闸门。`localDeletionAllowed=false` 及所有生产 fail-closed 开关保持不变。

## 接续

下一切片应从看板当前区选择仍未覆盖的独立 D13 入口，重新核对实际环境后再运行；不要重跑本次已通过的 918，也不要把本报告与其他合成 L1 拼成统一候选。总目标保持 active、未完成。
