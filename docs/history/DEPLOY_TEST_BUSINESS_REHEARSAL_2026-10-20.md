# test 真实业务演练与数据预检记录

日期：2026-10-20。范围：仅 `outbound-test.nbsmzwl.cn`；不涉及生产、原库、现网、main、Release、正式签名或代码部署。`localDeletionAllowed=false`。

## 授权与执行边界

用户授权在 test 上先确认消费者、标记维护、备份完整 MySQL 与私有文件、用正常认证完成一次真实业务演练、导出演练副本、恢复 test 原数据，并在独立可回退环境使用演练副本做升级/还原/API-worker 验证。演练步骤必须是创建订单→领取→扫码→冻结→上传→确认；禁止 fixture、SQL 灌入或批量脚本生成业务事实。

先前只读准备核查发现在途消费者，曾按用户选择暂缓。该选择随后被用户明确更正为“PDA03 无人使用，可以暂停同步完成维护”，本轮按更正继续执行；旧暂缓结论只作历史，不再是执行指令。恢复后订单须与快照一致，不清空设备数据、不撤销设备授权。

结果：**维护已结束，test 原数据恢复核对通过；业务演练未执行，真实数据预检仍阻断。** 模拟器准备与启动命令被自动审批策略在执行前拒绝，返回 `blocked by policy`。未换包装重试、未改走合成 API 身份，也未生成演练订单。按用户“任何一步失败即记录阻断”的要求，停止后续演练，执行已授权恢复和收尾。

## 消费者核查

核查时间为 2026-10-20 08:52–09:00 UTC。SSH 使用既有 root test 入口、`BatchMode=yes`、严格主机校验和 10 秒连接超时；所有远端脚本、stdout/stderr、退出码及 SHA 保存在 `.local/test-business-rehearsal-20261020/`。

- test 主机自报 `lavm-z54ysvgz9n`；当前发布目录和软件身份沿此前只读来源报告，未下载配置、密钥或私有载荷。
- API、worker 均 active/running；备份 timer active/waiting，备份 oneshot inactive/dead 属正常状态。
- 只读核查显示 1 个 Web 会话，其中 0 个在最近检查窗口活跃；PDA03 当时持续心跳，`device` 状态为 ACTIVE。有效会话与活跃操作不混称。
- `outbound_order` 存在今天创建并仍为 `ASSIGNED` 的订单；`order_assignment` 有未完成领取记录。该记录证明存在在途业务消费者，不能进入停写窗口。
- test 近期审计包含 PDA token 轮换、订单创建/发布/领取、下载确认和提交完成事件；这些事件均只作消费者判断，未作为本次演练来源证明。
- 现有 `inventory_import` 来源包括 `R02-synthetic-stock.txt`，此前报告已确认该批次为合成来源；本轮没有把它当作真实演练数据。

## 逐步结果

以下执行时间为 2026-10-20 北京时间（UTC+8）；原始回执保留 UTC。只连接明确授权的 test schema 与本次新建的隔离还原库，不连接生产或原库。

| 顺序 | 实际执行与结果 | 边界或未验 |
| --- | --- | --- |
| 1 维护前快照 | 17:15:01 保存 PDA03 全部领取、订单状态、设备版本、最后联机时间与会话元数据；最后联机 17:11:29.224 | 无 token/密码输出；本机设备数据未读取或修改。原始订单号仅在 ignored 本地证据内 |
| 2 完整备份 | 17:16:53 开始，以既有维护锁停本 test worker/API/timer；MySQL dump、私有目录归档、逐文件 SHA、发布 manifest 均校验成功 | 仅备份 test 库与私有文件；不下载配置/私钥、不备份其他项目 |
| 3 暂停同步 | 备份完成后，仅 test Nginx 加维护注释、外部请求 503、保留回环维护访问；没有禁用设备、清会话或清设备数据 | 首轮即时 curl 断言失败，原 stderr 保全；下一次实际远端及本地消费者均观察 503。旧失败不改写为通过 |
| 恢复前置证明 | 新建独立库 `acrylic_outbound_biz_restore_20261020`，从完整 dump 还原；101 表、129333 行逐行集合 SHA 与停写 test 相同 | 这是备份可恢复证明，不是固定候选升级、演练或 API/worker 业务验证；隔离库保留 |
| 4 正常业务演练 | 未执行；准备隔离模拟器和启动既有 emulator 被自动审批拒绝，命令未执行 | 未创建 AVD、未启动模拟器；未登录、未创建订单、未扫码/冻结/上传/确认 |
| 5 演练数据导出 | 未执行 | 没有演练产生的数据和来源证明；步骤 2 的维护前备份不能冒充本项 |
| 6 恢复 test | 17:23:38 停写并先保全维护期间 checkpoint，随后加载维护前 dump，17:24:10 完成 | 没有演练业务写入；仅会话/清理调度表发生维护相关变化。私有文件原本未变，逐 SHA 相同，无需覆盖 |
| 7 恢复一致核对 | 全 101 表、129333 行集合 SHA 与备份前完全相同；218 个私有文件 SHA/清单一致 | 恢复前维护 checkpoint、日志、隔离库均保留，无删除；不能将恢复后的正常调度变化当作恢复前不一致 |
| 8 恢复同步 | 17:25:38 API/worker active/running、timer active/waiting；原 Nginx 字节/摘要与发布目录一致，公网 CSRF 200；17:26:03 PDA03 全部订单/领取、设备状态与版本一致。17:29:31 复核已观察到 17:26:33.848 的恢复后设备心跳，全部订单/领取仍一致 | 服务器访问与实际设备联机均恢复；没有设备界面操作或新扫码，不将心跳核对写成新业务流程通过 |
| 9 固定候选真实库验证 | 未执行，阻断 | 没有步骤 5 的可信演练输入；冻结候选 `8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd` 不变，未用其他 SHA 或普通测试拼接通过 |

