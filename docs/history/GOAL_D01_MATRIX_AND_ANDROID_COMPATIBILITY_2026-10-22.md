# 27项证据增量盘点与提交源非空兼容性

本片补齐D01–D18、O01–O05、U01–U04的逐行退出标准、实现入口与报告输入映射，并实际执行明确开发输入的Android非空迁移、绑定及更新保护。盘点不计业务验收；兼容性只证明下述有界范围，不计D15/D17整体、D18或第8节通过。总目标active、未完成。

## 输入与写权

- 固定树D:/acrylic-pda-outbound，codex/development，输入HEAD/实核开发远端d075b4a805f46f7ebf32f98e185254582ecebfda，main未改。版本事实见看板生成块。
- 证据根.local/goal-20261022-resume/goal-matrix01/。baseline.json、inherited-wip.zip、tracked/staged.patch、status/refs/remote/worktrees及runtime-before.json保存40条展开继承WIP和运行断点。inheritance-before-docs.json在文档维护前核40条全部原字节相同。
- 本聊天为源码、索引及共享看板唯一写者，无子代理。另一个活动清理聊天范围限磁盘，不取得源码写权。未向其他聊天发送消息。
- 独立提交源.local/c22p/沿[四路径组合](GOAL_PDA_CLEAN_PAGES_FILE_COMBINATION_2026-10-22.md)复用：1162份提交文件、11份外部输入逐SHA核同；输入提交与ad11fb16的Android/Gradle/电脑工具无提交差异。不叠加40条继承源码。
- 其中若干旧archive/submission等JSON为占位内容，不能当可信签名输入。本片明确只选择不消费这些占位件的迁移方法；FORMAL/TEMP使用已绑定的非空契约输入，不制造新服务端回执。

## 逐行盘点及实际问题

matrix02.md/json包含27项原退出标准、94次实现入口绑定（89个不同路径）、82次报告引用（65份不同报告），逐路径保存工作树SHA、HEAD blob、最后提交及报告输入摘录。matrix02.json SHA-256为c14de3a46bffcef338356bf72da3069584669db9cfe797607ca5d331e12239d3。

原草表matrix.md/json、82份引用对象的report-bindings.json及首次路径探测保持；mapping-input02.json修正五处不存在的路径，bind-matrix.py生成正式本机映射。路径存在/报告哈希只证明对象，**没有逐项证明历史原始输入、依赖或候选覆盖无漂移**，不得据此把27项改记完成。代表性实现入口中仅AGENTS/Git工作流存在已保全的继承文档差异。

| 项目 | 核实后的关键增量或剩余标准 |
| --- | --- |
| D01 | 本片补27项映射，静态盘点不计业务通过 |
| D02/D03 | 原同人同机显式续作及高轮证据保留；不恢复取消换机链，候选复用仍须逐输入核同 |
| D04 | 后续结构绑定、旧STOP前缀及原Room/真实HTTP已补具体缺口；五丢响应不覆盖全部停止前置与后续处置，旧失败不追认 |
| D05 | 零/非零取消已有，实物/占用与原请求组合按实际候选核影响 |
| D06/D07 | clean Android/HTTP组合、默认关闭Chrome及旧非空迁移分别已有，不能拼为同次最终候选 |
| D08/D09 | 审批/撤销/缺账/返库的admitted TCP与新周期分项已有，原账与原签保留；整链及客户端组合仍须核 |
| D10 | 附件实际字节/容量/权限及原请求恢复已有分项，不能以入口查询替代完整受影响消费者 |
| D11/D12 | 导出套件、任务/下载/中转、四页面新包独立PC消费已有；各来源不是同一事务，不代表业务回执 |
| D13 | 多个文件/命令/历史只读入口已有后续TCP/P5证据，旧“全部未验”过时；跨入口/错根错库水位及实际候选仍未完成 |
| D14/D15 | 多截点、旧worker、升级回退已有；本片补Android非空兼容，不能替代实际完整恢复和运维组合 |
| D16 | 正常有效work_guard目标无合法退出依据；release只随未冻结VOID，不能删guard、改状态造资格。Python工具仅明确无目标guard合成范围，Android仅缓存 |
| D17 | 筛选、新身份卡、页面导出和提交源编译依赖已有；旧BCD/原资料逐材料及实际候选兼容仍须核 |
| D18 | 无统一最终候选，第7节整合及逐门自审未满足 |
| O01 | 原子VOID与旧序号保留已有，队列/冻结竞争及实际候选影响继续核 |
| O02/O03 | 原件导出/PC/回执返还已有分层证据；缺真实身份/SAF/物理介质不得用合成主机替代 |
| O04 | 独立采集、解析、多任务/对账已有分项，本片补独立原件迁移与新采集，非完整多来源业务组合 |
| O05 | 同机副本不代表外置备份，恢复/未保护增量与可取回边界保持 |
| U01/U02 | 高频/保存/结单/未知语义已有分项，实际候选业务/生命周期组合未豁免 |
| U03/U04 | 四路径页面/PC及四状态筛选已有，不重复开发；原字号/队列/生命周期只按各输入复用 |

