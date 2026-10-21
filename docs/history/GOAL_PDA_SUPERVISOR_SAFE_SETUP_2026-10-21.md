# 主管救援凭据：专用随机卡安全生成与保管确认

## 输入与范围

P01及持续自主设计授权下，补原主管签名导入的专用随机卡生成/保存/回读/保管确认，保留手工及撤销入口。作者自审、合成主机消费者和Debug构建只覆盖本切片，不计完整O02/U03/U04/D17、第8节或最终候选通过。总目标active、未完成；版本事实只见看板生成块。

基线62de9015180d004c4e16679bfe0d92b72c2fb0a5干净git archive，source.zip SHA256 c82a68c59bf74cd78db8230faea6d2a18e97524e11d665cccc175ee000441fd8；首轮12路径，补生命周期夹具后最终13路径。5970其他归档成员逐SHA核同，10份原合成辅助fixture独立列出。33份非看板继承WIP未叠加、未改、未暂存。

证据根.local/goal-20261021-resume/supervisor-setup01/。范围overlay-paths.json、输入source-manifest01.json及boundary01/input.json、最终source-manifest-final01.json；设计design.md、自审self-review-final.md。源码写前原字节及不存在记录见preimage.zip/json、viewmodel-preimage、import-layout-preimage、review01与boundary01；文档原件见document-preimage.zip/json，全WIP见resume01/wip-original.zip与snapshot.json。

13路径为RescueCredentials、SupervisorRescueSetupDraft、RescueExportActivity/ViewModel、RescueSupervisorImportDialog、SupervisorRescueSetupFlow/Dialog、两dialog布局、新Draft/Activity/UiFixture测试及原RescueSignedFileFixture的受控生命周期辅助。原手工importSupervisor双参数接口保持；无服务端、契约、迁移或生产配置变更。

## 行为与安全边界

先用原在线会话可信根核验原签名和指定救援人、设备、仓库及登记钥，不接受材料自带公钥作根；拒绝将撤销原件转成新口令。生成192位随机本地解锁口令，专用卡绑定原grant、原人机仓钥、指定救援人与授权摘要。卡不含Bearer、私钥或签名原token，不代表新授权。

显式CreateDocument保存目标并回读，重新OpenDocument逐字核同，4096字节上限；人工声明设备外独立保管后，才走原在线GET、原授权逐字核同、撤销/期限、PBKDF2与加密vault。不改变原签名、订单范围、期限、限速、恢复围栏或权限；不授扫码、提交、确认、放行、删除。相同授权成功预置替换旧本地口令并使旧grant失效，其他授权/原资料保留。

本片draft在原流程前、GET后、写后复查会话/登录与恢复代次、权限版本、原钥及未关闭状态。写后变化沿原revokeLocal收紧；不宣称能原子恢复旧口令。失败提示保留新旧卡和原件、重新联网核对，不推定旧方式仍有效。取消/重建不自动预置或恢复草稿；在途销毁后不能继续预置。页面生成期间锁原动作及迟到文件入口。

FLAG_SECURE、敏感输入不保存；不保存URI、随机口令或grant到Bundle/普通日志，不回填口令。可变数组finally清零；JSON/String不宣称全堆擦除。目标回读不证明物理外置或介质可取回。卡内口令目前供原手工解锁入口使用，主管卡的故障读卡消费者另片实施。

## 本次实际验证

| 运行 | tests/failures/errors/skipped | 退出码与范围 |
| --- | --- | --- |
| android01 | 39/0/0/0 | exit0，首轮六类，12路径输入 |
| android02 | 42/0/0/0 | exit0，补重建/在线撤销/GET在途销毁后，同次六类实际执行 |

最终12:35:51–12:38:39，Draft6、Activity13、原签名文件Activity8、原手工Dialog2、原SupervisorStore9、原有界材料读取4。本片及受影响旧断言均未削弱；XML方法/时间戳/SHA见android02/xml-summary-final.json。原XML包SHA256 67e00a3761e4f67f0e5227a58d258db8fc8694ba747e7d0fa7e7e26910c9efb9。两轮command/result/log和首轮XML/test-results独立封存，不拼作一次运行。

正向真实产品页面签名文件→专用卡→保存/回读→保管声明→原在线授权→加密vault，停止合成服务/清登录后仅原签范围可读；GET无body且不上传口令。反例覆盖错根、人机仓钥、撤销材料、会话/恢复/权限变化、超限/错卡/取消/旧卡、在线异文/到期、旧口令和held grant替换、取消保留旧vault、重建与在途销毁。销毁用成功HTTP响应、请求进入latch及lifecycle Job完成观测，不以固定sleep推定完成。

原RescueSignedFileFixture只补recreate/destroy及close幂等，不改旧业务断言。本片UiFixture初始化/关闭重置前片已证实的静态Application工厂，仅测试隔离，不改产品工厂。无产品仪表化修改。几次客户端传输语法错误未执行shell/Gradle，不算业务失败或测试执行。

## 构建与产物

同源build01执行lintDebug/assembleDebug，12:39:36–12:42:52，exit0；lint 0错误545警告，本片路径14项（HardcodedText13、SetTextI18n1），不称零警告或全部历史。Debug APK 14419206字节，SHA256 5adde793278561d1663a793b151974fe72e4390f76cce445e67dcd0eedfaa166，保存于本片android01/outputs/AcrylicOutboundPda_app/outputs/apk/debug/app-debug.apk。

badging、Debug测试签名、命令/退出码/输出SHA见build01/artifact-audit.json及badging-final/signature-final.txt；此前8件APK/JAR逐SHA保持。未安装、上传或部署；构建不替代业务验收。

## 清理及未验

仅准备8处可重建Gradle缓存清单，4502文件、90040645逻辑字节，35项输入/产物保全；manifest SHA256 4c87179c0cf60e6715eb139057fd680f2bc6be36a5ca5aba2010323864845410。Windows PowerShell长路径枚举失败在删除前；host pwsh只读清单成功。实际删除被自动审批审查在CreateProcess前拒绝，返回blocked by policy，无更细理由，不换命令重试目标。清理未执行、本次释放0字节；目录、失败说明和证据保留于cleanup01/02，不重复计历史清理量。

Robolectric、合成JCA/MockWebServer/file://不是实际Android Keystore、外部DocumentsProvider、原身份、M71/物理介质或真实后台HTTP。本片无非空订单导出、实际保管/输入次数、最终候选非空迁移与恢复组合。主管故障读卡、分用途页内复用、包解密材料与分步导出仍未完成；凭据整体整改及总目标未完成。

不恢复换机承接、多跳、接收者确认/激活或旧run03。生产开关、原库/现网、正式签名、main、Release、部署、APK上传/更新清单保持关闭，localDeletionAllowed=false。原资料、身份、回执/签名原件、唯一证据、最终APK及33继承保持。提交属于开发备份，不作验收进展。
