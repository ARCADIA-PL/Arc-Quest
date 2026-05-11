package org.arcadia.arc_quest.quest.builder;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.questmarker.api.*;

import java.util.*;
import java.util.function.ToIntFunction;

public final class ObjectiveBuilder {

    private final ObjectiveType type;
    private final Map<String, String> extraData = new LinkedHashMap<>();
    private final List<MarkSpec> relatedMarks = new ArrayList<>();
    private ResourceLocation targetId;
    private int requiredCount = 1;
    private QuestText displayText = QuestText.literal("???");
    private boolean hidden = false;
    private boolean optional = false;
    private ToIntFunction<ServerPlayer> countModifier;

    private ObjectiveBuilder(ObjectiveType type) {
        this.type = type;
    }

    public static ObjectiveBuilder kill(EntityType<?> entityType, int count) {
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
        Objects.requireNonNull(key, "EntityType not registered: " + entityType);
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.KILL);
        b.targetId = key;
        b.requiredCount = count;
        b.displayText = QuestText.translatable("arc_quest.obj.kill",
                QuestText.Arg.of((p, c) -> Component.translatable(entityType.getDescriptionId())),
                QuestText.Arg.of((p, c) -> count));
        return b;
    }

    public static ObjectiveBuilder collect(Item item, int count) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        Objects.requireNonNull(key, "Item not registered: " + item);
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.COLLECT);
        b.targetId = key;
        b.requiredCount = count;
        b.displayText = QuestText.translatable("arc_quest.obj.collect",
                QuestText.Arg.of((p, c) -> item.getDescription()),
                QuestText.Arg.of((p, c) -> count));
        return b;
    }

    public static ObjectiveBuilder collectTag(ResourceLocation itemTagId, int count) {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.COLLECT);
        b.targetId = itemTagId;
        b.requiredCount = count;
        b.extraData.put("target_tag", itemTagId.toString());
        b.displayText = QuestText.translatable("arc_quest.obj.collect",
                QuestText.Arg.of((p, c) -> Component.literal("#" + itemTagId)),
                QuestText.Arg.of((p, c) -> count));
        return b;
    }

    public static ObjectiveBuilder talk(ResourceLocation npcId) {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.TALK);
        b.targetId = npcId;
        b.requiredCount = 1;
        b.displayText = QuestText.translatable("arc_quest.obj.talk", QuestText.Arg.of((p, c) -> Component.literal(npcId.getPath())));
        return b;
    }

    public static ObjectiveBuilder deliver(Item item, int count, ResourceLocation npcId) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        Objects.requireNonNull(key, "Item not registered: " + item);
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.DELIVER);
        b.targetId = key;
        b.requiredCount = count;
        b.displayText = QuestText.translatable("arc_quest.obj.deliver",
                QuestText.Arg.of((p, c) -> item.getDescription()),
                QuestText.Arg.of((p, c) -> count));
        b.extraData.put("npc_id", npcId.toString());
        return b;
    }

    public static ObjectiveBuilder reachLocation(ResourceLocation locationId, int x, int y, int z, int radius) {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.REACH_LOCATION);
        b.targetId = locationId;
        b.requiredCount = 1;
        b.displayText = QuestText.translatable("arc_quest.obj.reach", QuestText.Arg.of((p, c) -> Component.literal(locationId.getPath())));
        b.extraData.put("x", String.valueOf(x));
        b.extraData.put("y", String.valueOf(y));
        b.extraData.put("z", String.valueOf(z));
        b.extraData.put("radius", String.valueOf(radius));
        return b;
    }

    public static ObjectiveBuilder interact(ResourceLocation targetId) {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.INTERACT);
        b.targetId = targetId;
        b.requiredCount = 1;
        b.displayText = QuestText.translatable("arc_quest.obj.interact", QuestText.Arg.of((p, c) -> Component.literal(targetId.getPath())));
        return b;
    }

    public static ObjectiveBuilder custom(ResourceLocation customId, int count) {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.CUSTOM);
        b.targetId = customId;
        b.requiredCount = count;
        return b;
    }

    public static ObjectiveBuilder nullObjective() {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.NULL);
        b.targetId = ResourceLocation.fromNamespaceAndPath(Arc_Quest.MOD_ID, "null");
        b.requiredCount = 1;
        return b;
    }

    public static ObjectiveBuilder offer(Item item, int count) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        Objects.requireNonNull(key, "Item not registered: " + item);
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.OFFER);
        b.targetId = key;
        b.requiredCount = count;
        b.displayText = QuestText.translatable("arc_quest.obj.deliver",
                QuestText.Arg.of((p, c) -> item.getDescription()),
                QuestText.Arg.of((p, c) -> count));
        return b;
    }

    public static ObjectiveBuilder offerTag(ResourceLocation itemTagId, int count) {
        ObjectiveBuilder b = new ObjectiveBuilder(ObjectiveType.OFFER);
        b.targetId = itemTagId;
        b.requiredCount = count;
        b.extraData.put("target_tag", itemTagId.toString());
        b.displayText = QuestText.translatable("arc_quest.obj.deliver",
                QuestText.Arg.of((p, c) -> Component.literal("#" + itemTagId)),
                QuestText.Arg.of((p, c) -> count));
        return b;
    }

    public ObjectiveBuilder display(String literal) {
        displayText = QuestText.literal(literal);
        return this;
    }

    public ObjectiveBuilder display(Component component) {
        displayText = QuestText.component(component);
        return this;
    }

    public ObjectiveBuilder display(QuestText text) {
        displayText = text;
        return this;
    }

    public ObjectiveBuilder count(int count) {
        requiredCount = count;
        return this;
    }

    public ObjectiveBuilder countModifier(ToIntFunction<ServerPlayer> resolver) {
        countModifier = resolver;
        return this;
    }

    public ObjectiveBuilder countModifyer(ToIntFunction<ServerPlayer> resolver) {
        return countModifier(resolver);
    }

    public ObjectiveBuilder hidden() {
        hidden = true;
        return this;
    }

    public ObjectiveBuilder optional() {
        optional = true;
        return this;
    }

    public ObjectiveBuilder extra(String key, String value) {
        extraData.put(key, value);
        return this;
    }

    public ObjectiveBuilder markRelatedObject(MarkableObject object) {
        return markRelatedObject(object, MarkActivations.always());
    }

    public ObjectiveBuilder markRelatedObject(MarkableObject object, MarkActivation activation) {
        String id = type.name().toLowerCase() + "::obj_mark_" + relatedMarks.size();
        relatedMarks.add(new MarkSpec(id, object, activation, MarkActivations.never(),
                QuestMarkerType.QUEST_OBJECTIVE, 0, 128, 20, true, false, Map.of()));
        return this;
    }

    public ObjectiveBuilder markRelatedObject(MarkSpec spec) {
        relatedMarks.add(spec);
        return this;
    }

    public ObjectiveEntry build() {
        Objects.requireNonNull(targetId, "targetId not set for ObjectiveBuilder");
        return new ObjectiveEntry(type, targetId, requiredCount, displayText,
                hidden, optional, new LinkedHashMap<>(extraData), new ArrayList<>(relatedMarks), countModifier);
    }
}
