# ArcQuestPlayer — 完全弃用 Capability 施工蓝图 (v3 真实纠错版)

> **状态**：基于 219 个实际编译错误全面修正
> **分支**：`feat/arcquest-player`（master 已回档到施工前）
> **创建日期**：2026-05
> **策略**：硬删除 — 无过渡期，直接删除所有 Forge Capability 类
> **预计工期**：约 3 天（~12-15h）
> **施工方法**：逐文件 Read → 精确 SearchReplace → compile — 禁止批量 regex

---

## 0. v2→v3 修正说明

v2 蓝图基于代码分析预估 ~32 个文件需要修改。实际施工后产生 219 个编译错误。

| v2 错误 | v3 修正 |
|------|------|
| 预估 ~32 文件修改 | 实际 **~55+ 文件**需要修改 |
| `IQuestCapability cap` → `ArcQuestPlayer data` | 与同方法中 `QuestRuntimeData data` 产生重复变量 → `QuestRuntimeData` 改为 `qdata` |
| 蓝图遗漏 15+ 个文件 | 全部补入（见第 3 节） |
| `GachaDataStore`/`S2CGachaStatePacket` 中 `IQuestCapability.GachaDrawRecord` 未覆盖 | 补充 |
| Python regex 批量替换不可靠 | 改为逐文件 Read→SearchReplace→compile |

---

## 1. 核心变更

### 1.1 删除文件（6 个）

| 文件 | 理由 |
|------|------|
| `quest/capability/IQuestCapability.java` | 被 `ArcQuestPlayer` 替代 |
| `quest/capability/QuestCapabilityImpl.java` | 内容并入 `ArcQuestPlayer` |
| `quest/capability/QuestCapabilityProvider.java` | 静态方法并入 `ArcQuestPlayerManager` |
| `quest/capability/CapabilityEventHandler.java` | 被 `ArcQuestPlayerLifecycleHandler` 替代 |
| `dialogue/capability/DialogueNpcPatch.java` | 被 `DialogueNpcStateManager` 替代 |
| `dialogue/capability/DialogueNpcPatchProvider.java` | 不再需要 |

### 1.2 保留在 `quest/capability` 包的文件

纯数据容器，不依赖 Forge Capability API，**不动**：

- `QuestRuntimeData.java`
- `GachaDataStore.java`  ⚠️ 改 `IQuestCapability.GachaDrawRecord` → `ArcQuestPlayer.GachaDrawRecord`
- `TradeDataStore.java`
- `NbtVersionManager.java`

### 1.3 新建文件（4 个）

| 文件 | 说明 |
|------|------|
| `quest/player/ArcQuestPlayer.java` | 纯数据容器 + DirtyKind + GachaDrawRecord + 5 个 default 方法 |
| `quest/player/ArcQuestPlayerManager.java` | ConcurrentHashMap + 生命周期 API |
| `quest/player/ArcQuestPlayerLifecycleHandler.java` | 替代 CapabilityEventHandler |
| `dialogue/capability/DialogueNpcStateManager.java` | 替代 DialogueNpcPatch |

---

## 2. 完整修改文件清单（~55 个，按替换模式分组）

### 组 A — 核心签名层（先改，其他文件依赖它们）

| 文件 | 具体替换 |
|------|------|
| `ArcQuestNetwork.java` | import `IQuestCapability`→`ArcQuestPlayer`, `QuestCapabilityProvider`→`ArcQuestPlayerManager`; `syncFullData(ServerPlayer, IQuestCapability)`→`syncFullData(ServerPlayer, ArcQuestPlayer)`; 同样改 `syncFlagsAndVars`, `pushSyncForActiveUIs`, `syncMarkers` |
| `QuestSyncCoordinator.java` | import `IQuestCapability`→`ArcQuestPlayer`, `QuestCapabilityImpl`→`ArcQuestPlayer`; 所有 `QuestCapabilityImpl`→`ArcQuestPlayer`, `IQuestCapability`→`ArcQuestPlayer`, `QuestCapabilityImpl.DirtyKind`→`ArcQuestPlayer.DirtyKind` |
| `S2CSyncFullDataPacket.java` | import `IQuestCapability`→`ArcQuestPlayer`; 构造器参数 `IQuestCapability cap`→`ArcQuestPlayer data` |
| `S2CSyncFlagsVarsPacket.java` | 同上 |
| `GachaDataStore.java` | import `ArcQuestPlayer`; 所有 `IQuestCapability.GachaDrawRecord`→`ArcQuestPlayer.GachaDrawRecord` |

