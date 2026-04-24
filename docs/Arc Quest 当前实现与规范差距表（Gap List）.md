# Arc Quest 当前实现与规范差距表（Gap List）

基于《同步策略基线规范 v1》对当前代码状态进行差距评估。

> 评估口径：已落地 / 部分落地 / 未落地  
> 本次为 **SYNC-05 收尾后的对齐版本**（用于反映当前真实进度）

---

## 一、总体结论（已对齐现状）

当前 Arc Quest 在 Trade/Gacha 的同步治理已从“修现象”进入“可持续演进”阶段：

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
  - 关键链路已接入观测计数
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
  - 已接入 sync request/sent/dropped 的核心观测
- 仍有差距：
  - 缺统一 trace 视图（open -> sync -> action -> result）
  - 缺按模块聚合报表化输出
- 优先级：P0

---

## 9) 回归测试体系

- 状态：**未落地（自动化层面）**
- 当前进展：
  - 人工验证链路可执行
- 仍有差距：
  - 缺同步专项自动化回归（断线重连、并发、状态回退）
- 优先级：P0

---

## 三、阶段映射（SYNC 执行进度对齐）

- SYNC-01：观测与日志基础接入（已完成）
- SYNC-02：Trade push-first 与活跃上下文（已完成）
- SYNC-03：Gacha push-first 与活跃上下文（已完成）
- SYNC-04：客户端缓存分层（已完成）
- SYNC-05：Quest -> Trade/Gacha 统一 push 入口收尾（已完成）

当前阶段判定：**P0 主干已打通，进入 P0 剩余收口 + P1 语义治理阶段**。

---

## 四、优先级整改清单（执行版）

### P0（当前迭代继续完成）

1. 同步专项回归清单落地（至少人工可重复执行版）
2. 可观测性补齐一条统一 trace 链（open->sync->action->result）
3. 稳定性守护：防止 open/sync 语义回退

### P1（下一迭代完成）

1. 持久化语义统一：`markDirty / saveIfChanged / sync`
2. 权限校验模板化 + 拒绝原因规范码
3. UI 读取进一步向分层 snapshot 收敛

### P2（中期优化）

1. Trade/Gacha 刷新统一抽象
2. Dialogue 纳入统一分层同步模型
3. 跨模块统一命名与目录规范

---

## 五、验收标准（Gap 关闭定义）

每项 Gap 关闭必须满足：

1. 代码实现完成
2. 回归场景通过（至少人工清单 + 关键录像/日志）
3. 指标可观测（至少一项）
4. 文档更新（规范或实施记录）

---

## 六、当前版本结论

当前 Arc Quest 已达到“同步主链路可持续演进”状态，但仍未达到“生产级同步治理完成”。

建议按 **P0 收口 -> P1 语义治理 -> P2 统一抽象** 顺序推进，避免重新进入“先堆功能、后补同步”的返工循环。
