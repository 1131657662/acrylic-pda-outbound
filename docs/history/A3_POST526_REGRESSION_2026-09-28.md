# A3避开526后的分批回归（2026-09-28）

## 范围与执行方式

用户将526改挂[疑似缺陷待专项](A3_526_LOCK_PENDING_SCOPE_2026-09-28.md)，授权继续47类，2–3轮、每批12–15类；避开条码同步，8类R08已有专用入口验收不重复。新失败只分类记录；条码同类或明确真bug停报。本次不深入526，不修业务、测试、仓库工具、契约或配置，不开启生产能力，不实施R09。

主树D:/acrylic-pda-outbound、codex/development，接续输入c7cf378；待专项文档3648629及归档路径恢复6261905先行推送。归档路径曾与既有文件重名，已恢复旧归档原文，并将本次快照另存独立路径；没有继续覆盖历史。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

实际执行使用tools/e2a-batch-it.ps1，每类一个新attempt、独立空库和storage。普通类用-NonE2a；身份锁类经本机忽略目录适配器，以新datadir提供测试原有守卫要求的端口/库名/账号和performance_schema只读权限。没有修改或削弱守卫；没有复用旧库。证据等级为真实MySQL+Spring/MockMvc/真实事务，非外部HTTP；夹具通过仅证明生成，未运行浏览器/Android消费。

预先检索所选类及共享fixture/driver，没有调用/pda/sync/barcodes、BarcodeSyncService或BarcodeStreamStore；正常业务写可能产生条码pending标记，不等于运行条码同步发布竞争。实际源码哈希、各attempt XML及哈希由本机manifest/结果表核对。

## 环境入口失败（全部保留）

| attempt/入口 | 结果 | 分类与处理 |
| --- | --- | --- |
| 529首次 | 0项，无XML；NOT-EXECUTED | 本机适配器未给MySQL主机参数加引号，被PowerShell解析为127；建库后连接/收尾失败。核对该attempt datadir后正常关闭实例，保留数据和日志 |
| 529再次入口（14:10:23） | 被“Attempt already exists”拒绝，无XML | 清理校验先失败，后续调用误沿用旧manifest；守卫阻止重用。没有重跑/重置该库，第二份入口日志保留；不另算一个测试attempt或覆盖类 |
| 530 | 0项，无XML；NOT-EXECUTED | 修本机参数时误影响cmd SQL模板引号，脚本解析失败；未建该attempt库。修正本机模板并解析检查后换新编号531 |
| 545之后的编排收尾 | IT为13/0/0/0，编排器退出1 | 本机run-wave用Windows PowerShell默认编码读取UTF-8中文XML失败；既有batch入口和UTF-8结果采集器均回读13项绿。改本机读取编码后从未执行的546继续，545未重跑 |

529/530无XML不是业务用例失败，不计通过、失败项数或JUnit skip；545的13项保留真实通过结果，编排收尾失败另列。只在本机忽略目录修入口，没有改产品/测试逻辑或超时。531起首类通过才将专用坐标通道记为当次已验证。

## 第2批（本次第1批）：共享表与身份锁

| attempt | 类 | tests/failures/errors/skipped | 分类 |
| --- | --- | --- | --- |
| 531 | IdentityLockTemporaryIT | 6/0/0/0 | 通过 |
| 532 | IdentityLockRevocationIT | 9/0/0/0 | 通过 |
| 533 | IdentityLockBusinessIT | 3/0/0/0 | 通过 |
| 534 | IdentityLockBypassIT | 3/0/0/0 | 通过 |
| 535 | IdentityLockAuthenticationIT | 5/0/0/0 | 通过 |
| 536 | IdentitySetFinalizationIT | 2/0/0/0 | 通过 |
| 537 | IdentitySetContinuationIT | 1/0/0/0 | 通过 |
| 538 | IdentitySetPcIT | 4/0/0/0 | 通过 |
| 539 | IdentitySetSessionIT | 2/0/0/0 | 通过 |
| 540 | TemporaryAuditContractFixtureIT | 1/0/0/0 | 通过 |
| 541 | TemporaryContractFixtureIT | 1/0/0/0 | 通过 |
| 542 | BackgroundJobLifecycleIT | 10/0/0/0 | 通过 |

本批12类47项全部通过，0失败/错误/跳过。531–539共9类身份锁35项；540–541两类生成夹具2项；542任务生命周期10项。25份锁/状态证据、两份夹具均保存；旧树97条状态前后差异0，端口已释放。未发现新业务失败或条码同类。余35类待跑，8类R08另列历史验收、不重复。提交ca985be已推送。

## 第3批（本次第2批）：人员、配对、查询和夹具

