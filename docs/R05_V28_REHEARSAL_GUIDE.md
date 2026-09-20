# R05 后端 V28 恢复副本演练与部署准备

日期：2026-09-19。用户明确选择**保持现网不变，只完成演练与文档**。本轮完成候选包、Linux 恢复副本迁移、API 冒烟和 Worker 中断恢复；没有切换公网后台，也没有完成 R05 整体验收。

后续接续已完成[本机V28/PDA归档端到端](R05_ARCHIVE_PDA_E2E_GUIDE.md)：Pixel_6独立包/新库的完整提交与两类归档响应丢失重试、Room重开及文件核对通过。本页Linux143启停复验仍未完成，现网不切换决定保持，两轮证据分别记录。

## 产物与实际环境

### 2026-09-20 统一监控告警处置

用户提供07:00:42的告警后，只读核对主机`lavm-z54ysvgz9n`：两个演练unit均为`failed/exit-code`、`ExecMainStatus=143`、`MainPID=0`，退出时间均为2026-09-19 16:40:31 CST，确认为下文已记录的历史停止标记。现网API及备份timer为active，current和JAR摘要仍与本页记录一致；现网PID本次为2308485，与前轮历史PID不同，本轮未重启现网。

保存原状态后，逐一断言仅这两个unit无进程、退出143且处于failed，执行精确范围的`systemctl reset-failed acrylic-outbound-r05-verify-api.service acrylic-outbound-r05-verify-worker.service`。复核均为inactive/dead、MainPID0，`systemctl --failed`为空；现网API/备份timer保持active，发布目录与JAR摘要保持。未启动演练服务、修改unit、停用监控、触碰数据库或切换公网版本。

证据保存在本机ignored目录`.local/r05-background-resume/monitor-readonly-before.txt`、`monitor-reset-result.txt`、`monitor-readonly-after.txt`。本次解决监控重复报告已停止演练服务的问题；下文143正常退出配置的实际启停复验仍未执行，既有自动审批阻断不计为解除。历史告警消息不会被删除，未手动发送监控通知。

| 项目 | 实际状态 |
| --- | --- |
| 分支与源码 | `codex/development`，基线提交 `963c8bc5c4b540a1d7dad9c1cc6adf61c7fa29ca`，包含未提交源码；不等同于已发布 Git 版本 |
| 后端候选 | 契约 `0.20.0` / Flyway V28；JAR SHA-256 `82efcedcb85e20fea6bfb6a260eb21d7bf1ca82ebdd56bba1a52070bfe203759` |
| 最终部署包 | `outputs/r05-v28/r05-backend-v28-20260919-02.tar.gz`，20个文件；只包含后端、运维脚本、源码摘要与脱敏结果，不含配置、密钥、数据库、Web或APK |
| 压缩包 SHA-256 | `17b9e7aff77c0853326b38f119adab0ed0e9f2880a8da12410bea17403c97cba` |
| manifest SHA-256 | `b82954de70fb977e0f09f748d735806bd7f6bf25bdc12a70a702e17b24e105fa` |
| 服务器候选目录 | `/opt/acrylic-outbound-test/candidates/r05-backend-v28-20260919-02`；仅暂存、校验，没有加入 `releases` 或切换 `current` |
| 公网后台 | 仍为 `0.18.0` / V23，`current` 指向 `releases/r05-sync-20260919-01`，PID2007878，JAR摘要 `7edb592b109055a5301067cef97578e9758734b062a0c6a03d88d270fed249f7` |
| 公网下载 | 仍为 `r05-download-16`，首次安装开放、旧包下限16、兼容标记0.18.0；清单摘要 `bdede672609f486a74267b3efdfc786872c21a3a5d0e65ee9b49d73a0d1d1ab7` |

部署包在本机与 Linux 均完成摘要核验，最终包文件权限已规范为普通文件0644、shell脚本0755，无组/其他用户写权限。`source-sha256.json`记录候选构建时全部后端主源码摘要，打包前逐项复核未变；manifest明确记录工作区未提交状态。没有执行提交、推送、合并、打标签、GitHub发布或生产部署。M71未改动。

## 本轮实际验证

