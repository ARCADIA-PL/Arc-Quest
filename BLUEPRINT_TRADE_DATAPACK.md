# 商店/抽奖模块完整数据包能力 — 施工蓝图 v2

> 全栈 6 角色 20 轮研讨总结（初研 10 轮 + 深审 10 轮）· 2026-05-16

---

## 一、项目背景

当前 `trade` 模块所有商店通过 Java `TradeShopBuilder` / `TradeEntryBuilder` 硬编码注册（`TradeContent.java`），缺少 datapack JSON → Spec → Compiler → Registry 的完整链路。本蓝图参照 `dialogue` 和 `questmarker` 模块的实践路径，为商店模块补齐 datapack 能力。

---

## 二、参考实践路径

### 2.1 对话模块标准链路

```
JSON 文件
  → DialogueDatapackResourceLoader (文件系统扫描)
    → DialogueSpecValidator (校验)
      → DialogueSpecCompiler (编译)
        → DialogueRegistry.registerDatapack() (注册)
          → ArcQuestReloadListener (热重载入口)
```

### 2.2 商店模块对标链路

```
JSON 文件 (.json in datapack)
  → TradeDatapackResourceLoader (扫描 trades/ 目录)
    → TradeSpecValidator (校验)
      → TradeSpecCompiler (编译)
        → TradeRegistry.registerDatapack() (注册)
          → ArcQuestReloadListener (热重载入口)
```

### 2.3 Datapack 目录约定

| 模块 | datapack 路径 |
|------|-------------|
| Quest | `data/<ns>/arc_quest/quests/*.json` |
| Dialogue | `data/<ns>/arc_quest/dialogues/*.json` |
| NPC | `data/<ns>/arc_quest/npcs/*.json` |
| **Trade** | **`data/<ns>/arc_quest/trades/*.json`** |

> **裁决 #19**：`TradeDatapackResourceLoader` 扫描 `trades/` 目录下所有 `.json` 文件。

---

## 三、现有模块摸底

### 3.1 Java trade 模块现有层（对照 dialogue）

| 层 | 对话模块 | 商店模块 | 状态 |
|------|----------|----------|------|
| API | `DialogueNode.java`, `DialogueTree.java`, `ConditionalSay.java` | `TradeEntry.java`, `TradeShopDefinition.java`, `TradeCategory.java` | ✅ 完整 |
| Builder | `DialogueTreeBuilder.java` | `TradeShopBuilder.java`, `TradeEntryBuilder.java` | ✅ 完整 |
| Registry | `DialogueRegistry.java` (代码+datapack 分离) | `TradeRegistry.java` (仅代码) | ⚠️ 缺少 datapack |
| Content | `EpicDialogueTrees.java` | `TradeContent.java` | ✅ 硬编码示例 |
| Network | `S2COpenDialoguePacket.java` | `S2COpenTradePacket.java`, `ClientTradeCache.java` | ✅ 完整 |
| Runtime | `DialogueSession.java` | `TradeSession.java`, `TradeEntryStateResolver.java` | ✅ 完整 |
| Spec | `DialogueSpec.java`, `DialogueNodeSpec.java` | ❌ 无 | 🔴 缺失 |
| Validator | `DialogueSpecValidator.java` | ❌ 无 | 🔴 缺失 |
| Compiler | `DialogueSpecCompiler.java` | ❌ 无 | 🔴 缺失 |
| Resource Loader | `DialogueDatapackResourceLoader.java` | ❌ 无 | 🔴 缺失 |
| Hot Reload | `DialogueDatapackHotReloadService.java` | ❌ 无 | 🔴 缺失 |
| JSON Reader/Writer | `DialogueSpecJsonReader.java` / `Writer` | ❌ 无 | 🔴 缺失 |

### 3.2 Gacha（抽奖）模块现状

