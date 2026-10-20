# test 数据来源核查与导出闸门

日期：2026-10-20。用户指定先确认 test 数据来源，再执行获授权的副本导出、必要脱敏、来源记录及隔离验证。此授权不含部署、生产或原库操作。版本事实见看板生成事实块。

## 核查结果

- 固定候选仍为 `8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd`；不拼接主开发树或其他 SHA 的通过结果。
- 已完成 test 只读元数据核查，SSH 严格校验既有主机密钥；未查询 SQL、未新建 dump、未复制私有文件、未改服务。
- 2026-10-20 08:30:06 UTC 实核：test `outbound-test.nbsmzwl.cn` 对应既有 SSH 目标 `114.67.203.242`，主机自报 `lavm-z54ysvgz9n`；`current` 指向 `/opt/acrylic-outbound-test/releases/pda-orderflow-20260923-01`。manifest 的 businessGitHead/deploymentGitHead 均为 `23ae8c492acf47e89875852d2573392fcf379f80`，revisionOf 为 `v34-386739c-r08name-01`。仅是其声明的软件来源字段，不证明业务数据来源，也不以旧对话中的 SHA 代替实核。
- API PID 2699473、worker PID 2699475 的启动参数均指向该发布目录 `server.jar`；本轮只取 JAR 路径，不回传完整启动参数。磁盘 JAR SHA-256 `f5c5bfe90ed5b17570a4da13e610a807b3d0f17de304afaf1414d98f33307672` 与 manifest 一致；manifest SHA-256 `0108b7c9b35ce29c70d90e327dd34205c9d498597fbd50f637f9813cc90a7c63`。没有重新构建历史源码来独立证明 manifest 的提交声明。
- API、worker 和备份 timer 均为已加载状态，服务分别运行/运行/timer waiting；服务器已有 39 个带时间目录的备份，覆盖 2026-09-18 至 2026-10-19。最新备份的 `SHA256SUMS` 记录了数据库压缩包、私有文件归档、manifest 等成员摘要；本轮未重新读取这些载荷。
- 初始导入脚本 `.local/r02-browser.mjs` 的 seed() 明确生成条码，上传 `R02-synthetic-stock.txt`，发布原因 `R02 synthetic test inventory`，订单备注 `R02 synthetic test only`；其实际执行记录 `.local/r02-browser-seed-final.log` 及 [R02 部署报告](../R02_TEST_DEPLOYMENT.md)明确合成库存/订单、真实设备操作。只能认定这一批合成来源，不能推断后续全部业务行都为合成，亦不能把真人扫码升格为非合成库存来源。
- 部署证据含 `replica-original.pdf/.xlsx`，其既有脚本和内容核验是订单导出产物，不构成生产数据库同步证明。`r05-v28` 的恢复记录仅把来源追到 test 既有备份，不能补出业务上游来源；其他演练 seed/fixture 指示不单独作为运行库所有数据的分类依据。
- 结论：**本次没有发现可证明属于真实业务演练或脱敏生产同步的 test 数据副本。** 备份完整性和服务运行状态不能替代来源证明，不能把合成 test 数据升级为可信真实数据。
- 证据目录：`D:/acrylic-pda-outbound/.local/test-source-acquisition-20261020/`。四次只读 SSH 调用均 exit 0、stderr 为空；其中来源核查首轮因文件名匹配表达式转义错误误报备份数 0，修正后复核为 39，首轮输出/回执单独保留，不计正确结果。首轮错用不带 test- 的服务名也已以实际单元名复核，不把 not-found 记作服务故障。
- 导出、脱敏、独立真实数据库升级、备份还原、真实 API/worker 验证均**阻断/未执行**；整体预检仍不放行。

## 下一步

恢复条件是取得 test 后续业务导入/演练或脱敏生产同步的可信来源证明，并按前次用户要求列明候选、等待其确认；不要求用户自行操作导出。本次五步流程授权用于来源核实及随后符合条件的副本链，不扩大为未知来源全库下载。条件未满足时保持阻断，不拿合成夹具替代。

若材料获确认，再执行一致性导出、必要脱敏和哈希登记；不会直接复制包含配置/签名私钥的旧完整归档。来源库保持只读，升级/还原/API-worker只在独立可回退副本进行；结果绑定同一个冻结候选与同一数据输入。test 实际部署仍须另行明确授权。生产开关、正式签名、原库/现网、main、Release 关闭，`localDeletionAllowed=false`。

## 服务器既有候选与时间

时间采用 UTC；备份目录名为命名时间，mtime 为文件属性，不冒充独立导出日志中的起止时间。本轮未执行或下载任何 dump。

| 候选 | 路径及时间 | 来源证据与判断 |
| --- | --- | --- |
| test 最近既有备份 | `/opt/backups/acrylic-outbound-test/20261019T201304Z/database.sql.gz`，12,361,952 字节，mtime 2026-10-19 20:13:06.791203 UTC | 同目录 `SHA256SUMS`、manifest、database-facts、active-services、release；有备份链，无非合成业务上游证明，不选用 |
| 同一截点私有文件及配置归档 | 同目录 `private-files-and-config.tar.gz`，2,013,359 字节，mtime 2026-10-19 20:13:08.118238 UTC | 与数据库备份同目录；历史备份方案还包含凭据/签名材料，未下载或展开，不视为可直接搬运的私有目录 |
| test 私有目录 | `/var/lib/acrylic-outbound-test/files` | 只确认目录存在，未列业务文件正文、未下载；不能单独构成来源可证完整库 |
| 历史恢复源 | `/opt/backups/acrylic-outbound-test/20260918T201056Z` | `deployment-evidence/r05-v28-20260919-01/restore-result.json` 记此来源；仅可证明恢复自 test 备份，非上游业务出处 |

最近备份 `SHA256SUMS` 声明数据库压缩包为 `c1b1c4e30c183fac08560367c82ea32e1c90acc9c04b19e77bcae1f56834b5b7`，归档为 `86d8aaafc8f9132bad35e9bb43586206e405cc2995512b449d70658e2ad76d57`。这是**原清单声明**，本轮未读载荷重新验算，不能写成备份校验通过或新导出哈希。

## 证据定位

- 元数据核查结果：[remote-metadata.stdout.json](../../.local/test-source-acquisition-20261020/remote-metadata.stdout.json)。
- 来源复核结果：[remote-provenance.stdout.json](../../.local/test-source-acquisition-20261020/remote-provenance.stdout.json)。
- 运行软件身份：[runtime-identity.stdout.json](../../.local/test-source-acquisition-20261020/runtime-identity.stdout.json)。
- SSH 回执：同目录 `remote-provenance-receipt.json`、`runtime-identity-receipt.json`、`remote-metadata.exit`、`remote-provenance.exit`、stderr 日志；匹配错误首轮为 `remote-provenance-attempt01*`。
- 脚本、原始输出、初始看板/索引字节及保全清单只存 ignored `.local/`，原备份/私有目录/失败日志/XML/隔离库未删除。仅对本次文档做引用、边界、敏感内容与 Git 增量审查，不运行或冒称业务测试。
