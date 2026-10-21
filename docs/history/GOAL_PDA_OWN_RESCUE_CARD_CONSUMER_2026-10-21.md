# 本人凭据卡受控读取：原只读校验器消费者

## 输入及范围

基线288cc879b0aba1b298b11725e6e23b64f33746b5干净git archive，仅叠加overlay-paths.json明列8个产品、布局及测试路径；5951个其他归档输入逐SHA保持。33份非看板继承WIP逐SHA保持、不暂存。证据根.local/goal-20261021-resume/own-rescue-card01，最终输入source-manifest-final02.json，核同input-conservation.json。修改前产品原字节preimage.zip/json、文档原字节document-preimage.zip/json均保留。版本只见看板事实块。

沿P01及既有自主设计授权接续[安全生成](GOAL_PDA_OWN_RESCUE_SAFE_SETUP_2026-10-21.md)，补故障解锁时受控选择原卡，避免人工转录系统随机口令。原卡是敏感明文口令载体，不是签名、授权或可信事实，不与主管/独立采集/文件解密合并。

## 实现及安全边界

实际RescueExportActivity的OpenDocument选择→OwnRescueCard严格UTF-8和4096字节有界读取→单JSON对象/全字符串白名单/拒重复键、额外字段、尾随正文、错误格式及口令形状→核选中本人、原设备/仓库/登记公钥摘要→RescueExportViewModel调用原RescueCredentials.unlockChoice→原PBKDF2、限速、加密vault、grant.recheck及Room只读订单列表。文件自身不能创建或恢复预置权限。

选择开始只保留本人choice ID，返回时选项变化、操作忙或已解锁即拒绝。页面重建不恢复pending卡请求，必须重新显式选择。取消不核验，保留手工备用。离线使用不要求重新联网登录；没有修改原离线授权、恢复代次或撤销可知性政策。

不持久卡、URI或口令到Bundle、SavedState、私有明文、剪贴板或日志；不上传口令。缓冲、卡持有数组、交给原校验器的数组分别清零；JVM临时String由GC管理，不宣称全堆可证明擦除。失败统一业务提示，不回显提供器错误/URI/正文。FLAG_SECURE保持，使用后仍应在设备外独立保管。

## 实际验证

1. android01：7类同次40项，0失败/错误/跳过，exit0。原XML及输入归档original-xml.zip、input-overlay.zip。随后作者自审补取消后重试、页面重建丢弃pending、主管choice不得复用本人卡入口，测试夹具改为自己的可重建Activity controller；产品输入未变。
2. android02：最终7类同次42项，0失败/错误/跳过，exit0。卡解析5、卡页面9、生成草稿6、生成页面9、原本人凭据4、主管签名文件页面8、本人状态1。xml-summary-final.json逐方法/原时间戳/哈希。init明确复用android01/outputs，旧XML在复用前归档；正确原XML为android02/actual-original-xml.zip，SHA256 3b2152029a38e7dbf6ae7401ff1efe13b2ab1374380bcac5d6605cc55829a046。命令、环境、日志SHA和退出码见command/result。
3. build01：同一最终输入实际执行lintDebug及assembleDebug，exit0。lint为0错误524警告；受影响路径27项（HardcodedText24、Overdraw1、UselessParent1、SetTextI18n1），不称零警告或全部历史警告。Debug APK为14,345,366字节，SHA256 35d88bd8d83bb5f2d9b7cea8dc9eb925ca999594f4d19b512adc578324fd8d0d；保存在本片android01/outputs/AcrylicOutboundPda_app/outputs/apk/debug/app-debug.apk。badging、签名验证、命令及哈希见build01/artifact-audit.json；仅Android Debug测试证书，未安装、上传或部署。

正向由系统生成卡、真实保存回调、原在线预置产生，再清登录/停合成服务，通过真实故障解锁Activity读卡成功，无长口令输入、无新增网络、原卡字节保持。旧卡替换、撤销、错设备/人员/仓库/钥、错误格式、超大及错误口令保持拒绝；伪造口令五次后正确原卡也受原持久限速约束。页面重建收到旧结果不解锁，重新选择可解锁；主管身份入口拒绝且不打开选择器。

边界替身仅Robolectric、主机进程内随机合成JCA设备/AES钥、MockWebServer及file://选择结果；不是实际Keystore、真实后端、外部DocumentsProvider、M71或原身份。正向无订单数据，验证只读能力和原范围，不覆盖非空订单导出。作者实现/自审和消费者执行分列，不称独立人员验收。

## 收口清理与保全

仅清本片及本人安全生成片新建的16处Gradle缓存、generated/intermediates/kotlin/kspCaches/tmp；源码、归档输入、原XML、日志、APK、原资料、身份、数据库和继承改动均不在目标中。原清单因Windows长路径漏列部分文件，Windows PowerShell首轮枚举失败、第二轮计数拒绝均发生在删除前且释放0；保留原脚本和失败记录，不把校验失败冒充已清理。

PowerShell 7重新完整枚举8805文件，原7638条逐项核同，补全文件SHA与大小后重新核目标绝对路径、reparse point和活动进程，按原16目录单一shell显式删除，exit0。清除逻辑字节177,378,198；D盘余量5,677,129,728→5,872,979,968，观察增量195,850,240字节（卷变化受分配粒度及同期活动影响，不将两种口径混同）。清理清单、执行脚本、命令和结果见cleanup01/；完整清单SHA256 48abfa1b85e68c5873c23ef445667c733e581ba87caea1b7e224fff117b09700。44项重建输入及保护产物删前/删后核同；不重试旧自动审批拒绝目标，不重复计算历史9.44GiB。

收口再次核对8路径主树/隔离源码、5951其他归档输入、33继承WIP及6件APK/JAR产物全部保持，见resume-closeout03/fresh-conservation.json；不机械重跑未受影响的已通过测试。开发备份只纳入8路径及本报告、两索引共11路径；看板与33份继承不暂存。

## 未完成与接续

本人安全生成与原卡解锁的主机有界路径已有证据；主管/独立采集安全生成、分用途复用、外置包解密材料及分步导出仍未完成。M71实际输入次数、外部提供器、物理独立保管、原身份、真实Keystore、非空导出、最终候选恢复/迁移与组合另验。本片不计完整O02/U03/U04/D17、D/O整体或第8节通过；总目标active、未完成。

仅开发备份；不安装/上传/部署、不改下载页/应用更新清单。原库/现网、正式签名、main、Release、生产开关与三取消链持续关闭，localDeletionAllowed=false。原APK及唯一证据保留；清理按独立记录核对重建条件和消费者，不重试此前自动审批拒绝目标。