| 组件 | 文件 | 状态 |
|------|------|------|
| API | `GachaShopDefinition.java`, `GachaPool.java`, `GachaItem.java`, `PityConfig.java` | ✅ 完整 |
| Builder | `GachaShopBuilder.java` | ✅ 完整 |
| Registry | `GachaRegistry.java` | ✅ 代码注册 |
| Content | `DemoGachaShops.java` | ✅ 硬编码示例 |
| Network | `S2CGachaStatePacket.java`, `C2SDrawGachaPacket.java` 等 | ✅ 完整 |

> **裁决 #8**：Gacha 模块 datapack 化作为 Phase 2，本次不纳入。但架构预留扩展点。

---

## 四、Spec 类设计（Java 端核心）

### 4.1 TradeShopSpec

> **关于 `displayName` / `description` 类型**：Spec 层复用 `DialogueTextSpec`（与 dialogue/NPC 保持一致），在 Compiler 中通过 `compileTradeText(DialogueTextSpec) → TradeText` 完成转换。`TradeText.literal()` / `TradeText.translatable()` 语义与 `DialogueText` 一致，无需新建 `TradeTextSpec`。（**裁决 #11**）

```java
package org.arcadia.arc_quest.trade.spec;

public class TradeShopSpec {
    public String shopId = "";
    public DialogueTextSpec displayName = new DialogueTextSpec();
    public DialogueTextSpec description = null;
    public List<TradeCategorySpec> categories = new ArrayList<>();
    public LinkedHashMap<String, TradeEntrySpec> entries = new LinkedHashMap<>();
    public ConditionSpec openCondition = null;
    public boolean simpleMode = false;
    public int themeColor = 0xE0C860;
    public String openSound = "";
    public String closeSound = "";
}
```

### 4.2 TradeEntrySpec

```java
public class TradeEntrySpec {
    public String entryId = "";
    public DialogueTextSpec displayName = new DialogueTextSpec();
    public DialogueTextSpec description = null;
    public List<TradeOfferSpec> costs = new ArrayList<>();
    public List<TradeOfferSpec> rewards = new ArrayList<>();
    public String category = "";
    public ConditionSpec visibleCondition = null;
    public ConditionSpec canBuyCondition = null;
    public String cooldownType = "NONE";
    public long cooldownValue = 0;
    public int resetTimeTicks = 0;
    public int maxPurchases = 0;
    public String rewardIcon = "";
    public String costIcon = "";
    public int sortOrder = 0;
    public int themeColor = -1;
    public String purchaseSuccessSound = "";
    public String purchaseFailSound = "";
    public String cooldownSound = "";
    public String limitReachedSound = "";
    public String conditionFailSound = "";
}
```

> **裁决 #4**：`purchaseResetCondition`（`Predicate<ServerPlayer>`）保留为 Code-only，不出现在 Spec。datapack 商店的 `maxPurchases` + `cooldownType` 足以表达常见限购+恢复行为。

### 4.3 TradeOfferSpec

```java
public class TradeOfferSpec {
    public String type = "item";  // item | command | effect | flag | composite
    // item
    public String itemId = "";
    public int count = 1;
    public String nbt = "";
    // command
    public String command = "";
    public String executeAs = "console";  // console | player
    // effect
    public String effectId = "";
    public int duration = 0;
    public int amplifier = 0;
    // flag
    public String flagName = "";
    // composite
    public List<TradeOfferSpec> offers = new ArrayList<>();
}
```

> **裁决 #2**：单一类 + `type` 字段区分多态。与 `ConditionSpec` 模式一致，避免 Java sealed class 复杂度。

### 4.4 TradeCategorySpec

```java
public class TradeCategorySpec {
    public String categoryId = "";
    public DialogueTextSpec displayName = new DialogueTextSpec();
    public int sortOrder = 0;
    public String formatting = "";  // RED|GREEN|AQUA|etc，空=无颜色
}
```

---

## 五、Registry 改造

### 5.1 双 Map 分离 + 线程安全

