# 部署工具口径统一与固定候选复验

日期：2026-10-20。上游备份64eaf491；独立树.local/worktrees/deploy-remediation-20261020，短期分支codex/deploy-preflight-20261020。本报告不代表部署授权。

## 工具修复

- release_facts只从契约、Android默认配置、Room源码及完整连续迁移序列推导版本；校验器/激活器/测试夹具/打包器不再各自固定旧口径。版本事实见看板生成块。
- 后端打包器只接受干净提交，记录完整tracked sourceHashes、独立source-sha256.json及同SHA成功构建回执；拒绝构建输入或JAR/renderer变化。不打包本地凭据/私钥/数据库/原始样本，不代发布Web/APK。
- 包内保留版本事实的必要源码，核对迁移文件与JAR内字节、server及renderer同一JAR；artifactHashes覆盖除manifest本身外每份文件，拒绝遗漏、多余、路径穿越、软链及重复JSON键。manifest与归档各有外部SHA回执。
- 装配report-rendering/acrylic-report-rendering完整installDist及与其Playwright依赖一致的Linux Chromium/Headless/FFMPEG目录；systemd两单元指向包内browsers。包tar设置脚本及ELF可执行位，不复制Playwright本机注册链接或缓存。
- 激活器先完整校验，再取包内推导schema核对数据库；验证失败发生在服务变更前。仍保留维护锁、API先启、readiness通过后启worker与失败停写。

## 探索及保全

租约/审计六类探索38/0/0/0。第一次激活自测4失败因校验器导入写__pycache__导致完整文件集不一致；改为-B/禁止字节码，原失败日志保留。早期脚本字符串解析失败发生于执行前，无源码副作用。下载Linux浏览器到D盘任务目录，实际about:blank截图exit0，只证本机浏览器运行，不能替代Linux服务器报表或API/worker。

后续结果统一绑定冻结提交；此处不拼各探索轮次作为候选通过。所有原失败日志/XML保留，未清库/未操作开发共享MySQL。

## 真实数据闸门

本地可见r02-original-upgrade为Android私有备份，不是MySQL备份；r05-v28-stage/test-v34-20260922-complete仅保存远端演练脚本、结果及少量产物，不能据此在本机重建可信非合成数据库。大量e07及d01/d18隔离测试库来源是合成夹具，不用于满足本轮真实数据要求。未连接共享13316、系统3306/33060或任何远端。

用户已要求由本会话只搜索本机既有备份、隔离库和设备归档，不连接数据库或导出新数据；如找到符合非合成、已授权、来源可证明的候选，先列清单等用户确认再使用。材料未确认前，真实非空升级、备份还原、真实API/worker均保持“未验/阻断”。详见本轮本地副本溯源报告。

## 冻结候选执行标准

一次冻结输入同时执行完整server test（不含IT）、bootJar、renderer test/installDist、Linux部署单测和服务检查脚本，保存命令、退出码/XML、原样本/字体SHA及完整tracked source清单；用成功构建回执生成不可变候选，原地与独立解包重新校验全部清单与SHA。失败/跳过各自列示。真实数据库和test部署授权另列，任一未满足仍不放行。

首轮冻结a95d6632的Linux部署18项有1项激活夹具超时（15秒），其余17项完成，exit1，原日志保留；未判业务通过。该轮在D盘WSL跨文件系统且并行全量构建下执行，后续仅将测试子进程等待上限调整为60秒，停写/旧链接/服务顺序原断言保持；产品readiness和激活超时未改。新提交重新冻结并全量复验，不复用首轮通过作最终结果。另发现工作树激活脚本仍含CRLF，而Git提交是LF；在新冻结输入前按既有gitattributes归一为提交原字节，整包解包须另核shell及可执行位。

## 冻结复验结果

唯一候选8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd的普通server4776/0失败/19跳过，renderer138/0/0/0，Linux部署18/0/0/0及checkpoint七项通过；首次renderer缺浏览器失败保全后同SHA环境补齐复验。整包独立解包与647份摘要自洽，但可信数据库/真实API-worker与实际部署仍未验、不放行。详见[固定候选复验](DEPLOY_FIXED_CANDIDATE_RECHECK_2026-10-20.md)及[本机副本溯源](DEPLOY_LOCAL_DATA_PROVENANCE_2026-10-20.md)。
