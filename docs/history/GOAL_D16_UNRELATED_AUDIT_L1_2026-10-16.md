# D16 无关审计引用保留 L1 开发备份

本切片让合成整单消费者在核验无关审计后保留其完整事实，避免其他订单审计一律阻断目标。82项实际CLI/SQLite验证通过；目标有效防重、恢复屏障和不透明引用仍拒绝。仅初轮FORMAL合成组合输入，不计D16整体、E07/E08 L2、D18 L3或总目标完成。

## 输入与决定

- 固定 `D:/acrylic-pda-outbound`、`codex/development`；输入HEAD/远端同为 `0e9034806dbcb87ea6ee1a18921cdd40c88d7c6e`。本聊天唯一活动写者；经用户授权发送协调消息，清理聊天再次回复 `complete-released`，没有在途清理或写入。其他同目录聊天及进程另行实查，无子代理。
- 证据根 `.local/goal-20261016/d16-audit-reference01/`。input01保存接管全树5352项，SHA `4ab4d0f0455bdb722e63082eaa29036c6ca418da0e288bafa4b0bd9df572be15`，与上批E07 input07完全相同。最终运行绑定input07，共5355项，SHA `04411284cb660ff4d9f55caf946abe8ddedb0e49093b018e4567695e43b7a354`。各轮binary diff、未跟踪原字节、旧看板、写权、Git/worktrees、环境和磁盘保留。
- 自主方案见 `design01.md`：逐段验证审计原签、公钥独立固定、连续事件摘要/字节、检查点与恢复请求原证明，再允许原样保留；不删除审计、不改检查点或guard制造资格。模拟核验时间必须仍在原五年留存范围，不将目标90天条件套到无关审计。
- 目标复用 `server/build/pda-archive-fixture.json`，SHA `e903687a492850e2c0b26ce9ac345f1cf0a0d9ef731d7f6fe36f7c0a7e7bc8e8`。无关链从E07旧恢复升级证据 `native01/upgrade-device/databases/` 的DB/WAL/SHM复制至新目录，恢复后显式转为停止的DELETE journal单文件；原三文件SHA/长度保持。新 `input02/audit-source.sqlite` SHA `9f6e2546288ff6a9b106c4590f3d9eabe0b432ca36abf9a070fd16c1d890667d`；独立审计公钥SHA `9402b43dbfa3d72e7bdfb78c18cc1cf69bbe514fe96b1ff0d671c79d52604077`。
- 两代原审计均覆盖序号1至15，检查点均15，恢复请求内原证明与完整原代次记录一致，未生成新签名。这是两组既有事实的合成组合，不是同一原PDA现场库，也未核原Keystore身份。版本事实只见看板生成块。

| 改动路径 | 影响层 | 必跑 | 复用依据（路径+SHA） |
| --- | --- | --- | --- |
| `tools/pda_retention/references.py`、`audit_reference.py` | Python资格/引用闭包 | 真CLI正向/拒绝、全表差异/恢复、default-off有无权、异文、并发、真实锁后撤权、末DELETE回滚 | consumer/store/proof/signatures/catalog各路径SHA见input07；原签fixture及上列捕获输入 |
| `tools/pda_retention/tests/audit_case.py`、`test_audit_reference.py` | 非空合成输入/回归 | 每项新库，两代审计/依赖逐表保留；签名/检查点/请求/身份/目标引用反例 | 继承consumer/concurrency测试原路径+input07 SHA，并实际在新审计输入执行 |
| README、正式报告与索引 | 使用边界/可追溯性 | 引用、范围、自审、输入和生成块核对 | 既有D16首片/无关防重及E07旧恢复L1报告只按原范围复用 |

没有修改Android/Web、服务模型、迁移或契约；直接受影响的Python入口已真跑，不新增Chrome/APK/HTTP/MySQL通过记录，不豁免后续L2/L3。

## 实现与验证