```java
public final class TradeRegistry {
    private static Map<String, TradeShopDefinition> codeShops = new LinkedHashMap<>();
    private static volatile Map<String, TradeShopDefinition> datapackShops = Map.of();
    private static boolean frozen = false;

    // 代码注册（freeze 前）—— 写入 codeShops
    public static void register(TradeShopDefinition definition) { ... }

    // freeze 冻结 codeShops 为不可变 Map
    public static void freeze() { ... }

    // Datapack 批量注册（freeze 后可用）—— 先构建新 map，再原子替换
    public static void replaceDatapack(Map<String, TradeShopDefinition> shops) {
        datapackShops = Collections.unmodifiableMap(new LinkedHashMap<>(shops));
    }

    // Reload 时清除所有 datapack 商店
    public static void clearDatapack() { datapackShops = Map.of(); }

    // 查找：datapack 优先，回退 code；getAll/size/getAllIds 均合并双 Map
    public static TradeShopDefinition get(String shopId) { ... }
}
```

**线程安全策略**：

- `codeShops`：`freeze()` 前写入 → `freeze()` 时转为不可变 Map → 之后只读
- `datapackShops`：`volatile` + `Collections.unmodifiableMap` 原子替换，无需锁
- `get()` / `getAll()` / `getAllIds()` / `size()`：合并双 Map 结果，读操作线程安全

**freeze() 调用时序**（`Arc_Quest.commonSetup`）：

```java
TradeContent.registerAll();    // → codeShops
TradeRegistry.freeze();         // → 冻结 codeShops
// datapackShops 在 reload 时由 HotReloadService 填充
```

> **裁决 #3 + #15 + #16**：双 Map + volatile 不可变替换 + freeze 时序保持现有代码不变。

---

## 六、Validator 设计

### 6.1 校验规则

| 路径 | 规则 | 严重度 |
|------|------|--------|
| `shopId` | 不能为空 | ERROR |
| `displayName` | 不能为空 | ERROR |
| `entries` | 至少一个条目 | WARN |
| `entries.${id}.entryId` | 不能为空，不能重复 | ERROR |
| `entries.${id}.costs` | 可为空（免费领取） | - |
| `entries.${id}.rewards` | 不能为空（必须至少一个奖励） | ERROR |
| `entries.${id}.costs[].type` | 必须是 item/command/effect/flag/composite | ERROR |
| `entries.${id}.costs[].itemId` | **type=item 时**不能为空 | ERROR |
| `entries.${id}.costs[].command` | **type=command 时**不能为空 | ERROR |
| `entries.${id}.costs[].effectId` | **type=effect 时**不能为空 | ERROR |
| `entries.${id}.costs[].flagName` | **type=flag 时**不能为空 | ERROR |
| `entries.${id}.costs[].offers` | **type=composite 时**不能为空 | ERROR |
| `entries.${id}.category` | 如果非空，必须在 categories 中 | WARN |
| `openCondition` | 如存在，递归校验节点 | WARN |
| `cooldownType` | 必须在 NONE/SECONDS/GAME_DAY/GAME_TICK 中 | ERROR |

> **裁决 #14**：只校验 offer.type 对应的必填字段，忽略其他 type 字段。避免编辑器切换 offer type 后"脏数据"误报。

---

## 七、Compiler 设计

### 7.1 编译映射

| Spec 字段 | API 目标 | 编译方式 |
|-----------|----------|----------|
| `displayName` | `TradeText` | `compileTradeText(DialogueTextSpec)` → `TradeText.literal()` / `.translatable()` |
| `description` | `TradeText` (nullable) | 复用同上 |
| `categories[].displayName` | `TradeText` | 复用同上 |
| `categories` | `List<TradeCategory>` | `compileCategories()` → `TradeCategory.of(id, text, sortOrder, color)` |
| `entries` | `LinkedHashMap<String, TradeEntry>` | `compileEntry()` 逐条编译 |
| `entries.${id}.category` (String) | `TradeCategory` (nullable) | 从 `categories` 列表中按 `categoryId` 查找匹配的 `TradeCategory` |
| `openCondition` | `ICondition` (nullable) | 复用 `ConditionBridge` |
| `costs` / `rewards` | `List<ITradeOffer>` | `compileOffer()` → 各子类实例 |