gates-and-next.md逐门记录第8节尚未满足的原因，未把开发缺口移入C01–C08。

## 非空兼容性实跑与修复

compatibility-design.json固定25个类/方法选择：FORMAL准备/冻结/召回/续作历史、同步/归档历史、TEMP准备/扫码/冻结/停止/原件队列及回执、独立资料/任务/采集迁移，以及旧绑定和更新保护。排除取消的handover/recovered-confirmation/activation用例；历史表的兼容迁移不等于执行取消链。

| 运行 | tests/failures/errors/skipped | 结果 |
| --- | --- | --- |
| compatibility01 | 31/8/0/0 | 原提交源，8项旧结构装配遗留独立采集新表，升级时报independent_capture已存在 |
| compatibility02 | 32/1/0/0 | 原31项全过；新保护测试漏设IO线程，夹具创建被Room主线程保护拒绝，未到保护断言 |
| compatibility03 | 32/0/0/0 | 最终三个测试路径，同次26套件全部执行，Gradle exit0，32任务实际执行 |

修复只涉及三个测试路径：

- 新LegacyIndependentTablesFixture：构建旧合成结构前，先检查全部16张后加独立采集表为空，再按外键顺序去除这些未来表；任一非空在任何DROP前拒绝。
- TemporaryContinuationMigrationFixture：旧结构装配入口调用上述测试helper。产品IndependentScanMigration的严格CREATE、Room主线程保护、生产源码/迁移/权限/开关均不变。
- 新LegacyIndependentTablesFixtureTest：实际创建非空独立任务及扫码/guard，旧结构装配必须拒绝，前后全部表逐值/类型/二进制内容保持。最终用IO线程，不放宽产品线程限制。

三个路径原字节/不存在状态、候选overlay、每轮SHA和有界决定在compatibility-edit-preimages、compatibility-patch、compatibility03-patch及bounded-fix-design.md。原先8项是同次同根因失败，不当作连续八次盲目重跑；后续新保护用例线程失败单列，原日志/XML和源码均保留。

每次使用独立build/cache/XML/HTML/binary、短D盘子进程TEMP及绝对资源classpath；offline/no-daemon/max-workers=1/no-build-cache，测试禁止UP-TO-DATE。运行器run-compatibility*.py、逐轮command/exit、inputs-before和XML原件保持，original-three-runs-xml-log.zip另作无损副本。最终XML结果SHA为4573bec02d2fa4f1e7a1bf664d9e65c044f856d975d65a4219b6530c5a52269e。

最终1162提交文件除明确测试overlay外保持，两个新测试文件及主树三个测试路径核同，11外部原件/副本保持。后两轮是同一产品提交加明确测试补丁，不声称未打补丁的提交已32项全过。作者/执行/自审为同一助手，不称独立人员审查。

## 保全、清理及边界

旧看板原字节在board-before.md/docs-before-closeout.zip，旧结论另见[本片前看板归档](ORCHESTRATION_STATUS_BEFORE_MATRIX_2026-10-22.md)。正式报告/索引修改前已保全；看板保持本地，历史索引提交只取HEAD加本片入口，不混入继承内容。

三轮用途结束的专用project-cache共10557827逻辑字节，经无损NTFS压缩减少分配6572050字节，删除0；全部长度/SHA保持。D盘余量39142129664→39148711936字节，卷变化与文件分配差异分开记录。cache-compact-plan/result/log保存精确路径、逐文件SHA、命令和退出码。既有删除自动审批拒绝未换包装重试，原XML/日志/失败材料/数据库事实、源归档、原资料/身份/Keystore/签名回执、最终APK及继承WIP全部保留。

收口无本片Java/Gradle/Python/模拟器在途消费者；系统MySQL80/3306/33060仅观察服务/监听，未认证库内身份、未连接SQL。ADB只读枚举M71 offline、MI6 device，未操作设备；签名材料只元数据，不用于运行。本片无APK构建/安装、真实HTTP/MySQL、Chrome、实体文件通道、全套迁移/恢复或最终候选。

下一有界动作：先核服务端普通回归19项skip的逐方法材料及提交输入；18项SKU/closure/review原材料只能在可取回且SHA核定后定向执行，Windows符号链接条件1项单列，不拿既有junction证据改写历史skip。不重复本片32项、筛选或页面正向，不重启旧暂缓/取消链。

七类生产决定/TEMP生产归档fail-closed、localDeletionAllowed=false；换机承接/多跳/接收者确认激活、旧run03不恢复。原库/现网、正式签名、main、部署和Release关闭。开发备份与清理不增加D/O/U验收等级，总目标继续active。
