# D07/R09 原TEMP正文与同一pin组合核验

输入树D:/acrylic-pda-outbound、codex/development、debc9af3391ca4f21acb9780c0f48fc1384920b0，加本批四份Java。版本事实只见[看板生成块](../ORCHESTRATION_STATUS.md)。设计和作者自审见`.local/d01-d18-20261009/native-composition-design.md`、`native-composition-self-review.md`；单助手、单写者，无独立人员审查。

## 实现和退出标准

真实TEMP冻结正文只有events，没有旧组合入口要求的auditCoverage/auditEvents。`RecoveryInitialSource.readTemporary`显式重读同一独立pin五原件，再实际核验原E2A OPEN/PAGE/STOP及全部对象，拒绝caller拼装通过Proof。原E2A身份必须与独立pin身份完全一致，只有STOP审计水位为冻结水位加一；原冻结payload hash逐字绑定，不补造或改写原正文。

原准备/ACK、逐保存资格、ABORTED终态、原capture迁移、历史权限、原占用及结束重读继续组合验证。仅首轮TEMP、完整原E2A、空签档前缀；FORMAL、高轮、混合/异来源、错误历史公钥、水位/正文、缺ACK、成功终态、撤权及竞争占用拒绝。旧read契约保持，返回证明无普通写权或删除权；无HTTP、数据库、DML、Spring配置、迁移、契约、客户端或签发变化。

## 当次验证

开发RSA/P256、合成身份与真实本机原编码文件夹具。定向01为96/1/0/0：成功终态反例签旧capture水位，先被RESULT_MISMATCH拒绝。修正夹具原水位并补FORMAL、高轮、签档前缀后，定向02为99/0/0/0、5XML、exit0/sourceUnchanged。

受影响Recovery/Draft/Evidence/Preparation回归03为3127/0/0/1、127XML、exit0/sourceUnchanged。普通test不含IT；Windows符号链接条件skip未执行。原命令、log/XML、result及源码前后hash在`.local/d01-d18-20261008/r09-native-composition-*`，失败原件保留。未重复原E2A子项的保留1151消费者，也未重跑1150/1151/UI-06或1100–1102。

作者自审核对请求限制、同一pin结束重读、E2A不可变字节、公钥显式选择、冻结/STOP水位与原正文hash，未发现待修行为缺陷。历史公钥及独立pin取得过程仍由调用方负责，开发签名不能代原登记根/身份。1151缺独立pin权限/占用，不能补造正向组合。

## 断点和接续

`native-composition-resume-04`保存30条全部dirty副本/hash/binary diff、Git/全进程/监听/磁盘；22继承逐hash一致。快照时回归在途，原session收取后已exit0；提交前重新保存最终环境。输入HEAD与实际远端一致。既有3306/13316/ADB不操作；新增证据/TEMP/Java临时/缓存继续D盘，无新数据库、HTTP、模拟器、APK或部署。

明确路径暂存检查、真实commit/push回执见`native-composition-precommit-05`；提交后的精确SHA/远端、全部dirty及环境见`native-composition-postcommit-06`。本批仅四份Java、本页及三个验收/历史/看板索引，不包含继承或先前历史补记。Git源码备份不代本机证据异地保管。

下一步故障前实际采集原准备/ACK/原设备登记根/权限连续窗口/占用/终态及独立pin；分配全新隔离编号，实际截点恢复后按原ID/正文/序号/回执闭事务重建，再接D07主管围栏、接收激活、最终提交、尾SQL回滚与重启。续期撤销、高轮/混合/跨来源及成功/取消/更正、D08–D18仍必做；原FORMAL准备缺失、原资料/旧BCD/526、失败资料和C01–C08保持。

D07/R09及总目标active、未完成。本子项是只读前置，不代表实际业务恢复。生产默认/签名及七类生产决定能力关闭，localDeletionAllowed=false；不接原库/现网、不合main、不发布部署/标签/Release、不正式签名。
