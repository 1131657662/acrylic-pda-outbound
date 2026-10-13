# E06：原资料与独立身份固定的本地任务计划

输入树为D:/acrylic-pda-outbound、codex/development，基线23fd1e9a40d3e9ea8ba9c4c4f4fb2853cb551108。唯一写者本聊天；版本事实只见看板生成块。本页只验任务计划切片，不计E06/O04/U03、D18或总目标完成。

## 实现与边界

同一Room追加耐久创建请求、任务、逐产品计划、状态与创建审计。请求先独立耐久保存；任务、计划、状态、审计同事务，尾审计失败整单回滚。UUID原键及固定字段长度前缀摘要约束全量输入，同键重试返回原任务，新单须显式新建。

固定已启用原sku/importId/摘要、原签名凭据与验证钥、初始可信时间锚、原人机仓/恢复代次/本地登录代次。后继资料或凭据不改旧任务；已耐久请求仍引用原资料，未完成请求不能被后继凭据替换授权或续期。新任务逐次核验原身份与可信期限，接入既有UpdateGate。计划产品仅来自固定资料；未知产品、编码、控制字符、数量与总量越界拒绝。请求/任务/计划/创建审计禁止更新删除，状态禁止删除。

实际页面从独立sku入口进入，提供车号、原因、产品计划、口令核验、原请求恢复和历史分页。本项只到PLANNED、实采零、待主管对账，不产生库存可用、正式资格、提交或放行。生产能力、localDeletionAllowed=false、原库/main/发布边界保持。扫码、防重、VOID、应急文件与主管对账继续后续切片。

## 当次证据

全部路径相对`.local/goal-20261013/`，原日志/XML/截图/HTTP/数据库与失败原件保留。

| 证据 | 实际结果 |
| --- | --- |
| e06-task-android-unit-02 / 03 | 最终03的44项全通过、APK/lint成功、输入manifest无漂移。覆盖原键/异文/并发、尾回滚/重开、旧资料/后继凭据、过期/撤销/重启/换人、边界、原件不可变、非空加法迁移及安装互斥门 |
| e06-task-android-unit-04 / 05 | 仅设备测试等待/截图定位修正；构建/lint成功，单测UP-TO-DATE，不重复计执行。生产APK与03相同字节 |
| e06-task-native-provision01 / source02 | 原5562登记钥、真实API预置；实际DocumentsUI选择获授权docs/sku.txt原字节，预检和启用通过 |
| e06-task-native-create01 / restart01 | API已停且Wi-Fi/data关闭，实际页面保存计划；force-stop后新进程原请求/计划/签名/唯一审计保持 |
| e06-task-native-tail01 / tail-restart01 | 实际Room最后审计INSERT触发故障，任务/计划/状态回滚、原请求保留；另一新进程沿原键/原输入/原授权恢复唯一完整任务 |
| e06-task-native-layout10/115/13/20；visual10/115/13/20 | 320dp四字号、IME可见、输入及底部操作可达、逐行宽度通过；后组修正top截图定位。截图为应用窗口绘制，不包含系统IME，IME由实际WindowInsets断言验证 |
| e06-task-visual-01 / 02 | 作者目视原资料、已创建计划及四字号顶部/输入/底部；完整原字段换行，按钮文字可读，选择摘要省略后可打开原记录。非U01/U02首屏标准验收 |
| e06-task-identity-files-01 | 独立Python以E03保全固定外部DER/原登记根核原凭据签名、人机仓/登记设备钥，不以本地新造签名代替 |
| e06-task-native-final-01 | 完整私有app tar由独立SQLite/Python消费，两单原sku字节/计划/命令摘要/凭据签名/唯一创建审计一致；66旧Room表逐值hash守恒 |
| e06-task-history-1508/native-final-business.json | 从保全1507 dump/私有文件恢复，未复种；75业务表与私有文件守恒，仅新增预期身份凭据。1508最终dump/hash认证停机 |
| e06-task-delivery-01 | 明确路径、最终输入manifest、作者自审、继承原字节比较、暂存diff、提交/推送及final全部脏快照 |

生产APK保留在e06-task-android-unit-03/app-debug.apk，SHA256为C01659700BA895AF1ADBCFC63BE080A1326EF8B0B600977A8ED5DC9F8E7839F2。lint无Error、392 Warning，完整报告保留。非完整服务/Android套件、物理PDA、生产或最终候选验收；资料和身份的历史通过仅按各自报告引用。

## 原失败、自审与环境

unit01缺SKU_PARITY_OUTPUT造成一项跳过，runner退出1，不计通过；补原服务向量后02/03实际全通过。source01主机选择文件超过40秒，NoActivityResumedException原日志/截图/XML保留；仅测试等待改120秒后source02通过。目视发现旧top截图实际位于滚动底部，修正测试定位后补证据，生产APK未变。不删除失败，不清继承暂缓计数。

最终作者自审核对原授权及期限、请求固定/事务边界、输入与原件、页面恢复、迁移与旧表守恒、生产围栏和暂存范围；不冒称独立人工评审。独立消费者与实际模拟器证据分别标明。

预览1494库19330、API19435/worker、Vite5174及原5562/5564保留。1508来源1507保全，已dump/hash认证停机；19331/19434空闲。5562保留原Keystore、两单计划与全部原件，断网、字体恢复1.0。新增产物在D盘.local，TEMP/TMP/Java临时目录仅子进程设置，本轮无清理；精确C/D余量与所有在途状态见delivery/final。

接续SCAN/VOID：复用串行入口/UpdateGate/前台lease，同事务协调FORMAL/TEMP/独立有效guard及未决输入，独立来源序列不污染旧审计；随后独立应急导出/主管人工对账，再E07–E09及全部闸门。U01/U02、D04旧前缀及恢复暂缓计数与原失败保持；总目标active。
