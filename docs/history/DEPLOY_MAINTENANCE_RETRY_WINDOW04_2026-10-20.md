# window04 本地完整验证与备份前停止

日期：2026-10-20。原始回执使用 UTC；不以文件修改时间冒充精确执行时间。

## 结论

**第4次尝试停止，真实数据闸门阻断，不放行、不部署。** 本机独立目录完成 LF 备份脚本 bash -n 和最小输入完整 dry-run，二者 exit 0，出现 BACKUP_COMPLETE_WRITERS_STOPPED，归档和七个摘要校验成功。然而随后本助手新增的本地证据读取/暂存准备脚本把 ASCII/UTF-8 日志硬编码为 UTF-16，发生 UnicodeDecodeError、exit 1。失败发生于 test 备份开始前。

这是本助手脚本错误，不是 test、M71 或 Windows 权限失败。用户明确规定“备份开始前或备份过程中再次失败，不再重试”，因此没有修正后继续，没有运行第4次远端备份、暂停同步、写围栏、探测或演练。按用户指定的闸门分类记为 **C 档现场条件阻断**，同时保留实际技术根因为本地证据脚本；分类不代表已证明真实客户端路径永久不可用。

未生成可信演练后输入，真实数据库升级、备份还原及相应 API/worker 验证仍未验。固定候选另有 Android 编译失败、Linux Java 运行未验和十九项跳过，不能写“除真实数据库链外其余全部通过”。

## 输入与写权

- 主树 D:/acrylic-pda-outbound，codex/development，输入 HEAD 1de2ccac26cdb00b64b70f2d63143575ec42895d。
- 固定候选 8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd；不拼主开发新增 WIP 或其他 SHA 的通过。
- 私有证据根 .local/deploy-real-client-20261020/window04/。input-manifest.json、inherited-wip-before.patch、git-status-before.txt 保全输入及继承改动；不改产品代码，不暂存或提交其他开发聊天 WIP。
- 主树 HEAD、共享看板和历史总索引写权仍属另一活动开发聊天；仅创建本专项报告、私有断点/索引与待归并增量，不抢写。收口时该聊天在普通服务端回归，不占本次维护。
- 既有 M71/PDA03 独占及 admin/01/02 真实身份授权仍记录；test 部署没有授权。

## 本地验证与新失败

1. 从 window03 已修正 LF 脚本复制新窗口，限定原 test 目标。新备份源 SHA-256：bed0d577a2ce8c960291ca29d437e04f4767268e664454f07d55cd76a4e5fd4c，CRLF 数为 0。原失败源没有改写。
2. 本机 Ubuntu WSL Bash 执行 bash -n，exit 0。Linux 用户态仅用于本地脚本机制验证，没有重放或变更模拟器策略。
3. dry-run/path-substitutions.json 把绝对目录映射到本地独立 sandbox，仅替代外部边界 hostname、systemctl、mysqldump、mysql。没有网络或真实数据库连接；最小 SQL 和文件明确为合成输入，只验证机制。
4. 实际执行 Bash、flock、gzip、tar、sha256sum 及 facts 逻辑，完整走到备份结束；七项摘要通过，gzip 可读，tar 含最小文件。原 stdout 171 字节，SHA-256：84c5652269200df6085ff6634fc4491219ab914529379e05ebe337872e1e8797。完整输出、空 stderr、两项退出码及替身调用明细保留。
5. prepare_stage04.py 第15行以 utf-16 读取 stdout，实际原字节为 ASCII/有效 UTF-8、无 UTF-16 BOM，exit 1。原错误为 UnicodeDecodeError: 'utf-16-le' codec can't decode byte 0x0a in position 170: truncated data。本助手未检查实际编码即硬编码，是确定根因。
6. 错误发生在生成 local-validation.json、stage04.py 或调用任何 SSH 维护命令之前；组合命令检查退出码后停止，随后 readiness/activity 也未执行。失败源另存 prepare_stage04.failed-original.py，SHA-256：aa5a91f7409966b50118cbb6b4fe06dc519ecc41de5c2104e1d0fc5c87b784ed。
7. 事后仅只读解码与哈希复核，见 local-diagnosis.json，不是重跑暂存或备份。未来最小修法为读取原字节后显式按 UTF-8 解码，本轮未应用、未重试。失败精确时刻未持久化；failure-tool-receipt-transcript.json 仅为工具回执摘录，不冒充原始 stderr 或补造微秒时间。

