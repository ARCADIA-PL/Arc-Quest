# 抽奖系统 HUD - ClientTradeCache API 参考

## 核心类

```java
var cache = ClientTradeCache.INSTANCE;
```

---

## 数据容器

### GachaSessionData
```java
public static class GachaSessionData {
    int getPityCounter()              // 当前保底计数
    int getTotalDraws()               // 总抽奖次数
    String getLastDrawnItemId()       // 最近抽中物品ID（可为null）
    String getLastRarityName()        // 最近稀有度名称（可为null）
    int getLastActualCount()          // 最近实际数量
    boolean isLastPityTriggered()     // 最近是否触发保底
    long getLastDrawTime()            // 最近抽奖时间戳
    List<DrawRecord> getDrawHistory() // 历史记录（最多50条）
}
```

### DrawRecord
```java
public record DrawRecord(
    String itemId,           // 物品ID
    String rarityName,       // 稀有度名称 (SSR/SR/R)
    int actualCount,         // 实际数量
    boolean pityTriggered,   // 是否保底触发
    long drawTime            // 时间戳
) {}
```

---

## HUD API 清单（14个方法）

### 1. 保底进度

```java
// 获取当前保底计数
int progress = cache.getPityProgress(shopId);
// 返回: >=0 有效, -1 无数据

// 获取保底剩余次数
int remaining = cache.getPityRemaining(shopId, pityThreshold);
// 返回: >=0 剩余次数, -1 无数据

// 获取保底进度百分比 (0-100)
int percent = cache.getPityProgressPercent(shopId, pityThreshold);
// 返回: 0-100

// 检查是否已触发保底
boolean triggered = cache.isPityTriggered(shopId, pityThreshold);
// 返回: true/false
```

### 2. 抽奖次数

```java
// 总抽奖次数
int total = cache.getTotalDraws(shopId);
// 返回: >=0

// 剩余可抽次数（考虑限购）
int remaining = cache.getRemainingDraws(shopId, maxDraws);
// 返回: -1=无限, -2=无数据, >=0=剩余次数

// 是否达到限购
boolean limitReached = cache.isDrawLimitReached(shopId, maxDraws);
// 返回: true/false

// 限购进度百分比 (0-100)
int progressPercent = cache.getDrawProgressPercent(shopId, maxDraws);
// 返回: 0-100
```

### 3. 历史记录

```java
// 获取最近一次抽奖结果
DrawRecord last = cache.getLastDrawResult(shopId);
if (last != null) {
    String itemId = last.itemId();
    String rarity = last.rarityName();
    int count = last.actualCount();
    boolean pity = last.pityTriggered();
}

// 获取完整历史记录（不可变列表，最多50条）
List<DrawRecord> history = cache.getDrawHistory(shopId);
```

### 4. 统计分析

```java
// 指定稀有度的抽取次数
int ssrCount = cache.getRarityDrawCount(shopId, "SSR");

// 稀有度分布统计
Map<String, Integer> distribution = cache.getRarityDistribution(shopId);
// 返回示例: {"SSR": 5, "SR": 20, "R": 75}

// 平均每次获得的物品数量
double avgCount = cache.getAverageItemCount(shopId);

// 保底触发总次数
int pityTriggers = cache.getPityTriggerCount(shopId);

// 距离下次保底的预计次数
int estimated = cache.getEstimatedDrawsToPity(shopId, pityThreshold);
```

---

## 快速使用示例

### 示例1：保底进度条

```java
int pityThreshold = 10; // 从 GachaShopDefinition 获取

int percent = cache.getPityProgressPercent(shopId, pityThreshold);
int remaining = cache.getPityRemaining(shopId, pityThreshold);

// 渲染进度条
int barWidth = (int) ((percent / 100.0) * maxWidth);
fill(poseStack, x, y, x + barWidth, y + 10, themeColor);

// 渲染文本
font.draw(poseStack, 
    String.format("保底: %d/%d (%d%%)", 
        pityThreshold - remaining, pityThreshold, percent),
    x, y + 15, 0xFFFFFFFF);
```

### 示例2：最近结果展示

```java
DrawRecord last = cache.getLastDrawResult(shopId);
if (last != null) {
    Color color = switch (last.rarityName()) {
        case "SSR" -> new Color(0xFFD700); // 金色
        case "SR"  -> new Color(0xA020F0); // 紫色
        case "R"   -> new Color(0x4169E1); // 蓝色
        default    -> Color.WHITE;
    };
    
    String text = String.format("%s x%d %s", 
        last.itemId(), 
        last.actualCount(),
        last.pityTriggered() ? "⭐" : "");
    
    font.draw(poseStack, text, x, y, color.getRGB());
}
```

### 示例3：稀有度分布

```java
Map<String, Integer> dist = cache.getRarityDistribution(shopId);
int total = dist.values().stream().mapToInt(Integer::intValue).sum();

for (Map.Entry<String, Integer> entry : dist.entrySet()) {
    float percentage = (float) entry.getValue() / total * 100;
    String text = String.format("%s: %d次 (%.1f%%)", 
        entry.getKey(), entry.getValue(), percentage);
    
    Color color = getRarityColor(entry.getKey());
    font.draw(poseStack, text, x, y, color.getRGB());
    y += 10;
}
```

### 示例4：限购状态

```java
int maxDraws = 50; // 从 GachaShopDefinition 获取

int remaining = cache.getRemainingDraws(shopId, maxDraws);
boolean limitReached = cache.isDrawLimitReached(shopId, maxDraws);

String text;
if (maxDraws < 0) {
    text = "限购: ∞";
} else if (limitReached) {
    text = "已达上限";
} else {
    text = String.format("剩余: %d/%d", remaining, maxDraws);
}

font.draw(poseStack, text, x, y, 0xFFCCCCCC);
```

---

## 辅助方法

### 获取商店配置

```java
var shopDef = GachaRegistry.get(shopId);
if (shopDef != null) {
    int pityThreshold = shopDef.getPityConfig().getPityThreshold();
    int maxDraws = shopDef.getMaxDraws();
    int themeColor = shopDef.getDefaultThemeColor();
}
```

### 稀有度颜色映射

```java
private Color getRarityColor(String rarityName) {
    return switch (rarityName) {
        case "SSR" -> new Color(0xFFD700); // 金色
        case "SR"  -> new Color(0xA020F0); // 紫色
        case "R"   -> new Color(0x4169E1); // 蓝色
        default    -> Color.WHITE;
    };
}
```

---

## 注意事项

1. **空值检查**：`getLastDrawResult()` 可能返回 null
2. **无效值处理**：`getPityProgress()` 返回 -1 表示无数据
3. **限购判断**：`maxDraws < 0` 表示无限
4. **性能优化**：避免每帧调用 `getRarityDistribution()`，建议缓存结果
5. **线程安全**：仅在客户端主线程调用

---

**版本**: 1.0  
**更新日期**: 2026-04-21
