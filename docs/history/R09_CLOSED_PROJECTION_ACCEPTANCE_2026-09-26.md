# R09 关闭阶段数据库投影验收

2026-09-26。主树 `codex/development@dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `codex/r06-e2a-migration@568683c1a1f8257ad0596a654c6b3558c767ed95`，加当批未提交差异。没有 commit/push/main/发布/部署。

## 实现及自审

完成标准见 [关闭投影设计](../parallel/R09/CLOSED_DATABASE_PROJECTION.md)。V39新增安装登记、UUID到原数字代次映射、追加控制投影三表及不可改删约束；迁移不插行、不回填旧来源。`RecoveryClosedProjection`在已验固定快照租约内先锁server_state，显式登记真实MySQL server_uuid/库名与调用方固定数字基线；只有QUIESCING revision1可以首次登记。后续要求完整原链、安装/实例一致，同版同hash幂等，变代只在RECOVERING新增UUID映射到数字+1，始终RECOVERY_PENDING。

自审发现只取最大revision会遗漏中间投影/映射损坏，已改为验证完整数据库前缀、初始hash及各UUID映射原hash/首版。相同值UPDATE可能报告0受影响行，采用既有排他锁证明原行存在，不把0误判丢行。schema名称显式指定utf8mb4，不依赖数据库默认字符集。

## 当批验证

证据根：`outputs/r06-product-20260925-01a0d8fa/`。

- `closed-projection-compile-01.log`及XML：46项既有控制/租约定向全部通过；它只证明编译/相关回归，不替代投影验收。
- `closed-projection-full-01.log`、97份XML：671项=670pass、1既有符号链接条件skip、0failure/error；bootJar成功。跳过不计通过。
- attempt79：非空V34 seed→V38→V39、四阶段/并发/错库/失败回滚及HTTP兼容通过；补强前输入，保留原库/日志，不替代最终80。
- **attempt80最终输入**：专用19330 MySQL，非空V38→V39后逐列原驱动字节比对全部旧表、原文件及迁移历史前缀；三张新表均为空。随后先执行真实HTTP/浏览器原TEMP普通/短装导出兼容，再停止应用，保存投影前全表/文件快照。
- Python生成原控制记录，Java持原件租约投影到真实MySQL；QUIESCING→RECOVERING→VERIFYING→RELEASE_READY、每版新实例重读及同键重放通过。原实例跨推进拒绝；两个事务实际等待同一server_state排他锁、同时持有共享租约，原Python命令拒绝推进，放锁后只登记一份并返回同一结果。
- 原数字基线不符、错安装、实际复制库名不符、登记server_uuid被替换、同版异hash、缺投影、缺中间版、映射首hash损坏、跳版、DB模式不符、库外半指针、整个库外历史回退均拒绝。错实例为**绑定字段损坏反例**，没有声称跨第二台MySQL实例搬迁验证；错库名使用同一真实mysqld内另一实际schema。
- 六个UPDATE/DELETE约束及映射FK反例通过；投影INSERT故障导致安装/映射/投影全部回滚；Long.MAX_VALUE换代溢出不留下半映射。错误schema与原件保留。全目录回退测试将原历史移入保全目录，测试后复制恢复，保全副本未删。
- 投影前后全部原业务表逐列字节相同，包括原S/Job/审计/签名/请求/回执数字；原文件相同。仅server_state与三个新控制表变化。快照保存在服务端 `.local/e2a-cross-20260925-80/projection-*.json`，不上传原材料。
- 最终Java IT1/1、浏览器1/1，无skip；Java进程退出后该账户其他连接0、INNODB_TRX 0，认证shutdown通过。79/80均关闭，19330/19434/19435/19436空闲；共享3306 PID5448/父4412未动。
- 1004项 `closed-projection-source-manifest.json` SHA256 `b12daed8d0a48f407a9ab9d69645bea4117bcec9d0a15f033ed057e0dbcb08f5`；JAR SHA256 `c0f6472fc2232c608873f43d14f9482f4f084b7e9f64dc63efc6aa897991a652`。全套后至80无源码漂移；两树完整差异/路径见 `closed-projection-final-main/server-status.txt` 和 `.diff`。首次manifest脚本误写根settings.gradle路径而中止，纠正为实际settings.gradle.kts/server/settings.gradle后成功；未伪造文件。

## 适用范围

作者/自审为当前单助手；Python→Java为独立实现消费，真实MySQL/HTTP/浏览器为组合验证，无独立人员。新组件没有注册业务bean/路由；HTTP是原接口兼容回归，不是共享门禁已接入。无Android变更，未跑新APK/lint；真机、现场、OS断电、长时、生产审批继续未验。

接下来完成[关闭意图拒新写](../parallel/R09/CLOSING_INTENT.md)、NORMAL初始化/跨存储原命令确认和API/Session/Worker/Flyway/文件入口接线。UUID传输迁移、完整恢复、其余R06–R09与两树整合仍未完成。普通业务生产放行、七类决定/关闭能力不开放；localDeletionAllowed=false。总目标未完成。
