# 主管凭据卡：原签名范围的受控故障读取

## 输入与范围

按P01、凭据交互接入和持续自主设计授权，在原离线取回页补主管专用读卡；消除生成口令的手工转录，不新增授权或延长范围。作者自审、合成主机消费者及Debug构建仅覆盖本切片，不计完整O02/U03/U04/D17、第8节或最终候选通过。总目标active、未完成；版本事实只见看板生成块。

基线03f54b565118d379b183f4777502110fd5281e07干净git archive+11路径，5979其他归档成员逐SHA核同；33份非看板继承WIP未叠加、未改、未暂存。source.zip SHA256 41bcf183f16aeaba68e64982ec2bf4a3298685fe8800d20d877d593c50b8df9c，10份原合成辅助fixture另列。证据根.local/goal-20261021-resume/supervisor-card01/，source-manifest01/final01.json、overlay-paths.json逐项绑定。

11路径为SupervisorRescueCard、原RescueSupervisorStore/RescueCredentials、SupervisorRescueCardPicker、原RescueExportActivity/ViewModel及布局、新解析/页面/边界/UiFixture测试。写前原字节/不存在记录见preimage.zip/json；设计design.md、自审self-review-final.md，文档原字节见document-preimage.zip/json。无服务端/契约/迁移/生产配置变更。

## 行为和边界

显式OpenDocument，仅本实例待处理的原选择可以消费结果。严格4096字节、UTF-8、11字段白名单、字符串/非空、固定用途/格式、摘要和口令形状；拒绝重复键、未知字段、尾文、坏编码、截断和超限。卡绑定原grantId、指定救援人、原人机仓钥及原签名授权摘要；卡不提供可信根，卡不是签名或授权。

RescueSupervisorStore同步锁内从已有vault选原条目，用其原可信根验签，逐字段核卡后仍走原unlock/PBKDF2、持久限速、撤销、可信期限及held grant有效性。无法凭卡导入/重签/恢复已撤销授权；故障取回不要求重新联网登录，沿既有有限/永久只读语义。有限授权过期或重启时间未证实仍拒绝；既有永久只读可跨重启，不假称离线知道服务端新撤权。

页面成功仅持有原只读grant，后续订单/快照/文件仍走原范围和恢复围栏；不授扫码/提交/确认/放行/删除。取消、选择改变或重建旧回调不解锁。本人、主管、独立采集与包解密入口分开；主页面busy/主管生成期间禁新入口。

沿原FLAG_SECURE；不回填口令、不保存URI或卡到Bundle、剪贴板或普通日志，不新增网络/任务/持久grant。可变数组finally清零，JSON/String不宣称全堆擦除。原卡/原资料不改，读取成功不称外置备份、物理保管或业务确认成功。

## 实际验证

android01在12:57:35–13:01:23执行同次十类70/0/0/0、exit0，无失败轮次。新解析6、页面7、边界6；原SupervisorStore9、生成Draft6、生成Activity13、签名文件Activity8、手工Dialog2、材料读取4、本人读卡Activity9。旧业务断言未削弱，原生成draft/在线导入/加密vault用于本片夹具，不伪造免口令grant。

正向原卡→所选原签名范围→清登录/停合成服务→页面解锁，口令不转录/回填，无新增HTTP；反例含七绑定字段、重复/未知/错类型/坏用途/超限/编码/读取失败、取消、选择变化、重建、旧卡替换、撤销、五次失败持久限速跨重建、有限授权到期/重启及既有永久只读重启正向。原卡及范围反例守恒。

原XML与逐方法/时间戳/SHA见android01/actual-original-xml.zip、xml-summary-final.json；XML包SHA256 a4568d10ebac3d40e8b19c7bb82cf359034570b07dfe811edc49b73289f3e8fd。命令、环境、退出码、日志和输入清单均同根保存，不以复制历史XML计执行。夹具仅测试初始化/关闭重置前片已证实的静态Application工厂，产品工厂不变，无产品仪表化改动。客户端传输语法错误未执行shell，不算业务失败。

## 构建与产物

同源build01执行lintDebug/assembleDebug，13:02:19–13:05:28，exit0；lint 0错误547警告，本片路径29项（HardcodedText26、Overdraw1、UselessParent1、SetTextI18n1），不称零警告或全是历史。Debug APK 14432502字节，SHA256 f2a1a0ed8b0d1fc476b8901e2efa1b22db2fd50bb78608c43e173a3cd805a034，保存于本片android01/outputs/AcrylicOutboundPda_app/outputs/apk/debug/app-debug.apk。

badging、Debug测试签名、命令/退出码/输出SHA见build01/artifact-audit.json和badging-final/signature-final.txt；9件旧APK/JAR逐SHA保持。未安装、上传或部署；构建不代业务验收。

## 清理与未验

只读盘点本片7处可重建缓存、4501文件、86591533逻辑字节及33保全输入/产物，cleanup01/manifest.json SHA256 dbd68a399b163033e5f3c8943ccaf2c4ae2cd442bc02e2a0f431f849312c2ea2。前片同类缓存删除已被自动审批审查拒绝；本片不继续发同类删除命令，不绕过限制。目录保留，本片释放0字节。源码、原日志/XML、原身份/签名/回执、唯一证据、最终APK及33继承均保留。

Robolectric、合成JCA/MockWebServer/file://不是实际Keystore、外部DocumentsProvider、原身份、真机或真实后台HTTP。非空订单导出、实物介质/独立保管、M71输入次数/布局和最终候选非空迁移/恢复仍未验。分用途页内复用、包解密材料/分步导出仍需独立实现及验证，不能称完整凭据整改。

生产开关、原库/现网、正式签名、main、Release、部署、APK上传/下载页/更新清单保持关闭，localDeletionAllowed=false。不恢复换机承接、多跳、接收者确认/激活或旧run03。开发备份提交/清理不作验收进展；总目标继续active。
