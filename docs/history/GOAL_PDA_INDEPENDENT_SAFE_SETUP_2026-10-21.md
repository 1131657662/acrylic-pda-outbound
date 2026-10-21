# 独立采集凭据：安全生成、回读与独立保管声明

## 输入与范围

依据P03、[凭据交互输入](GOAL_PDA_CREDENTIAL_UX_INTAKE_2026-10-21.md)及持续自主设计授权，补既有独立身份预置页的有界交互，不修改已定认证、期限或恢复政策。基线b432bb3356a5973bb00238b3cf133325edf06922干净git archive，叠加overlay-paths.json明列12路径，5956其他归档输入逐SHA保持；33份非看板继承不叠加、不暂存且核同。版本事实只见看板生成块。

证据根.local/goal-20261021-resume/independent-setup01；设计design.md、修改前preimage.zip/json及model/provider补充原件、source-manifest-final02.json和input-conservation.json保存原始输入。初始10路径增补状态回报与专用合成JCA夹具，两项在写入前分别登记原字节/不存在；不隐瞒范围变化。文档修改前document-preimage.zip/json保留。

## 实现与边界

IndependentCredentialActivity新增独立用途安全生成入口。专用内存草稿生成192位随机口令、32个URL-safe字符，绑定原人/设备/仓库/登录代次/恢复代次/人员与设备权限版本/登记公钥摘要；敏感卡仅显式CreateDocument写出并逐字回读，再OpenDocument重新选择核同，人工勾选设备外独立保管后才能沿原IndependentCredentials.provision预置。文件回读只证明字节，不证明物理外置。

新草稿在prepare或POST前沿原IndependentCredentialContext.requireIntent复核绑定；POST仍只有原设备/钥摘要、原幂等键，随后原签名核验、加密vault、可信期限及持久限速/撤销。卡不是签名或授权，不改变48小时原期限，不授出库提交/确认/放行/库存可用或删除权，独立记录始终待对账；不能用于本人只读救援、主管授权或文件解密。旧手工备用调用保持兼容，普通作业未增加新前置。

设置期间禁止旧预置/核验/撤销操作；取消或回读不匹配不准备请求、不POST、不替换旧身份。关闭草稿不能用旧卡恢复；原登录/版本/钥变化及恢复围栏拒绝。预置已经开始后的失败/页面销毁可能结果未知，提示保留新旧卡和原资料重新联网核对，不推定旧方式仍有效。遗失只能重新联网核对设置，不支持离线重置或旧包密码恢复。

FLAG_SECURE；不存URI/口令/卡到Bundle、私有明文、剪贴板或日志，不上传口令。变长数组和读缓冲清零，但JVM临时不可变String由GC管理，不宣称全堆可证明擦除。本人和独立流程各自命名，不抽取带用途模式的万能凭据服务。

## 实际验证

- android01：7类44项，3失败/0错误/0跳过，exit1。parity缺少E06_CREDENTIAL_VECTOR_OUTPUT在输入门拒绝；两项取消断言未等待主Looper关闭。原输入overlay、原XML/日志、退出码及failure.md保留，产品未为这三项修改。
- android02：修运行器绑定既有服务生产者原向量，修取消等待并加旧草稿/恢复围栏反例；最终同次7类46项，0失败/错误/跳过，exit0。独立草稿6、页面11、原store7、签名3、服务向量1、本人生成9、本人卡9。原方法/时间戳/哈希见xml-summary-final.json。
- 第二轮明确复用android01/outputs；首轮XML/输入已先行归档，最终原XML为android02/actual-original-xml.zip，SHA256 0e97158b9356578139f8623dc761dc00466f81058644476f343f0f0740227888，不能复制旧XML算当次执行。
- 原服务向量来自.local/goal-20261013/e06-identity-server-unit-03/server-original-vector.json，2618字节，SHA256 9fe81d5926ae7ac3798c2199733f8f8b254adf482b37065d0663fc1dc2b5be05；本片复制后逐字核同，未用Android合成替换。
- build01：lintDebug及assembleDebug实际exit0；lint 0错误534警告，受影响路径22项（HardcodedText20、Overdraw1、LabelFor1），不称零警告或全为历史。Debug APK 14,373,871字节，SHA256 b51957915da426f71571b71d13384e941246ff2c655f29510f675d62f52c0479，保存在本片android01/outputs/AcrylicOutboundPda_app/outputs/apk/debug/app-debug.apk；badging、测试证书和命令输出见build01/artifact-audit.json/signature.txt。未安装/上传/部署；此前6件APK/JAR逐SHA保持。

页面正向实际经历生成/显式保存/重新选择/保管声明→原POST/幂等头→原RS256验签/AES-GCM vault→停合成服务后原离线核验，无口令上传。取消新设置保旧密文和原口令，旧授权被新预置替代、签名错仓/换登录/版本/恢复围栏/旧草稿及重启仍拒绝；卡不能被本人卡解析器消费。主机边界为Robolectric、进程内随机合成JCA、MockWebServer及file://选择结果；不是实际Keystore、外部DocumentsProvider、原身份、M71或真实后端HTTP，也不覆盖非空独立采集/导出。

## 切片收口清理

仅本片新建8处可重建Gradle缓存与编译中间目录，PowerShell 7完整枚举4454文件，逐文件SHA/大小、命令/依赖配置/退出码和重建说明保存于cleanup01/manifest.json，SHA256 0d0a98815c218b69315f4dc53c08221a722ca387783e44ecb043c4ffb677fbcb。核无Java/Gradle/AVD/Python消费者、绝对路径和reparse后单一shell显式删除，exit0。逻辑清除89,299,817字节；D盘余量5,623,193,600→5,722,124,288，观察增量98,930,688字节，二者不混同。33项重建输入/产物删前删后核同；源码、原XML、日志、APK、原向量、任何数据库/身份与33份继承不清。没有重试旧拒绝目标，没有重复计算前片或历史空间。

## 未完成与接续

此片只覆盖独立凭据生成/预置；后续使用仍通过原手工核验入口，受控读卡、主管安全生成/分用途复用、包解密材料及分步导出还需实现或验证。真实输入次数、介质保管、外部提供器、原身份、非空数据、最终整合输入恢复/迁移及D/O/U组合另验。开发备份不计完整O04/U03/U04/D17或第8节通过；总目标active、未完成。

不安装/上传/部署、不改下载页或更新清单；原库/现网、正式签名、main、Release、生产开关及已取消三链继续关闭，localDeletionAllowed=false。原资料/身份/回执/唯一证据/原APK及33继承保持；清理另记，不重试旧自动审批拒绝目标。
