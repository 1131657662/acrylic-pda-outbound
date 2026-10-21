# 主管救援签名原件文件入口：主机消费者验证

## 范围与输入

基线408b75eea3d9f19626e5e21f2c5cfae459ce9cc8干净git archive，只叠加3个测试文件：RescueFileJvmKeyStore.kt、RescueSignedFileFixture.kt、RescueSignedSupervisorFileActivityTest.kt。产品源码、布局、权限、契约、Room迁移、服务端均未修改。5942个归档输入逐SHA保持；33份非看板继承WIP逐SHA保持且不暂存。

证据根：.local/goal-20261021-resume/credential-signed-file01。source-manifest-android03.json是最终3测试路径输入；source.zip是固定基线。design-and-impact.md记录影响层及必跑集合，review.json为作者自审，不代表独立人员审查。文档修改前原字节与SHA在document-preimage.zip/json。

## 实际产品消费者

实际执行RescueExportActivity选择主管文件按钮→OpenDocument结果回调→SupervisorMaterialFile严格读取→原FLAG_SECURE确认→RescueExportViewModel→RescueCredentials→原RS256验签/绑定与在线原件GET→原AES/GCM私有vault→退出登录、服务关闭后的只读解锁。没有替换产品校验器或保存层。

边界替身仅为主机JCA进程内随机合成EC/AES钥，以及MockWebServer合成授权服务；不是设备Android Keystore生成、原身份、正式签名、真实后端或现场回执。file://结果模拟系统选择边界，不证明外部DocumentsProvider/M71。正向无订单业务数据，验证解锁能力及其限定范围，未覆盖非空订单快照/导出。

## 运行与失败

1. android01：编译exit1，Provider构造参数用了Android API不可用的String版本重载；未执行测试。不计业务失败或通过。原gradle.log、command/result、input-overlay.zip保留。
2. android02：编译后执行8项，8失败、0错误、0跳过，exit1；共同原因是MockWebServer.url取canonicalHostName，本机名称不在原HTTP测试allowlist，全部在夹具绑定初始化被拒。原XML已在复用输出前归档android02/original-xml.zip，输入与failure-summary.md保留。只修夹具显式回环IP，不放宽产品HTTPS规则。
3. android03：同8项实际执行，8通过、0失败、0错误、0跳过，exit0。独立日志/cache/命令，init明确复用android02/outputs；正确XML归档android03/actual-original-xml.zip，SHA256 d0b689ce254228a11515dcf4d14e030a3782f28ca24f763c7103179c58f661d3。xml-summary.json逐方法列出实际执行；不是复制旧通过结果。

实际8项：有效签名文件仅显式确认后在线GET并加密保存，登录清除且服务停机仍能通过页面解锁及新实例核验原范围；有效材料取消零网络/零保存；文件自带签名根不可自授权；错设备拒绝；非指定已登录人员拒绝；线上原授权异文拒绝；已到期原授权拒绝；离线导入原撤销签名后移除选择并阻断已持有grant。原文件字节保持；口令和Bearer不写入保存的明文或业务日志。

## 结论与剩余

仅补齐[文件导入首片](GOAL_PDA_CREDENTIAL_FILE_INTAKE_2026-10-21.md)缺少的合成主机入口正向与授权反例。没有产品改动，没有新APK、安装、设备操作、数据库启动、部署/上传或更新清单；统一构建APK仍固定先前输入，不包含凭据首片。

不计完整O02/U03/U04/D17、D/O整体或第8节通过。安全生成凭据、设备外独立保管、遗失恢复、分用途失效/复用、分步导出及非空导出组合仍需开发/验证；真实M71、外部文件提供器、实际Keystore及最终候选组合另验。原库/现网、正式签名、main、部署/Release、生产开关和三取消链持续关闭，localDeletionAllowed=false。

本片清理0字节；旧审批拒绝目标不换工具/包装重试。原失败、唯一证据及最终APK保留。