### 组 B — API 接口层

| 文件 | 具体替换 |
|------|------|
| `MarkActivation.java` | `@FunctionalInterface boolean test(ServerPlayer, IQuestCapability)`→`ArcQuestPlayer` |
| `CollectionRuleContext.java` | 字段/构造器/getter `IQuestCapability capability`→`ArcQuestPlayer playerData` |
| `DialogueTextContext.java` | `IQuestCapability questCap`→`ArcQuestPlayer questData` |
| `TradeTextContext.java` | `IQuestCapability questCap`→`ArcQuestPlayer questData` |

### 组 C — 网络包 handler 层（服务端）

| 文件 | 具体替换 |
|------|------|
| `C2SRequestQuestActionPacket.java` | import + `getCapability`→`ArcQuestPlayerManager.get` |
| `C2SClaimCollectionRewardPacket.java` | 同上 |
| `C2SRequestTradePacket.java` | 同上 |
| `C2SGachaControlPacket.java` | 同上 |
| `C2SDrawGachaPacket.java` | 同上 |
| `C2SConfirmDrawPacket.java` | 同上 |
| `S2CGachaStatePacket.java` | import `IQuestCapability`→`ArcQuestPlayer`; `List<IQuestCapability.GachaDrawRecord>`→`List<ArcQuestPlayer.GachaDrawRecord>` |

### 组 D — 交易系统

| 文件 | 具体替换 |
|------|------|
| `TradeShopDefinition.java` | `getDisplayName/getDescription` 参数 `@Nullable IQuestCapability cap`→`@Nullable ArcQuestPlayer data` |
| `TradeSession.java` | import; `IQuestCapability getCap()`→`ArcQuestPlayer getData()`; 所有 `getCap()`→`ArcQuestPlayerManager.get(player)`; `cap.xxx`→`data.xxx`（确认无 `QuestRuntimeData` 冲突） |
| `TradeRequestValidator.java` | import; `IQuestCapability requireCapability`→`ArcQuestPlayer requireData` |
| `FlagTradeOffer.java` | import `IQuestCapability`→`ArcQuestPlayer`, `QuestCapabilityProvider`→`ArcQuestPlayerManager` |
| `TradeEntryStateResolver.java` | 所有方法签名 `IQuestCapability cap`→`ArcQuestPlayer data` |

### 组 E — 抽奖系统

| 文件 | 具体替换 |
|------|------|
| `GachaShopDefinition.java` | `IQuestCapability cap`→`ArcQuestPlayer data`（6 个方法） |
| `GachaPool.java` | `IQuestCapability cap`→`ArcQuestPlayer data`（8 个方法） |
| `GachaItem.java` | `IQuestCapability cap`→`ArcQuestPlayer data`（6 个方法） |
| `GachaEvents.java` | 7 个内部 event 类: `IQuestCapability capability`→`ArcQuestPlayer playerData`（字段+构造器+getter） |
| `GachaSession.java` | 字段/构造器 `IQuestCapability capability`→`ArcQuestPlayer playerData` |
| `GachaEntryStateResolver.java` | 所有方法签名 `IQuestCapability cap`→`ArcQuestPlayer data` |
| `GachaRequestValidator.java` | `IQuestCapability requireCapability`→`ArcQuestPlayer requireData` |
| `PendingDrawManager.java` | import `IQuestCapability`→`ArcQuestPlayer`, `QuestCapabilityProvider`→`ArcQuestPlayerManager` |
| `GachaScreenOpener.java` | 所有方法签名 `IQuestCapability cap`→`ArcQuestPlayer data` |

