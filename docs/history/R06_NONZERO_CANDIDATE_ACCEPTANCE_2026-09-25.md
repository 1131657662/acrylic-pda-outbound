# R06 非零候选独立消费者验收

日期：2026-09-25。当前单助手作者/自审；Python与Java为独立语言实现，不声称独立人员审查。

## 完成标准与实现

从原始数据库投影独立计算北京接收日±7天、同仓WEB来源、车号精确匹配或产品交集；核对全部候选、顺序、工作状态、计划差异、完整指纹、HTTP分页上下文和游标。非零SHORT_APPROVE只能记独立短装批准，NORMAL_CONFIRM继续拒绝未排除候选。

- Python新增`candidate_facts.py`、`candidate_context.py`、`candidate_consumer.py`及`test_decision_candidates.py`；扩展决定消费者，保持首映射/首轮边界。
- 服务端仅扩展隔离测试投影/向量：21条候选经真实cookie/CSRF HTTP创建，覆盖首尾日期、READY与PC作业，五页每页5条。原始投影包含30条订单，其中跨仓7条、非WEB4条，类别有交叉；独立消费者自行筛选。
- 自审补强：初版只检查上下文hash一致；最终从原正文与DB状态重建完整上下文及hash，并逐页解码核对游标最后一条锚点。加入错误上下文/摘要/游标、漏行/重复/逆序、能力开放、合法重签错指纹和批准冒充确认反例。
- 服务端确认前后状态不变；修改候选修订后旧游标返回409；原决定同键返回原字节，真实HTTP原文与内部事务原响应一致。

## 当次验证

| 项目 | 结果 |
| --- | --- |
| Java compileTestJava | 通过 |
| Python纯计算 | 最终9/9通过，无跳过 |
| attempt60 | 独立非空V34 seed→V37，服务端IT1/1，Python53/53；最终上下文补强前输入 |
| attempt61最终 | 新datadir/schema/账户，非空V34 seed→V37，MySQL19330/HTTP19434，服务端IT1/1、Python54/54，无跳过；两次均认证shutdown |
| 服务端完整单测 | 577项，576通过、1符号链接条件跳过，0失败/错误；跳过不算通过 |

本批没有Android/Web生产变更，未重跑APK/lint/浏览器；Room27的实际验收仍固定到attempt59。未运行全量MySQL集成，不把历史或此次单测写成全仓端到端通过。身份与权限准备使用隔离合成夹具；真实HTTP/数据库不代表现场身份或生产审批。

## 证据与边界

- 主树`codex/development @ dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树`codex/r06-e2a-migration @ 568683c1a1f8257ad0596a654c6b3558c767ed95`。本批未提交/推送/整合/main/发布/部署。
- 证据`outputs/r06-product-20260925-01a0d8fa/candidate-*`：完整单测XML/日志、两次IT/Python日志及认证关闭标识、两树全部路径快照。向量及完整失败/运行资料保留服务端`.local/e2a-cross-20260925-60/61`；private不复制。
- 最终源码manifest949文件，SHA256 `4e2103d9ecc62280a86b117a1c103df4c6bbf84f91ae20682aeb9c0a3a7b4102`。本批完整单测在此输入运行。
- 证据整理一次默认GBK读取UTF8失败，改用显式UTF8后完成，未影响原资料。Room27旧服务端manifest漏列`server/build.gradle`：保留原manifest，不倒填历史。当前核对该文件SHA256为`335946f35f91bf1015660db8db7b81b91ead9e2fa13cb05f57f5150fa6dfe843`，只作本次补记，不能追溯证明attempt59当时的遗漏输入。
- 排除政策、关联/mapping2+、高轮次、拒绝/处置/续作仍未完成。生产七类决定handler/POST、放行和关闭能力未开放；`localDeletionAllowed=false`。
- 下一接其他未提交来源及归档历史可信校验；共享MySQL3306 PID5448未操作，下一隔离attempt从62核目录分配。
