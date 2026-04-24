# Arc Quest 当前实现与规范差距表（Gap List）

基于《同步策略基线规范 v1》对当前代码状态进行差距评估。

> 评估口径：已落地 / 部分落地 / 未落地

---

## 一、总体结论

当前 Arc Quest 在 Trade/Gacha 已完成关键的协议收口（open/sync 与 result/failed 分层更清晰），并已引入变化检测雏形，整体方向正确。

主要差距集中在：

1. 推送优先策略仍不完整（周期拉取比重偏高）
2. 缓存职责拆分不彻底（Authority/UI Feedback 仍有耦合）
3. 持久化语义与同步语义在部分模块仍不够清晰
4. 统一抽象与统一回归体系尚未建立

---

## 二、分项差距

## 1) 协议分层（L0/L1/L2）

- 状态：**部分落地**
- 现状：
  - Quest 侧已有全量/增量（`S2CSyncFullDataPacket`、`S2CDeltaProgressPacket`）
  - Trade/Gacha 已有 State + Result/Failed 分层
- 差距：
  - 各模块仍缺统一层级命名规则（L0/L1/L2未制度化）
  - Dialogue 同步层级尚未纳入统一框架
- 建议优先级：P1

---

## 2) open/sync 语义

- 状态：**已落地（Trade/Gacha）**
- 现状：
  - Trade：`S2COpenTradePacket` + `S2CSyncTradeStatePacket`
  - Gacha：`C2SGachaControlPacket(Action.OPEN/SYNC)` + `S2CGachaStatePacket(Mode.OPEN/SYNC)`
- 差距：
  - 仍需确保“OPEN 只做打开语义”在后续变更中不回退
- 建议优先级：P0（守住现状）

---

## 3) 变化检测（服务端）

- 状态：**部分落地**
- 现状：
  - Gacha `GachaScreenOpener` 已有 fingerprint 缓存与无变化拦截
  - Trade `C2SRequestTradePacket` 已有 fingerprint 拦截
- 差距：
  - 缺统一指标输出（请求次数/下发次数/拦截次数）
  - baseline 生命周期策略未标准化（登录、切队、切维度、断线）
- 建议优先级：P0

---

## 4) 推送优先，拉取兜底

- 状态：**部分落地**
- 现状：
  - 客户端 0.5s 周期请求已稳定
  - 关键行为后会触发同步（如 confirm）
- 差距：
  - 仍偏“定时拉取优先”，服务端主动推送覆盖不足
  - 对背包变化、条件变化等触发点的推送仍不系统
- 建议优先级：P0

---

## 5) 客户端缓存分层（Authority vs Feedback）

- 状态：**未落地（结构化层面）**
- 现状：
  - `ClientTradeCache` / `ClientGachaCache` 已承载大量状态
- 差距：
  - 缓存职责未显式拆分为 AuthorityState 与 UiFeedbackState
  - 更新路径仍存在“状态覆盖顺序依赖”风险
- 建议优先级：P1

---

## 6) 持久化与同步分离

- 状态：**部分落地**
- 现状：
  - 模块内已有 dirty 标记逻辑
- 差距：
  - “保存完成”的语义边界尚未在全项目统一
  - 需要统一规范：markDirty / saveIfChanged / sync
- 建议优先级：P1

---

## 7) 权限与安全校验

- 状态：**部分落地**
- 现状：
  - C2S 入口已有 shop/capability 基础校验
- 差距：
  - 关键修改类 C2S 包权限校验仍需统一模板化
  - 缺“拒绝原因规范码”统一
- 建议优先级：P1

---

## 8) 可观测性（日志/指标）

- 状态：**未落地（体系层面）**
- 现状：
  - 有局部业务日志
- 差距：
  - 缺统一 sync 指标：request/sent/dropped
  - 缺按模块的链路 trace（open->sync->action->result）
- 建议优先级：P0

---

## 9) 回归测试体系

- 状态：**未落地（自动化层面）**
- 现状：
  - 以人工验证为主
- 差距：
  - 缺同步专项回归用例（断线重连、同队并发、状态回退）
- 建议优先级：P0

---

## 三、优先级整改清单（执行版）

### P0（本迭代必须完成）

1. 建立 sync 指标日志：request / sent / dropped
2. 完善推送优先触发点（关键状态变更即推送）
3. 制定并执行同步专项回归清单

### P1（下一迭代完成）

1. 缓存职责分层（AuthorityState / UiFeedbackState）
2. 持久化与同步语义统一重构
3. 权限校验模板化与错误码标准化

### P2（中期优化）

1. Trade/Gacha 刷新统一抽象
2. Dialogue 纳入统一分层同步模型
3. 跨模块统一命名与目录规范

---

## 四、验收标准（Gap 关闭定义）

每项 Gap 关闭必须满足：

1. 代码实现完成
2. 回归场景通过
3. 指标可观测（至少一项）
4. 文档更新（规范或实施记录）

---

## 五、当前版本结论

当前 Arc Quest 已通过关键重构进入“可持续演进”阶段，但尚未达到“生产级同步治理完成”状态。

建议按本表 P0 -> P1 -> P2 顺序推进，避免再次进入“先堆功能、后补同步”导致的返工循环。
