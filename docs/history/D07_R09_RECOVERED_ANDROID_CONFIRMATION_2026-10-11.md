# D07/R09 限定恢复 Android 耐久接收确认

主树D:/acrylic-pda-outbound、codex/development，输入342fb7ffda5fbe13ae687c8f5db7a8dae0fc5316加本批明确Android/测试改动。单助手唯一写者；received-confirmation-android-running-02/resume-03保存46dirty、副本/hash/diff、Git、写者/进程/监听及C/D余量，69历史比较、22原STOP和原pause-final中29继承非看板路径无漂移。版本事实只见看板生成块；本项完成不等于D06/D07或D01–D18及第8节完成。

## 实现与边界

新增独立恢复确认命令/回执Room表，外键引用恢复原件stage。明确实物意图后事务保存一次commandId、canonical原命令与SHA；POST、并发、丢响应、进程重开及重试复用原命令。专用产品POST返回原回执byte，RS256专用audience/purpose/status、原请求/资格/PREPARE/清单/本人session/版本全部精确核对，所有能力false。

回执与CONFIRMED进度在同一Room事务保存；末写失败无半回执。网络/协调器忙保留原键自动重试，硬拒绝ACTION_REQUIRED保留原件，重新明确意图仅恢复原命令。完成后重读仍核签名、摘要和身份，不仅信状态字段。非空迁移保留下载原件、普通TEMP与RECONCILING围栏；旧暂存迁移测试固定对应migration.startVersion，避免数据库总版本增长时误测。

沿既有debug显式恢复开关，默认/staging/release关闭。本人LOGIN、本地登录代次、权限版本、仓库/设备/公钥、source-target代次、期限、key及恢复围栏重验。没有向普通TEMP准备/确认/激活/guard/work写记录，没有放宽旧source代次或旧票据。页面确认按钮、worker调度、丢token新LOGIN替换、激活及最终提交待后续。

## 实际验证

- unit-01/02各9测试全过、零失败/错误/跳过，native Room：明确意图、原键重开/并发、合成丢响应、末Room SQL回滚、错签名/身份与损坏回执、非空迁移。fixture签名/身份明确为合成，不代实际HTTP。
- 1334新隔离目标只读重建原1233，服务前置1/0/0/0/sourceUnchanged。实际本人密码/P-256 LOGIN及产品GET→Android生产repository/native Room→首次产品POST，confirm/restart两个独立Android JVM各1/0/0/0，重开原db和原HTTP回执一致；后继服务JVM原pin/Bearer/回执回放成功，没有新LOGIN或重签原身份。
- 实际POST首次收到服务原回执后在客户端边界注入丢响应；原键重试中注入最后Room SQL失败、整个回执写入回滚；再明确核对后原键成功。此丢响应是客户端边界注入，不称网络断电；[产品HTTP批次](D07_R09_RECOVERED_CONFIRMATION_HTTP_2026-10-11.md)真实Socket丢正文为不同证据，不外推首次创建网络丢确认。
- 服务桥核152表只有recovery_handover_confirmation精确新增一行，其余原表/raw/files和迁移历史保持。Node基础16输入/9语义拒绝、原件/签名/新JVM通过；独立原生SQLite只读核两db、13输入、全部重开行相同、原LOGIN/命令/回执/正文/RECONCILING与12语义拒绝通过。SQLite最终快照不独立证明中间Room回滚，回滚由实际测试/XML计证。工具消费者不代独立人员或现场。
- Android完整711测试/173XML，698通过、13条live HTTP条件skip，零失败/错误；本恢复确认HTTP在1334已单独实际执行，其余skip继续单列。debug APK/lint通过，lint211 Warning、零Error/Fatal，未称零警告。服务生产源码未变，上一HTTP批次普通recovery/handover回归按不受影响输入复用，不称全server/全部IT。
- 服务/两Android组合/全量各2806项源码/依赖输入，运行及收口逐hash无漂移。原1233两个固定hash、1293UNKNOWN围栏hash保持。作者自审见本机self-review；没有新增产品行为修复。

## 失败与保全

1333前置选用已消费session重启的方法，后继桥被completedSession守卫以“Session restart already consumed”拒绝；Android未启动，不称组合通过。仅修本机运行器选既有Android前置方法，换1334新库，不删marker、不放宽守卫。1333失败库/XML/log/input/dump/binlog保留；1333/1334均dump成功、认证shutdown=0、原PID退出、任务监听空，保全不复用。

收口脚本首次用PowerShell布尔判断空XML skipped节点，漏列13项；保留首轮json/故障记录，修SelectSingleNode并精确核skip数量后通过。业务输入没有变化，没有为脚本修正重跑业务。原失败/中间输入、原资料/旧BCD/526及历史关键未验继续保留。

## 原件位置与接续

私有材料/数据库/APK/日志在D盘.local，未上传或异地备份，无清理。

- .local/d01-d18-20261008/received-confirmation-android-{unit-01,unit-02,full-01}/：命令、输入hash/diff、日志/XML、结果与APK/lint；r09-temporary-evidence-restore-mysql-1334/及received-confirmation-android-http-1334-{confirm,restart}/保存实际组合。
- .local/e2a-cross-20260925-1334/：实际HTTP原件/LOGIN私有输入、两native Room、服务全表快照、签名原件、datadir/dump/binlog；.local/d01-d18-20261010/temporary-evidence-restore-mysql-{1333,1334}/保存分配/运行器/认证停机/PID与重启退出证据。
- .local/d01-d18-20261010/received-confirmation-android-{baseline-consumer-01,consumer-01}/、design.md、self-review.md、close-verification.json、skips.json保存设计/作者自审/独立消费与最终核对；1333-failure.md、close-parser-failure-01.md保留失败。

debug APK SHA-256 b7b07d72b942e9a5c5fe2986476cc1533b61c628800edbe431569af7e114e9ed，保存在full-01/app-debug.apk，是默认关闭开发构建，非D18候选/正式签名。C/D余量、全部dirty和Git以precommit/postcommit快照为准；[开发前看板](ORCHESTRATION_STATE_before_confirmation_android_2026-10-11.md)/[收口前看板](ORCHESTRATION_STATE_before_confirmation_android_close_2026-10-11.md)保留原结论。

下一步接入恢复资料卡显式实物确认/耐久worker与页面失败恢复，然后本人丢token新LOGIN替换、限定恢复激活→最终提交；D06/D07余项、D08–D18及全部闸门继续。不开NORMAL/普通TEMP/首轮，不借跨仓机或重启原设备，不跳过原表守恒，旧票据不可激活。生产默认/签名关闭、localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/正式签名。不重跑1150/1151/UI-06或1100–1102，原1233只读、1293UNKNOWN保留，保全/失败库不复用。