1. 审计命名空间与检查点集合必须相同，每段连续、无未决，范围和资源上限有效。RSA原签/unsigned、公钥固定、设备/仓库/序号/摘要/字节/存储政策及五年留存均核验。事件完整，实际assignment与origin一致，订单/准备身份、capture/仓库/操作员/设备及设备后续序号有效。
2. 恢复请求必须含完整原证明，且恰好对应现存原代次记录；数字epoch严格递增、时间不逆转，设备/仓库/范围/摘要/字节保持。不接受无签名DTO。所有审计、检查点、guard及无关行不加入删除计划，目标引用包含事件ID。
3. `tests05.log`、`tests05-result.json`：实际 `python -m unittest discover -s tools/pda_retention/tests -t . -v`，82项、0失败/错误/跳过，exit0，63.386秒。新 `tests05/` 的参数、来源SHA、子进程TEMP/TMP均登记。此前tests04的81项也通过；自审补目标事件ID保护后再次复验。
4. 实际CLI删除目标，源字节不变，无关事实及两代审计/检查点原样保留；backup恢复到新库后全表相同。重放、default-off有/无许可、启用无权、候选异文、同计划并发一次COMMITTED一次REPLAY、真实锁等待后撤权、末条DELETE失败全回滚均通过。
5. 签名/unsigned字节数、公钥、绑定/前缀/事件缺失或移位、检查点前后/缺失/未决/孤立、恢复原证明缺失、请求异文、订单/capture/设备序号不一致、实际事件交叉归属及目标引用均拒绝；拒绝后源/候选全表相同，无计划或许可。有效目标guard和恢复屏障仍拒绝。
6. 独立只读 `audit_evidence.py` 不调用产品资格/删除函数；核各case原源SHA、完整性/FK、真实提交的backup/候选及存在的恢复副本。`evidence-audit05.json`覆盖五轮404个case、37次真实合成提交、3221文件，SHA `f3a7b005444142adbb1c521ba7683f3c1a71aba7c1cb79d71ca5b4ca27713334`。失败轮真实写入同样完整保全。
7. 自审 `review-result01.json`：5352项继承输入仅本批references/README/看板变化；运行代码与input07一致，原捕获三文件及各轮原件稳定，3724文件/1207133785 bytes双次SHA/长度一致，清单SHA `63690d86be77076cdd89bd7acd8a60c1b53454d9ddce4e62cdaefd541201410c`。受影响代码不足200行、函数不超过30行；独立工具消费者不等于独立人员审查。

## 失败保全与限制

- tests01：80项中30 errors，backup保留WAL模式，空sidecars触发停止单文件检查。原input/脚本/日志/case保留，5次既有合成提交保全后，新input02显式DELETE模式；未修改原捕获库。
- tests02：80项中3 errors/1 failure。两项反例先撞设备序号唯一约束，一项先撞订单FK，均未到CLI；另一项被更早绑定检查拒绝而消息断言不符。保留8次实际合成提交和664文件；修反例/断言不放宽产品校验。
- tests03：81项中1 error，第三订单沿用唯一orderId，夹具插入先失败；8次实际合成提交和682文件保留。分配独立orderId后新tests04通过。failure01/02/03保留根因和新证据，原失败不追认通过，其他历史计数不清零。
- 自审补实际事件assignment/origin一致性及目标事件ID引用保护；后者以未归档尾部引用原目标事件的实际CLI反例验证。
- 尚无正常带有效防重目标的合法清理资格；目标审计前缀删除、其他复杂历史链、D13–D15可取回/恢复组合、D16整体及D18最终候选全链继续。旧Chrome审批阻断、SKU返回导航暂缓和原件保留，不机械重试或绕过。
- 副本、日志和快照全在D盘，无本批Java/MySQL/AVD业务写入或新增监听。完成测试C/D余量 `83,023,749,120 / 25,772,232,704` bytes，后续以快照为准。原TEMP AVD及身份未核清共享MySQL不操作；未清理失败库、原日志或唯一证据。
- 七类生产能力、TEMP生产归档边界及 `localDeletionAllowed=false` 保持关闭；不接原库/现网、不合main、不发布部署、不用正式签名。明确路径提交推送开发备份，实际SHA见看板；本机证据不冒充异地备份。