### 组 F — 对话系统

| 文件 | 具体替换 |
|------|------|
| `DialogueSession.java` | `buildDialogueVars(IQuestCapability cap)`→`ArcQuestPlayer data` |
| `DialogueEvalContext.java` | `IQuestCapability questCap()`→`ArcQuestPlayer questData()`; import `QuestCapabilityProvider`→`ArcQuestPlayerManager` |
| `DialogueCondition.java` | `HasQuest.test()` 中 `cap` 变量→`data`; `PhaseEnterable.test()` 中 `cap` 变量→`data`（⚠️ 注意内部有 `QuestRuntimeData data` 冲突→改为 `qdata`） |
| `DialogueAction.java` | import `IQuestCapability`→`ArcQuestPlayer`, `QuestCapabilityProvider`→`ArcQuestPlayerManager` |
| `ConditionalTextEvaluator.java` | `matchesCondition(DialogueEvalContext ctx, IQuestCapability cap, ...)`→`ArcQuestPlayer data` |
| `DialogueSessionManager.java` | import `DialogueNpcPatch`→`DialogueNpcStateManager`; `EntityDialogueExtensionHandler.java` 引用者需同步改 |

### 组 G — NPC 对话处理（需手工重写方法体）

| 文件 | 具体替换 |
|------|------|
| `NpcDialogueHandler.java` | 删除 import `RegisterCapabilitiesEvent`/`AttachCapabilitiesEvent`/`DialogueNpcPatch`/`DialogueNpcPatchProvider`; 加 `DialogueNpcStateManager`; 删除 `onRegisterCapabilities`/`onAttachEntityCapabilities`; 重写 `onEntityTick` — 用 `DialogueNpcStateManager.get(entity)` 替代 `DialogueNpcPatch.get(entity).tick()` |
| `EntityDialogueExtensionHandler.java` | 删除 import `RegisterCapabilitiesEvent`/`AttachCapabilitiesEvent`; 删除 `onAttachCapabilities`+`onRegisterCapabilities`; `onLivingTick`/`checkDistance`/`controlNpcBehavior`/`ensureDialogueNpcPatch` 全部用 `DialogueNpcStateManager` 替代 `DialogueNpcPatch.get/getCapability` |
| `IDialogueNpc.java` | 删除 import `DialogueNpcPatch` |

### 组 H — Quest 逻辑层（⚠️ 变量冲突重灾区）

| 文件 | 具体替换 |
|------|------|
| `QuestProgressHandler.java` | import `IQuestCapability`→`ArcQuestPlayer`, `QuestCapabilityProvider`→`ArcQuestPlayerManager`。方法签名 `IQuestCapability cap`→`ArcQuestPlayer data`。**注意**：若同方法已有 `QuestRuntimeData data`，后者改为 `qdata`。如 `checkPhaseCompletion(ServerPlayer, ArcQuestPlayer data, QuestRuntimeData qdata, ...)` |
| `QuestCapabilityTickHandler.java` | import; `IQuestCapability cap`→`ArcQuestPlayer data`; `player.getCapability(...).ifPresent(cap -> {...})`→`ArcQuestPlayer data = ArcQuestPlayerManager.get(player); if (data != null) {...}` |
| `QuestMarkerService.java` | import; `IQuestCapability cap`→`ArcQuestPlayer data` |
| `QuestEventManager.java` | import `IQuestCapability`→`ArcQuestPlayer`, `QuestCapabilityProvider`→`ArcQuestPlayerManager` |
| `QuestOfferService.java` | 同上 |
| `FlagReward.java` | 同上 |
| `VariableReward.java` | 同上 |

### 组 I — Collection 系统

