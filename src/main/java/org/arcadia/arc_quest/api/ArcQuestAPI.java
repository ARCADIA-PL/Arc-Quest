package org.arcadia.arc_quest.api;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.data.registry.RegistrySourceInfo;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.dialogue.registry.EntityDialogueExtensionManager;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideGroupDefinition;
import org.arcadia.arc_quest.guide.registry.GuideGroupRegistry;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;
import org.arcadia.arc_quest.quest.registry.QuestGroupRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questmarker.api.MarkTargetResolver;
import org.arcadia.arc_quest.questmarker.api.MarkTargetResolverRegistry;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.jetbrains.annotations.Nullable;

public final class ArcQuestAPI {

    private ArcQuestAPI() {
    }

    public static void registerQuest(QuestDefinition definition) {
        QuestRegistry.register(definition);
    }

    @Nullable
    public static QuestDefinition getQuest(ResourceLocation id) {
        return QuestRegistry.get(id);
    }

    public static QuestDefinition getQuestOrThrow(ResourceLocation id) {
        return QuestRegistry.getOrThrow(id);
    }

    public static boolean hasQuest(ResourceLocation id) {
        return QuestRegistry.get(id) != null;
    }

    public static void registerMarkTargetResolver(String resolverId, MarkTargetResolver resolver) {
        MarkTargetResolverRegistry.register(resolverId, resolver);
    }

    public static void unregisterMarkTargetResolver(String resolverId) {
        MarkTargetResolverRegistry.unregister(resolverId);
    }

    @Nullable
    public static MarkTargetResolver getMarkTargetResolver(String resolverId) {
        return MarkTargetResolverRegistry.get(resolverId);
    }

    @Nullable
    public static RegistrySourceInfo getQuestSourceInfo(ResourceLocation id) {
        return QuestRegistry.getUnifiedSourceInfo(id);
    }

    public static void registerQuestGroup(QuestGroupDefinition definition) {
        QuestGroupRegistry.register(definition);
    }

    public static void assignQuestToGroup(ResourceLocation questId, ResourceLocation groupId) {
        QuestGroupRegistry.assign(questId, groupId);
    }

    @Nullable
    public static QuestGroupDefinition getQuestGroup(ResourceLocation groupId) {
        return QuestGroupRegistry.get(groupId);
    }

    @Nullable
    public static QuestGroupDefinition getQuestGroupForQuest(ResourceLocation questId) {
        return QuestGroupRegistry.getGroupForQuest(questId);
    }

    public static void registerGuide(GuideDefinition definition) {
        GuideRegistry.register(definition);
    }

    @Nullable
    public static GuideDefinition getGuide(ResourceLocation id) {
        return GuideRegistry.get(id);
    }

    public static boolean hasGuide(ResourceLocation id) {
        return GuideRegistry.get(id) != null;
    }

    @Nullable
    public static RegistrySourceInfo getGuideSourceInfo(ResourceLocation id) {
        return GuideRegistry.getUnifiedSourceInfo(id);
    }

    public static void registerGuideGroup(GuideGroupDefinition definition) {
        GuideGroupRegistry.register(definition);
    }

    public static void assignGuideToGroup(ResourceLocation guideId, ResourceLocation groupId) {
        GuideGroupRegistry.assign(guideId, groupId);
    }

    @Nullable
    public static GuideGroupDefinition getGuideGroup(ResourceLocation groupId) {
        return GuideGroupRegistry.get(groupId);
    }

    @Nullable
    public static GuideGroupDefinition getGuideGroupForGuide(ResourceLocation guideId) {
        return GuideGroupRegistry.getGroupForGuide(guideId);
    }

    public static void registerDialogueTree(DialogueTree tree) {
        DialogueRegistry.INSTANCE.register(tree);
    }

    @Nullable
    public static DialogueTree getDialogueTree(String dialogueId) {
        return DialogueRegistry.INSTANCE.get(dialogueId);
    }

    public static boolean hasDialogueTree(String dialogueId) {
        return DialogueRegistry.INSTANCE.get(dialogueId) != null;
    }

    @Nullable
    public static RegistrySourceInfo getDialogueSourceInfo(String dialogueId) {
        return DialogueRegistry.INSTANCE.getSourceInfo(dialogueId);
    }

    public static void registerDialogueExtension(IEntityDialogueExtension<?> extension) {
        EntityDialogueExtensionManager.INSTANCE.register(extension);
    }

    public static void registerNpcSpec(NpcSpec spec) {
        NpcBindingRegistry.INSTANCE.registerCode(spec);
    }

    @Nullable
    public static RegistrySourceInfo getNpcBindingSourceInfo(String bindingId) {
        return NpcBindingRegistry.INSTANCE.getSourceInfo(bindingId);
    }

    public static void registerTradeShop(TradeShopDefinition shop) {
        TradeRegistry.register(shop);
    }

    @Nullable
    public static TradeShopDefinition getTradeShop(String shopId) {
        return TradeRegistry.get(shopId);
    }

    public static boolean hasTradeShop(String shopId) {
        return TradeRegistry.get(shopId) != null;
    }

    @Nullable
    public static RegistrySourceInfo getTradeShopSourceInfo(String shopId) {
        return TradeRegistry.getSourceInfo(shopId);
    }

    public static void registerGachaShop(GachaShopDefinition shop) {
        GachaRegistry.register(shop);
    }

    @Nullable
    public static RegistrySourceInfo getGachaShopSourceInfo(String shopId) {
        return GachaRegistry.getSourceInfo(shopId);
    }
}
