# D04 审计前缀流式 JDBC 测试覆盖修复（2026-10-20）

本轮输入 `c5e6f3848a21aa706168aac8b2e52baaf53cb36a`；证据根 `.local/goal-20261020-resume/d04-audit-streaming-test01/`。从独立[部署预检](DEPLOY_PREFLIGHT_2026-10-20.md)发现的四项断言中，单独处理审计前缀测试一项。其他三项恢复租约断言及部署包阻断仍保留，不把本轮定向结果换算为完整测试已通过。

## 实际问题

`SourceEvidenceIntakeAuditPrefixTest.conflictingFormalSubmissionDigestFailsClosed` 仍用 `queryForList(..., String.class, ...)` 注入冲突摘要；实际 `PdaAuditFacts.verifyAll()` 已通过 `jdbc.query(..., RowCallbackHandler, ...)` 流式读取两张审计表。旧 mock 从未让冲突行进入产品校验，因此本测试没有验证预期边界；这不是已证实的真实数据库 fail-closed 失效。

当前主树以原测试单独复现 `9 tests / 1 failure / 0 errors / 0 skipped`、退出 1，失败名与预检相同，见 `gradle-red01.log`、`red01.xml`。原错误未删除或覆盖。初次归档路径误写不存在的 `server/build.gradle.kts`，部分 `edit-inputs.zip` 保留；核实实际 `server/build.gradle` 后，完整原字节保存在 `edit-inputs02.zip`，源码编辑始于完整快照之后。

## 修复与自审

- 从旧大类移出失效用例，原 204 行类变为 196 行，保留其他 8 项。新增专门的 `SourceEvidenceIntakeAuditStreamingTest`，只在 JDBC 行回调边界提供合成 `ResultSet`，真实执行 `SourceEvidenceIntakeAuditPrefix` 和 `PdaAuditFacts`。没有 mock 待验产品函数。
- 参数化覆盖 `pda_audit_event` / `submitted_event` 的 UUID 与设备原序号两种查询：四种不同摘要均拒绝，四种相同摘要均可幂等核对。
- 匹配行之后出现冲突仍拒绝，JDBC 资源失败仍转为 `E2A_AUDIT_PREFIX_UNVERIFIABLE`，不吞异常转成功。
- 生产两源码逐 SHA 与编辑前相同。没有改生产 SQL、范围、错误码或放宽断言，没有任何数据库/HTTP/业务写入。

## 结果与层级

```text
gradlew.bat -p server test --tests com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeAuditPrefixTest --tests com.acrylic.outbound.temporary.evidence.SourceEvidenceIntakeAuditStreamingTest --offline --no-daemon --console=plain -Pr08FontFile=<既有固定SHA字体绝对路径>
```

`gradle-final02`：原类 8 项、新类 10 项，共 **18 / 0 / 0 / 0**，Gradle 退出 0。逐 XML、SHA 和最终测试输入见 `xml-final02/`、`test-result-final02.json`、`verified-inputs.json`。字体沿用已安装的项目构建输入，未跳过字体任务。TEMP/TMP 和 Java 临时目录仅本次子进程指向证据根 D 盘目录。

这是作者/自审的主机边界测试，不是 MySQL、事务、真实 HTTP、客户端或完整候选验收；不关闭 D04、O/U、D18、第 8 节或部署放行。原预检的三项租约测试/生命周期差异仍须单独核对，部署脚本口径和完整产物布局仍未完成。

## 保全与接续

原件、失败证据和全部继承改动逐 SHA 保持；另会话的预检报告/历史索引增补不代提交。仅四条明确路径作开发备份：两测试、专项报告及进度索引。无隔离库、APK、设备或可回收大产物，本轮清理释放 0。总目标 active、未完成。下一项按实际租约生命周期审查剩余三断言，不把 `retire()` 延迟退出当作真实 owner 死亡，不删掉应有孤儿/围栏要求。

生产开关、正式签名、原库/现网、main、部署和 Release 继续关闭；`localDeletionAllowed=false`，不恢复换机承接、多跳或接收者确认/激活链。
