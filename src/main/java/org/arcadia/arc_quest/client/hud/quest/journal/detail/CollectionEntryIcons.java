package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconContext;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconsClient;
import org.arcadia.arc_quest.quest.api.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reuses the registered two-dimensional icon providers; these views are never executable objectives. */
public final class CollectionEntryIcons {
    private static final Map<CollectionEntryDefinition, ObjectiveEntry> VIEWS = new LinkedHashMap<>(32, .75f, true);
    private CollectionEntryIcons() {}

    public static ObjectiveIconContext context(String quest, String phase, String binding, CollectionEntryDefinition entry) {
        ObjectiveEntry view = VIEWS.computeIfAbsent(entry, value -> {
            ObjectiveType type = switch (value.getSubjectKind()) {
                case ENTITY -> ObjectiveType.KILL;
                case ITEM -> ObjectiveType.COLLECT;
                case CUSTOM -> ObjectiveType.CUSTOM;
            };
            Map<String, String> extras = new LinkedHashMap<>();
            if (value.getItemTag() != null) {
                extras.put("target_tag", value.getItemTag().toString());
                if (value.getPresentationItemTagMembers() != null) extras.put(ObjectiveItemResolver.FROZEN_TAG_MEMBERS,
                        ObjectiveItemResolver.encodeFrozenTagMembers(value.getPresentationItemTagMembers()));
            }
            ResourceLocation target = value.getSubjectId() == null ? value.getEntryId() : value.getSubjectId();
            return new ObjectiveEntry("entry_icon", type, target, 1, QuestText.component(value.getDisplayName()),
                    false, false, extras, List.of(), null, value.getIcon());
        });
        while (VIEWS.size() > 256) VIEWS.remove(VIEWS.keySet().iterator().next());
        return new ObjectiveIconContext(quest, phase + "#entry/" + binding, 0, view, 0, 1,
                ObjectiveIconsClient.generation());
    }
}
