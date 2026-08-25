package org.arcadia.arc_quest.api.event.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.fml.event.IModBusEvent;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.dialogue.api.DialogueTree;
import org.arcadia.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideGroupDefinition;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;
import org.arcadia.arc_quest.trade.api.TradeShopDefinition;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;

/**
 * Arc Quest 业务内容注册事件的基类。
 * <p>注册事件会在注册表冻结前发布，附属模组可以在事件监听器中继续使用现有的 {@code ArcQuestAPI}、构建器和公开注册表。</p>
 */
public abstract class ArcQuestRegistrationEvent extends Event implements IModBusEvent {

    private ArcQuestRegistrationEvent() {
    }

    /** 相关处理说明。 */
    public static final class Quest extends ArcQuestRegistrationEvent {

        public void register(QuestDefinition definition) {
            ArcQuestAPI.registerQuest(definition);
        }

        public void registerGroup(QuestGroupDefinition definition) {
            ArcQuestAPI.registerQuestGroup(definition);
        }

        public void assignQuestToGroup(ResourceLocation questId, ResourceLocation groupId) {
            ArcQuestAPI.assignQuestToGroup(questId, groupId);
        }

        public void assignQuestToGroup(String questId, String groupId) {
            assignQuestToGroup(ResourceLocation.parse(questId), ResourceLocation.parse(groupId));
        }
    }

    /** 相关处理说明。 */
    public static final class Guide extends ArcQuestRegistrationEvent {

        public void register(GuideDefinition definition) {
            ArcQuestAPI.registerGuide(definition);
        }

        public void registerGroup(GuideGroupDefinition definition) {
            ArcQuestAPI.registerGuideGroup(definition);
        }

        public void assignGuideToGroup(ResourceLocation guideId, ResourceLocation groupId) {
            ArcQuestAPI.assignGuideToGroup(guideId, groupId);
        }

        public void assignGuideToGroup(String guideId, String groupId) {
            assignGuideToGroup(ResourceLocation.parse(guideId), ResourceLocation.parse(groupId));
        }
    }

    /** 相关处理说明。 */
    public static final class Trade extends ArcQuestRegistrationEvent {
        public void register(TradeShopDefinition definition) {
            ArcQuestAPI.registerTradeShop(definition);
        }
    }

    /** 相关处理说明。 */
    public static final class Gacha extends ArcQuestRegistrationEvent {
        public void register(GachaShopDefinition definition) {
            ArcQuestAPI.registerGachaShop(definition);
        }
    }

    /** 相关处理说明。 */
    public static final class Npc extends ArcQuestRegistrationEvent {
        public void register(NpcSpec spec) {
            ArcQuestAPI.registerNpcSpec(spec);
        }

        public void registerExtension(IEntityDialogueExtension<?> extension) {
            ArcQuestAPI.registerDialogueExtension(extension);
        }
    }

    /** 相关处理说明。 */
    public static final class Dialogue extends ArcQuestRegistrationEvent {
        public void register(DialogueTree tree) {
            ArcQuestAPI.registerDialogueTree(tree);
        }
    }
}