**Offer 编译 isCost 语义**：遍历 `spec.costs` 时 `isCost = true`，遍历 `spec.rewards` 时 `isCost = false`。列表位置天然区分成本/奖励。

| Offer type | 编译目标 | 关键参数 |
|-----------|---------|---------|
| `item` | `ItemTradeOffer` | `itemId` (parse Item), `count`, `isCost` |
| `command` | `CommandTradeOffer` | `command` (含 `{player}` 占位符) |
| `effect` | `EffectTradeOffer` | `effectId`, `duration`, `amplifier` |
| `flag` | `FlagTradeOffer` | `flagName` |
| `composite` | `CompositeTradeOffer` | `offers` 递归编译 |

| `cooldownType` | `CooldownType` | 枚举解析（复用 `parseCooldownType`） |
| `sounds` (×7) | `SoundEvent` (nullable) | `parseNullableSound()` |

> **裁决 #6 + #12 + #13**：最大程度复用已有编译工具，category 通过 ID 字符串反向查找，isCost 由列表位置决定。

### 7.2 条件复用

`visibleCondition` / `canBuyCondition` 使用已有的 `ConditionSpec` 和 `ConditionBridge`（与 dialogue 复用）。**无需新建条件体系。**

---

## 八、JSON 输出格式

```json
{
  "shopId": "arc_quest:blacksmith_shop",
  "displayName": { "mode": "translatable", "value": "arc_quest.trade.shop.blacksmith_shop.name" },
  "description": { "mode": "translatable", "value": "arc_quest.trade.shop.blacksmith_shop.desc" },
  "categories": [
    { "categoryId": "weapons", "displayName": { "mode": "translatable", "value": "arc_quest.trade.category.weapons" }, "sortOrder": 0, "formatting": "RED" }
  ],
  "entries": {
    "iron_sword": {
      "entryId": "iron_sword",
      "displayName": { "mode": "translatable", "value": "arc_quest.trade.entry.iron_sword.name" },
      "costs": [
        { "type": "item", "itemId": "minecraft:emerald", "count": 5 }
      ],
      "rewards": [
        { "type": "item", "itemId": "minecraft:iron_sword", "count": 1 }
      ],
      "category": "weapons",
      "maxPurchases": 2,
      "cooldownType": "GAME_TICK",
      "resetTimeTicks": 0,
      "sortOrder": 1
    }
  },
  "simpleMode": false,
  "themeColor": 15532032,
  "openSound": "",
  "closeSound": ""
}
```

---

## 九、编辑器端设计

### 9.1 State 扩展

```javascript
trade: {
    q: createTradeSkeleton(),
    meta: {file: 'new_shop.json', dirty: false},
    ui: { sel: {t: 'overview'}, entryFold: false },
    diag: []
}
```

### 9.2 工厂函数

| 函数 | 说明 |
|------|------|
| `createTradeSkeleton()` | 商店骨架（shopId/displayName/entries/categories） |
| `createTradeEntrySkeleton()` | 条目骨架（entryId/costs/rewards/conditions/cooldown） |
| `createTradeOfferSkeleton()` | Offer 骨架（type + 对应字段，默认 type=item） |

### 9.3 编辑器 UX 设计

```
左栏: 目录树                        中栏: 页面
┌─ 商店: blacksmith_shop ────┐     ┌─ ⚙ 商店配置 (概览页) ─────┐
│ 📋 概览                     │     │ 商店 ID / 显示名称 / 主题色   │
│ 📦 条目 (5)                 │     │ [条目摘要卡片] [条目摘要卡片]  │
│   ├─ iron_sword             │     │        ＋ 添加                │
│   ├─ diamond_sword          │     └─────────────────────────────┘
│   └─ ...                    │
└─────────────────────────────┘     点击卡片 → 条目编辑页（独立页）
                                         ← 返回 | entryId/displayName
                                         costs 列表（可折叠 offer 编辑）
                                         rewards 列表
                                         conditions / cooldown
```