| 范围 | 结果及适用边界 |
| --- | --- |
| 后端重新回归 | `test integrationTest bootJar --rerun-tasks`：32项单元、336项真实MySQL集成，0失败/错误/跳过。最初一次 Verify 全为 UP-TO-DATE，未计作本轮重跑 |
| 备份恢复 | 校验既有 `20260918T201056Z` 备份，恢复到独立数据库 `acrylic_outbound_r05_v28_verify_20260919`；84表计数/校验和匹配，5个私有文件（含测试签名密钥对）还原 |
| V23→V28 | 使用实际候选JAR运行Flyway，V1～23历史校验通过，新增V24～28成功；84张原表的全部原有行保留，仅迁移历史增加5行，新增4表；私有文件字节摘要保持 |
| API冒烟 | 仅监听回环地址18087的恢复副本完成CSRF、原管理员登录、`/api/v1/me`核对，恢复代次1、NORMAL；不输出密码、Cookie或令牌 |
| Worker实际进程 | 独立非Web进程，连接池2、单调度线程；核对无TCP监听，普通sweep任务成功 |
| Worker中断恢复 | 锁住副本提醒表使真实任务处于RUNNING/attempt1，SIGKILL仅终止副本Worker；systemd自动重启，保留原两分钟租约，实际到期及退避后attempt2成功，未伪造/缩短租约 |
| 备份服务状态检查 | 7项通过：正常、备份失败、keep-stopped、无Worker、API已停、全部已停、部分停止失败；使用systemctl边界桩，不操作真实服务 |
| 后端激活脚本检查 | 8项通过：正常顺序、包损坏、链接文件、错误发布ID、错误schema、unit安装失败、API readiness失败、Worker启动失败；只操作临时目录和命令边界桩，不代表现网切换已验收 |
| 最终包检查 | Linux `bash -n`、Python编译、systemd unit校验及20文件摘要/权限通过；最终包与通过上述脚本测试的包正文摘要相同，仅打包权限/发布ID修正 |

恢复副本位于同一测试Linux主机，但使用独立数据库、数据库账号、私有目录和runtime-only服务；服务隔离禁止访问现网配置和私有文件目录。迁移后副本数据库账号已撤回DDL权限，仅保留该副本的SELECT/INSERT/UPDATE/DELETE。没有新公网监听或Nginx变更。

中断任务ID为 `523b5cf7-7541-4885-864f-9ef630d4510e`，Worker进程2155548→2155702，最终SUCCEEDED、attempt2。恢复备份中没有待审超时订单，故本轮证明调度、租约和重试恢复，不证明Linux上的真实业务提醒；30分钟提醒/审批后不提醒/并发幂等由既有3项MySQL业务集成覆盖。

## 收尾发现与未通过范围

1. 首次还原脚本把tar目录成员误当文件，修正为检查 `member.isdir()`；保留已经还原的数据库，核对后续接文件还原，没有清库重来。
2. umask077使私有目录实际为0700，服务账号无法读取隔离配置，首次迁移启动报JDBC URL错误，尚未开始迁移。明确设为0750并加入失败即停后，实际迁移成功。
3. API验证脚本最初错用 `success` 字段，实际约定为 `code: "OK"`；修正后登录及原身份通过。曾误用非公开health路径得到401，改用实际公开CSRF路径；一条远端SQL查询因PowerShell嵌套引号失败，改为Python通过SSH标准输入后通过。另一次端口断言没有兼容 `ss` 的IPv4映射显示，改为核对实际18087行及127.0.0.1后通过。
4. 激活脚本测试桩对无第二参数的 `systemctl daemon-reload` 报unbound variable；修正后8项全过。
5. 压缩包上传尚未结束时提前校验摘要，校验正确拒绝，未解包；等待上传完成后通过。第一版包沿用了Windows可写mode，Linux unit检查提示world-writable；最终使用新ID尾号02并规范tar权限，尾号01保留作为失败记录，不能用于后续部署。
6. **实际停止副本API/Worker时，Java已完成正常关闭并退出143，但systemd将该退出码标成failed。** 两个MainPID均为0，副本进程已停止；日志显示Tomcat/Hikari关闭完成，没有超时强杀。源码unit补上 `SuccessExitStatus=143`，最终包systemd语法检查通过；修改runtime-only副本unit并重启复验的工具调用被自动审批拒绝，仅返回 `blocked by policy`，未重试或绕过。该修正尚无实际启停复验，不能把它写成运行验收通过。

临时服务名为 `acrylic-outbound-r05-verify-api.service`、`acrylic-outbound-r05-verify-worker.service`，未设开机启用，当前均无进程但保留failed/143状态供查证。独立数据库、`/var/lib/acrylic-outbound-r05-v28-verify`及证据保留，不删除、不覆盖。现网API及备份timer仍在运行。

## 部署支持改动

