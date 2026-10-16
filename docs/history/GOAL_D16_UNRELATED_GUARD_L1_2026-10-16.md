# D16 其他订单防重保留 L1 开发备份

本切片修正离线合成消费者会被其他订单防重记录一律阻断的问题。目标合成订单可在资格满足时实际删除；其他订单防重及其引用事实完整保留。目标仍有有效防重时继续拒绝，不因日龄、备份或测试许可释放。D16整体、正常带防重目标、复杂引用/D13–D15组合、E07 L2、D18 L3和总目标仍未完成。

## 输入、决定与范围

- 固定工作区 `D:/acrylic-pda-outbound`、分支 `codex/development`；输入HEAD/远端同为 `7a91ad0b3db5740901c643b0589fbd9281cec0d8`。本聊天为唯一活动写者；清理聊天notLoaded、进度聊天idle已实查，无项目并行写者。继承87项差异/未跟踪内容继续保护，main未改。
- 证据根 `.local/goal-20261016/d16-guard-reference01/`。input01保存旧看板和全部输入；input06绑定最终49项运行，5348项路径/长度/SHA清单 `1b53067093ad1c21d42eb48247641501077d4285f7dc9a3d79732ba601439f4b`。快照包含binary diff、未跟踪原字节、Git/worktrees、写权、进程/监听及C/D余量。
- 自主决定见 `design01.md`：保留无关、可核验的防重链，避免无关订单阻断符合首切片条件的合成目标。按Q12与总目标D16，不删除有效guard、不修改Android/schema、不生成生产删除资格；未知/不透明引用仍失败关闭。正常带guard的目标清理尚无合法产品路径，不伪造缺失guard或原设备事实。

| 改动路径 | 影响层 | 必跑 | 复用依据（路径+SHA） |
| --- | --- | --- | --- |
| `tools/pda_retention/references.py`、`guard_reference.py` | 离线SQLite引用闭包 | 实际CLI允许/拒绝、其他guard原样保留、全表受控差异与完整恢复 | 首片原签fixture `server/build/pda-archive-fixture.json` SHA `e903687a492850e2c0b26ce9ac345f1cf0a0d9ef731d7f6fe36f7c0a7e7bc8e8`；schema路径/原SHA见input06与review01 |
| `tools/pda_retention/tests/guard_case.py`、`test_guard_reference.py` | 合成独立数据库与CLI子进程 | default-off有/无许可、异文、并发重放、真实锁等待撤权、末DELETE回滚、各类交叉/损坏引用 | 既有consumer/concurrency测试原路径的代码SHA见input06，继承其实际行为断言并用非空无关防重输入执行 |
| README、报告、验收索引、看板 | 使用边界与可追溯断点 | 引用、暂存范围、生成块与证据一致 | 首片报告 `GOAL_D16_WHOLE_ORDER_L1_2026-10-16.md` 仅按其原范围引用 |

本批没有网页、Android、HTTP、服务模型或迁移变更；直接受影响Python消费者已实际执行。没有新增Chrome/APK/MySQL验收，也不以本次结果免除后续L2/L3。

## 实现与实际验证

1. 仅特判 `work_guard`：先用目标assignment/submission/order/capture/barcode拒绝交叉引用，再核无关guard的capture与scan存在、assignment/订单/来源/条码/周期一致、capture为COMMITTED且scan为ACTIVE。其表及依赖行均不加入DELETE计划。其他非空不透明引用表仍拒绝；已审schema边界不变。
2. `tests04.log`/`.exit`：实际执行 `python -m unittest discover -s tools/pda_retention/tests -t . -v`，49项、0失败/错误/跳过，exit0，31.026秒。运行根为新建 `tests04/`，没有复用失败库。实际CLI正向删除、源字节守恒、全表受控变化、备份后恢复、原计划重放均通过。
3. 带无关guard的default-off有/无许可、启用但无许可、候选异文、同计划双CLI一个COMMITTED一个REPLAY、实际锁等待后撤权、末条prepared_order DELETE拒绝后的全部回滚通过。目标guard和交叉归属、条码/周期/订单/来源/状态不一致全部拒绝；拒绝后源/候选事实相同且无plan/permit。
4. 独立只读Python `audit_evidence.py` 不调用产品选择/删除函数，核每个合成源原字节SHA；对所有成功结果实际读取全表，比对原源、backup、候选受控差异及存在的完整恢复副本。`evidence-audit03.json`覆盖144个case/15个已提交候选（含失败跑批中的真实成功写入）、1153原件哈希/长度，SHA `9e13eaaac6ccf2e541def3234d14b1a12596672759b4776049c3bc837439a83b`。最终重复清单及代码/继承改动核对见review01；独立工具消费者不等于独立人员审查。

## 失败保留与未完成

- `baseline01`：新增正向反例在旧产品上被 `Protected or opaque reference table: work_guard` 拒绝，1个预期错误，非验收通过。
- `tests02`：45项中1个夹具SHA断言失败，新增无关记录后缓存spec_data仍为旧SHA；原spec/源字节一致。已实际删除和恢复，故先保存input03、全部数据库/结果/清单并独立核事实，再修夹具。原件及 `failure02.md` 保留。
- `tests03`：49项中2个错误消息断言失败；交叉归属实际在更早的冻结原件集合校验处拒绝，测试误要求guard消息。input05、全量证据及 `failure03.md` 保留；只改这两项预期，新副本tests04业务复验通过。没有把失败跑批追认为通过，也没有清零历史其他失败计数。
- 未解决目标本身有有效guard时的合法整单清理资格、审计连续前缀/复杂引用可取回组合和D13–D15受影响组合；不得移除guard制造正向。Android实际恢复组合、E06受阻Chrome与最终候选全链仍须继续，已取消换机进度承接不恢复。
- 全部新增副本/证据在D盘，未清理原件、未连共享MySQL或原库、无本批Java/服务/AVD写入。运行前C/D余量约83.03/32.87GB（十进制），精确采样和后续环境见快照；本批增长为保留的合成SQLite副本，生产删除仍关闭、`localDeletionAllowed=false`。

本次按明确路径提交推送开发备份；实际SHA及后续断点见看板。下一步接E07恢复客户端组合，并逐项补D16引用/恢复缺口；不以本L1标记D步骤或总目标完成。