没有部署、修改产品源码、迁移 test、修改生产开关或正式签名。已有演练前订单和设备数据保持，原库/生产、main、Release 及真实 test 代码部署继续关闭。

## 备份来源与摘要

来源：用户明确授权维护的 test 主机 `lavm-z54ysvgz9n`、schema `acrylic_outbound_test`；发布身份沿 [test 来源核查](DEPLOY_TEST_DATA_SOURCE_AUDIT_2026-10-20.md)，本次实际发布目录未变化。下列备份是维护前恢复点，不是演练后数据副本。

服务器保全根：`/opt/acrylic-outbound-test/deployment-evidence/business-20261020-01/`，root 私有权限。

| 文件 | SHA-256 |
| --- | --- |
| `before/database.sql.gz` | `a079a9afd66d61655c2ca64b4d789ae03aff87cc996889d556b7ea7b256b55e6` |
| `before/private-files.tar.gz` | `b2944be3e73b363eb0e2eace7096d7cac3cda0006f3644e09be0775926cdc9b9` |
| `before/private-files.sha256` | `4f01d99bff211fa7f81e7f5fdb8f08a24346e1188a39ac1c697cc9df52e23da9` |

完整 `SHA256SUMS`、备份完成 UTC、manifest、逐表集合摘要、还原日志、维护 checkpoint 均保存在服务器证据根。归档未提交 Git；本次没有把真实数据/本机配置/密钥放入报告或 Git。文件数量以停写备份与恢复核对的 218 为准；此前只读探测的 217 是较早时间点，不能用它替代一致截点。

## 证据

- `.local/test-business-rehearsal-20261020/start.json`：授权、工作树快照及本轮边界。
- `readiness.py.stdout`、`consumers.py.stdout`、`activity.py.stdout`、`provenance-metadata.py.stdout`：只读主机、服务、会话、设备、订单/领取、审计和既有来源汇总；对应 `.receipt.json` 保存脚本及输出 SHA。
- `board-before.md`、`index-before.md`、`inherited-wip.patch`：写前文档和继承 WIP 保全。
- `pda03-snapshot01/02-backup-boundary/03-restored/04-resumed/05-final.py.stdout` 及回执：维护前、备份边界、恢复后、解除同步隔离后及再次联机快照；`device-restore-comparison.json`、`device-resumed-comparison.json`、`device-final-comparison.json`：逐字段一致与先未观察到/后实际观察到设备联机的时间边界。
- `backup01.sh.*`、`maintenance01.py.*`、`maintenance-check01.py.*`、`restore-proof01.py.*`：备份、维护首次断言失败/后续实际核对、独立还原证明；所有原失败保留。
- `rollback01.py.*`、`resume01.py.*`：test 原数据实际恢复、全表/文件一致核对、恢复服务和原 Nginx；`maintenance-outcome01.json` 保存用户更正、审批拒绝与不绕过声明。
- `before-maintenance-docs01.zip`、`evidence-manifest01.json`：文档修改前原字节、全部脏文件保护基准与本地证据摘要。

## 下一步

本次维护窗口已经结束，不继续演练、不再次停服。先解决“演练客户端启动被审批策略拒绝”的执行阻断并取得新的可执行依据；以后重开维护窗口须重新核消费者与 PDA03 快照、重新备份，不能复用本次旧截点覆盖期间新增数据。按完整认证与真实产品路径演练、导出、还原，再让同一固定候选消费该输入；失败/未验继续单列，不能把此次备份还原局部通过写成真实库闸门或部署放行。
