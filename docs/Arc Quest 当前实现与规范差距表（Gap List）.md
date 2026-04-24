# Arc Quest 当前实现与规范差距表（Gap List）

基于《同步策略基线规范 v1》对当前代码状态进行差距评估。

> 评估口径：已落地 / 部分落地 / 未落地  
> 本次为 **SYNC-07 对齐版本**（含回归清单）

---

## 一、总体结论（已对齐现状）

当前 Arc Quest 在 Trade/Gacha 的同步治理已进入“可持续演进”阶段：

- open/sync 语义已收口并稳定运行
- Quest -> Trade/Gacha 的 push-first 链路已统一入口
- 客户端缓存已完成 Authority / Feedback 分层（含兼容 API）
- compile 验证通过，主链路可运行

剩余差距已从“链路打通”转为“治理深化”：

1. 回归体系仍以人工为主，缺自动化同步专项
2. 持久化语义与同步语义还未全域统一
3. 权限/错误码规范化与跨模块统一抽象尚未完成

---

## 二、分项差距（按当前真实状态）

## 1) 协议分层（L0/L1/L2）

- 状态：**部分落地**
- 当前进展：
  - Quest 已有全量/增量分层（`S2CSyncFullDataPacket`、`S2CDeltaProgressPacket`）
  - Trade/Gacha 已稳定采用 State 与 Result/Failed 分层
- 仍有差距：
  - L0/L1/L2 的跨模块命名与目录规范未制度化
  - Dialogue 尚未纳入统一分层框架
- 优先级：P1

---

## 2) open/sync 语义

- 状态：**已落地（Trade/Gacha）**
- 当前进展：
  - Trade：`S2COpenTradePacket` + `S2CSyncTradeStatePacket`
  - Gacha：`C2SGachaControlPacket(Action.OPEN/SYNC)` + `S2CGachaStatePacket(Mode.OPEN/SYNC)`
- 守护项：
  - 防止后续变更回退为“open 承担 sync 语义”
- 优先级：P0（守住现状）

---

## 3) 变化检测（服务端）

- 状态：**部分落地（核心已落地）**
- 当前进展：
  - Trade 与 Gacha 均有 fingerprint 去重与无变化拦截
  - 关键链路已接入 request/sent/dropped 观测
- 仍有差距：
  - baseline 生命周期策略（登录/切维度/断线）未形成统一规范
- 优先级：P0

---

## 4) 推送优先，拉取兜底

- 状态：**已落地（核心链路）**
- 当前进展：
  - Quest 同步后统一触发 Trade/Gacha 活跃界面 push
  - Gacha push 链路已统一入口（`GachaScreenOpener.pushSync(...)`）
  - 周期拉取保留为兜底
- 仍有差距：
  - 触发点治理仍可继续扩展（如背包/条件变化的系统化覆盖）
- 优先级：P0（持续增强）

---

## 5) 客户端缓存分层（Authority vs Feedback）

- 状态：**已落地（Trade/Gacha）**
- 当前进展：
  - `ClientTradeCache`：AuthorityState / FeedbackState 结构化分层
  - `ClientGachaCache`：Authority / Feedback / History 分层，并保留兼容读取 API
- 仍有差距：
  - UI 读取面可继续向 snapshot/分层接口收敛
- 优先级：P1（优化项）

---

## 6) 持久化与同步分离

- 状态：**部分落地**
- 当前进展：
  - 已有 dirty 标记和同步调用链
- 仍有差距：
  - `markDirty / saveIfChanged / sync` 语义边界未在全模块统一
  - “保存成功”与“已同步客户端”的状态语义仍可混淆
- 优先级：P1

---

## 7) 权限与安全校验

- 状态：**部分落地**
- 当前进展：
  - 关键 C2S 入口具备基本 shop/capability 校验
- 仍有差距：
  - 缺统一模板化校验流程
  - 缺拒绝原因规范码（便于日志/前端提示/排障）
- 优先级：P1

---

## 8) 可观测性（日志/指标）

- 状态：**部分落地**
- 当前进展：
  - 已接入 sync request/sent/dropped 指标
  - 已补齐 trace 基础链路（可通过 `-Darcquest.sync.trace=true` 开启）