#### Offer 编辑交互

每个 offer 行内显示 type 选择器 + 对应字段 + 删除按钮。切换 type 时自动切换可见字段，但不丢失其他 type 字段的值：

```
成本 #1: [▼item] [emerald      ] [x5] [✕]
成本 #2: [▼command] [say hello   ] [执行: ▼console] [✕]
成本 #3: [▼composite] → [子offer列表可展开]
＋ 添加成本
```

> **裁决 #9 + #18**：行内可折叠模式，type 下拉自动切换对应表单。编辑器端 `themeColor` 提供 int ↔ hex 转换 + 颜色预览块。

### 9.4 编辑器 Mode Tab 波及清单

新增第 4 模式"商店"需同步修改：

| # | 文件 | 位置 | 改动 |
|---|------|------|------|
| 1 | `index.html` | `dom.modeBar` | 新增 `<div data-mode="trade">商店</div>` |
| 2 | `app.js` | `dom.newBtn.onclick` | trade 分支：`state.trade.q = createTradeSkeleton()` |
| 3 | `app.js` | `dom.validateBtn.onclick` | trade 校验分支 |
| 4 | `app.js` | `modeBar.onclick` | `state.mode = 'trade'` 时重置 tab |
| 5 | `app.js` | `rerender()` | 新增 `else if (state.mode === 'trade') renderTrade()` |
| 6 | `import-export.js` | 类型检测 | 检测 `json.entries && json.shopId` 判定为 trade |
| 7 | `import-export.js` | 导出分支 | `exportTradeToDatapack(state.trade.q)` |

---

## 十、波及面评估

### 10.1 Java 端

| 模块 | 受影响类型 | 文件数 |
|------|-----------|--------|
| `trade/spec/` | **新建** | 4 Spec + 3 Validator |
| `trade/spec/compile/` | **新建** | 1 Compiler + 1 Exception |
| `trade/spec/io/` | **新建** | 1 Reader + 1 Writer |
| `trade/io/` | **新建** | 1 Loader + 1 HotReload |
| `trade/registry/TradeRegistry.java` | 修改 | 双 Map + 新方法 |
| `data/ArcQuestReloadListener.java` | 修改 | 注册 HotReloadService |

**小计**：15 新建文件 + 2 修改 = **17 文件**

### 10.2 编辑器端

| 模块 | 受影响类型 | 文件数 |
|------|-----------|--------|
| `index.html` | 修改 | 新增 mode-tab"商店"标签 |
| `core/` | 修改 + 新建 | state.js, factories.js (修改); trade-normalizer.js, trade-validators.js, trade-shape.js (新建) |
| `editors/` | **新建** | trade-editor.js, trade-entry-editor.js |
| `renderers/` | **新建** | trade-tree-renderer.js |
| `renderers/center-renderer.js` | 修改 | renderTradeCenter |
| `renderers/event-bindings.js` | 修改 | bindTradeEditorActions |
| `app.js` | 修改 | renderTrade + ensureValid + 4 处 mode 分支 |
| `app/import-export.js` | 修改 | trade 类型检测 + 导入/导出 |

**小计**：8 新建文件 + 6 修改 = **14 文件**

---

## 十一、裁决清单

