# R06 Web 原命令恢复反例方案（2026-10-01）

## 真实断点与依据

主树 D:/acrylic-pda-outbound、codex/development，输入 b74f4d468cce022fcd5acac3f638f3d3f5e51c76。上一轮交接 GET 已推送；本轮先按 R06 优先核对来源/候选消费者。旧欠债文档中的候选摘要错口径已被源码 commandContextSha256 和近期验收取代，不恢复旧未修结论。分页806、REJECT/排除权限继续停报；八项预存保护文件不编辑或暂存。

[剩余计划](../REMAINING_DEVELOPMENT_PLAN.md)顺序3要求“原请求恢复、不可变回执……有反例与组合验证”。[R06验收](../parallel/R06/ACCEPTANCE.md)D12明确“发送前存储失败则不发；刷新恢复原命令”；E06要求关联丢响应后“原键重试”。[交互设计](../parallel/R06/INTERACTION.md)要求发送前保存账号/仓库/来源/摘要/版本/键，存储失败不发送。

实际 useTemporaryCommand.ts 仅用 hook 内 Map 保存键，每次 execute 从当前 facts、候选、reason 和 currentRecoveryEpoch 重建正文；无耐久存储/恢复。需要实测界定本机边界，不把源码推断写成服务器已重复记账。A1.1 Android队列已验不覆盖这个Web客户端。

## 本批动作与退出标准

1. 新增专用 Node 测试，以真实 Vue hook 和命令事实 schema 为产品路径，唯一替换 API 传输边界；不修改 hook/API/现有测试。全部测试只在显式环境开关及仓库 .local 独立证据目录下运行，默认跳过，不接真实服务器/数据库/设备。
2. 自包含合成事实：LINK 或 NORMAL_CONFIRM 首次 POST 边界丢响应，同页原意图重试。断言原键、原始 JSON 正文相同；分别改变候选响应或界面理由，保存每次调用参数及 SHA，再断言不得改写未决正文。
3. 销毁并重建 hook 后恢复同一业务上下文，断言不能生成新键发送；所有 Web Storage 写入抛错时断言 POST 调用为0。内存存储在同一场景内保留，不伪称独立浏览器/JVM重启。
4. 运行前校验脚本 parse0、保护摘要、产品摘要、磁盘/设备枚举；Web typecheck/build成功后只执行一轮新测试，记录每条原断言及总数。真实断言失败保留，不改产品、不放宽断言/改名刷绿；若无失败按证据收口。
5. 作者自审、文档链接/范围/敏感资料校验；先归档看板，再更新断点、进度索引、自主报告并提交推送。本批是缺陷实证，不当作功能交付；停止依赖该hook的恢复子项，下一批继续独立R06/R07能力。

## 边界

沿 brainstorming 的有界分析、testing-best-practices 与 single-responsibility 执行；用户已明确无人值守自主方案授权，不另等例行确认。无产品/默认开关/历史迁移/契约改动，不触碰原数据库；AVD与定向设备命令均0，真机只枚举前后一致，D保持20GiB以上。全部.local和失败轮保留。本反例只能证明 hook 到传输边界的行为，不能证明真实HTTP请求、服务器账务或关闭浏览器后的存储实现。


## 当次结果与缺陷定性

一次attempt-1实际10项：2通过、8失败、0错误、0跳过，Node exit1，约399.5ms。Web typecheck+生产打包、JS语法、PowerShell解析均成功；没有重跑失败断言或改产品。

| 场景 | 实际 | 结论 |
| --- | --- | --- |
| LINK/NORMAL_CONFIRM 同页相同意图控制组 | 两次同键同正文，2通过 | 现有hook内存重试的有限保证成立 |
| LINK/NORMAL_CONFIRM 首次结果未知后改理由 | 同键、正文reason不同，2失败 | 原键不足以保证原正文 |
| LINK/NORMAL_CONFIRM 销毁后重建Vue hook | 仓库/批次不变，生成新键，2失败 | 没有持久原命令恢复 |
| LINK/NORMAL_CONFIRM Web Storage均拒绝写入 | 各调用POST边界1次，setItem均0，2失败 | 没有发送前耐久落盘门 |
| LINK候选摘要变更 | 同键、expected候选摘要不同，1失败 | 未决意图被刷新值覆盖 |
| NORMAL_CONFIRM恢复代次变化 | 同键、transportRecoveryEpoch从1变2，1失败 | 原正文被当前环境重建；服务端commandSha256包含该字段 |

根因定位：useTemporaryCommand.ts 的 keys 为每个hook私有内存Map；execute每次拼装body，并使用本次reason、候选摘要和当前恢复代次。TemporaryCommandAction.vue在错误后仍允许编辑理由和重新提交，未决原请求不存在不可变保存记录。本次仅直接调用真实hook及schema，API返回合成事实，postRaw边界记录JSON后抛TypeError；不能把这些POST调用写成真实HTTP流量，也没有证明服务端重复记账或出库。

测试显式门控 TEMP_WEB_RECOVERY_VERIFY=true，证据目录须在本仓.local且有一次性marker。合成 UUID 每场景独立，同一场景重挂载保留存储对象；未加载真实浏览器页面，不冒称关页/重开或OS死亡实证。两个控制通过不抵销八个失败；默认跳过不是通过，本批实际执行无跳过。

证据根.local/autonomous-20261001/temp-web-recovery/：before含完整保护/产品摘要、Git/远端/写者/监听/设备/磁盘；offline-1含build及源摘要；attempt-1含XML、日志、10个JSON和结果。D31578689536→31578468352字节；真机仅枚举前后一致，AVD/定向设备/数据库/服务启动均0。最终提交磁盘、设备与SHA见commit-review/git-final.json。所有历史失败与.local保留。

处理：真实缺陷停报，不修既有产品、不放宽原断言、不重跑。依赖该hook的D12恢复继续未完成；后续需有原身份/仓库/来源绑定的耐久请求、不可变原字节、原回执查询及页面重建恢复验证，不能用新增键替换未知请求。继续独立R06/R07工作；所有旧停报及现场边界保持。作者/自审/执行同一助手，非独立评审。

工具级读失败与草稿格式问题：Windows路径glob/brace误用、一个不存在文件的定向读取均只读失败，没有测试/数据库影响；一次apply_patch因多行编码拒绝整包，没有部分应用；归档围栏草稿在docs-closeout保留，最终按整文件四反引号重写。均不作为业务失败重试或通过。
