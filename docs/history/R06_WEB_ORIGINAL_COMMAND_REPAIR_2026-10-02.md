# R06 Web原命令恢复验收（2026-10-02）

产品提交e2fa8e88883aac03b8abe64316b3b5547e2bd68c，已推送codex/development。作者与自审为本助手；无独立消费者验收。版本事实见看板生成块。

## 验收标准与结果

- 按登录用户、仓库、提交和命令类型恢复原键与完整正文；原请求保存并读回成功才POST。读/写失败、损坏记录及缺少跨页锁均阻止发送。
- 未决重试使用原JSON字节、原恢复代次、原理由与候选摘要；刷新、重挂载、事实变化或跨页并发不创建替代原意图。
- 匹配原键与类型的回执才进入完成状态；完成记录跨刷新保留。新决定须明确清理已完成记录，未决或清理失败拒绝换键。
- 身份在异步获取事实与CSRF后复核；本地按身份隔离记录。服务端授权仍是最终边界。
- TEMP审阅schema拒绝writeConfirmationAvailable=true；与现有服务端契约恒false一致，未修改生产能力默认值。

## 实际验证

| 证据 | 结果与范围 |
| --- | --- |
| web-recovery/final-2/unit | 35项通过，0失败/跳过；真实Vue hook，合成API/存储/锁边界 |
| web-recovery/final-2/regression/build.log | vue-tsc与Vite构建通过 |
| web-recovery/final-2/regression/test-temporary-review.log | 7项通过，含真实MockMvc样本消费者及权限反例 |
| web-recovery/final-2/regression/check-contract.log | OpenAPI校验通过 |
| web-recovery/final-2/regression/browser.json | Chrome 10项通过；实际组件/API模块/native localStorage/Web Locks，合成HTTP响应 |
| e2a-cross-20260925-840/result.json | TemporaryReviewContractIT 1项零失败/错误/跳过；MockMvc+真实隔离MySQL、合成身份 |
| web-recovery/closeout/git-final.json | 产品SHA、远端及磁盘回执 |

证据根.local/autonomous-20261002/web-recovery/；840另在.local/e2a-cross-20260925-840/，含样本SHA、数据库dump及认证停机记录。失败证据保留：regression-1缺样本、browser-1清理断言竞态、final-1写能力schema反例失败。

## 自审与限制

生产模块每文件少于200行；命令生命周期局部封装，避免长闭包，不引入跨业务共享基类。存储损坏采用拒绝发送；读回不是断电耐久承诺。浏览器主动清空/驱逐本地存储、跨页重新认证与服务端原键授权必须分别处理，不能据此声称现场完整恢复。

没有执行真机、现场、真实后台HTTP浏览器组合或完整服务端integrationTest；无部署、原库、现网、main/标签/Release、生产默认开关变更或.local证据删除。