| 文件 | 具体替换 |
|------|------|
| `CollectionQuestEngine.java` | ~15 处 `IQuestCapability cap`→`ArcQuestPlayer data`（⚠️ 检查 `QuestRuntimeData` 冲突） |
| `CollectionObjectiveDispatcher.java` | `IQuestCapability cap`→`ArcQuestPlayer data` |
| `CollectionVisibilityResolver.java` | `IQuestCapability cap`→`ArcQuestPlayer data` |

### 组 J — 条件系统

| 文件 | 具体替换 |
|------|------|
| `ConditionBridge.java` | import `QuestCapabilityProvider`→`ArcQuestPlayerManager`; `getOrNull(player)`→`ArcQuestPlayerManager.get(player)` |
| `ConditionEvaluator.java` | 同上 |

### 组 K — 命令系统

| 文件 | 具体替换 |
|------|------|
| `QuestCommands.java` | `IQuestCapability getCap(ServerPlayer)`→`ArcQuestPlayer getData(ServerPlayer)`; 体内 `getOrNull`→`ArcQuestPlayerManager.get` |
| `DialogueCommands.java` | 同上 |
| `TradeCommands.java` | 同上 |
| `GachaCommands.java` | 同上 |
| `MarkerTestCommand.java` | import `QuestCapabilityProvider`→`ArcQuestPlayerManager` |

---

## 3. 逐文件替换速查表（精确版）

### import 替换

| 旧 import | 新 import |
|------|------|
| `import ...quest.capability.IQuestCapability;` | `import ...quest.player.ArcQuestPlayer;` |
| `import ...quest.capability.QuestCapabilityProvider;` | `import ...quest.player.ArcQuestPlayerManager;` |
| `import ...quest.capability.QuestCapabilityImpl;` | `import ...quest.player.ArcQuestPlayer;` |
| `import ...quest.capability.CapabilityEventHandler;` | `import ...quest.player.ArcQuestPlayerLifecycleHandler;` |
| `import ...dialogue.capability.DialogueNpcPatch;` | `import ...dialogue.capability.DialogueNpcStateManager;` |
| `import ...dialogue.capability.DialogueNpcPatchProvider;` | （删除） |
| `import ...RegisterCapabilitiesEvent;` | （删除） |
| `import ...AttachCapabilitiesEvent;` | （删除） |

### 类型替换

| 旧类型 | 新类型 |
|------|------|
| `IQuestCapability` | `ArcQuestPlayer` |
| `QuestCapabilityProvider` | `ArcQuestPlayerManager` |
| `QuestCapabilityImpl` | `ArcQuestPlayer` |
| `CapabilityEventHandler` | `ArcQuestPlayerLifecycleHandler` |
| `DialogueNpcPatch` | `DialogueNpcStateManager.State`（as 参数类型）或 `DialogueNpcStateManager`（as 工具类） |
| `IQuestCapability.GachaDrawRecord` | `ArcQuestPlayer.GachaDrawRecord` |
| `QuestCapabilityImpl.DirtyKind` | `ArcQuestPlayer.DirtyKind` |

### 调用替换

| 旧调用 | 新调用 |
|------|------|
| `QuestCapabilityProvider.getOrNull(player)` | `ArcQuestPlayerManager.get(player)` |
| `player.getCapability(QUEST_CAP).ifPresent(cap -> { ... })` | `ArcQuestPlayer data = ArcQuestPlayerManager.get(player); if (data != null) { ... }` |
| `cap.serializeNBT()` | `data.serializeNBT()` |
| `cap.copyFrom(old)` | `data.copyFrom(oldData)`（注意 old 也是 ArcQuestPlayer） |
| `event.addCapability(CAP_ID, new QuestCapabilityProvider())` | 删除整行 |
| `event.addCapability(CAP_ID, new DialogueNpcPatchProvider(...))` | 删除整行 |
| `new CapabilityToken<>(){}` | 删除 |
| `event.register(IQuestCapability.class)` | 删除 |
| `event.register(DialogueNpcPatch.class)` | 删除 |
| `DialogueNpcPatch.get(entity)` | `DialogueNpcStateManager.get(entity)` |
| `patch.isConversing()` | `state != null && state.conversingPlayer() != null && state.conversingPlayer().isAlive()` |
| `patch.getConversingPlayer()` | `state.conversingPlayer()` |
| `patch.setConversing(player)` | `DialogueNpcStateManager.setConversing(entity, player)` |
| `patch.clearConversing()` | `DialogueNpcStateManager.clear(entity)` |
| `patch.tick()` | 内联：距离检查 + 注视控制（见 §2.2） |