另有本地准备和文档保存的工具执行器 JavaScript 序列化语法错误，在文件写入前被拒；这些不是 shell、SSH 或维护执行。纠正 transport 后仅继续本地保存，不计业务通过，工具回执保留在会话。

## test 与设备收口

失败后仅只读核对授权 test。closeout-readonly.py.receipt.json：开始 2026-10-20T12:46:20.213310+00:00，完成 12:46:21.236568+00:00，exit 0；远端观察 12:46:20.899938 UTC。

- 远端 business-20261020-04 目录不存在，没有本轮暂存、备份或维护标记。
- API、worker、backup timer 均 active，公网 CSRF GET 返回 200。该 GET 仅核可用性，不证明数据库逐行或字节无正常后台变化。
- Nginx 无维护规则，配置 SHA-256 与历史原件一致：a48ff1d79163a3495f768dce8224ef8bd3b9caaf6162542039d18bee5adba463；release 链接保持原目标。
- 该时间段日志仅正常后台任务，无本轮 stop/start/reload。本轮无 MySQL 连接、dump、恢复或订单操作。
- M71 ADB 在线，http_proxy 为 null，adb reverse 列表为空；未安装、登记、登录、代理、清空或操作业务。未启动模拟器。
- PDA03 同步从未暂停，未改变设备数据/订单，未进入维护无需恢复。**不将无需恢复写成恢复验证通过，也不声称本窗口订单逐项核同。**

## 证据缺口和其他预检项

**首次快照正文已丢失，完整性受损。** 这是 window03 同名采集覆盖的历史缺口，本轮未用后快照或工具哈希补写正文。第4次在新快照前停止，没有本轮维护前后订单比较。新采集器增加不可覆盖、失败退出传播和超时部分输出保存，但没有完成真实维护验证。

十九项均来自固定候选，计通过 0，详见[逐项分类](DEPLOY_FIXED_CANDIDATE_SKIPPED_TESTS_2026-10-20.md)：

| 类别 | 数量 | 条件 |
| --- | --- | --- |
| 环境 | 1 | SKU_PARITY_OUTPUT 未配置 |
| 环境 | 1 | Windows 符号链接异常后 assumption 跳过；XML 无具体 OS 根因 |
| 原件条件未启用 | 8 | E06_REVIEW_ORIGINALS 未配置 |
| 归档条件未启用 | 9 | E06_CLOSURE_ARCHIVE 未配置 |

原 XML/日志及已有隔离库保持，不用本地替身、维护前备份或历史 APK 替代可信数据及固定候选验收。[固定候选复验](DEPLOY_FIXED_CANDIDATE_RECHECK_2026-10-20.md)与[真实客户端评估](DEPLOY_REAL_CLIENT_PATH_ASSESSMENT_2026-10-20.md)的失败/未验继续成立。

本轮失败源、原 dry-run 输出、归档及工具回执摘录保留，未清理或覆盖证据。evidence-index.json 登记哈希，current-status.md 为专项断点，shared-doc-update-pending.md 为共享看板/总索引待归并内容。共享文档和 Git 保存未执行，不声称已提交推送或已更新共享看板。

## 后续与边界

本轮及依赖它的真实库链停止，不再开维护窗口。独立检查仅核对既有固定候选包字节完整性和未验项清单，不重跑已通过业务测试、不改候选、不放宽闸门；收口证据另见 independent-package-audit.json。重新评估维护需明确新授权和新依据，当前不请求重复授权。

没有部署、Release、main 合并、正式签名或生产开关变更；不碰生产、原库、现网，localDeletionAllowed=false。本轮机制验证与文档治理不计 D/O/U 进度。