| # | 议题 | 裁决 | 理由 | 施工优先级 |
|---|------|------|------|-----------|
| 1 | Spec 层位置 | `trade/spec/` 目录，对标对话模块 | 层级一致性 | P0 |
| 2 | TradeOfferSpec 多态 | 单一类 + `type` 字段 | Gson 序列化简单 | P0 |
| 3 | Registry 改造 | 双 Map 分离 code/datapack | 对标 DialogueRegistry | P0 |
| 4 | purchaseResetCondition | 保留 Code-only，不出现在 Spec | Predicate 无法 JSON 表达 | P0 |
| 5 | Validator 范围 | 必填检查 + 类型合法性 + ID 唯一性 | 对标 DialogueSpecValidator | P0 |
| 6 | Compiler 复用 | 复用 ConditionBridge / compileText / parseSound | 最小化重复 | P0 |
| 7 | Reload 路径 | ResourceLoader → Validator → Compiler → Registry | 与 dialogue 链路一致 | P0 |
| 8 | Gacha 模块 | Phase 2，本次不纳入 | 控制施工范围 | P1 |
| 9 | 编辑器 TradeOffer 编辑 | 行内可折叠模式，type 选择器控制字段 | UX 空间节省 | P1 |
| 10 | 施工顺序 | Java Spec → Compiler+Validator → Loader+HotReload → Registry → Editor State → Renderer → EventBinding | 先 Java 可独立验证，再对接编辑器 | - |
| 11 | TradeText 编译 | `DialogueTextSpec` → `TradeText` 在 Compiler 中转换 | TradeText 与 DialogueText 语义一致，无需新建 TradeTextSpec | P0 |
| 12 | category 编译 | `TradeEntrySpec.category` (String) 在 Compiler 中从 `categories` 列表按 ID 查找匹配 `TradeCategory` | JSON 中 category 为 ID 字符串 | P0 |
| 13 | offer isCost 语义 | Compiler 遍历 costs 时 `isCost=true`，遍历 rewards 时 `isCost=false` | 列表位置天然区分，无需额外字段 | P0 |
| 14 | Validator offer 校验 | 只校验 `offer.type` 对应的必填字段，忽略其他 type 字段 | 避免编辑器切换 type 后"脏数据"误报 | P0 |
| 15 | 线程安全 | `datapackShops` 用 `volatile` + `Collections.unmodifiableMap` 原子替换 | 对标 DialogueRegistry 模式，无需锁 | P0 |
| 16 | freeze 调用时序 | `TradeContent.registerAll()` → `freeze()` → reload 填充 `datapackShops` | 现有代码调用链无需改动 | P0 |
| 17 | 编辑器 mode tab | 新增第 4 模式"商店"，波及 7 处 | 全面覆盖模式切换 | P1 |
| 18 | 编辑器 themeColor | 编辑器端实现 int ↔ hex 颜色转换 + 预览 | 用户不能用 10 进制数字配置颜色 | P1 |
| 19 | datapack 目录 | `data/<ns>/arc_quest/trades/*.json` | 与其他模块命名一致 | P0 |
| 20 | entries 为空 | 商店允许 0 条目，Validator 降为 WARN | 允许商店骨架先行创建 | P0 |

---

## 十二、施工顺序

### Phase 1: Java 全链路 (P0)

```
1. trade/spec/TradeShopSpec.java          ← 数据结构
2. trade/spec/TradeEntrySpec.java         ← 数据结构
3. trade/spec/TradeOfferSpec.java         ← 数据结构
4. trade/spec/TradeCategorySpec.java      ← 数据结构
5. trade/spec/validate/TradeValidationIssue.java   ← 校验模型
6. trade/spec/validate/TradeValidationReport.java  ← 校验报告
7. trade/spec/validate/TradeSpecValidator.java     ← 校验逻辑
8. trade/spec/compile/TradeCompileException.java   ← 编译异常
9. trade/spec/compile/TradeSpecCompiler.java       ← 编译逻辑
10. trade/spec/io/TradeSpecJsonReader.java          ← JSON 读取
11. trade/spec/io/TradeSpecJsonWriter.java          ← JSON 写入
12. trade/io/TradeDatapackResourceLoader.java       ← 文件扫描
13. trade/io/TradeDatapackHotReloadService.java     ← 热重载
14. trade/registry/TradeRegistry.java               ← 双 Map 改造
15. data/ArcQuestReloadListener.java               ← 注册服务
```

> Phase 1 完成后执行 `./gradlew compileJava` 验证。

### Phase 2: 编辑器端 (P1)

