# 常见问题 (FAQ)

**最后更新**: 2026-04-17

---

## 📋 目录

1. [安装与配置](#安装与配置)
2. [任务系统](#任务系统)
3. [对话系统](#对话系统)
4. [视觉系统](#视觉系统)
5. [网络同步](#网络同步)
6. [性能优化](#性能优化)
7. [调试技巧](#调试技巧)

---

## 安装与配置

### Q1: 如何安装 Arc Quest 模组？

**A**: 
1. 确保已安装 Minecraft 1.20.1 Forge
2. 将编译好的 `.jar` 文件放入 `mods/` 文件夹
3. 启动游戏

**依赖要求**:
- Minecraft 1.20.1
- Forge 47.x
- Java 17+

---

### Q2: 如何为模组添加中文翻译？

**A**: 

**步骤1**: 创建语言文件
```
src/main/resources/assets/arc_quest/lang/zh_cn.json
```

**步骤2**: 添加翻译内容
```json
{
  "quest.my_quest.title": "我的任务",
  "quest.my_quest.desc": "这是一个示例任务",
  "phase.step1": "第一步",
  "objective.collect_logs": "收集橡木原木"
}
```

**步骤3**: 使用 DataGen 生成（可选）
```bash
./gradlew runData
```

---

### Q3: 如何修改默认配置？

**A**: 

配置文件位置: `config/arc_quest.toml`

**常用配置项**:
```toml
[general]
enable_splash_animation = true   # 启用的立绘动画
typewriter_speed = 50            # 打字机速度(ms/字符)
max_active_quests = 20           # 最大活跃任务数

[performance]
use_incremental_sync = true      # 启用增量同步
dirty_flag_check_interval = 20   # 脏标记检查间隔(ticks)
```

---

## 任务系统

### Q4: 为什么任务进度不更新？

**A**: 按以下步骤排查：

**1. 检查任务状态**
```bash
/quest list @p
# 确认任务处于 ACTIVE 状态
```

**2. 验证目标追踪器**
```java
// 在日志中搜索
[ArcQuest] Objective registered: quest_id/phase_id/#0
```

**3. 检查事件监听**
```java
// 确保 ObjectiveTracker.registerListeners() 已调用
@SubscribeEvent
public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
    LOGGER.debug("Item pickup detected: {}", event.getItemStack());
}
```

**4. 手动测试**
```bash
# 手动推进进度
/quest progress @p arc_quest:test 0 5
```

---

### Q5: 如何重置玩家的所有任务数据？

**A**: 

**方法1**: 使用命令
```bash
/quest resetall @p
```

**方法2**: 代码实现
```java
IQuestCapability cap = player.getCapability(QuestCapabilityProvider.QUEST_CAP).orElse(null);
if (cap != null) {
    cap.clearAllData();
    ArcQuestNetwork.syncFullData(player, cap);
}
```

**注意**: 这会清除：
- ✅ 所有活跃任务
- ✅ 已完成任务记录
- ✅ 已失败任务记录
- ✅ 所有 flags
- ✅ 所有 variables

---

### Q6: 如何实现可重复任务？

**A**: 

**步骤1**: 在任务定义中标记为可重复
```java
QuestBuilder.create("daily_collect")
    .category(QuestCategory.DAILY)
    .repeatable(true)  // ← 关键配置
    .phase(...)
    .buildAndRegister();
```

**步骤2**: 完成后允许重新接受
```java
// acceptQuest() 中的逻辑
if (cap.isQuestCompleted(questId) && !def.isRepeatable()) {
    return false;  // 不可重复，拒绝
}

// 如果是可重复任务，先重置
if (cap.isQuestCompleted(questId)) {
    cap.removeCompletedQuest(questId);
}
```

---

### Q7: 任务阶段卡在某个点无法推进？

**A**: 

**常见原因**:

**1. 目标未完成**
```bash
/quest debug @p
# 查看每个目标的进度
```

**2. 条件未满足**
```java
// 检查 PhaseTransition 的条件
.thenGoToIf("next_phase", new FlagSetCondition("required_flag"))
// 确保 "required_flag" 已设置
```

**3. 有分支选择未处理**
```java
// 检查当前阶段是否有 choices
PhaseDefinition phase = def.getPhase(currentPhaseId);
if (phase.hasChoices()) {
    // 需要玩家选择，不会自动推进
    // 客户端应显示 PhaseChoiceScreen
}
```

---

## 对话系统

### Q8: 对话为什么不显示？

**A**: 

**排查步骤**:

**1. 检查对话注册**
```bash
/quest registry
# 输出应包含你的对话ID
```

**2. 验证 NPC 绑定**
```java
// 方式1: Capability
entity.getCapability(DialogueNpcPatchProvider.DIALOGUE_NPC_CAP)
    .ifPresent(patch -> {
        LOGGER.info("Dialogue ID: {}", patch.getDialogueId());
    });

// 方式2: 接口
if (entity instanceof IDialogueNpc npc) {
    LOGGER.info("Dialogue ID: {}", npc.getDialogueId());
}

// 方式3: 注册表
String dialogueId = DialogueRegistry.INSTANCE.getDialogueForNpc("villager");
LOGGER.info("Bound dialogue: {}", dialogueId);
```

**3. 检查网络包**
```java
// 在服务端日志中查找
[ArcQuest] Sending S2COpenDialoguePacket to player Steve
```

**4. 客户端日志**
```java
// 在客户端日志中查找
[ArcQuest] Received dialogue: villager_greeting
[ArcQuest] Opening DialogueScreen
```

---

### Q9: 如何实现多语言对话？

**A**: 

**步骤1**: 定义翻译键
```properties
# en_us.json
{
  "dialogue.villager.start.text": "Hello, traveler!",
  "dialogue.villager.start.choice1": "What's happening?"
}

# zh_cn.json
{
  "dialogue.villager.start.text": "你好，旅行者！",
  "dialogue.villager.start.choice1": "发生了什么事？"
}
```

**步骤2**: 使用 Component.translatable()
```java
DialogueTreeBuilder.create("villager")
    .node("start")
        .say(Component.translatable("dialogue.villager.start.text").getString())
        .choice(Component.translatable("dialogue.villager.start.choice1").getString(),
                c -> c.goTo("explain"))
        .build()
    .buildAndRegister();
```

**步骤3**: 切换游戏语言测试
```
Options → Language → 简体中文
```

---

### Q10: 对话选项为什么不可见？

**A**: 

**原因1**: 可见性条件未满足
```java
.choice("隐藏选项", c -> c.goTo("secret"))
    .visibleIf(new FlagSetCondition("unlocked_secret"))  // ← 需要此flag

// 解决：设置flag
/quest flag @p set unlocked_secret
```

**原因2**: 条件逻辑错误
```java
// 错误：条件永远为false
.visibleIf(new FlagNotSetCondition("always_set_flag"))

// 正确：移除条件或修正逻辑
.choice("始终可见", c -> c.goTo("normal"))
```

---

## 视觉系统

### Q11: 立绘为什么不显示？

**A**: 

**检查清单**:

**1. 纹理文件存在**
```
src/main/resources/assets/arc_quest/textures/gui/splash/my_splash.png
```

**2. ResourceLocation 正确**
```java
ResourceLocation.fromNamespaceAndPath(
    "arc_quest",  // ← 命名空间
    "textures/gui/splash/my_splash.png"  // ← 路径
)
```

**3. 在客户端调用**
```java
// ❌ 错误：服务端无法渲染
if (!player.level().isClientSide()) {
    QuestSplashRenderer.INSTANCE.trigger(...);
}

// ✅ 正确：通过网络包触发
ArcQuestNetwork.syncQuestState(player, data);
// 客户端接收后自动触发
```

**4. 查看纹理加载日志**
```
[Worker-Main-X/WARN]: Failed to load texture: arc_quest:textures/gui/splash/my_splash.png
java.io.FileNotFoundException: ...
```

---

### Q12: 图标模糊或变形？

**A**: 

**解决方案**:

**1. 使用2的幂次方尺寸**
```
✅ 16x16, 32x32, 64x64, 128x128
❌ 20x20, 50x50, 100x100
```

**2. 使用PNG格式**
```
✅ my_icon.png
❌ my_icon.jpg, my_icon.bmp
```

**3. 调整缩放比例**
```java
// 如果图标太小
new VisualAsset(texture, 1.5f)  // 放大1.5倍

// 如果图标太大
new VisualAsset(texture, 0.8f)  // 缩小到80%
```

---

### Q13: 如何自定义主题色？

**A**: 

**方法1**: 使用 ChatFormatting
```java
.themeColor(ChatFormatting.GOLD)
```

**方法2**: 使用 ARGB 整数
```java
.themeColor(0xFFFFD700)  // 金色
```

**在线取色工具**: https://htmlcolorcodes.com/

**常用颜色**:
```java
0xFFFFD700  // 金色
0xFF4FC3F7  // 天蓝色
0xFF66FF66  // 浅绿色
0xFFFF66CC  // 粉色
0xFFFFFF66  // 浅黄色
```

---

## 网络同步

### Q14: 客户端数据不同步？

**A**: 

**排查步骤**:

**1. 检查网络包注册**
```java
// 在 ArcQuestNetwork.register() 中
CHANNEL.messageBuilder(S2CSyncQuestStatePacket.class, id++)
    .encoder(S2CSyncQuestStatePacket::encode)
    .decoder(S2CSyncQuestStatePacket::decode)
    .consumerMainThread(S2CSyncQuestStatePacket::handle)
    .add();
```

**2. 验证包发送**
```java
// 服务端日志
[ArcQuest] Sending S2CSyncQuestStatePacket to player Steve
```

**3. 检查客户端接收**
```java
// 客户端日志
[ArcQuest] Received quest state update: arc_quest:test
```

**4. 测试网络连通性**
```bash
# 单人游戏
/quest give @p arc_quest:test

# 多人游戏（需要OP权限）
/quest give <player_name> arc_quest:test
```

---

### Q15: 带宽占用过高？

**A**: 

**优化方案**:

**1. 启用增量同步**
```toml
# config/arc_quest.toml
[performance]
use_incremental_sync = true
```

**2. 减少同步频率**
```java
// ❌ 错误：每次变化都同步
for (int i = 0; i < 100; i++) {
    ArcQuestNetwork.syncQuestState(player, data);
}

// ✅ 正确：批量处理后同步一次
for (int i = 0; i < 100; i++) {
    data.incrementProgress(i, 1, 10);
}
ArcQuestNetwork.syncQuestState(player, data);  // 只同步一次
```

**3. 使用脏标记防抖**
```java
// P2优化已在 QuestCapabilityTickHandler 中实现
// 每20 ticks检查一次，仅保存有变化的数据
```

**效果**: 带宽减少 ~90%

---

## 性能优化

### Q16: 游戏卡顿怎么办？

**A**: 

**常见原因及解决方案**:

**1. 立绘动画过多**
```toml
# 限制队列大小
[max_splash_queue]
size = 3
```

**2. Stream API 热点路径**
```java
// ❌ 错误：每帧创建Stream对象
return def.getAllPhases().stream()
    .filter(p -> p.getPhaseId().equals(phaseId))
    .findFirst().orElse(null);

// ✅ 正确：传统for循环
for (PhaseDefinition phase : def.getAllPhases()) {
    if (phase.getPhaseId().equals(phaseId)) {
        return phase;
    }
}
return null;
```

**3. 过多的Toast通知**
```toml
# 限制同时显示的Toast数量
[max_toasts]
count = 3
```

---

### Q17: 内存占用过高？

**A**: 

**优化建议**:

**1. 使用 FastUtil 集合**
```java
// ✅ 已优化：QuestRuntimeData 使用 FastUtil
private final Object2IntOpenHashMap<String> variables = new Object2IntOpenHashMap<>();
```

**2. 及时清理无用数据**
```bash
# 定期清理已完成的任务
/quest resetall @p
```

**3. 纹理压缩**
```
使用 PNG-8 而非 PNG-24
立绘尺寸不超过 1920x1080
```

---

## 调试技巧

### Q18: 如何查看任务详细信息？

**A**: 

**命令**:
```bash
# 查看玩家所有任务
/quest list @p

# 查看任务详细调试信息
/quest debug @p

# 查看注册表
/quest registry
```

**输出示例**:
```
=== Quest Debug Info for Steve ===
Active Quests:
  - arc_quest:epic_prologue
    State: ACTIVE
    Phase: gather_wood
    Progress: [5/10, 0/3]
    Flags: [started_prologue]
    Variables: {reputation=50}

Completed Quests:
  - arc_quest:tutorial

Failed Quests:
  (none)

Global Flags:
  [met_villager, started_prologue]

Global Variables:
  {reputation=50, coins=100}
```

---

### Q19: 如何监控网络包？

**A**: 

**方法1**: 启用调试日志
```toml
# log4j2.xml
<Logger name="org.com.arc_quest.network" level="DEBUG"/>
```

**方法2**: 使用网络监控工具
```bash
# Wireshark 过滤 Minecraft 流量
tcp.port == 25565
```

**方法3**: 代码埋点
```java
// 在 ArcQuestNetwork 中添加
public static void sendPacket(ServerPlayer player, Packet packet) {
    LOGGER.debug("[Network] Sending {} to {}", 
        packet.getClass().getSimpleName(), 
        player.getGameProfile().getName());
    
    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
}
```

---

### Q20: 如何快速测试任务流程？

**A**: 

**使用管理员命令组合**:

```bash
# 1. 给予任务
/quest give @p arc_quest:test

# 2. 跳转到指定阶段
/quest phase @p arc_quest:test gather_wood

# 3. 手动推进进度
/quest progress @p arc_quest:test 0 10

# 4. 强制完成
/quest complete @p arc_quest:test

# 5. 重置重新开始
/quest reset @p arc_quest:test
```

**自动化测试脚本**:
```bash
# test_quest.sh
/quest give @p arc_quest:test
sleep 2
/quest progress @p arc_quest:test 0 10
sleep 1
/quest progress @p arc_quest:test 1 5
sleep 1
/quest list @p
```

---

## 附录

### 获取帮助的途径

1. **查阅文档**: [docs/README.md](../README.md)
2. **查看示例**: `EpicMainlineDemo.java`, `TestDialogues.java`
3. **提交Issue**: GitHub Issues
4. **社区讨论**: Discord 服务器

---

### 报告 Bug 的模板

```markdown
**问题描述**:
简要描述遇到的问题

**复现步骤**:
1. ...
2. ...
3. ...

**预期行为**:
应该发生什么

**实际行为**:
实际发生了什么

**环境信息**:
- Minecraft 版本: 1.20.1
- Forge 版本: 47.x.x
- Arc Quest 版本: 1.0.0
- Java 版本: 17

**日志文件**:
附上 `logs/latest.log` 或 `logs/debug.log`

**截图/视频**:
如有必要，提供截图或录屏
```

---

**文档结束**

*如未找到答案，请提交 Issue 或在社区提问。*
