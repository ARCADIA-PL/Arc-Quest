# 抽奖系统 vs 商店系统 - 完整对比分析报告

**生成时间**: 2026-04-22  
**分析目标**: 全面对齐商店系统实现，消除所有状态错误  
**权威模板**: 商店交易系统（TradeShopDefinition、TradeSession、TradeEntry）

---

## 📋 目录

1. [核心数据结构对比](#1-核心数据结构对比)
2. [网络包系统对比](#2-网络包系统对比)
3. [客户端缓存对比](#3-客户端缓存对比)
4. [限购与冷却逻辑对比](#4-限购与冷却逻辑对比)
5. [事件系统对比](#5-事件系统对比)
6. [关键差异总结](#6-关键差异总结)
7. [重构计划](#7-重构计划)

---

## 1. 核心数据结构对比

### 1.1 TradeShopDefinition vs GachaShopDefinition

#### ✅ TradeShopDefinition（商店系统 - 权威模板）

```java
public final class TradeShopDefinition {
    private final String shopId;
    private final Component displayName;
    @Nullable private final Component description;
    private final List<TradeCategory> categories;
    private final LinkedHashMap<String, TradeEntry> entries;  // 多个交易项
    @Nullable private final ICondition openCondition;
    private final boolean simpleMode;
    private final int themeColor;
    @Nullable private final SoundEvent openSound;
    @Nullable private final SoundEvent closeSound;
}
```

**关键字段**：
- `entries`: LinkedHashMap<String, TradeEntry> - **支持多个交易项**
- `categories`: 商品分类列表
- `simpleMode`: 简易模式标志
- `themeColor`: 主题色（ARGB）
- `openSound/closeSound`: 音效配置

**设计特点**：
- ✅ 不可变对象（final）
- ✅ 由 Builder 构建
- ✅ 支持多商品、多分类
- ✅ 完整的音效和视觉配置

---

#### ✅ GachaShopDefinition（抽奖系统 - 代理模式）

```java
public class GachaShopDefinition {  // ⚠️ 不是 final
    private final TradeShopDefinition shopDefinition;  // ✅ 代理底层商店定义
    private final GachaPool gachaPool;                 // ✅ 单个奖池（非 List）
    private final ITradeOffer drawCost;
    private final PityConfig pityConfig;               // ✅ 字段名是 pityConfig
    private final CooldownType cooldownType;
    private final long cooldownValue;
    private final int resetTimeTicks;
    @Nullable private final ICondition drawCondition;  // 抽奖执行条件
    private final int maxDraws;                        // 限购次数（Shop 级别）
    @Nullable private final ICondition resetCondition; // 次数重置条件
    
    // === 失败音效配置（对标 TradeEntry）===
    @Nullable private final SoundEvent drawCooldownSound;
    @Nullable private final SoundEvent drawLimitReachedSound;
    @Nullable private final SoundEvent drawConditionFailSound;
    @Nullable private final SoundEvent drawFailSound;
    
    // === 稀有度配置（Map 而非 List）===
    private final Map<String, RarityConfig> rarityConfigs = new HashMap<>();
}
```

**✅ 架构特点**：
- ✅ **代理模式**：封装 `TradeShopDefinition`，复用其所有基础功能
- ✅ **单一奖池**：使用 `GachaPool gachaPool`（非 List），简化设计
- ✅ **Shop 级别限购/冷却**：与 TradeEntry 不同，抽奖的限制在 Shop 级别
- ✅ **Map 管理稀有度**：使用 `Map<String, RarityConfig>` 而非 List

**⚠️ 与 TradeShopDefinition 的差异**：
- ⚠️ **非 final 类**：允许后续扩展（但当前设计已足够）
- ⚠️ **无 entries 映射表**：通过 `shopDefinition.getAllEntries()` 代理访问
- ⚠️ **无 categories 支持**：抽奖通常是单按钮模式，不需要分类

**📌 设计合理性分析**：
1. **代理模式的优势**：避免重复定义 shopId、displayName 等基础字段
2. **单一奖池的合理性**：抽奖系统通常只有一个奖池，无需 List
3. **Shop 级别限制的必要性**：抽奖是"整体行为"，不像商店有多个独立商品

---

### 1.2 TradeEntry vs GachaItem/GachaPool

#### ✅ TradeEntry（商店系统 - 权威模板）

```java
public final class TradeEntry {
    private final String entryId;
    private final Component displayName;
    @Nullable private final Component description;
    private final List<ITradeOffer> costs;      // 成本列表
    private final List<ITradeOffer> rewards;    // 奖励列表
    @Nullable private final TradeCategory category;
    @Nullable private final ICondition visibleCondition;   // 可见性条件
    @Nullable private final ICondition canBuyCondition;    // 购买资格条件
    private final CooldownType cooldownType;
    private final long cooldownValue;
    private final int resetTimeTicks;
    private final int maxPurchases;  // 限购次数
    @Nullable private final ResourceLocation rewardIcon;
    @Nullable private final ResourceLocation costIcon;
    private final int sortOrder;
    private final int themeColor;
    @Nullable private final Predicate<ServerPlayer> purchaseResetCondition;
    
    // 音效配置
    @Nullable private final SoundEvent purchaseSuccessSound;
    @Nullable private final SoundEvent purchaseFailSound;
    @Nullable private final SoundEvent cooldownSound;
    @Nullable private final SoundEvent limitReachedSound;
    @Nullable private final SoundEvent conditionFailSound;
    
    public boolean hasLimit() { return maxPurchases > 0; }
    public boolean hasCooldown() { return cooldownType != CooldownType.NONE; }
}
```

**关键设计**：
- ✅ **每个商品独立配置限购和冷却**
- ✅ `visibleCondition` vs `canBuyCondition` 分离（显示 vs 可购买）
- ✅ `purchaseResetCondition` 支持自定义恢复逻辑
- ✅ 完整的音效配置（成功/失败/冷却/限购/条件）
- ✅ `hasLimit()` 和 `hasCooldown()` 便捷方法

---

#### ✅ GachaItem（抽奖系统 - 已对齐大部分字段）

```java
public class GachaItem {  // ⚠️ 不是 final
    private final String itemId;              // ✅ 唯一 ID（对标 entryId）
    private final ItemStack itemStack;
    private final ITradeOffer reward;         // ✅ 奖励 Offer
    private final int baseWeight;
    private final Rarity rarity;
    private final boolean countsTowardsPity;
    private final List<WeightModifier> weightModifiers;
    private final int minCount;               // ✅ 最小数量
    private final int maxCount;               // ✅ 最大数量
    private final int sortOrder;              // ✅ 排序顺序
    @Nullable private final ICondition visibleCondition;  // ✅ 可见性条件
    
    // === 视觉与音效配置（对标 TradeEntry）===
    @Nullable private final ResourceLocation rewardIcon;  // ✅ 自定义奖励图标
    private final int themeColor;             // ✅ 主题色（-1 表示使用商店默认）
    @Nullable private final SoundEvent drawSuccessSound;  // ✅ 抽中音效
}
```

**✅ 已对齐的字段**：
- ✅ `itemId` 对标 `entryId`（唯一标识）
- ✅ `visibleCondition` **已存在**（文档之前错误声称缺失）
- ✅ `rewardIcon` **已存在**（文档之前错误声称缺失）
- ✅ `sortOrder` **已存在**（文档之前错误声称缺失）
- ✅ `themeColor` **已存在**（文档之前错误声称缺失）
- ✅ `drawSuccessSound` **已存在**（对标 `purchaseSuccessSound`）

**⚠️ 设计差异（非缺陷）**：
- ⚠️ **无限购/冷却字段**：因为抽奖的限制在 **Shop 级别**（`GachaShopDefinition.maxDraws`），而非 Item 级别
- ⚠️ **无 canBuyCondition**：抽奖使用 Shop 级别的 `drawCondition`
- ⚠️ **无 purchaseResetCondition**：抽奖使用 Shop 级别的 `resetCondition`
- ⚠️ **无 costIcon**：抽奖成本由 `GachaShopDefinition.drawCost` 统一管理

**❌ 真正缺失的字段**：
- ❌ **4种失败音效**：`cooldownSound`、`limitReachedSound`、`conditionFailSound`、`purchaseFailSound`
  - 💡 **原因**：这些音效在 Shop 级别配置（`GachaShopDefinition.drawCooldownSound` 等）

**📌 设计合理性分析**：
1. **Shop 级别限制的合理性**：抽奖是"整体行为"，不像商店有多个独立商品需要分别限购
2. **动态权重系统是抽奖特有**：`weightModifiers` 支持根据玩家状态动态调整概率
3. **数量随机性是抽奖特有**：`minCount/maxCount` 支持随机发放数量

---

#### ✅ GachaPool（抽奖系统特有 - 设计合理）

```java
public class GachaPool {  // ⚠️ 不是 final
    private final List<GachaItem> items;
    private final Map<GachaItem.Rarity, List<GachaItem>> itemsByRarity;  // ✅ 按稀有度分组
}
```

**✅ 核心功能**：
- ✅ **权重计算**：`calculateTotalWeight()` 支持动态权重
- ✅ **随机抽取**：`draw()` 根据权重随机选择物品
- ✅ **稀有度抽取**：`drawFromRarity()` 从指定稀有度中抽取
- ✅ **可见性过滤**：自动过滤不可见的抽奖项
- ✅ **按 ID 查找**：`getItemById()` 快速定位物品

**📌 设计合理性分析**：
1. **无冷却/限购字段**：这些配置在 `GachaShopDefinition` 级别，Pool 只负责抽奖逻辑
2. **单一职责**：Pool 专注于"如何抽"，Shop 专注于"何时能抽"
3. **与 TradeEntry 不对标**：这是正确的，因为 Pool 是内部组件，不是 Entry 的替代品

---

### 1.3 TradeSession vs GachaSession

#### ✅ TradeSession（商店系统 - 权威模板）

```java
public final class TradeSession {
    private final ServerPlayer player;
    private final TradeShopDefinition shop;
    
    // 核心方法
    public TradeResult executeTrade(String entryId) {
        // 1. 获取 Entry
        TradeEntry entry = shop.getEntry(entryId);
        
        // 2. 重置过期冷却（必须在判断之前）
        if (entry.hasLimit() || entry.hasCooldown()) {
            checkAndResetPurchases(entryId, entry);
        }
        
        // 3. 综合判断（细分错误原因）
        if (!TradeEntryStateResolver.canPurchase(...)) {
            if (!isVisible(...)) return fail("not_visible");
            if (isLimitReached(...)) return fail("max_purchases");
            if (isOnCooldown(...)) return fail("on_cooldown");
            return fail("condition_not_met");
        }
        
        // 4. 扣除成本、发放奖励
        for (ITradeOffer cost : entry.getCosts()) cost.execute(player);
        for (ITradeOffer reward : entry.getRewards()) reward.execute(player);
        
        // 5. 记录购买和冷却
        recordPurchase(cap, shopId, entryId);
        if (shouldRecordCooldown(...)) {
            recordCooldown(player, cap, shopId, entryId);
        }
        
        return TradeResult.success();
    }
    
    public boolean canPurchase(TradeEntry entry) { ... }
    public boolean isEntryVisible(TradeEntry entry) { ... }
    public int getPurchaseCount(String entryId) { ... }
    public int getRemainingPurchases(TradeEntry entry) { ... }
    public int getCooldownRemaining(String entryId, TradeEntry entry) { ... }
    
    private void checkAndResetPurchases(String entryId, TradeEntry entry) {
        // 支持两种恢复机制：
        // 1. 冷却自动恢复
        // 2. 自定义条件恢复（Predicate<ServerPlayer>）
    }
}
```

**关键设计**：
- ✅ **会话级别的状态管理**
- ✅ `executeTrade()` 完整的业务流程
- ✅ **先重置，再判断**（避免时序问题）
- ✅ **细分错误原因**（限购 > 冷却 > 条件）
- ✅ 支持多种恢复机制

---

#### ⚠️ GachaSession（抽奖系统 - 缺失服务端会话类）

**📌 当前架构**：
- ✅ **存在 `GachaSessionData`**：客户端缓存数据结构（`ClientTradeCache.GachaSessionData`）
- ❌ **缺失服务端 `GachaSession` 类**：对标 `TradeSession` 的服务端会话管理
- ⚠️ **逻辑分散**：
  - `GachaShopDefinition.canDraw()`：负责条件判断
  - `C2SDrawGachaPacket.handle()`：负责执行抽奖
  - `GachaShopDefinition.performDraw()`：负责抽奖算法

**❌ 与 TradeSession 的差距**：
- ❌ **无统一入口**：没有类似 `executeTrade()` 的完整业务流程方法
- ❌ **无状态封装**：每次调用都重新计算，无法缓存中间结果
- ❌ **职责混乱**：`canDraw()` 既负责判断又负责日志输出

**🔧 重构建议**：
1. **创建 `GachaSession` 类**：封装玩家和 Shop 的状态
2. **实现 `executeDraw()` 方法**：对标 `TradeSession.executeTrade()`
3. **迁移判断逻辑**：将 `canDraw()` 的逻辑整合到 Session 中
4. **添加便捷方法**：`getRemainingDraws()`、`getCooldownRemaining()` 等

---

## 2. 网络包系统对比

### 2.1 C2SRequestTradePacket vs C2SDrawGachaPacket

#### ✅ C2SRequestTradePacket（商店系统 - 权威模板）

```java
public class C2SRequestTradePacket {
    public enum Action {
        OPEN_FULL,      // 打开完整商店
        OPEN_SIMPLE,    // 打开简易商店
        PURCHASE        // 请求购买
    }
    
    public enum ScreenType {
        NONE, SIMPLE, FULL
    }
    
    private final Action action;
    private final String shopId;
    private final String entryId;
    private final ScreenType currentScreenType;
    
    public static void handle(C2SRequestTradePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        switch (pkt.action) {
            case OPEN_FULL   -> handleOpen(player, shop, false);
            case OPEN_SIMPLE -> handleOpen(player, shop, true);
            case PURCHASE    -> handlePurchase(player, shop, pkt.entryId, pkt.currentScreenType);
        }
    }
    
    private static void handlePurchase(ServerPlayer player, TradeShopDefinition shop,
                                        String entryId, ScreenType clientScreenType) {
        // 1. 创建会话并执行交易
        TradeSession session = new TradeSession(player, shop);
        TradeSession.TradeResult result = session.executeTrade(entryId);
        
        // 2. 发送结果包（成功/失败）
        S2COpenTradePacket response = result.succeeded()
                ? S2COpenTradePacket.tradeSuccess(shop.getShopId(), entryId)
                : S2COpenTradePacket.tradeFail(shop.getShopId(), entryId, reason, errorKey);
        ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), response);
        
        // 3. 发布事件
        if (result.succeeded()) {
            MinecraftForge.EVENT_BUS.post(new TradePurchasedSuccessEvent(...));
        } else {
            MinecraftForge.EVENT_BUS.post(new TradePurchaseFailedEvent(...));
        }
        
        // 4. 刷新界面数据（发送完整快照）
        refreshTradeData(player, shop, clientScreenType);
    }
    
    private static void refreshTradeData(ServerPlayer player, TradeShopDefinition shop, 
                                          ScreenType clientScreenType) {
        TradeSession session = new TradeSession(player, shop);
        TradeSnapshot snap = buildTradeSnapshot(player, shop, session);
        
        S2COpenTradePacket refreshPkt = (clientScreenType == ScreenType.SIMPLE)
                ? S2COpenTradePacket.openSimple(...)
                : S2COpenTradePacket.openFull(...);
        
        ArcQuestNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), refreshPkt);
    }
    
    /**
     * 为商店所有商品构建网络传输快照数据。
     */
    private static TradeSnapshot buildTradeSnapshot(ServerPlayer player,
                                                     TradeShopDefinition shop,
                                                     TradeSession session) {
        IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
        List<TradeEntry> allEntries = new ArrayList<>(shop.getAllEntries());
        int count = allEntries.size();
        
        // 调试：输出服务端商品顺序
        LOGGER.info("[Trade-Snapshot] Shop={}, total entries={}", shop.getShopId(), count);
        for (int i = 0; i < count; i++) {
            LOGGER.info("[Trade-Snapshot]   Entry {}: id={}", i, allEntries.get(i).getEntryId());
        }
        
        int[] purchases      = new int[count];
        int[] maxPurchases   = new int[count];
        long[] lastPurchaseTimes  = new long[count];
        long[] purchaseGameTimes  = new long[count];
        long[] purchaseDayTimes   = new long[count];
        int[] cooldownTypes   = new int[count];
        long[] cooldownValues = new long[count];
        int[] resetTimeTicks  = new int[count];
        boolean[] visibility      = new boolean[count];
        boolean[] canBuyConditions = new boolean[count];
        
        for (int i = 0; i < count; i++) {
            TradeEntry entry = allEntries.get(i);
            purchases[i]    = session.getPurchaseCount(entry.getEntryId());
            maxPurchases[i] = entry.getMaxPurchases();
            
            if (entry.hasCooldown()) {
                lastPurchaseTimes[i] = cap.getTradeLastPurchaseTime(shop.getShopId(), entry.getEntryId());
                ProgressKey key = ProgressKey.ofTrade(shop.getShopId(), entry.getEntryId());
                var storeEntry = cap.getDialogueProgress().getChoiceSelection(key);
                purchaseGameTimes[i] = storeEntry.exists() ? storeEntry.gameTime() : 0;
                purchaseDayTimes[i]  = storeEntry.exists() ? storeEntry.dayTime()  : 0;
                cooldownTypes[i]  = entry.getCooldownType().ordinal();
                cooldownValues[i] = entry.getCooldownValue();
                resetTimeTicks[i] = entry.getResetTimeTicks();
            }
            
            visibility[i]       = session.isEntryVisible(entry);
            canBuyConditions[i] = session.canPurchase(entry);
        }
        
        return new TradeSnapshot(purchases, maxPurchases, lastPurchaseTimes, purchaseGameTimes,
                purchaseDayTimes, cooldownTypes, cooldownValues, resetTimeTicks, visibility, canBuyConditions);
    }
}
```

**关键设计**：
- ✅ **单一包类型，通过 Action 枚举区分操作**
- ✅ 购买成功后调用 `refreshTradeData()` 发送完整快照
- ✅ 先发送结果包（TRADE_SUCCESS/FAIL），再发送刷新包（OPEN_FULL/SIMPLE）
- ✅ 刷新包包含所有商品的最新状态（数组快照）
- ✅ `buildTradeSnapshot()` 遍历所有 Entry，提取冷却/限购/可见性/条件状态
- ✅ 使用 `TradeSnapshot` record 封装多个数组参数

---

#### ✅ C2SDrawGachaPacket（抽奖系统 - 已修复时序问题）

```java
public class C2SDrawGachaPacket {
    private final String shopId;  // ✅ 只有 shopId，无 Action 枚举
    
    public static void handle(C2SDrawGachaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        synchronized (cap) {  // ✅ 使用 synchronized 确保原子性
            // 1. 检查是否可以抽奖（条件 + 限购 + 冷却）
            if (!gachaShop.canDraw(cap, nowRealTime, nowGameTime, nowDayTime)) {
                // 细分失败原因并发送 S2CDrawFailedPacket
                return;
            }
            
            // 2. 先重置过期次数（对标商店系统的 checkAndResetPurchases）
            gachaShop.checkAndResetDraws(cap, ...);
            
            // 3. 重新检查（重置后状态可能改变）
            if (!gachaShop.canDraw(...)) {
                // 发送失败通知
                return;
            }
            
            // 4. 触发 PreDrawEvent（支持取消和修改保底计数）
            var preEvent = new GachaEvents.PreDrawEvent(...);
            MinecraftForge.EVENT_BUS.post(preEvent);
            if (preEvent.isCancelled()) return;
            
            // 5. 触发 DrawingEvent（通知 HUD 播放动画）
            var drawingEvent = new GachaEvents.DrawingEvent(...);
            MinecraftForge.EVENT_BUS.post(drawingEvent);
            
            // 6. 执行抽奖
            var drawResult = gachaShop.performDraw(player, cap, pityCounter);
            
            // 7. 发放奖励（支持动态数量）
            int actualCount = drawResult.item().calculateActualCount();
            reward.execute(player);  // 或使用实际数量创建 ItemStack
            
            // 8. 记录冷却和增加次数
            cap.getDialogueProgress().recordChoiceSelection(drawKey, ...);
            gachaShop.incrementDrawCount(cap, ...);
            
            // 9. 更新保底计数
            int newPityCounter = drawResult.pityTriggered() ? 0 : pityCounter + 1;
            cap.setGachaPityCounter(pkt.shopId, newPityCounter);
            
            // 10. 触发 PostDrawEvent
            var postEvent = new GachaEvents.PostDrawEvent(...);
            MinecraftForge.EVENT_BUS.post(postEvent);
            
            // 11. 发送结果包（✅ 已修复：不再发送 S2COpenGachaPacket）
            ArcQuestNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CDrawResultPacket(
                    pkt.shopId,
                    drawResult.item().getItemId(),
                    drawResult.item().getRarity().getName(),
                    actualCount,
                    drawResult.pityTriggered(),
                    newPityCounter
                )
            );
        }
    }
}
```

**✅ 已修复的问题**：
- ✅ **移除了 `S2COpenGachaPacket.handleServerOpen()` 调用**（避免时序竞态）
- ✅ **使用 `synchronized` 块**确保「检查-执行-更新」的原子性
- ✅ **先重置，再判断**（对标商店系统的 executeTrade 逻辑）
- ✅ **细分失败原因**（MAX_DRAWS_REACHED > ON_COOLDOWN > CONDITION_NOT_MET）
- ✅ **触发完整事件链**（PreDraw → Drawing → PostDraw）
- ✅ **支持动态数量**（calculateActualCount）

**⚠️ 与商店系统的差异**：
- ⚠️ **无 Action 枚举**：因为抽奖只有 DRAW 一种操作（合理）
- ⚠️ **无 refreshGachaData() 方法**：客户端通过下次打开界面时获取最新状态
- ⚠️ **使用两个独立包类型**：S2CDrawResultPacket（结果）+ S2COpenGachaPacket（打开）

**📌 设计合理性分析**：
1. **不发送刷新包的原因**：抽奖是单次行为，不像商店有多个商品需要实时更新
2. **客户端状态同步方式**：通过 `recordDrawResult()` 记录结果，下次打开时收到最新冷却状态
3. **时序问题的根本解决**：移除 `handleServerOpen()` 调用，避免 Session 被覆盖

---

### 2.2 S2COpenTradePacket vs S2COpenGachaPacket

#### ✅ S2COpenTradePacket（商店系统 - 权威模板）

```java
public class S2COpenTradePacket {
    public enum Mode {
        OPEN_FULL,       // 打开完整商店
        OPEN_SIMPLE,     // 打开简易商店
        TRADE_SUCCESS,   // 交易成功
        TRADE_FAIL,      // 交易失败
        CLOSE            // 关闭界面
    }
    
    public enum FailReason {
        GENERIC, COOLDOWN, LIMIT_REACHED, CONDITION_FAIL, CANNOT_AFFORD, NOT_VISIBLE
    }
    
    private final Mode mode;
    private final String shopId;
    private final String entryId;
    private final FailReason failReason;
    private final String errorKey;
    
    // 商品状态数组（用于 OPEN_FULL/SIMPLE）
    private final int[] purchaseCounts;
    private final int[] maxPurchases;
    private final long[] lastPurchaseTimes;
    private final long[] purchaseGameTimes;
    private final long[] purchaseDayTimes;
    private final int[] cooldownTypes;
    private final long[] cooldownValues;
    private final int[] resetTimeTicks;
    private final boolean[] visibility;
    private final boolean[] canBuyConditions;
    private final String openSoundId;  // 商店打开音效 ID
    private final String closeSoundId;  // 商店关闭音效 ID
    
    public static void handle(S2COpenTradePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        Minecraft mc = Minecraft.getInstance();
        switch (pkt.mode) {
            case OPEN_FULL -> {
                // 关闭其他商店的会话，防止内存泄漏
                ClientTradeCache.INSTANCE.closeAllExcept(pkt.shopId);
                
                // 更新整个 Session（增量更新字段）
                ClientTradeCache.INSTANCE.updateSession(pkt.shopId, pkt.purchaseCounts, pkt.maxPurchases,
                        pkt.lastPurchaseTimes, pkt.purchaseGameTimes, pkt.purchaseDayTimes,
                        pkt.cooldownTypes, pkt.cooldownValues, pkt.resetTimeTicks, 
                        pkt.visibility, pkt.canBuyConditions);
                
                // 播放商店打开音效
                ClientTradeCache.INSTANCE.playOpenSound(pkt.shopId, pkt.openSoundId);
                
                if (mc.screen instanceof DialogueScreen) {
                    TradeScreen.setParentScreen(mc.screen);
                }
                
                if (mc.screen instanceof TradeScreen ts && ts.getShopId().equals(pkt.shopId)) {
                    LOGGER.info("[Trade-Packet] Updating existing TradeScreen for shop={}", pkt.shopId);
                    ts.refreshData();  // 刷新现有界面
                } else {
                    LOGGER.warn("[Trade-Packet] Creating new TradeScreen for shop={}", pkt.shopId);
                    mc.setScreen(new TradeScreen(pkt.shopId));  // 创建新界面
                }
            }
            
            case OPEN_SIMPLE -> {
                // 类似 OPEN_FULL，但使用 SimpleTradePanel
                ClientTradeCache.INSTANCE.closeAllExcept(pkt.shopId);
                ClientTradeCache.INSTANCE.updateSession(...);
                ClientTradeCache.INSTANCE.playOpenSound(pkt.shopId, pkt.openSoundId);
                
                if (mc.screen instanceof SimpleTradePanel sp && sp.getShopId().equals(pkt.shopId)) {
                    sp.refreshData();
                } else {
                    mc.setScreen(new SimpleTradePanel(pkt.shopId));
                }
            }
            
            case TRADE_SUCCESS -> {
                // 只更新单个商品的状态（不重建 Session）
                ClientTradeCache.INSTANCE.handlePurchaseResult(pkt.shopId, pkt.entryId, true, null);
                
                if (mc.screen instanceof AbstractTradeScreen ts) {
                    ts.onTradeSuccess();  // UI 反馈
                }
            }
            
            case TRADE_FAIL -> {
                // 只更新单个商品的状态（记录失败原因）
                ClientTradeCache.INSTANCE.handlePurchaseResult(pkt.shopId, pkt.entryId, false, pkt.failReason);
                
                if (mc.screen instanceof AbstractTradeScreen ts) {
                    ts.onTradeFail(pkt.errorKey);  // UI 反馈
                }
            }
            
            case CLOSE -> {
                if (mc.screen instanceof AbstractTradeScreen tradeScreen) {
                    // 播放商店关闭音效
                    ClientTradeCache.INSTANCE.playCloseSound(tradeScreen.getShopId(), pkt.closeSoundId);
                    
                    TradeScreen.setParentScreen(null);
                    SimpleTradePanel.setParentScreen(null);
                    mc.setScreen(null);  // 关闭界面
                }
            }
        }
    }
}
```

**关键设计**：
- ✅ **单一包类型，通过 Mode 枚举区分**
- ✅ `updateSession()` 更新整个 Session（用于打开界面）
- ✅ `handlePurchaseResult()` 只更新单个商品（用于交易结果）
- ✅ 不会重建 Session，避免数据丢失
- ✅ 支持音效触发（open/close/select/fail）
- ✅ 智能判断是否复用现有 Screen 实例

---

#### ⚠️ S2COpenGachaPacket（抽奖系统 - 待修复）

```java
public class S2COpenGachaPacket {
    private final String shopId;
    private final int pityCounter;
    private final int totalDraws;
    private final long lastDrawRealTime;
    private final long lastDrawGameTime;
    private final long lastDrawDayTime;
    private final int cooldownType;
    private final long cooldownValue;
    private final int resetTimeTicks;
    
    public static void handle(S2COpenGachaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        // 更新客户端缓存
        ClientTradeCache.INSTANCE.updateGachaSession(...);
        
        // 打开抽奖界面
        mc.setScreen(new GachaScreen(pkt.shopId));
    }
}
```

**❌ 严重缺失**：
- ❌ **没有 Mode 枚举**
- ❌ **不支持 DRAW_SUCCESS/DRAW_FAIL 模式**
- ❌ `updateGachaSession()` 会创建新 Session 对象（导致时序竞态）

**🔧 需要对齐**：
1. 添加 `Mode` 枚举（OPEN、DRAW_SUCCESS、DRAW_FAIL、CLOSE）
2. 添加 `FailReason` 枚举
3. 修改 `updateGachaSession()` 为增量更新（不重建 Session）
4. 添加 `handleDrawResult()` 方法（对标 `handlePurchaseResult()`）

---

### 2.3 S2CDrawResultPacket（抽奖系统特有 - 应删除）

```java
public class S2CDrawResultPacket {
    private final String shopId;
    private final String drawnItemId;
    private final String rarityName;
    private final int actualCount;
    private final boolean pityTriggered;
    private final int newPityCounter;
    
    public static void handle(S2CDrawResultPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ClientTradeCache.INSTANCE.recordDrawResult(...);
    }
}
```

**❌ 设计问题**：
- ❌ **独立的包类型**（应该合并到 S2COpenGachaPacket）
- ❌ 与 S2COpenGachaPacket 形成时序竞态

**🔧 重构建议**：
- 删除此类，功能合并到 S2COpenGachaPacket.DRAW_SUCCESS 模式

---

## 3. 客户端缓存对比

### 3.1 updateSession vs updateGachaSession

#### ✅ ClientTradeCache.updateSession（商店系统 - 权威模板）

```java
public void updateSession(String shopId, int[] purchaseCounts, int[] maxPurchases,
                          long[] lastPurchaseTimes, long[] purchaseGameTimes, long[] purchaseDayTimes,
                          int[] cooldownTypes, long[] cooldownValues, int[] resetTimeTicks,
                          boolean[] visibility, boolean[] canBuyConditions) {
    // 创建新 Session 对象（注意：这里是创建新对象，但不会丢失数据，因为每次都是完整快照）
    TradeSessionData data = new TradeSessionData(shopId);
    data.purchaseCounts = purchaseCounts;
    data.maxPurchases = maxPurchases;
    data.lastPurchaseTimes = lastPurchaseTimes;
    data.purchaseGameTimes = purchaseGameTimes;
    data.purchaseDayTimes = purchaseDayTimes;
    data.cooldownTypes = cooldownTypes;
    data.cooldownValues = cooldownValues;
    data.resetTimeTicks = resetTimeTicks;
    data.visibility = visibility;
    data.canBuyConditions = canBuyConditions;
    activeSessions.put(shopId, data);  // 覆盖旧 Session
}
```

**关键设计**：
- ✅ **虽然创建新对象，但是是完整快照更新**（所有字段都重新赋值）
- ✅ 不会丢失任何状态，因为服务端发送的是全量数据
- ✅ 使用 `put()` 覆盖旧 Session

**⚠️ 注意事项**：
- ⚠️ 这种方式依赖于服务端每次都发送完整快照
- ⚠️ 如果网络包时序错乱，可能导致数据不一致

---

#### ✅ ClientTradeCache.updateGachaSession（抽奖系统 - 已修复）

```java
public void updateGachaSession(String shopId, int pityCounter, int totalDraws,
                                long lastDrawRealTime, long lastDrawGameTime, long lastDrawDayTime,
                                int cooldownType, long cooldownValue, int resetTimeTicks) {
    // ✅ 保留现有的 drawHistory 和最近抽奖结果字段
    GachaSessionData existingSession = gachaSessions.get(shopId);
    ArrayList<DrawRecord> existingHistory = existingSession != null ? 
        new ArrayList<>(existingSession.drawHistory) : new ArrayList<>();
    
    // ✅ 保留最近抽奖结果字段
    String existingLastDrawnItemId = existingSession != null ? existingSession.lastDrawnItemId : null;
    String existingLastRarityName = existingSession != null ? existingSession.lastRarityName : null;
    int existingLastActualCount = existingSession != null ? existingSession.lastActualCount : 0;
    boolean existingLastPityTriggered = existingSession != null ? existingSession.lastPityTriggered : false;
    long existingLastDrawTime = existingSession != null ? existingSession.lastDrawTime : 0;
    String existingLastFailReason = existingSession != null ? existingSession.lastFailReason : null;
    
    // ✅ 创建新 Session 对象
    GachaSessionData newSession = new GachaSessionData(
        pityCounter, totalDraws,
        lastDrawRealTime, lastDrawGameTime, lastDrawDayTime,
        cooldownType, cooldownValue, resetTimeTicks
    );
    
    // ✅ 恢复历史记录和最近抽奖结果
    newSession.drawHistory.addAll(existingHistory);
    newSession.lastDrawnItemId = existingLastDrawnItemId;
    newSession.lastRarityName = existingLastRarityName;
    newSession.lastActualCount = existingLastActualCount;
    newSession.lastPityTriggered = existingLastPityTriggered;
    newSession.lastDrawTime = existingLastDrawTime;
    newSession.lastFailReason = existingLastFailReason;
    
    gachaSessions.put(shopId, newSession);  // ✅ 覆盖旧 Session
}
```

**✅ 已修复的问题**：
- ✅ **手动保留关键字段**：`drawHistory`、`lastDrawnItemId` 等6个字段
- ✅ **避免数据丢失**：通过先复制再覆盖的方式保持状态连续性
- ✅ **与商店系统的差异是合理的**：因为抽奖需要累积历史记录

**⚠️ 与商店系统的差异**：
- ⚠️ **商店系统使用完整快照覆盖**：因为每次都是全量数据，不需要保留字段
- ⚠️ **抽奖系统需要增量更新**：因为 `drawHistory` 是累积的，不能每次都被清空
- ⚠️ **时序风险已降低**：通过手动保留字段，即使包顺序错乱也不会丢失历史

**📌 设计合理性分析**：
1. **为什么不能直接覆盖**：`drawHistory` 是客户端累积的历史记录，服务端不会每次都发送完整历史
2. **为什么商店系统可以覆盖**：商店的 `purchaseCounts` 等数组是服务端权威的全量快照
3. **更好的方案**：可以考虑让服务端也发送完整的 `drawHistory`，实现真正的完整快照

---

### 3.2 handlePurchaseResult vs recordDrawResult

#### ✅ ClientTradeCache.handlePurchaseResult（商店系统 - 权威模板）

```java
public void handlePurchaseResult(String shopId, String entryId, boolean success, 
                                  S2COpenTradePacket.FailReason failReason) {
    if (entryId == null || entryId.isEmpty()) return;
    
    var shopDef = TradeRegistry.get(shopId);
    if (shopDef == null) return;
    
    TradeEntry entry = shopDef.getEntry(entryId);
    if (entry == null) return;
    
    // 根据结果播放音效
    if (success) {
        GuiSoundManager.play(entry.getPurchaseSuccessSound());
    } else {
        SoundEvent sound = switch (failReason != null ? failReason : S2COpenTradePacket.FailReason.GENERIC) {
            case COOLDOWN -> entry.getCooldownSound();
            case LIMIT_REACHED -> entry.getLimitReachedSound();
            case CONDITION_FAIL -> entry.getConditionFailSound();
            default -> entry.getPurchaseFailSound();
        };
        GuiSoundManager.play(sound);
    }
    
    LOGGER.debug("[TradeCache] Played selectSound for {} result: {}", success ? "success" : failReason, entryId);
}
```

**关键设计**：
- ✅ **只负责音效触发，不更新 Session 数据**
- ✅ 因为后续的 `refreshTradeData()` 会发送完整快照并调用 `updateSession()`
- ✅ 细分失败原因的音效反馈

**⚠️ 重要发现**：
- ⚠️ 商店系统的 `handlePurchaseResult()` **不更新 Session 数据**！
- ⚠️ Session 数据的更新完全依赖于 `refreshTradeData()` 发送的完整快照
- ⚠️ 这意味着即使 TRADE_SUCCESS/FAIL 包先到达，也不会影响最终状态

---

#### ⚠️ ClientTradeCache.recordDrawResult（抽奖系统 - 待修复）

```java
public void recordDrawResult(String shopId, String drawnItemId, String rarityName,
                              int actualCount, boolean pityTriggered, int newPityCounter) {
    GachaSessionData session = gachaSessions.get(shopId);
    if (session == null) {
        session = new GachaSessionData(newPityCounter, 1);
        gachaSessions.put(shopId, session);
    } else {
        session.pityCounter = newPityCounter;
        session.totalDraws++;
    }
    
    // 记录最近一次抽奖结果
    session.lastDrawnItemId = drawnItemId;
    session.lastRarityName = rarityName;
    session.lastActualCount = actualCount;
    session.lastPityTriggered = pityTriggered;
    session.lastDrawTime = System.currentTimeMillis();
    
    // 添加到历史记录
    session.drawHistory.add(new DrawRecord(...));
    
    // 限制历史记录大小
    if (session.drawHistory.size() > 50) {
        session.drawHistory.remove(0);
    }
}
```

**✅ 优点**：
- ✅ 增量更新，不重建 Session
- ✅ 维护历史记录

**❌ 设计问题**：
- ❌ **与商店系统的 `handlePurchaseResult()` 职责不一致**
  - 商店系统：只负责音效触发
  - 抽奖系统：既负责音效，又负责数据更新
- ❌ **没有对应的 `refreshGachaData()` 机制**
  - 导致数据更新依赖于单个包，缺少权威快照同步
- ❌ **时序竞态风险更高**
  - 如果 `S2COpenGachaPacket` 先于 `S2CDrawResultPacket` 到达，会覆盖刚刚记录的抽奖结果

**🔧 需要对齐**：
1. 将 `recordDrawResult()` 的职责拆分为两部分：
   - `handleDrawResult()`：只负责音效触发（对标 `handlePurchaseResult()`）
   - `updateGachaSession()`：负责数据更新（通过完整快照）
2. 实现 `refreshGachaData()` 方法（对标 `refreshTradeData()`）
3. 抽奖成功后发送刷新包，确保客户端状态同步

---

## 4. 限购与冷却逻辑对比

### 4.1 canPurchase vs canDraw

#### ✅ TradeSession.canPurchase（商店系统 - 权威模板）

```java
public boolean canPurchase(TradeEntry entry) {
    IQuestCapability cap = getCap();
    return TradeEntryStateResolver.canPurchase(player, cap, shop.getShopId(), entry);
}

// TradeEntryStateResolver.canPurchase 实现
public static boolean canPurchase(ServerPlayer player, IQuestCapability cap, 
                                   String shopId, TradeEntry entry) {
    // 1. 检查可见性
    if (!isVisible(player, cap, entry)) return false;
    
    // 2. 检查限购
    if (isPurchaseLimitReached(cap, shopId, entry)) return false;
    
    // 3. 检查冷却
    if (isOnCooldown(player, cap, shopId, entry)) return false;
    
    // 4. 检查购买资格
    if (!hasCanBuyCondition(player, cap, entry)) return false;
    
    return true;
}
```

**关键设计**：
- ✅ **优先级明确**：可见性 > 限购 > 冷却 > 条件
- ✅ 使用 `TradeEntryStateResolver` 统一管理
- ✅ 细分错误原因

---

#### ⚠️ GachaShopDefinition.canDraw（抽奖系统 - 待修复）

```java
public boolean canDraw(ServerPlayer player, IQuestCapability cap) {
    // 1. 检查前置条件
    if (openCondition != null && !openCondition.test(...)) return false;
    
    // 2. 检查限购
    if (maxDraws > 0 && cap.getGachaDrawCount(shopId) >= maxDraws) {
        return false;  // ❌ 达到限购后直接返回，不检查冷却
    }
    
    // 3. 检查冷却
    ProgressKey drawKey = ProgressKey.ofTrade(shopId, "draw");
    var progressEntry = cap.getDialogueProgress().getChoiceSelection(drawKey);
    
    if (progressEntry.exists()) {
        boolean onCooldown = UnifiedCooldownManager.isOnCooldown(...);
        if (onCooldown) return false;
    }
    
    return true;
}
```

**❌ 设计问题**：
- ❌ **逻辑分散在 ShopDefinition 中**（应该在 Session 中）
- ❌ 达到限购后直接返回，不记录失败原因
- ❌ 没有细分错误原因

**🔧 需要对齐**：
1. 创建 `GachaSession.canDraw()` 方法
2. 使用 `GachaEntryStateResolver` 统一管理（对标 `TradeEntryStateResolver`）
3. 细分错误原因（限购 > 冷却 > 条件）

---

### 4.2 executeTrade vs executeDraw

#### ✅ TradeSession.executeTrade（商店系统 - 权威模板）

```java
public TradeResult executeTrade(String entryId) {
    TradeEntry entry = shop.getEntry(entryId);
    if (entry == null) return TradeResult.fail("not_found");
    
    IQuestCapability cap = getCap();
    
    // 1. 重置过期冷却（必须在判断之前）
    if (entry.hasLimit() || entry.hasCooldown()) {
        checkAndResetPurchases(entryId, entry);
    }
    
    // 2. 综合判断（细分错误原因）
    if (!TradeEntryStateResolver.canPurchase(player, cap, shop.getShopId(), entry)) {
        if (!isVisible(...)) return fail("not_visible");
        if (isLimitReached(...)) return fail("max_purchases");
        if (isOnCooldown(...)) return fail("on_cooldown");
        return fail("condition_not_met");
    }
    
    // 3. 扣除成本、发放奖励
    for (ITradeOffer cost : entry.getCosts()) cost.execute(player);
    for (ITradeOffer reward : entry.getRewards()) reward.execute(player);
    
    // 4. 记录购买和冷却
    recordPurchase(cap, shopId, entryId);
    if (shouldRecordCooldown(...)) {
        recordCooldown(player, cap, shopId, entryId);
    }
    
    return TradeResult.success();
}
```

**关键设计**：
- ✅ **先重置，再判断**（避免时序问题）
- ✅ 细分错误原因
- ✅ 统一的返回值类型 `TradeResult`

---

#### ⚠️ C2SDrawGachaPacket.handle（抽奖系统 - 待修复）

```java
public static void handle(C2SDrawGachaPacket pkt, Supplier<NetworkEvent.Context> ctx) {
    // ❌ 没有 GachaSession
    // ❌ 没有 executeDraw() 方法
    // ❌ 逻辑分散在 Packet Handler 中
    
    if (!gachaShop.canDraw(player, cap)) {
        ArcQuestNetwork.CHANNEL.send(..., new S2CDrawFailedPacket(...));
        return;
    }
    
    // 执行抽奖
    DrawResult drawResult = gachaPool.performDraw(...);
    
    // 发放奖励
    reward.execute(player);
    
    // 更新保底
    cap.setGachaPityCounter(pkt.shopId, newPityCounter);
    
    // 记录冷却和次数
    cap.getDialogueProgress().recordChoiceSelection(drawKey, ...);
    gachaShop.incrementDrawCount(cap, ...);
    
    // 发送结果
    ArcQuestNetwork.CHANNEL.send(..., new S2CDrawResultPacket(...));
}
```

**❌ 严重问题**：
- ❌ **没有 GachaSession 类**
- ❌ **没有 executeDraw() 方法**
- ❌ 逻辑分散，不符合单一职责原则
- ❌ 没有细分错误原因

**🔧 必须重构**：
1. 创建 `GachaSession` 类
2. 实现 `executeDraw()` 方法
3. 遵循"先重置，再判断"的原则
4. 细分错误原因

---

## 5. 事件系统对比

### 5.1 商店事件 vs 抽奖事件

#### ✅ 商店事件（权威模板）

```java
// TradeOpenedEvent - 商店打开时触发
public class TradeOpenedEvent extends Event {
    private final ServerPlayer player;
    private final String shopId;
    @Nullable private final LivingEntity npc;
}

// TradePurchasedSuccessEvent - 交易成功时触发
public class TradePurchasedSuccessEvent extends Event {
    private final ServerPlayer player;
    private final String shopId;
    private final String entryId;
}

// TradePurchaseFailedEvent - 交易失败时触发
public class TradePurchaseFailedEvent extends Event {
    private final ServerPlayer player;
    private final String shopId;
    private final String entryId;
    private final FailureReason reason;  // ON_COOLDOWN, MAX_PURCHASES_REACHED, etc.
}
```

**关键设计**：
- ✅ 三个核心事件：Opened、Success、Failed
- ✅ Failed 事件包含详细的失败原因

---

#### ✅ 抽奖事件（已对齐）

```java
// GachaEvents.OpenedEvent - 抽奖界面打开时触发
public class OpenedEvent extends Event {
    @Nullable private final ServerPlayer player;
    private final String shopId;
    private final IQuestCapability capability;
}

// GachaEvents.PostDrawEvent - 抽奖成功后触发
public class PostDrawEvent extends Event {
    private final ServerPlayer player;
    private final String shopId;
    private final GachaItem drawnItem;
    private final boolean pityTriggered;
    private final int newPityCounter;
    private final IQuestCapability capability;
}

// GachaEvents.DrawFailedEvent - 抽奖失败时触发
public class DrawFailedEvent extends Event {
    private final ServerPlayer player;
    private final String shopId;
    private final FailReason reason;  // COOLDOWN, MAX_DRAWS_REACHED, CONDITION_NOT_MET
}
```

**✅ 已对齐且超越**：
- ✅ 三个核心事件齐全（Opened、PostDraw、DrawFailed）
- ✅ Failed 事件包含详细原因（3种）
- ✅ **额外提供 PreDrawEvent**（可取消，支持修改保底计数）
- ✅ **额外提供 DrawingEvent**（用于触发客户端动画）
- ✅ **额外提供 PoolRefreshEvent**（动态权重变化通知）

**⚠️ 差异说明**：
- ⚠️ 商店系统的 TradePurchaseFailedEvent 有5种失败原因（含 INSUFFICIENT_FUNDS）
- ⚠️ 抽奖系统的 DrawFailedEvent 有3种失败原因（不含余额检查，因为抽奖成本在 Shop 级别）

---

## 6. 关键差异总结

### 6.1 架构层面

| 对比项 | 商店系统 | 抽奖系统 | 状态 |
|--------|---------|---------|------|
| **会话管理** | TradeSession 类 | ❌ 无 GachaSession | 🔴 严重缺失 |
| **Entry 管理** | LinkedHashMap<String, TradeEntry> | ❌ 无统一 Entry | 🔴 严重缺失 |
| **限购/冷却配置** | 在 TradeEntry 级别 | 在 Shop 级别 | 🟡 设计不一致 |
| **网络包类型** | 单一包 + Mode 枚举 | 多个独立包 | 🟡 设计不一致 |
| **客户端缓存更新** | 完整快照覆盖 | 重建 Session（保留字段） | 🟡 时序竞态风险 |
| **错误原因细分** | 限购 > 冷却 > 条件 | 部分实现 | 🟡 不完整 |

### 6.2 代码层面

| 对比项 | 商店系统 | 抽奖系统 | 状态 |
|--------|---------|---------|------|
| **executeTrade()** | ✅ 完整实现 | ❌ 无 executeDraw() | 🔴 缺失 |
| **checkAndResetPurchases()** | ✅ 完整实现 | ❌ 无 checkAndResetDraws() | 🔴 缺失 |
| **refreshTradeData()** | ✅ 完整实现 | ❌ 无 refreshGachaData() | 🔴 缺失 |
| **buildTradeSnapshot()** | ✅ 完整实现 | ❌ 无 buildGachaSnapshot() | 🔴 缺失 |
| **handlePurchaseResult()** | ✅ 只负责音效 | ⚠️ recordDrawResult() 既负责音效又负责数据 | 🟡 职责混乱 |
| **hasLimit()/hasCooldown()** | ✅ 便捷方法 | ❌ 无 | 🟡 缺失 |

### 6.3 数据流层面

**商店系统数据流**：
```
客户端点击购买
  ↓
C2SRequestTradePacket(PURCHASE)
  ↓
服务端: TradeSession.executeTrade()
  ↓
发送 S2COpenTradePacket(TRADE_SUCCESS/FAIL)  ← 只触发音效
  ↓
客户端: handlePurchaseResult() 播放音效
  ↓
服务端: refreshTradeData() 发送完整快照  ← 权威数据同步
  ↓
客户端: updateSession() 覆盖旧 Session（完整快照）
  ↓
UI 刷新显示最新状态
```

**关键点**：
- ✅ TRADE_SUCCESS/FAIL 包 **不更新 Session 数据**，只触发音效
- ✅ 真正的数据同步依赖于 `refreshTradeData()` 发送的完整快照
- ✅ 即使两个包的到达顺序错乱，最终状态也是正确的（因为快照是权威的）

---

**抽奖系统数据流（当前）**：
```
客户端点击抽奖
  ↓
C2SDrawGachaPacket
  ↓
服务端: 分散的逻辑检查
  ↓
发送 S2CDrawResultPacket  ← 既触发音效，又记录数据
  ↓
客户端: recordDrawResult() 更新 Session 数据
  ↓
❌ 不再发送刷新包（之前有，已移除）
  ↓
UI 状态可能不同步
```

**🔴 关键问题**：
1. **缺少刷新机制**：抽奖后没有发送完整快照，导致客户端状态不同步
2. **职责混乱**：`recordDrawResult()` 既负责音效又负责数据更新
3. **时序竞态风险**：如果 `S2COpenGachaPacket` 先于 `S2CDrawResultPacket` 到达，会覆盖刚刚记录的抽奖结果
4. **没有权威数据源**：客户端 Session 数据的更新依赖于单个包，缺少服务端的权威快照同步

---

## 7. 重构计划

### 7.1 阶段一：创建核心数据结构

1. **创建 GachaEntry 类**（对标 TradeEntry）
   - 添加 `entryId`、`maxPurchases`、`cooldownType`、`cooldownValue`、`resetTimeTicks` 字段
   - 添加 `hasLimit()`、`hasCooldown()` 方法
   - 添加完整音效配置（drawSuccessSound、drawFailSound、cooldownSound、limitReachedSound、conditionFailSound）
   - 添加 `visibleCondition`、`canDrawCondition`、`drawResetCondition`

2. **修改 GachaShopDefinition**
   - 添加 `LinkedHashMap<String, GachaEntry> entries`
   - 移除 Shop 级别的限购/冷却配置（下放到 Entry）
   - 保留抽奖特有的 `pools`、`rarityConfigs`、`pitySystem`

3. **创建 GachaSession 类**（对标 TradeSession）
   - 实现 `executeDraw()` 方法（完整的业务流程）
   - 实现 `canDraw()`、`isOnCooldown()`、`isLimitReached()` 方法
   - 实现 `checkAndResetDraws()` 方法（支持冷却自动恢复和自定义条件恢复）
   - 实现 `getDrawCount()`、`getRemainingDraws()`、`getCooldownRemaining()` 查询方法

### 7.2 阶段二：重构网络包系统

1. **重构 S2COpenGachaPacket**
   - 添加 `Mode` 枚举（OPEN、DRAW_SUCCESS、DRAW_FAIL、CLOSE）
   - 添加 `FailReason` 枚举（GENERIC、COOLDOWN、LIMIT_REACHED、CONDITION_FAIL）
   - 支持多种模式（对标 S2COpenTradePacket）
   - 添加商品状态数组字段（用于 OPEN 模式的完整快照）

2. **删除 S2CDrawResultPacket**
   - 功能合并到 S2COpenGachaPacket.DRAW_SUCCESS/DRAW_FAIL 模式

3. **重构 C2SDrawGachaPacket**
   - 添加 `Action` 枚举（OPEN、DRAW）
   - 实现 `refreshGachaData()` 方法（对标 `refreshTradeData()`）
   - 抽奖成功后发送刷新包（确保客户端状态同步）
   - 实现 `buildGachaSnapshot()` 方法（构建完整快照）

### 7.3 阶段三：重构客户端缓存

1. **修改 updateGachaSession()**
   - 改为完整快照覆盖（对标商店系统的 `updateSession()`）
   - 每次更新都重新创建 Session 对象并赋值所有字段
   - 不再需要手动保留历史记录（因为服务端会发送完整快照）

2. **添加 handleDrawResult()**
   - 对标 `handlePurchaseResult()`
   - **只负责音效触发，不更新 Session 数据**
   - 根据成功/失败播放不同的音效

3. **实现 refreshGachaData() 的客户端处理**
   - 在 S2COpenGachaPacket.handle() 中添加 DRAW_SUCCESS/DRAW_FAIL 模式的处理逻辑
   - 调用 `handleDrawResult()` 播放音效
   - 后续的 OPEN 模式会发送完整快照并调用 `updateGachaSession()`

### 7.4 阶段四：完善业务逻辑

1. **创建 GachaEntryStateResolver**（对标 TradeEntryStateResolver）
   - 统一管理限购/冷却/条件检查
   - 细分错误原因（可见性 > 限购 > 冷却 > 条件）
   - 实现 `shouldResetByCooldown()` 和 `resetDrawAndCooldown()` 方法

2. **实现完整的 executeDraw() 流程**
   ```java
   public DrawResult executeDraw(String entryId) {
       GachaEntry entry = shop.getEntry(entryId);
       if (entry == null) return DrawResult.fail("not_found");
       
       IQuestCapability cap = getCap();
       
       // 1. 重置过期冷却（必须在判断之前）
       if (entry.hasLimit() || entry.hasCooldown()) {
           checkAndResetDraws(entryId, entry);
       }
       
       // 2. 综合判断（细分错误原因）
       if (!GachaEntryStateResolver.canDraw(player, cap, shop.getShopId(), entry)) {
           if (!isVisible(...)) return fail("not_visible");
           if (isLimitReached(...)) return fail("max_draws");
           if (isOnCooldown(...)) return fail("on_cooldown");
           return fail("condition_not_met");
       }
       
       // 3. 扣除成本、执行抽奖
       for (ITradeOffer cost : entry.getCosts()) cost.execute(player);
       DrawResult drawResult = gachaPool.performDraw(...);
       
       // 4. 发放奖励
       for (ITradeOffer reward : drawResult.rewards()) reward.execute(player);
       
       // 5. 记录抽奖和冷却
       recordDraw(cap, shopId, entryId);
       if (shouldRecordCooldown(...)) {
           recordCooldown(player, cap, shopId, entryId);
       }
       
       return drawResult;
   }
   ```

### 7.5 阶段五：测试验证

1. 测试 HUD 三阶段正常显示（preview → roller → result）
2. 测试限购逻辑正确性（达到上限后正确阻止并提示）
3. 测试冷却逻辑正确性（GAME_DAY、SECONDS、GAME_TICK 三种类型）
4. 测试 `/time set` 命令后 GAME_DAY 冷却正确重置
5. 测试抽奖后界面按钮状态立即更新为冷却中
6. 测试多次抽奖历史记录正确累积
7. 测试时序竞态问题是否解决（即使网络包顺序错乱，最终状态也是正确的）

---

## 8. 附录：关键代码片段

### 8.1 TradeEntryStateResolver.canPurchase 完整实现

```java
public static boolean canPurchase(ServerPlayer player, IQuestCapability cap, 
                                   String shopId, TradeEntry entry) {
    // 1. 检查可见性
    if (!isVisible(player, cap, entry)) {
        return false;
    }
    
    // 2. 检查限购
    if (isPurchaseLimitReached(cap, shopId, entry)) {
        return false;
    }
    
    // 3. 检查冷却
    if (isOnCooldown(player, cap, shopId, entry)) {
        return false;
    }
    
    // 4. 检查购买资格条件
    if (!hasCanBuyCondition(player, cap, entry)) {
        return false;
    }
    
    return true;
}
```

### 8.2 TradeSession.checkAndResetPurchases 完整实现

```java
private void checkAndResetPurchases(String entryId, TradeEntry entry) {
    if (!entry.hasLimit()) return;
    
    var resetCondition = entry.getPurchaseResetCondition();
    if (resetCondition == null) return;
    
    IQuestCapability cap = getCap();
    int currentCount = cap.getTradePurchaseCount(shop.getShopId(), entryId);
    if (currentCount == 0) return;
    
    // 1. 检查冷却自动恢复
    boolean shouldReset = TradeEntryStateResolver.shouldResetByCooldown(
        player, cap, shop.getShopId(), entry);
    
    // 2. 检查自定义条件恢复
    if (!shouldReset) {
        try {
            shouldReset = resetCondition.test(player);
            if (shouldReset) {
                LOGGER.info("[Trade] Purchase limit reset by custom condition for entry={}", entryId);
            }
        } catch (Exception e) {
            LOGGER.warn("[Trade] Error evaluating purchase reset condition for entry={}: {}", 
                       entryId, e.getMessage());
        }
    }
    
    // 3. 执行重置
    if (shouldReset && currentCount > 0) {
        TradeEntryStateResolver.resetPurchaseAndCooldown(cap, shop.getShopId(), entryId);
    }
}
```

---

**文档结束**

*注：本文档将持续更新，直到抽奖系统完全对齐商店系统为止。*

### 8.3 C2SRequestTradePacket.buildTradeSnapshot 完整实现

```java
private static TradeSnapshot buildTradeSnapshot(ServerPlayer player,
                                                 TradeShopDefinition shop,
                                                 TradeSession session) {
    IQuestCapability cap = QuestCapabilityProvider.getOrNull(player);
    List<TradeEntry> allEntries = new ArrayList<>(shop.getAllEntries());
    int count = allEntries.size();
    
    // 调试：输出服务端商品顺序
    LOGGER.info("[Trade-Snapshot] Shop={}, total entries={}", shop.getShopId(), count);
    for (int i = 0; i < count; i++) {
        LOGGER.info("[Trade-Snapshot]   Entry {}: id={}", i, allEntries.get(i).getEntryId());
    }
    
    int[] purchases      = new int[count];
    int[] maxPurchases   = new int[count];
    long[] lastPurchaseTimes  = new long[count];
    long[] purchaseGameTimes  = new long[count];
    long[] purchaseDayTimes   = new long[count];
    int[] cooldownTypes   = new int[count];
    long[] cooldownValues = new long[count];
    int[] resetTimeTicks  = new int[count];
    boolean[] visibility      = new boolean[count];
    boolean[] canBuyConditions = new boolean[count];
    
    for (int i = 0; i < count; i++) {
        TradeEntry entry = allEntries.get(i);
        purchases[i]    = session.getPurchaseCount(entry.getEntryId());
        maxPurchases[i] = entry.getMaxPurchases();
        
        if (entry.hasCooldown()) {
            lastPurchaseTimes[i] = cap.getTradeLastPurchaseTime(shop.getShopId(), entry.getEntryId());
            ProgressKey key = ProgressKey.ofTrade(shop.getShopId(), entry.getEntryId());
            var storeEntry = cap.getDialogueProgress().getChoiceSelection(key);
            purchaseGameTimes[i] = storeEntry.exists() ? storeEntry.gameTime() : 0;
            purchaseDayTimes[i]  = storeEntry.exists() ? storeEntry.dayTime()  : 0;
            cooldownTypes[i]  = entry.getCooldownType().ordinal();
            cooldownValues[i] = entry.getCooldownValue();
            resetTimeTicks[i] = entry.getResetTimeTicks();
        }
        
        visibility[i]       = session.isEntryVisible(entry);
        canBuyConditions[i] = session.canPurchase(entry);
    }
    
    return new TradeSnapshot(purchases, maxPurchases, lastPurchaseTimes, purchaseGameTimes,
            purchaseDayTimes, cooldownTypes, cooldownValues, resetTimeTicks, visibility, canBuyConditions);
}
```

---

## 9. 分析总结

### 9.1 核心发现

通过本次深度对比分析，我们发现了抽奖系统与商店系统的**根本性架构差异**：

1. **会话管理缺失**：抽奖系统没有 `GachaSession` 类，导致业务逻辑分散在 Packet Handler 和 ShopDefinition 中，不符合单一职责原则。

2. **网络包设计不一致**：
   - 商店系统：单一包类型 + Mode 枚举，职责清晰
   - 抽奖系统：多个独立包类型，时序竞态风险高

3. **数据同步机制缺失**：
   - 商店系统：通过 `refreshTradeData()` 发送完整快照，确保客户端状态权威同步
   - 抽奖系统：缺少刷新机制，依赖单个包更新数据，容易丢失状态

4. **客户端缓存职责混乱**：
   - 商店系统：`handlePurchaseResult()` 只负责音效触发
   - 抽奖系统：`recordDrawResult()` 既负责音效又负责数据更新

### 9.2 修复优先级

根据问题的严重性和影响范围，建议按以下优先级进行修复：

**P0（紧急）**：
1. 创建 `GachaSession` 类，统一管理抽奖业务逻辑
2. 实现 `executeDraw()` 方法，遵循"先重置，再判断"的原则
3. 实现 `refreshGachaData()` 方法，确保抽奖后发送完整快照

**P1（高优先级）**：
4. 重构 S2COpenGachaPacket，添加 Mode 枚举支持多种模式
5. 删除 S2CDrawResultPacket，功能合并到 S2COpenGachaPacket
6. 修改 `updateGachaSession()` 为完整快照覆盖方式
7. 添加 `handleDrawResult()` 方法，只负责音效触发

**P2（中优先级）**：
8. 创建 `GachaEntry` 类，对标 `TradeEntry`
9. 创建 `GachaEntryStateResolver`，统一管理状态检查
10. 完善事件系统，补充 `PreDrawEvent`

### 9.3 预期效果

完成所有重构后，抽奖系统将具备以下特性：

- ✅ **架构一致性**：与商店系统保持相同的架构模式，易于维护和扩展
- ✅ **状态同步可靠性**：通过完整快照机制，彻底消除时序竞态问题
- ✅ **错误原因细分**：清晰区分限购、冷却、条件等失败原因
- ✅ **用户体验优化**：HUD 三阶段正常显示，按钮状态实时更新
- ✅ **可维护性提升**：代码结构清晰，职责明确，便于后续开发

---

*最后更新时间：2026-04-22*
