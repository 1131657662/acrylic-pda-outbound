# R06 正式侧 TEMP 反向候选提示验收

日期：2026-09-25。当前助手实现、自审和隔离环境验证；无独立人员复审。此子项通过，前九步目标仍active，未部署或合main。

## 实现与验收边界

- 服务端新增只读 `GET /orders/{id}/temporary-candidates`：实时order:read及仓库权限、WEB订单头/当前版本，RR快照内筛选同仓原首映射PDA_TEMP的SUCCEEDED/REVIEW_REQUIRED来源。
- 匹配车号二进制精确相同或产品ID交集；默认正式建单北京时间前后7天，按TEMP正文接收日期筛选，可完整指定不超过366天的范围。半开UTC时间边界覆盖首尾自然日；候选提示不把技术时间窗当业务政策。
- 成功优先，再按接收时间/id倒序；每页20条，返回total和实际page。每次读取独立快照，跨页候选可能变化；这不是冻结候选全集或排除证明。原人/机/私有正文和服务器内部路径不在此摘要返回中。
- OrderService.create/revise、OrderReleaseService.publish共同返回的OrderViews.Receipt增加稳定temporaryCandidateCheck入口，旧原操作身份/版本/状态保持。动态候选独立GET，不混入幂等回执。PC START原保存回执增加同一入口（包括DRAFT嵌套发布）；旧已存PC回执保持原字节且可能无此字段。
- Web正式订单详情和PC作业页面实际显示候选，在建单、发布、READY修订、PC开始及刷新后重读，核返回orderId/revision/version。失败和版本变化明确显示“尚未核对”，不会显示空列表；可调整日期、翻页、跳到原临时单。成功记录优先和300px内部滚动已截图自审，避免候选挤走录入区。
- 同车号/产品只提示人工核对，不自动阻断、合并、关联，不修改原TEMP成功或账务；正式已作业则提示保留双方证据进入差异/更正。无作业重复仍按既有作废权限处理，不新增取消、更正或生产决定能力。

## 当次验证

证据根：`outputs/r06-e2a-takeover-20260925-01a0d612/`。

| 层次 | 实际结果 |
| --- | --- |
| 定向模型测试 | 3/3：北京时间边界、完整范围/上限、稳定回执入口 |
| 服务端最终全套 | 78 XML，494项：493通过，1 Windows符号链接条件skip，0失败/错误；`server-candidates-final.log`、final-results及JSON。调整排序后重新运行，不借用旧491/494结果 |
| Web最终构建 | vue-tsc与Vite通过；`web-candidates-layout-final-build.log`。仍有既有大chunk警告 |
| 契约 | 服务端树0.33.0/128路径，主树121路径均校验通过。主树尚未整合服务端E2a路径，保留其原版本/品牌变更；只同步本项新增契约 |
| 独立MySQL/HTTP | attempt41服务端IT 1/1，通过ACL/datadir/schema/user/19330隔离非空V34 seed→V36；真实cookie登录、CSRF、正式写入及GET，TEMP成功由内部真实确认服务建立 |
| Chrome组合 | attempt41三个用例3/3：候选往返分页/成功优先/失败恢复/错版本拒绝、READY修订刷新、新建→发布→PC开始与提示；真实19434 API，经19436 Vite代理，无Mock业务响应替代正向组合 |
| 作者自审 | 最终源码hash、两树diff --check、实际截图。无独立人员复审 |

attempt41：`D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-41`。XML SHA256 `7DB35D46B1998176010A065FC4CADB523958F3DC9D6EF695060A6D66243237A9`。
最终服务端/前端hash：`server-candidates-hashes-final.json`、`web-candidates-hashes-final.json`。截图在attempt41/browser；不复制private中的账号/密钥到普通证据或Git。

真实HTTP断言覆盖：建单/发布/READY修订/PC嵌套发布成功且回执重放不变；21个成功/待审来源不因两产品JOIN倍增；无匹配、仅车号、仅产品、跨仓成功排除；未登录/无权限/异仓对象拒绝；日期开始/末端毫秒、非法范围/页码；已有正式作业提示；原TEMP结果/回执/决定/审计/库存/dispatch/ledger/outbox不变。浏览器负向错版本响应使用route修改真实GET响应，仅用作客户端拒绝测试。

## 失败与限制保留

- attempt34：localhost明文HTTP不回送Secure cookie，登录CSRF 403。修复仅该测试启动参数，生产Secure默认不变。
- attempt35：测试误把项目跨仓404期望为403；修正断言。
- attempt36：服务端HTTP通过，尚未加入浏览器。
- attempt37：2/3浏览器通过，脚本点击隐藏checkbox失败；改用可见确认文字。
- attempt38：2/3浏览器通过，脚本漏填既有PC原因要求；修正夹具步骤。
- attempt39：1/1+3/3通过，但自审改进成功优先与表内滚动，作为历史。
- attempt40：2/3浏览器通过，返回首页精确文本定位器失败；按实际可访问分页名称修正。
- attempt41最终1/1+3/3通过，全部attempt均认证shutdown，失败库/日志不清理。

本项支持现有首映射TEMP成功与待审，不宣称未来mapping2+/更高轮次已完成。日期筛选、匹配摘要和实时分页不证明无重复；长时大量数据性能/人工业务判定/独立人员/现场/真机/生产审批未验。Android未改，沿Room23历史验证引用，不冒称本轮重跑APK。生产七类决定、TEMP确认/归档产品写入口、放行/关闭/删除仍关闭，localDeletionAllowed=false。

## Git与下一项

主树codex/development @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树codex/r06-e2a-migration @ `568683c1a1f8257ad0596a654c6b3558c767ed95`。origin均GitHub 1131657662/acrylic-pda-outbound。全部未提交路径见 `R06_TEMPORARY_CANDIDATES_WORKTREE_STATUS_2026-09-25.md`；已有out.xlsx等保留，未提交/推送/整合/部署。

下一接独立协议消费者验证真实NORMAL_CONFIRM/SHORT_APPROVE的businessDecision及嵌套成功回执；现有R06_LEDGER_PROTOCOL仍只接受INTERNAL_TEST_ONLY。继续内部验证，不开放生产handler。后续R09归档V2三文件/决定引用恢复、E2a剩余来源恢复及R07–R09逐项推进；非零候选排除/拒绝权限不臆定。