| attempt | 类 | tests/failures/errors/skipped | 分类 |
| --- | --- | --- | --- |
| 543 | UserManagementConcurrencyIT | 3/0/0/0 | 通过 |
| 544 | UserManagementRollbackIT | 1/0/0/0 | 通过 |
| 545 | UserPasswordPolicyIT | 13/0/0/0 | 通过；编排收尾编码错误另列 |
| 546 | PairingConcurrencyIT | 4/0/0/0 | 通过 |
| 547 | PairingApiIT | 9/0/0/0 | 通过 |
| 548 | PcSearchIT | 2/0/0/0 | 通过 |
| 549 | OrderNumbersIT | 5/0/0/0 | 通过 |
| 550 | BackgroundJobWorkerIT | 8/0/0/0 | 通过 |
| 551 | DeviceBrowserFixtureIT | 1/0/0/0 | 通过 |
| 552 | OrderUiBrowserFixtureIT | 1/0/0/0 | 通过 |
| 553 | PairingBrowserFixtureIT | 1/0/0/0 | 通过 |
| 554 | PdaAndroidFixtureIT | 1/0/0/0 | 通过 |
| 555 | UserManagementBrowserFixtureIT | 1/0/0/0 | 通过 |
| 556 | R09V33FixtureIT | 10/0/0/0 | 通过 |

本批14类60项全部通过，0失败/错误/跳过。源码输入ca985be，业务/测试/仓库工具仍与接续输入一致；7份新生成的查询/分页证据与浏览器/Android夹具副本已保存并核对哈希。556只要求精确端口/库/账号及显式测试条件，使用新attempt datadir，保留19309/r09_ops_source/r09_source守卫；10项实际执行通过。这是既有恢复样本回归，不是R09 C2实施。

本次两批合计26类107项全绿，未发现新业务失败或条码同类。普通通道540–555的16次运行仍含既有cleanup空变量警告；XML结果和实例正常关闭分别核验，警告原样留证，未修改仓库工具。旧树97条状态前后差异0；19309/19321/19330/19434/23308均无监听，无在途测试，系统3306未动。

## 专项环境与输入限制

以下21类不属于本次普通空库或已验专用坐标通道，未用条件关闭制造绿色，也未改守卫强行套通道：

| 组 | 类数 | 核对到的接续条件 |
| --- | --- | --- |
| Ledger作者/生产边界 | 2 | LedgerRuntimeEnvironment逐字校验旧树datadir、端口/库/账号、签名与storage；两套允许datadir均已有历史数据，需先制定证据保全与独立环境方案 |
| Foundation前代迁移 | 6 | FoundationEnvironment绑定旧树及基线/负向副本，涉及迁移前状态、签名和原件链；固定datadir已有历史数据，不能以全新已迁完空库替代 |
| Ledger独立验证 | 11 | LedgerVerifyEnvironment绑定旧树datadir和证据目录；seed为单次空库，seed-started.json已存在，retained要求原事实来源。未启动或重置旧库 |
| OrderNumberMigrationIT | 1 | 专用前代库与可选predecessor location；测试自行迁移且断言阶段差额，不适用-NonE2a先迁完的普通构造器，输入组合需单列验证 |
| TemporaryAndroidSubmissionIT | 1 | 实际Android冻结正文必须匹配同库原授权；不能用旧body配新空库或合成正文冒充Android原件 |

固定目录“已有数据”只读核实到存在；未读取凭据、启动旧数据库、判断旧库可直接复用或改写其内容。本次完成两批后在2–3轮限制内交回决定，专项组另待环境/输入接续；完整类名保留在剩余目录。

## 后续与证据边界

所有原XML、报告、失败日志和隔离库留在.local/it-batches及.local/e2a-cross-20260925-*；本机编排、manifest、结果摘要、源码/XML哈希、夹具副本及旧树状态在.local/a3-resume-20260928。原日志/夹具可能含临时测试凭据，均不提交。每批达到实际执行结果后独立提交推送，不以准备或历史通过计当次验收。

旧验证树D:/acrylic-pda-worktrees/r06-e2a-migration的既有未提交记录保持；不修改旧树业务或旧专项库。剩余21类及环境条件见[逐类目录](A3_NON_E2A_REMAINING_2026-09-28.md)。原69类清点中累计执行40个不同类，其中39类全绿、526另挂疑似专项；21类未跑、8类R08引用专用历史验收。重复取证、入口失败和历史去重不增加执行类数。实际attempt用至556，557+执行前须查重；本次两批收尾，交回用户决定A3专项环境接续或其他方向。R09 C2只保留范围登记，未实施。
