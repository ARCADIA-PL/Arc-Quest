package org.arcadia.arc_quest.api.event.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.Event;
import org.arcadia.arc_quest.api.ArcQuestAPI;
import org.arcadia.arc_quest.guide.api.GuideGroupDefinition;
import org.arcadia.arc_quest.quest.api.QuestGroupDefinition;

/**
 * Base type for Arc Quest business-content registration events.
 *
 * <p>Arc Quest posts these events to every mod bus before its registries are frozen. Add-ons may
 * keep using the existing {@code ArcQuestAPI}, builders, and public registries from their event
 * listeners.</p>
 */
public abstract class ArcQuestRegistrationEvent extends Event {

    private ArcQuestRegistrationEvent() {
    }

    /** Registers quest definitions. */
    public static final class Quest extends ArcQuestRegistrationEvent {

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

    /** Registers guide definitions. */
    public static final class Guide extends ArcQuestRegistrationEvent {

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

    /** Registers trade shop definitions. */
    public static final class Trade extends ArcQuestRegistrationEvent {
    }

    /** Registers gacha shop definitions. */
    public static final class Gacha extends ArcQuestRegistrationEvent {
    }

    /** Registers NPC dialogue extensions and bindings. */
    public static final class Npc extends ArcQuestRegistrationEvent {
    }

    /** Registers dialogue tree definitions. */
    public static final class Dialogue extends ArcQuestRegistrationEvent {
    }
}
