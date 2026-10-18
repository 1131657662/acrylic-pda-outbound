# E06/D12 TEMP 仓库恢复组合候选（run743）

本记录登记当前工作树上的全新隔离候选，覆盖 TEMP 产品会话在成功响应丢失后保存
`RESULT_UNKNOWN`、原 ID 查询恢复、非空迁移和独立 Python 消费。它不恢复
recovered-confirmation、换机进度承接、多跳或接收者确认/激活链。

## 范围与输入

- 运行树为 `D:/acrylic-pda-outbound`，分支为 `codex/development`，运行输入 HEAD/远端为
  `a8fad58a768920b1f4bd928e4339461550bbe1e4`；继承改动和接管快照保存在
  `.local/goal-20261018-resume/takeover06/`。
- 隔离实例为 `.local/e2a-cross-20260925-743`，MySQL `19653`、HTTP `19654`、代理
  `19655`，数据库为 `acrylic_e2a_cross_20260925_743`，账号为
  `e2a_cross_20260925_743`。运行器命令和当前代码 SHA 清单见
  `.local/goal-20261018-resume/e06-temp-repository-run743/run743-command.json` 与
  `new-code-sha01.json`。
- 运行命令为本地隔离 runner 的 `-Run 743 -DbPort 19653 -HttpPort 19654
  -DatabaseName acrylic_e2a_cross_20260925_743 -DatabaseUser e2a_cross_20260925_743
  -ArchiveRecovery`；独立消费者命令为 `C:/Python314/python.exe consume743.py
  --out consumer743.json`。

## 实际结果

- Java `E2aTemporaryRepositoryRestore` driver exit `0`，`test-completed.marker` 存在；
  source/restored 两阶段产品 Android 单测 XML 均为 `tests=1, failures=0, errors=0,
  skipped=0`，额外的 Android source XML 也为 `1/0/0/0`。
- 真实隔离 HTTP/MySQL 先以 V34 非空种子启动，再完成全量迁移；恢复快照为 179 个应用表、
  8 个私有文件，dump 为 180 张表和 99 条迁移 INSERT。Room 三阶段各 83 张表、行总数
  `source=73, unknown=71, restored=73`，状态为 `SYNCED -> RESULT_UNKNOWN -> SYNCED`，
  只改变 `temporary_submission`、`temporary_success_response`、
  `temporary_success_evidence`。
- source 代理实际收到两次 GET，第一次丢弃响应、第二次成功；restored 收到一次成功 GET。
  原 ID、payload SHA、RS256 JWS `kid`/回执、响应字节和本机三表差异均由消费者核对。
- 独立 Python 消费者 `consumer743.json` 为 `PASS`，75 项检查全通过；run743 的输入、
  停机身份、端口清零、Android XML、运行器绑定和非空迁移均在报告输入中可追溯。
- Chrome 不适用：本候选没有 Web/UI 产品入口或页面变更；不能把“不适用”计作 Chrome
  业务通过。Android 为产品 JVM 测试，不是原 PDA、原模拟器或现场身份。
- 隔离账号认证停机成功，`connected-identity.tsv` 与 `shutdown-identity.tsv` SHA 相同；
  `19653/19654/19655` 均无监听，未连接原库/现网、未使用正式签名。

## 证据绑定

| 路径 | SHA-256 |
| --- | --- |
| `.local/e2a-cross-20260925-743/integration.log` | `80A5AD79EDEA28FB844F633730D46ED18C07BF1227A3A95BAD6F1011AF5002F4` |
| `.local/e2a-cross-20260925-743/private/archive-snapshot.sql` | `EFC07F26C1BDAD9E7ECE084B9E1EFFC1690282DE151A4064761C284C731E14AB` |
| `.local/e2a-cross-20260925-743/private/archive-snapshot-facts.json` | `A5440E8D21618E1362753776D5E3E97F05848EEC0558E113529DE8C59079B958` |
| `.local/e2a-cross-20260925-743/temp-product-source.xml` | `C2395759A8D8345F287EE57ED8A402072C6436971A8042FF925FD677F9C635CC` |
| `.local/e2a-cross-20260925-743/temp-product-restored.xml` | `C398640BECF7A5CD14D4C77C4BE2D370B66FFE032D5F148BDA15B73838919993` |
| `.local/e2a-cross-20260925-743/temp-proxy-source-requests.json` | `6D0BD7E92CB96473C57A2BF4BC366097B95B56CAE7AA794D84B3C55FEA6CE709` |
| `.local/e2a-cross-20260925-743/temp-proxy-restored-requests.json` | `637601EB2EA47912D04B16E0D39AE6D3A2BB20AED3EBBD2487E7B5D6A387A45D` |
| `.local/goal-20261018-resume/e06-temp-repository-run743/consumer743.json` | `A98EDBB5A97518EC663452DDEFD16C69D49B54000631ACD013709FAF821D352F` |
| `.local/goal-20261018-resume/e06-temp-repository-run743/new-code-sha01.json` | `0AFF0ECBE32183BBE129D259935DE61EA1AA80F66369677B7D5A59F95BCE2A27` |

## 失败与边界

- 消费者首次封装因 `run692-command.json` 残留和一次路径分隔符元数据错误失败；原件
  `.local/goal-20261018-resume/e06-temp-repository-run743/consumer743-binding-failure.json`
  （SHA `7532EF7F9337BE6E21FA39A91205CF4F4C7B151A2ACE169D4494CAA304087AD9`）保留。修正
  绑定后同一 run743 输入的最终消费者为 75/75，通过过程中没有重跑产品或数据库。
- 本候选使用合成账号、开发签名、合成 Room 前驱和隔离数据库；不替代原 PDA、原身份、
  原模拟器、现场回执、外置介质或生产数据库。因此只计 E06/D12 的局部开发组合候选，
不计 E06 整体、D12/D17、E07、D18 L3、统一最终候选或第 8 节闸门。