- 仍有差距：
  - action/result 覆盖率仍需继续扩展
  - 缺按模块聚合报表化输出
- 优先级：P0

---

## 9) 回归测试体系

- 状态：**部分落地（人工清单已落地）**
- 当前进展：
  - 已形成可重复执行的人工同步专项回归清单
- 仍有差距：
  - 缺自动化同步专项（断线重连、并发、状态回退）
- 优先级：P0

---

## 三、阶段映射（SYNC 执行进度对齐）

- SYNC-01：观测与日志基础接入（已完成）
- SYNC-02：Trade push-first 与活跃上下文（已完成）
- SYNC-03：Gacha push-first 与活跃上下文（已完成）
- SYNC-04：客户端缓存分层（已完成）
- SYNC-05：Quest -> Trade/Gacha 统一 push 入口收尾（已完成）
- SYNC-06：Gap List 对齐 + trace 基础链路（已完成）
- SYNC-07：同步专项回归清单（人工可执行版）（已完成）

当前阶段判定：**P0 主干已打通，进入 P0 回归收口 + P1 语义治理阶段**。

---

## 四、优先级整改清单（执行版）

### P0（当前迭代继续完成）

1. 扩展 trace 覆盖到 action/result 全链路
2. 用回归清单持续守护 open/sync 不回退
3. 固化一份最小“发布前同步检查”脚本

### P1（下一迭代完成）

1. 持久化语义统一：`markDirty / saveIfChanged / sync`
2. 权限校验模板化 + 拒绝原因规范码
3. UI 读取进一步向分层 snapshot 收敛

### P2（中期优化）

1. Trade/Gacha 刷新统一抽象
2. Dialogue 纳入统一分层同步模型
3. 跨模块统一命名与目录规范

---

## 五、同步专项回归清单（人工可重复执行版）

> 目标：覆盖最容易回退的同步路径，确保 push-first 与缓存分层不被后续改动破坏。

### 场景 A：材料刚好耗尽（Gacha）

1. 打开 Gacha 界面，确保材料仅够 1 次抽取
2. 发起 draw 并 confirm
3. 返回 preview 后立即观察按钮/shortfall

通过标准：

- 不需要二次点击才更新
- 按钮状态立即从可抽变为不可抽
- 出现 shortfall 提示（如配置存在）
- trace 开启时可见 `open -> sync_sent` 相关链路

### 场景 B：Quest 条件变化触发 Trade/Gacha push

1. 保持 Trade 或 Gacha 界面活跃
2. 完成会改变条件的 Quest 行为（进度/flags/vars）
3. 观察界面状态是否自动刷新

通过标准：

- 无需手动重开界面即可刷新权威状态
- fingerprint 未变化时出现 dropped（避免重复下发）
- 有变化时出现 sent 且 UI 有对应更新

### 场景 C：冷却边界（Gacha/Trade）

1. 在冷却即将结束前打开界面
2. 跨过冷却边界，触发一次 Quest 同步或主动 sync
3. 观察可购买/可抽状态与倒计时

通过标准：

- 冷却结束后状态正确切换
- 没有“倒计时结束但按钮仍不可用”的卡住现象

### 场景 D：断线重连后恢复

1. 打开 Trade/Gacha 后断线重连
2. 重新进入并恢复对应界面
3. 检查缓存状态与服务端权威是否一致

通过标准：

- 不出现历史状态残留覆盖权威状态
- 可抽/可买、剩余次数、shortfall 与服务端一致

### 观测开关建议

- verbose 计数日志：`-Darcquest.sync.log.verbose=true`
- trace 链路日志：`-Darcquest.sync.trace=true`

---

## 六、验收标准（Gap 关闭定义）

每项 Gap 关闭必须满足：

1. 代码实现完成
2. 回归场景通过（至少人工清单 + 关键录像/日志）
3. 指标可观测（至少一项）
4. 文档更新（规范或实施记录）

---

## 七、当前版本结论

当前 Arc Quest 已达到“同步主链路可持续演进”状态，但仍未达到“生产级同步治理完成”。

建议按 **P0 回归收口 -> P1 语义治理 -> P2 统一抽象** 顺序推进，避免重新进入“先堆功能、后补同步”的返工循环。
