# PDA领取前会话绑定定向修补

## 范围与发现

输入为 `codex/development` 的 `d33e203d69f75ac77ca63a2d4c69b53d3b5790a6` 加已有工作区改动；逐路径基线和原字节在 `.local/goal-20261022-resume/claim-session-binding01/baseline.json`、`pre-edit/`。用户批准的四档只读筛选已有实现和原生消费者报告，本次没有重复开发或重跑。

源码检查发现 `claim()` 在二次预览后只核恢复代次，可能先插入领取意图再因换人拒绝；`retryClaim()` 在设备刷新GET之后没有复核完整身份，而直接使用届时会话发送POST。新行为测试通过回环MockWebServer在GET中途切换合成vault字段，首次实际运行两方法均失败：新领取在保存意图后才抛原人员错误，重试则继续完成了POST。不是运行器失败。

## 实施

- 二次领取预览GET绑定捕获的会话；创建或替换领取意图前用既有 `SessionIdentity.requireSame` 比较 `userId/deviceId/warehouseId/serverRecoveryEpoch/localSessionId`。已有READY、订单、版本及仓库守卫保留。
- 原意图重试的设备GET绑定同一会话，返回后完整复核；领取POST改用既有 `session.call(... expected=auth)`，继续使用原正文、原恢复代次及原幂等键。
- 只修改仓库和行为测试；不改Room结构、服务端契约、生产开关或已取消链。原意图按原用户/设备/恢复代次恢复，未新增跨会话身份承接能力。

## 实际验证

首次RED：`attempt01`，Gradle退出1，2方法/2失败/0错误/0跳过。原XML、命令、输入及日志保存，XML SHA-256 `8136b91a9804b2b403181cd3be5dcafb6793f06e03f57197007d275318389858`，诊断清单见 `attempt01/red-result.json`。

修补后同次 `attempt02`：Gradle离线、独立输出、单worker与短路径子进程TEMP，退出0。实际三类共14方法/0失败/0错误/0跳过：

| 类 | 方法数 | XML SHA-256 |
| --- | ---: | --- |
| ClaimPreviewRepositoryTest | 8 | `c6d766997da173f9f4a32e478698500fb5ea36359abde580c499bff7088ca362` |
| ClaimPreviewDialogTest | 4 | `4d2e0e04edb6fcc5c820d95241304f7315591a471bec2a893278645c7f60ffc1` |
| PdaNetworkFailureTest | 2 | `30b677e4fbb52cadecf1f5b2cf2f3650b8529b8a84ec32ae4ea8a195121a3493` |

仓库行为覆盖五字段分别切换时拒绝新建、拒绝替换REJECTED原意图且不增加历史、拒绝刷新后POST且原意图逐值不变；正常确认仅一次POST；丢响应保留原意图并以原键/正文/代次重试；版本冲突保留REJECTED且再次重试不发请求。页面取消/未完整预览不领取，DNS和POST丢响应边界保持。

测试前后1096个输入文件哈希不变。39份Room schema路径/长度/SHA核同，独立补充记录 `attempt02/schema-verification.json` 修正 `validation-result.json` 内首次筛选误得0项的问题，原记录未覆盖。产品、测试最终SHA-256分别为 `5445df55bd4e19d345fd1935c1fa84e3ee914f1e4e50b4596adb3bdd43f06c07`、`58a1bbf22f982f98e97e1034633061d43417369fc43377a99eb1363dab389693`。全部命令、输入/退出码、XML及输出哈希见 `attempt02/`，不同attempt互不覆盖。

## 证据与限制

上片缺失的 `checkpoint-final01/final-record.json` 已在2026-10-22补建，明确为事后补充快照，不伪称恢复了10-21瞬间；此前仓库守卫首份XML被覆盖的缺口仍见[原报告](GOAL_PDA_CLAIM_PREVIEW_WAREHOUSE_RECHECK_2026-10-21.md)。

本片只使用合成身份、主机NATIVE Room和回环HTTP，不是实际服务、MySQL、原Keystore、模拟器或真机业务验收；未构建APK、安装、连接test服务/原库/现网、正式签名、部署、Release或合main。ADB仅只读枚举，M71为offline；服务端缺少warehouseId响应的根因仍未取得真实响应证实。已有失败原件、唯一证据、最终APK及继承源码保持。

这是有界缺陷修补及定向验证，不代表D/O/U整体、第8节最终候选或总目标完成。总目标保持active、未完成；`localDeletionAllowed=false`，生产决定继续fail-closed。下一步先核对同一最终候选的剩余D/O/U矩阵和独立消费者，按实际输入补齐，不机械重跑本片已过用例。
