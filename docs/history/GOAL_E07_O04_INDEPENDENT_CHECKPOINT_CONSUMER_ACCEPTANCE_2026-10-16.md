# E07/O04 独立采集跨截点消费者验收

## 范围

本批只消费已经完成并停机的 `629` 隔离实例，核对 E07/O04 独立采集人工关联跨截点恢复的原件、身份、签名、附件、数据库和 HTTP 证据。没有启动数据库、服务或业务写入；626、627、628 的失败实例、日志、dump、binlog 与保全结果继续保留。本批是开发切片，不能计作 E07 L2、D14 整体、D18 L3 或总目标完成。

## 冻结输入与实现

- 工作树：`D:/acrylic-pda-outbound`，分支 `codex/development`，接管时 HEAD/远端为 `22852deeea70e5b84b0dd56ac459e5b52a295db3`；接管以来未改动既有产品源码。
- 冻结输入：`.local/goal-20261015/e07-independent-checkpoint01/resume629-consumer01.json`，5,292 项，SHA-256 `83d69a0491cd917119af41480c95e0b0d30920cf0268e45488dab56e0402debd`。
- 独立事实读取：`consumer629_facts.py` SHA-256 `6e137a4dc5f931458b53d1b41470f44496e1695088675d78514e050a8bfdd5b7`；签名与原件读取：`consumer629_signatures.py` SHA-256 `2d7d66cb7a2bada7811f235502df8e8d23e56ec1d0dd5366a3bddcab4db0eada`；总入口 `consume629.py` SHA-256 `c8c1e9d7b4dcf55e7e770fc2aa8b99d0413a3f1813003055b032bede3833d445`。
- 入口只读 `.local/e2a-cross-20260925-629` 与 `preserve-629`；新结果为 `consumer629-result.json`，SHA-256 `eb7b4ff08b9234a2c32a86e5a97f2cc94b8192420d725a91e561c86a2bbe0c6f`。

## 实际验证

命令：`python -m py_compile .local/goal-20261015/e07-independent-checkpoint01/consumer629_signatures.py .local/goal-20261015/e07-independent-checkpoint01/consume629.py`；随后 `python .local/goal-20261015/e07-independent-checkpoint01/consume629.py`，退出码 0。

- JUnit `TEST-com.acrylic.outbound.e2averify.E2aIndependentCheckpointIT.xml` 为 1/1/0/0，`exit.txt` 为 `testExit=0`，完成标记存在；本批未重新执行 JUnit，只消费原始 XML、marker、终点事实和停机保全。
- 三库 540 表、数据库 dump、原 ROW binlog 终点、迁移/触发器/文件集合及四个存储副本逐字节守恒；四阶段短装关闭、缺关联、重建、默认关闭 facts 守恒。保全结果为 3 个数据库、854 个文件、认证停机、监听数 0。
- 两份 RS256 `signedProof` 的 header/payload/签名、固定 `kid` 和业务正文绑定通过；独立采集 credential 的发行者、受众、用途、scope、能力和签名摘要通过，历史采集时效按原记录核对；`localDeletionAllowed=false` 保持。
- 原 ZIP SHA `1577709afac180638244ea37623e13d63a57a2f73c520df595a7df6ba9c0c8e6`、manifest 13 项、设备 ECDSA manifest 签名、设备 DER 和所有成员长度/SHA 通过；附件 71 字节、附件摘要、`physical_check` 关联通过。
- HTTP 状态包含短装关闭 409/403、缺关联 404、evidence-only 409、原状态 200、重建无权 403、异文 409、默认关闭 409/403；目标订单仍为 `DRAFT`，没有 submission/dispatch/result 或 submission scope。

## 失败与边界

626 缺精确短装批准、627 编译/启动短装开关、628 误查 submission 字段的原失败及对应数据库均保全；本批没有删除或覆盖失败证据，也没有把三次不同根因重写为通过。合成隔离主管、库存、实物、credential 和 JVM 证据不代表现场 PDA、原主管或生产事实；外置介质真实保管回取、设备/人工关联组合、E07 L2 和最终 D18 L3 仍未验。

## 接续

下一步复用本批输入与结果进入 E07 受影响组合验证，再推进 E08/E09 及剩余 D/O/U 闸门；最终候选仍必须实际执行 Android、真实 HTTP/MySQL、Chrome、Python 消费、非空迁移、原模拟器和恢复组合。总目标保持 `active`，生产默认、放行、删除、原库/现网和发布边界保持关闭。
