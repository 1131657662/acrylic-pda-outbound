# E06主管网页短装批准：L1开发备份

[最小设计](GOAL_E06_ASSOCIATION_SHORTFALL_WEB_DESIGN_2026-10-14.md)，输入`efe88980163209f05024496f605d5d06a19f4a66`；版本事实见看板生成块。现有人工关联入口显示原任务/目标两差额及原短装原因，主管显式勾选并填写说明；切换目标清空未保存批准。差额按完整依据计算、不可人工改数字；原计划不自洽、异产品或逐产品超装阻断。保存后的批准随原body/key/epoch恢复，成功和未明态均显示原批准，不自动放行/记账。

`AssociationForm/ShortfallApproval/association-shortfall/association-api及定向消费者 → 显式短装批准及持久原请求 → 6项实际回执/边界/持久化、类型构建、真实Chrome与全表守恒 → 服务/迁移/Android未受影响，复用efe88980对应1532/1533服务L1的默认关闭、权限、事务/并发/末SQL/锁后撤权及固定钥证据，理由为只接网页预核与批准字段。旧无差额body不添加字段，旧原键存储和冲突引用沿原链；E06 L2及最终L3待跑。`

证据`A=.local/goal-20261013/e06-conflict-association-audit-1534`、`B=.local/goal-20261014`，固定D盘/同一作者/单数据库写者。1534从1514保全dump/hash和原private/storage恢复；原Android包字节未改，目标/peer/实物/主管明确合成。目标计划先设为4，再冻结实物和冲突，两种短装均2；不以模拟器或合成签名冒充原PDA现场。

| 实测范围 | 结果 |
| --- | --- |
| 定向/构建 | `B/e06-shortfall-web-unit01`六项零失败/跳过、类型及构建退出零。实际短装关联回执、未勾选/空或过长说明、旧无差额字段缺省、精确计划/产品反例、持久原body/key/epoch；`consumer04`对1534新Chrome真实回执再消费六项通过 |
| Chrome实际入口 | `A/browser02`一场景16.3秒通过：候选真实逐页limit=1完整装载；缺冲突引用/未批准/无说明/未排除时零关联POST，显式批准正向；真实服务器200后故意丢响应、刷新/双页原key恢复，3次POST均同key/body/epoch，说明及两差额逐值保持；干净浏览器无本地记录仍读服务结果 |
| UI | `browser02/390-pending.png`及`390-recovered.png`实际390宽截图目视；无横向溢出，原批准/目标/冲突引用/未明恢复与成功状态可读。失败截图显示可见勾选框，未用force click |
| 全表守恒 | `A/post-browser02`以`web-prepare01/facts-1`和当次结束`post-browser01/facts-1`保存的逐表hex事实补核：仅一关联/一冲突引用/一幂等、三次已知主管登录和一关联审计，原审计多重集/原冲突三表及库存/占用/提交/结果/账务逐值不变 |
| 源码与自审 | `unit01/source-inputs.json/source-browser-inputs.json/browser-binding.json`，产品源码/锁/契约与实际单测构建输入完全相同，只有Chrome测试改用可见checkbox标签；数据精确整数、原结束/产品预核、显式非空批准、目标切换清空、scope/代次/原键与冻结态检查。网页做摘要/结构绑定，RSA仍由服务验证，不称独立人员审查 |

失败保全：`A/browser01.log`120秒超时及原截图/上下文，Element Plus隐藏原生checkbox无法`.check()`，截图中可见标签正常，关联POST未发生。等待enabled后点击可见标签并断言checked，`browser02`有效通过；原数据不重新准备。`post-browser01.log`守恒脚本误取冲突形成前prepare01快照，路径证据明确；修为web-prepare01与当次已保存结束快照，严格四业务表白名单及逐行审计补核，不重跑业务、不扩大允许变化。原失败不记通过，旧CLI/UI/native/恢复计数不清零。

1534用途结束后dump/hash/认证停机、零监听消费者与适用NTFS压缩，保全`B/preserve-1534-e06-association-shortfall-web-l1-01`；全部datadir/dump/binlog/HTTP/原资料/身份/失败证据保留，预览与1519不动。明确路径提交推送及全脏快照见`B/e06-association-shortfall-web-delivery01`。仅网页L1；多任务/增量冲突、E06组合、E07恢复/外置副本、E08留存/UI、E09与全部闸门继续，目标active未完成。
