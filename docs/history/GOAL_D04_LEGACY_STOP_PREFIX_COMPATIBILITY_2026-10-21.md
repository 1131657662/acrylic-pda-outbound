# D04旧结构STOP审计前缀兼容：有界MySQL复验

本片验证已有兼容分支，没有修改产品、契约、迁移或Android行为。输入为开发提交4eb4b26ed4b4dca0d66a8ad0ae3fc58b4846b4ea，加本片两份新测试；仅合成同人同机首轮旧结构，不代表D04、E07/D14、D18或总目标第8节通过。

## 新依据与重新入队范围

旧run732的原XML第96行记录BadSqlGrammarException，原SQL无条件引用当时未安装的后续续作表。原报告与XML保留；不能把该异常概括为“没有上传完整审计前缀”，也不能由本次成功追认旧运行通过。

开发备份2ca7c660e02cbff0e170ff137087af49cfee8f36已经加入表存在性守卫，以及严格首轮temporary_work_origin/temporary_authorization绑定。本片仅检验该差异。run730–732、1043/A1.4原失败计数和其他完整前缀问题保留；没有重启旧库或重跑旧Android链。

## 输入、命令与隔离

- 证据根：.local/goal-20261021-resume/d04-schema-prefix01/。
- baseline.json、wip-before.zip保全34展开继承路径；33份非看板继承SHA收口核同。
- candidate-source.zip由git archive提取提交；两轮source-manifest.json各2622文件。新增的仅LegacyAuditPrefixFixture.java及LegacyAuditPrefixCompatibilityIT.java，未叠加PublicationDriver、PdaApplication或取消链WIP。
- diagnostic-inputs.json及diagnostic-inputs/保全旧XML、原报告、修复diff和相关迁移；failure-excerpt.json定位原异常。
- 验证设计先保存于verification-design.md。run_legacy.py执行隔离Gradle integrationTest --tests *LegacyAuditPrefixCompatibilityIT；run_unit.py执行test --tests *SourceEvidenceIntakeAuditPrefixTest。完整参数、子进程环境、结果分别在test-plan.json及unit01/command.json；密码只保存在本机新库配置，不入Git。
- 两轮各用新回环MySQL与新datadir：首轮19389/acrylic_d04_prefix_20261021_2189，复验19390/acrylic_d04_prefix_20261021_2190。连接前校验端口、库名、用户、datadir、禁binlog及空库；之后在历史目标结构植入非空合成事实，再升级至对应旧兼容结构，原表逐值哈希核同，Flyway历史新增单列。
- build、报告与TEMP/TMP/Java临时目录全部在本片；只有一个Gradle重型运行。共享3306/33060仅观察，未连接/停止；ADB仅枚举M71，没有设备操作。开发IT钥仅核文件哈希，本片不签名、不使用正式钥。

## 实际结果

| 运行 | 结果 | 解释 |
| --- | --- | --- |
| attempt01 | 13测试/1失败/0错误/0跳过，exit1 | 异来源反例复用已有client，唯一键先于产品校验拦截；不是产品拒绝通过。原source/XML/log/dump保留 |
| attempt02 | 13/0/0/0，exit0 | 改为事务内新合成origin，另用新库、新源码/build/report完整复验 |
| unit01 | 8/0/0/0，exit0 | 已有普通前缀测试定向回归；含mock，仅算普通回归，不替代真实MySQL |

13项包括：完整FORMAL+TEMP连续前缀及正确STOP正向；原无守卫SQL实际触发缺表异常；缺首事件、异文hash、较高scope轮次、异client、资格epoch/操作员、跨仓、STOP事件/操作员/资格/批次绑定错误均拒绝。变更反例均事务回滚；每项后103表内容哈希全等且原正向复读成功。没有插入或执行接收者确认/激活链。

两轮原表基线/终态、源码2622文件核同及33份非看板继承守恒见conservation.json。该消费者由同一助手以独立脚本执行，不称独立人员审查。

| 原件 | SHA-256 |
| --- | --- |
| attempt01 XML | 28f3f4a3d5f8cfd33bbdd52ff578b75d76c7bfe80cd1f73ca0aedcc0c8c1b2ee |
| attempt02 XML | 7d33005a4f9599bb978aa14fef9455edd2886b9f5a7ae1a88d646e002c1b9dba |
| attempt02 final.sql | 7eccfbbabbb13e7dd082a234bebd7f0c1c5b720274f6816bb6ff60d688d6e7c0 |
| attempt02表内容基线/终态 | 1c9e9581a2804c3bfef2043fb798e4e1aafd2c2ee9a2e4d65b9e67a55b1ba144 |
| unit01 XML | 28b273e9facb4c49e4cdbfc55d5090d6186700121131566212888d56832e24ab |

## 收口与未验

两个新库均已核身份、dump及认证停机，端口无监听。cleanup-plan.json保全本片两个合成datadir共415250588字节逐文件SHA和重建输入；清理命令在CreateProcess前被自动审批blocked by policy，删除/释放0，未换工具或包装重试。旧日志、dump、原身份、唯一证据和全部APK保持。

这是已有旧结构SQL兼容分支的定向L1证据，不是当前完整结构/部分升级表组合，也没有HTTP路由、Android Room、丢响应、完整来源上传、签名或跨截点恢复验证。原run732需要先隔离其硬编码Android路径/报告输出，再基于新假设选择真实组合；run1043完整前缀问题不能合并为已解。

总目标active且未完成。所有生产决定、原库/现网、正式签名、main、部署/Release继续关闭；localDeletionAllowed=false。治理和开发备份不计验收。