### 变量名规则

| 场景 | 规则 |
|------|------|
| 方法只有一个 `ArcQuestPlayer` 参数 | `ArcQuestPlayer data` |
| 方法同时有 `ArcQuestPlayer` 和 `QuestRuntimeData` | `ArcQuestPlayer data` + `QuestRuntimeData qdata` |
| 类字段（如 GachaEvents 内部类） | `private final ArcQuestPlayer playerData` |

---

## 4. 施工拓扑

```
Layer 0  ── ArcQuestPlayer.java                    [新建] → compileJava
Layer 1  ── ArcQuestPlayerManager.java              [新建] → compileJava
            DialogueNpcStateManager.java             [新建]
Layer 2  ── ArcQuestPlayerLifecycleHandler.java     [新建] → compileJava
Layer 3  ── 组 A（核心签名层 5 文件）                  → compileJava
Layer 4  ── 删除 6 旧文件                             → compileJava
Layer 5  ── 组 B（API 接口层 4 文件）+ 组 K（命令 5 文件）  → compileJava
Layer 6  ── 组 C（网络包 7 文件）+ 组 H/I/J（Quest 逻辑 10+） → compileJava
Layer 7  ── 组 D/E（交易+抽奖 ~15 文件）               → compileJava
Layer 8  ── 组 F/G（对话+NPC 需手工重写 8 文件）        → compileJava
```

每层独立编译通过后进入下一层。

---

## 5. 施工阶段规划

| 阶段 | 内容 | 文件数 | 预估 |
|:---:|------|:---:|:---:|
| 一 | 新建 4 个文件 | 4 | 2.5h |
| 二 | 核心签名层（组 A） + 删除旧文件 | 11 | 2h |
| 三 | API 接口层 + 命令（组 B/K） | 9 | 1.5h |
| 四 | 网络包 + Quest 逻辑（组 C/H/I/J） | ~20 | 3h |
| 五 | 交易+抽奖（组 D/E） | ~15 | 2h |
| 六 | 对话+NPC 手工重写（组 F/G） | 8 | 2h |
| 七 | compileJava 全面修复 + 测试 | — | 2h |
| **总计** | | **~60 文件** | **~15h** |

---

## 6. 施工规则（必须遵守）

1. **禁止**任何形式的批量 regex 替换
2. **每改 5 个文件**，运行 `./gradlew compileJava --no-daemon` 验证
3. 构造函数时**读出完整上下文**，确认不会产生变量名冲突
4. `DialogueNpcPatch` 涉及文件（3 个）必须在 `DialogueNpcStateManager` 新建完成后、最后阶段处理
5. Layer 3-8 不必同一个 commit——每层单独 commit

---

## 7. 测试清单

| 测试项 | 验证方法 |
|------|------|
| 编译 | `./gradlew clean compileJava` |
| 启动 | `./gradlew runClient` |
| 新建玩家登录 | 新建世界 → 无 crash |
| 登录恢复 | 退出 → 重进 → 任务数据不丢 |
| 接受任务 | accept → 任务显示 |
| 推进阶段 | 完成 objective → phase 推进 |
| 死亡重生 | `/kill` → 重生 → 数据不丢 |
| Reload | `/reload` → 注册表正常 |
| 对话 | 与 NPC 对话 → 对话树正常 |
| 交易 | 商店 → 购买正常 |
| 抽奖 | 抽奖 → 结果正常 |
