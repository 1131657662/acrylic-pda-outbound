# D07/R09 故障前原设备登记根采集与pin读取

输入D:/acrylic-pda-outbound、codex/development、fa61ec259bde333850d7a8811109c1329ec278fc，加本批三份产品/四份测试Java。版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。设计和作者自审在`.local/d01-d18-20261009/device-registration-design.md`及`device-registration-self-review.md`；单助手、单写者，无独立人员审查。

## 范围和退出标准

`RecoveryDeviceRegistrationCapture`是显式故障前只读接口；独立repeatable-read事务读取实际数据库UTC时间、实例UUID/schema/epoch、原设备登记时间/版本/status/TEMP标志、公钥及原Base64文本hash、完整配对审批。预期原实例/schema/epoch、仓库和设备必须显式匹配，非NORMAL、嵌入既有事务、未知设备或错仓拒绝；不接受caller历史时间，不读取私钥，不写业务库。

`RecoveryDeviceRegistration`从库外根和独立pin读取原canonical观察，不连恢复DB、不自动取得pin。原实例/schema/epoch、身份、原登记时间和独立提供的notAfter上限、公钥文本hash/P256以及配对原Approval.binding均重验；control前后重读，恢复集合/control/checkpoint路径拒绝。`RecoveryDeviceRegistrationFacts`只做严格语义核验，缺字段/坏公钥/错审批/晚观察拒绝。输出字节防御复制，Proof无普通写权或删除权。

直接登记和COMPLETED配对登记均支持；登记hash是Base64文本hash，不能用E2A DER keyId代替。历史DISABLED仍能保存公钥，不赋今日资格。原登记观察不证明连续权限、库存占用、终态或现场身份；notAfter必须由可信调用者从原资格/事件取得。本接口无Spring bean、HTTP、迁移、契约、客户端、签发或生产配置变化，原TEMP消费者契约保持。

## 当次证据

- 定向01为77/0/0/0、3XML、exit0/sourceUnchanged：非空native组合消费独立pin公钥、直接/配对原审批与数据库/身份/时间/目录等反例；使用合成身份/控制和内存开发RSA/P256。
- 跨33树核对并分配全新1156；真实MySQL首轮1/1/0/0、sourceUnchanged。作者夹具误写warehouse不存在的created_at，尚未执行采集器。原XML/log/dump/datadir/binlog及脚本hash在device-registration-mysql-1156、r09-device-registration-mysql-1156和e2a-cross-20260925-1156，认证停机/原PID退出、端口空，失败库不复用。
- 修夹具后重新跨树分配全新1157；真实MySQL 1/0/0/0、1XML、exit0/sourceUnchanged。直接/配对实际时间观察、显式实例/未知设备/错仓/嵌入事务/恢复模式反例，四条非空submission等全量表/私有文件与各采集前快照一致；输入是隔离合成身份/开发P256/合成control，未实际业务恢复。
- Node独立消费者01遗漏canonical协议终止LF，exit1失败脚本和归因保留；02修后exit0，7份输入hash不变。逐原字节canonical/pin/P256/文本hash/配对绑定、原数据库device逐列、全表/文件/schema/历史快照核验，仅读公钥、不连接DB。独立工具不等于独立人员或异地介质保管。
- Recovery/Evidence/Devices/Pairing受影响普通回归02为3115/0/0/1、122XML、exit0/sourceUnchanged。普通test不含IT，Windows符号链接条件skip未执行。原命令/log/XML/source前后hash/result在`.local/d01-d18-20261008/r09-device-registration-*`；消费者在`.local/d01-d18-20261009/device-registration-consumer-1157-*`。

1157 dump独立重算一致、认证停机/原PID退出、目标监听空；retention和dump-result在device-registration-mysql-1157，实际DB原文件在e2a-cross-20260925-1157。两份原观察及独立pin、全表快照、原材料目录指针分别为registration-direct/paired.json相关文件。本批无真实HTTP/Android/Web/模拟器/APK/真机/部署；1150/1151/UI-06及1100–1102未重跑。

## Git和接续

device-registration-resume-07保存当时全部33dirty副本/hash/diff、Git/进程/监听/磁盘，22继承逐hash一致；本聊天唯一写者，既有3306/13316/ADB不操作。新增证据/TEMP/Java/cache及全新隔离产物留D盘；必要失败资料保持，无清理。本机证据未异地备份，当前消费者只证明本地原文件可读取。

明确暂存11条路径、检查、commit/push回执见device-registration-precommit-08；提交后精确SHA/远端、全部dirty和环境见device-registration-postcommit-09。产品/测试输入在有效回归后未变；后续纯Git断点更新不机械重跑已验范围。

下一步实际原权限连续窗口及原占用/准备/ACK/终态采集与独立pin保管。必须有实际变化界限和可信原历史，不能由恢复后的两份观察或未撤权快照填满权限窗口。然后全新隔离截点恢复→原ID/正文/序号/回执闭事务重建→D07主管围栏、接收激活和最终提交、尾SQL回滚与重启。登记根接口不代这些必做项。

D06/D07/R09、D08–D18和总目标active、未完成；D04/D05政策缺口、原FORMAL准备缺失、原资料/旧BCD/526、失败/恢复限制及C01–C08保持。生产默认/签名和七类生产决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。
