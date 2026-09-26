# R09 V34 来源证据隔离快照恢复记录

日期：2026-09-24。作者树 `D:/acrylic-pda-worktrees/r09-v33-recovery`，分支 `codex/r09-v33-recovery`，固定已提交业务基线 `23ae8c492acf47e89875852d2573392fcf379f80`（V34）。验证时本批尚未提交或推送；最终提交/远端状态以 Git 和交接回报为准。未合并、发布或部署；写入仅限 `docs/parallel/R09/`。`r09-ops` 原树与既有 V33 演练/失败证据未改变。

## 范围

- `seed-v34` 只接受 V34 专用新 lab、空 source 库和与固定提交一致的 server/report-rendering 字节；10 个既有合成 integrationTest 场景生成普通/TEMP、归档与审计事实，再由 R09 私有工具写入两份显式非设备证据文件、一个集合/明细及一个 `PARTIAL` 行。未修改业务 fixture 或生产 writer，R06 未提交 V35/V36、STOP/PAGE 不作稳定输入。
- `setup-v34` 使用专用回环 19434/19435（source/restore）和预留 18434；创建带随机后缀的全新 schema/datadir/账号配置。加载时校验用途、端口、schema 与目录后缀，数据库操作前再核对端口/datadir/当前账号。先协调重型槽并核对监听/写者；不得复用既有 lab。
- 备份冻结专用 source，检查旧业务及 V34 对象文件 SHA-256/长度、STOP 对象关联、集合明细数量和验证行→集合摘要引用；完整快照含表、私有文件、配置。空 restore 校验归档并比较表事实、结构、规范化数据摘要和文件树。故障入口仅在已验证 restore 上可逆注入，最终再比全表与全部文件。

## 作者验证及失败

| 阶段 | 结果及原始证据 |
| --- | --- |
| 离线 | 专用 venv（PyMySQL 1.1.2、cryptography 48.0.0）最终 88 项/0 失败；`compileall` 和 `git diff --check` 通过。普通 Python 全量首次因未安装 PyMySQL 导入失败；V34 首轮 14 项中 3 个测试目录层级断言失败，修正后通过。未把首轮失败写成通过。 |
| 首次隔离库 | `.local/r09/20260924T060520Z-v34-001c89/` 种子成功，备份被 `AUDIT_SOURCE_SHAPE_MISMATCH` 阻断；原 `backups/20260924T060706Z-6f0c25.partial/`、库、日志保留并鉴权停机。原因是旧审计查询只在 V32/V33 选择 TEMP 原来源两列；已补 V34 查询、来源关联及 2 项离线回归。没有在该库重试。 |
| 新实验 | `.local/r09/20260924T061019Z-v34-0b63cc/` 的 `source/fixture-v34-1790230251010649500.log` 及独立留存 `source/integration-results/` 的 5 份 XML：Gradle integrationTest 10 项，0 失败/错误/跳过，bootJar 成功；JAR SHA-256 `c7b3c64d2dd672fe101d546a466ab6a07a5799e24ef0b982cbeca1bc29415e94`。 |
| 备份与空库恢复 | `backups/20260924T061131Z-b23c73/` 归档预校验 PASS，`SHA256SUMS` SHA-256 `202c7df353877dd5f71db1a62cccd0bcb3fd6615a78b9e3a0e03065711e289ab`。恢复 PASS：101 表、239 组外键、16 处既有文件引用、7 份签名终态；V34 新增 2 个合成对象文件、1 集合、1 明细、1 `PARTIAL` 行。13.419 秒只是本机小合成样本。`restore/recovered/verification.json` SHA-256 `e08528a66eaad9851e1cc52fa2c1a20df4cc1103198eef2defdabb58`。 |
| 负例与归还 | `restore/v34-fault-drill.json` 8 项 PASS：对象摘要、长度、文件缺失/损坏、集合明细数、验证行摘要引用、未知 schema、全表/文件原值恢复；SHA-256 `32953b4160dd9884d975949abd52a22e7d3519f2f61cc552f8d126f67b0e9552`，`localDeletionAllowed=false`。两库经专用账号鉴权停机，19434/19435/18434/19330 无监听；共享 3306/PID 5448 未操作，重型槽已通知 R06 释放。 |

原凭据、私钥、库、备份与日志留在被忽略的 `.local/r09/`；未上传 Git。字体是本机缓存的校验副本 SHA-256 `2c76254f6fc379fddfce0a7e84fb5385bb135d3e399294f6eeb6680d0365b74b`。以上为**作者**验证；无独立消费者、组合实库/HTTP、APK、模拟器、真机、现网或部署验收。完成演练后只收紧了新 lab 配置与目录后缀的加载检查，并跑离线测试；该收紧版未声称再次跑过实库。

## 复现（只建新隔离库）

先取得重型槽、核对监听和活动写者，准备 MySQL 8.0 CLI、JDK 21、Python ≥3.12 与 `config/requirements.txt` 依赖。将经 SHA 校验的字体**复制到新 lab** 后设置 `R09_FONT_FILE`。`<新lab>` 和 `<完整备份>` 仅指本次新建私有路径，不能指向上述旧实验；不要使用共享 3306、R06 19330、原库或现网。

```powershell
python docs/parallel/R09/ops/cli.py setup-v34 --mysql-bin 'C:\Program Files\MySQL\MySQL Server 8.0\bin'
$env:R09_FONT_FILE = '<新lab>/NotoSansCJKsc-Regular.otf'
python docs/parallel/R09/ops/cli.py seed-v34 --lab '<新lab>/lab.json'
python docs/parallel/R09/ops/cli.py backup --lab '<新lab>/lab.json'
python docs/parallel/R09/ops/cli.py verify --backup '<完整备份>'
python docs/parallel/R09/ops/cli.py restore --lab '<新lab>/lab.json' --backup '<完整备份>'
python docs/parallel/R09/ops/cli.py v34-faults --lab '<新lab>/lab.json'
python docs/parallel/R09/ops/cli.py stop --lab '<新lab>/lab.json'
```

文件摘要与引用检查**不计算** V34 协议 manifest/setHash、原始记录、设备签名或 `known_facts_sha256` 的可信语义。表名为 `source_evidence_verification` 的行只核对保留引用，不等于验证其结论。服务端共享写屏障、API、Worker、后续迁移、R07 账务、库外权威切代及截点外事实追补仍是接口缺口。不得据快照解除隔离、开放整单清理或认定完整灾后重建；`localDeletionAllowed=false`。
