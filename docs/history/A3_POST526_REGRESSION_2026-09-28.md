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

以上不是业务用例失败，不计通过、失败项数或JUnit skip；只在本机忽略目录修入口，没有改产品/测试逻辑或超时。531起首类通过才将专用坐标通道记为当次已验证。

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

本批12类47项全部通过，0失败/错误/跳过。531–539共9类身份锁35项；540–541两类生成夹具2项；542任务生命周期10项。锁/状态证据、两份夹具均保存；旧树97条状态前后差异0，端口已释放。未发现新业务失败或条码同类。余35类待跑，8类R08另列历史验收、不重复。

## 后续与证据边界

所有原XML、报告、失败日志和隔离库留在.local/it-batches及.local/e2a-cross-20260925-*；本机编排、manifest、结果摘要、源码/XML哈希、夹具副本及旧树状态在.local/a3-resume-20260928。原日志/夹具可能含临时测试凭据，均不提交。每批达到实际执行结果后独立提交推送，不以准备或历史通过计当次验收。

旧验证树D:/acrylic-pda-worktrees/r06-e2a-migration的既有未提交记录保持；不修改旧树业务或旧专项库。剩余类及环境条件见[逐类目录](A3_NON_E2A_REMAINING_2026-09-28.md)。526保持疑似缺陷待专项；R09 C2只保留范围登记，未实施。
