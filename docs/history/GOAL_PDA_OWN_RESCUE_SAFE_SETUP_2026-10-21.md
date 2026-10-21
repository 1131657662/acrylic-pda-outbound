# 本人只读救援：安全生成、回读与独立保管确认

## 范围与固定输入

基线 c90b3e8e86a10444b757522b2694e2321eb3ebfa 的干净 git archive，仅叠加 overlay-paths.json 明列的10个产品/布局/测试文件。证据根 .local/goal-20261021-resume/own-rescue-generation01；最终测试输入为 source-manifest-final02.json，resume-closeout01/input-conservation.json 核实10路径主树、隔离树与清单逐SHA一致，其他5943个归档输入保持。33份非看板继承改动逐SHA保持且不暂存。

依总目标P01和既有自主设计授权执行，不修改已定认证/授权政策。设计及理由见 design.md；修改前产品原字节见 preimage.zip/json，文档原字节见 document-preimage.zip/json。版本事实只读看板生成事实块。

## 实现及安全含义

本人设置页增加系统随机生成入口：24随机字节形成32字符口令，只读用途与主管材料、独立采集、外置包解密分别说明，原手工入口保留。草稿绑定原人员、设备、仓库、恢复代次、本地登录代次及登记公钥摘要。

步骤为 CreateDocument 显式保存敏感明文凭据卡并逐字回读→OpenDocument 重新选取本次原卡并逐字核同→本人勾选已移至设备以外且与应急包分开→沿原 RescueCredentials 在线GET核验和AES/GCM vault预置。卡含解锁口令，不能普通分享；文件回读只证明字节，设备外保管只是本人声明，应用不能证明物理异地。

应用不把卡、URI或口令保存到私有明文文件、Bundle、持久URI、剪贴板或日志；页面FLAG_SECURE。关闭草稿/结果缓冲及传给原预置的可变口令数组在对应finally清零；JVM中临时不可变String仍受GC管理，不宣称全堆可证明擦除。

原预置前加可选expected会话比较；既有单参数调用兼容。原密码规则、限速、设备绑定、验签和vault不变。遗失卡后可重新联网预置，替换后旧凭据及旧grant按原机制失效，原作业资料保持；不能离线重置或恢复旧应急包密码。预置已开始后生命周期关闭或IO失败可能留下未知结果，通用失败明确保留新旧卡/原资料并联网核对，不保证旧凭据未变。

## 实际验证

1. android01：首版同次24项，0失败/错误/跳过，exit0。随后作者自审补finally清口令、跨线程关闭标记、固定4097字节读取及清零、输入流失败关闭，增加4项反例；首轮输入与XML在复用输出前归档，不拿首轮结果替代最终输入。
2. android02：最终同次28项，0失败/错误/跳过，exit0。草稿6、生成页面9、原本人凭据4、主管签名文件页面8、本人状态页1。xml-summary-final.json列每类/方法、原时间戳和哈希；实际输出由init明确复用android01/outputs。原XML归档 android02/actual-original-xml.zip，SHA256 3f354023011e9d49a3c15b55afeeb53c93cca1b1194752ffcd0840fcc353a34e。命令、环境、开始/结束、退出码和日志SHA见command/result。
3. build01：同最终产品输入串行执行lintDebug和assembleDebug，exit0；lint为0错误、522警告。受影响路径19条警告为17条HardcodedText、1条Overdraw和1条UselessParent，未抑制或冒称零警告。APK 14332614字节，SHA256 b80f67c9c8f8626852c213680f54b1e0454d7fe1cb749d676dac143f28e1fa49，位于本证据根android01/outputs/AcrylicOutboundPda_app/outputs/apk/debug/app-debug.apk；apksigner验证为Android Debug测试证书。build01/artifact-audit.json含原lint、命令和旧统一APK/JAR守恒。未安装、上传或部署，不替代业务验收。

最终测试覆盖真实Activity回调→保存/重新选卡→人工保管确认→原在线GET和加密vault→清登录、停服务后原只读grant可用；网络只GET且零口令正文。取消、不一致/超大回读、写失败、关闭后旧卡、登录代次变化均不预置；重新设置使旧凭据及已持有grant失效；原失败限速/设备变化/主管签名反例同时回归。

边界替身是Robolectric、进程内合成JCA设备/AES钥、MockWebServer、file://选择回调；不是实际Keystore、外部DocumentsProvider、真实后端、原身份、真机或现场。测试无非空订单导出。本片为作者实现及作者自审，不称独立人员验收。

## 未完成与接续

随机卡用于设置后，故障解锁页仍需手工输入卡内口令；受控原卡读取以减少此处人工转录是下一有界改进，仍须核对原绑定并调用原只读校验器。主管/独立采集安全生成、分用途复用、包解密材料和分步导出尚未完成。M71实际输入次数、外部提供器、物理独立保管、真实Keystore、非空导出、最终候选恢复/迁移与组合另验。

不计完整O02/U03/U04/D17、D/O整体或第8节通过。旧统一构建APK固定先前订单修复输入，不含本片。仅开发备份，不安装/上传/部署，不更新下载页或应用更新清单。原库/现网、正式签名、main、Release、生产开关及三取消链持续关闭，localDeletionAllowed=false。总目标保持active、未完成。

本片清理记录独立保存；旧自动审批拒绝目标不换工具或包装重试。原失败、原资料/身份/回执、唯一证据、最终APK及继承改动保留。