```
16. core/state.js                     ← trade 字段
17. core/factories.js                 ← 3 骨架函数
18. core/trade-normalizer.js          ← 导入/导出
19. core/trade-validators.js          ← 校验
20. core/trade-shape.js              ← data-b 路径绑定
21. editors/trade-editor.js           ← 概览页
22. editors/trade-entry-editor.js     ← 单条目编辑页
23. renderers/trade-tree-renderer.js  ← 左栏树
24. renderers/center-renderer.js      ← renderTradeCenter 路由
25. renderers/event-bindings.js       ← bindTradeEditorActions
26. app.js                            ← renderTrade + ensureValid
27. app/import-export.js              ← trade 导入/导出
28. index.html                        ← 新增"商店" mode-tab
```

---

## 十三、Java 端变更文件清单

| # | 文件路径 | 类型 | 说明 |
|---|----------|------|------|
| 1 | `trade/spec/TradeShopSpec.java` | 新建 | 商店 Spec |
| 2 | `trade/spec/TradeEntrySpec.java` | 新建 | 条目 Spec |
| 3 | `trade/spec/TradeOfferSpec.java` | 新建 | Offer Spec（多态 type 字段） |
| 4 | `trade/spec/TradeCategorySpec.java` | 新建 | 分类 Spec |
| 5 | `trade/spec/validate/TradeSpecValidator.java` | 新建 | 校验器 |
| 6 | `trade/spec/validate/TradeValidationIssue.java` | 新建 | 校验问题 |
| 7 | `trade/spec/validate/TradeValidationReport.java` | 新建 | 校验报告 |
| 8 | `trade/spec/compile/TradeSpecCompiler.java` | 新建 | 编译器 |
| 9 | `trade/spec/compile/TradeCompileException.java` | 新建 | 编译异常 |
| 10 | `trade/spec/io/TradeSpecJsonReader.java` | 新建 | JSON 读取（Gson） |
| 11 | `trade/spec/io/TradeSpecJsonWriter.java` | 新建 | JSON 写入（Gson） |
| 12 | `trade/io/TradeDatapackResourceLoader.java` | 新建 | datapack 文件扫描 |
| 13 | `trade/io/TradeDatapackHotReloadService.java` | 新建 | 热重载服务 |
| 14 | `trade/registry/TradeRegistry.java` | 修改 | 双 Map + registerDatapack |
| 15 | `data/ArcQuestReloadListener.java` | 修改 | 注册 TradeHotReloadService |

## 十四、编辑器端变更文件清单

| # | 文件路径 | 类型 | 说明 |
|---|----------|------|------|
| 1 | `scripts/core/state.js` | 修改 | 新增 `trade` 字段 |
| 2 | `scripts/core/factories.js` | 修改 | 新增 3 个骨架函数 |
| 3 | `scripts/core/trade-normalizer.js` | 新建 | 导入规范化 + 导出清理 |
| 4 | `scripts/core/trade-validators.js` | 新建 | 商店/条目校验 |
| 5 | `scripts/core/trade-shape.js` | 新建 | `data-b` 路径 → state 绑定 |
| 6 | `scripts/editors/trade-editor.js` | 新建 | 概览页渲染 |
| 7 | `scripts/editors/trade-entry-editor.js` | 新建 | 单条目编辑页（含 offer 行内编辑） |
| 8 | `scripts/renderers/trade-tree-renderer.js` | 新建 | 左栏目录树 + 点击绑定 |
| 9 | `scripts/renderers/center-renderer.js` | 修改 | 新增 `renderTradeCenter` 路由 |
| 10 | `scripts/renderers/event-bindings.js` | 修改 | 新增 `bindTradeEditorActions` |
| 11 | `scripts/app.js` | 修改 | `renderTrade` + `ensureValidTradeSelection` + mode 分支（4处） |
| 12 | `scripts/app/import-export.js` | 修改 | 商店类型检测 + 导入/导出 |
| 13 | `index.html` | 修改 | mode-tab 新增"商店"标签 |
| 14 | `styles/editor.css` | 修改 | Trade 新增 class（按需） |
