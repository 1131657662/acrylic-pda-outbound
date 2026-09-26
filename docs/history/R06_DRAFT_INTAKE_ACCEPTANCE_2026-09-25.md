# R06 未提交草稿独立原件接收验收（2026-09-25）

本项完成服务器独立原件接收，不宣称其他来源可信业务事实或完整R06已完成。两树未提交/未整合，未推送、合main、部署；精确路径见同名WORKTREE_STATUS。

## 实现和自审

服务端V37新增local_draft_evidence、receipt、item、complete四表、复合身份外键和8个不可更新/删除触发器。OPEN携带Room25原停止事务签名和metadata；ITEM接规范描述与octet-stream原字节，内容寻址文件fsync/回读后同DB事务保存描述及签名原回执；VERIFY流式重新核全集合后写原回执和完成标记。

新写遵循E→G→原用户/仓库/设备/session锁并复核身份。原键跨操作/证据/请求冲突拒绝；新key不能重复OPEN来源或重复VERIFY。恢复模式仅已有原请求重放，原回执查询需要当前pda:recover及原人/机/仓身份。原响应从DB原字节返回，no-store/nosniff。

准备资料必须匹配服务器原authorization及receipt，验证manifest原字符串SHA、可信当前/历史公钥、JWT受众/用途/身份/资格代次/版本/时间，以及原snapshot/mapping/policy。过期资格只允许资料保全；上传自带公钥不构成信任。原设备公钥来自已登记及配对身份校验。preparationRequestKey保留且规范校验，不把fixture自造请求key称为原HTTP幂等证明。

自审收紧：HTTP先查权限再限量读取（JSON256KiB、单ITEM32MiB）；每次回执重读核签名、完整字段、false能力、header来源、原请求、ITEM原DB描述和VERIFY完成marker；ITEM检查原文件，VERIFY重新核签名集合和返回统计。失败不返回成功。仅ORIGINALS_STORED、businessFactsStatus=NOT_EVALUATED；不创建S、占K、恢复扫码、确认、放行、清理。

Android生产PdaNetwork.original增加octet-stream原请求并保存原响应字节能力；没有改变既有JSON路径。新真实HTTP测试覆写登记设备key签名，不使用fixture自造key冒充登记原机。Room仍25、APK仍code20；本项没有草稿传输Room队列，测试driver内存保存原键只验证服务器重放，不声明产品原键重启耐久。

## 当次验证

- 服务端定向28/28；全套577=576通过/1符号链接条件跳过，84XML。
- Android定向46=45通过/1HTTP跳过；全套408=404通过/4专用HTTP跳过，102XML。APK及lint通过，0错误/0fatal/186既有warning。
- attempt56：独立19330 MySQL、私有凭据/新datadir/schema/user、binlog开启，非空V34 seed升级到V37；IT1/1与Android真实HTTP1/1。OPEN/ITEM/VERIFY、缺件拒绝、跨操作key/坏原字节拒绝、Room重开仍原停止签名、原响应GET/POST重放、服务器关闭重启、恢复模式只重放、未授权/跨来源/伪造签名、原键冲突、8触发器与复合FK、文件损坏409及恢复原文件200。
- 业务submission/scope/bale_commitment/dispatch/mapping/decision计数不增加。只在测试库修改恢复模式和合成原件文件，原文件另存并按原SHA恢复；未操作共享3306/原库/现网。
- 当前助手作者及自审；Java消费实际Android Room数据为跨语言组合验证，无独立人员。身份/库存准备由MockMvc合成，接收请求为真实HTTP19434，不冒称现场身份或生产回执。
- focused1因Mockito queryForList重载歧义编译失败，指定byte[]后focused2/3通过；attempt55误用RESTORING而违反既有server_state约束，整项失败；修为RECOVERY_PENDING后新attempt56全过。55失败库/日志完整保留，55/56均认证shutdown。

## 证据和未验

统一证据根outputs/r06-e2a-takeover-20260925-01a0d612，server-draft-intake-*、android-draft-intake-*、draft-intake-attempt55/56-*。完整HTTP响应和私有凭据只在服务端.local/e2a-cross-20260925-56，private不复制普通证据/Git。1315文件draft-intake-source-manifest.json SHA256 cb21f17a0ca442b68902b55997bdd4a8c4c9d0000de0a329cd646cb89da426be。

APK android-draft-intake.apk SHA256 249f423330213393a380706a422253f788f7b75df1bc9522f4f9a5d7f917fc9d。

下一子项：Android草稿OPEN/ITEM/VERIFY原键、请求与原回执Room持久化/保守恢复、产品单写者队列。其他来源业务事实/归档历史可信事实、V37完整灾后恢复语义仍未完成。真机/Keystore/OS死亡断电/现场/长时/生产权限审批未验。七类生产决定及TEMP生产归档/放行保持关闭，localDeletionAllowed=false；持续目标active。