- `backup.sh`通过`service_checkpoint.sh`记录最初运行的API/Worker，先停Worker再停API；正常或备份失败后仅恢复原来运行的服务，部分停止失败也恢复原状态；成功`--keep-stopped`保留停写状态。备份增加带校验和的`active-services.txt`。
- `migrate.sh`对新后端包先验摘要，备份后使用共用维护锁执行迁移，明确禁用Worker调度。旧完整部署包保持原检查方式。
- `activate_backend.sh`要求准确V28、有效发布ID与完整产物摘要，先停写再原子切换；API就绪后才启Worker。启动阶段失败停止两类写入，不自动降数据库或回切旧JAR。该脚本不安装Nginx配置或覆盖独立Web/下载目录。
- 独立Worker unit设置256MiB堆、384MiB MemoryHigh、512MiB MemoryMax、50%CPU、128任务上限及原API同等系统隔离；复用现有运行账号，不取得迁移账号权限。API/Worker新unit都增加143正常退出码，但运行复验仍待补。

## 以后获准切换时的操作与恢复边界

以下是准备说明，**本轮没有执行，也不构成现网切换授权**。用户现已明确要求现网保持不变。

1. 先补新unit在隔离副本上的启停复验，并完成所需PDA与归档接口联调。重新检查实际现网schema、current、APK/下载清单、资源和待审批次；本轮旧备份仅用于演练，不能作为以后切换时的新鲜恢复点。
2. 对照上述压缩包摘要核验尾号02包，再提取到同名全新 `releases` 目录；顶层目录使用root/运行服务组0750，JAR和脚本须可被预定账号读取，部署目录不可由运行账号写入。先执行包内 `verify_backend_release.py`，检查manifest的真实未提交源码边界。
3. 调用新包 `migrate.sh <绝对release目录>` 会生成包含DB、文件、配置、旧发布和原服务状态的完整新备份，并暂停两类写入。备份失败不得继续；成功后保留停写，核对SHA256SUMS及备份路径。实际迁移的schema/data结果与文件摘要须留证，再进入激活。
4. 只使用新包 `activate_backend.sh <绝对release目录>`；不使用旧 `activate.sh`，后者会改变Nginx/Web根目录。核对API登录/原身份、恢复代次/模式、Worker队列、只读与写入冒烟、归档接口、备份timer，以及公开下载页/签名清单仍符合真实兼容范围。
5. 失败时先保持API与Worker停止并保全失败DB/文件/日志。V24～28虽在本轮为新增表/索引，仍不能直接删表、倒退Flyway或默认旧JAR可安全运行在V28上。迁移尚未发生时可核对原服务状态后恢复原版本；迁移已经发生时先判断兼容性并优先修复前进。
6. 如确需完整恢复，必须把DB、私有文件、配置和旧JAR作为同一恢复点，先在另一个隔离库/目录实际恢复并核对，再按明确授权处置现网。若新版本已接受写入，不能用旧快照静默丢弃这些记录；须先保存差异并按R09恢复代次/设备对账约束处理。本轮没有演练V28写入后整库回退，不能宣称R09已完成。

公网兼容标记不是随契约号自动升级的显示字段。真实原始code15仅接受0.17/0.18，当前code16更新来源、下限16及不可变APK继续保留；后台后续升级不得顺带修改公开清单或重签旧包。扩大旧版覆盖范围仍需新发布ID/更高versionCode及真实15/16验证，见[升级记录](PDA_UPGRADE_15_16_GUIDE.md)。

## 证据与后续

本机证据目录 `.local/r05-v28-stage/`，服务器证据目录 `/opt/acrylic-outbound-test/deployment-evidence/r05-v28-20260919-01/`。关键文件：`fresh-test-results.json`、`server-fresh-verification.log`、`restore-result.json`、`migration-before.json`、`migration-result.json`、`api-smoke-result.json`、`worker-recovery-result.json`、`backend-activation-tests-final.log`、`service-checkpoint-tests-final.log`、`final-environment-check.json`、`initial-stop-143.log`、`rehearsal-stopped-status.txt`、`service-stop-policy-block.json`、`package-receipt.json`及`package-final-verification.log`。服务器JSON/log已回收至本机`remote-evidence/`；含数据指纹/私有文件摘要的证据留在ignored目录，不提交。

下一具体动作是补齐隔离unit启停复验，再继续0.20/V28归档与PDA联调准备；现网切换遵守用户本轮“不切换”决定。App内15→16完整升级链路仍受独立HTTPS/自动审批条件阻挡，保留原断点。第二台PDA、真机/异地、长时/断电与真实待审现场尚待补；整单事实90天清理依赖R09备份恢复及引用核对。R05仍在开发。
