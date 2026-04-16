package org.com.arc_quest.quest.builder;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.com.arc_quest.quest.api.ObjectiveEntry;
import org.com.arc_quest.quest.api.ObjectiveType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 流式构建单个 ObjectiveEntry。
 * <p>
 * 用法示例：
 * <pre>
 *   ObjectiveBuilder.kill(EntityType.ZOMBIE, 3)
 *       .display("击杀 3 只僵尸")
 *       .build();
 * </pre>
 */
public final class ObjectiveBuilder {

    private final ObjectiveType type;
    private final Map<String, String> extraData = new LinkedHashMap<>();
    private ResourceLocation targetId;
    private int requiredCount = 1;
    private Component displayText = Component.literal("???");
    private boolean hidden = false;
    private boolean optional = false;

    private ObjectiveBuilder(ObjectiveType type) {
        this.type = type;
    }

    // ════════════════════════════════════════
    //  静态工厂——常用目标类型快捷创建
    // ════════════════════════════════════════

    /**
     * 击杀目标
     *
     * @param entityType Minecraft 实体类型
     * @param count      需要击杀数量
     */
    public static ObjectiveBuilder kill(EntityType<?> entityType, int count) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
        Objects.requireNonNull(key, "EntityType not registered: " + entityType);
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.KILL);
        b.targetId = key;
        b.requiredCount = count;
        b.displayText = Component.translatable("arc_quest.obj.kill",
                Component.translatable(entityType.getDescriptionId()), count);
        return b;
    }

    /**
     * 收集物品（检测背包中持有）
     */
    public static ObjectiveBuilder collect(Item item, int count) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        Objects.requireNonNull(key, "Item not registered: " + item);
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.COLLECT);
        b.targetId = key;
        b.requiredCount = count;
        b.displayText = Component.translatable("arc_quest.obj.collect",
                item.getDescription(), count);
        return b;
    }

    /**
     * 与 NPC 对话
     *
     * @param npcId 自定义 NPC 标识符（如 "arc_quest:elder_npc"）
     */
    public static ObjectiveBuilder talk(ResourceLocation npcId) {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.TALK);
        b.targetId = npcId;
        b.requiredCount = 1;
        b.displayText = Component.translatable("arc_quest.obj.talk",
                Component.literal(npcId.getPath()));
        return b;
    }

    /**
     * 提交物品给 NPC
     */
    public static ObjectiveBuilder deliver(Item item, int count, ResourceLocation npcId) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        Objects.requireNonNull(key, "Item not registered: " + item);
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.DELIVER);
        b.targetId = key;
        b.requiredCount = count;
        b.displayText = Component.translatable("arc_quest.obj.deliver",
                item.getDescription(), count);
        b.extraData.put("npc_id", npcId.toString());
        return b;
    }

    /**
     * 抵达区域
     *
     * @param locationId 自定义位置标识符
     */
    public static ObjectiveBuilder reachLocation(ResourceLocation locationId,
                                                 int x, int y, int z, int radius) {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.REACH_LOCATION);
        b.targetId = locationId;
        b.requiredCount = 1;
        b.displayText = Component.translatable("arc_quest.obj.reach",
                Component.literal(locationId.getPath()));
        b.extraData.put("x", String.valueOf(x));
        b.extraData.put("y", String.valueOf(y));
        b.extraData.put("z", String.valueOf(z));
        b.extraData.put("radius", String.valueOf(radius));
        return b;
    }

    /**
     * 右键交互指定方块/实体
     */
    public static ObjectiveBuilder interact(ResourceLocation targetId) {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.INTERACT);
        b.targetId = targetId;
        b.requiredCount = 1;
        b.displayText = Component.translatable("arc_quest.obj.interact",
                Component.literal(targetId.getPath()));
        return b;
    }

    /**
     * 完全自定义目标
     */
    public static ObjectiveBuilder custom(ResourceLocation customId, int count) {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.CUSTOM);
        b.targetId = customId;
        b.requiredCount = count;
        return b;
    }

    // ════════════════════════════════════════
    //  链式配置
    // ════════════════════════════════════════

    public ObjectiveBuilder display(String literal) {
        this.displayText = Component.literal(literal);
        return this;
    }

    public ObjectiveBuilder display(Component component) {
        this.displayText = component;
        return this;
    }

    public ObjectiveBuilder count(int count) {
        this.requiredCount = count;
        return this;
    }

    public ObjectiveBuilder hidden() {
        this.hidden = true;
        return this;
    }

    public ObjectiveBuilder optional() {
        this.optional = true;
        return this;
    }

    public ObjectiveBuilder extra(String key, String value) {
        this.extraData.put(key, value);
        return this;
    }

    // ════════════════════════════════════════
    //  构建
    // ════════════════════════════════════════

    public ObjectiveEntry build() {
        Objects.requireNonNull(this.targetId, "targetId not set for ObjectiveBuilder");
        return new ObjectiveEntry(
                this.type,
                this.targetId,
                this.requiredCount,
                this.displayText,
                this.hidden,
                this.optional,
                new LinkedHashMap<>(this.extraData)
        );
    }
}