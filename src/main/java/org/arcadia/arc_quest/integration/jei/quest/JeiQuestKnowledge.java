package org.arcadia.arc_quest.integration.jei.quest;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persisted disclosure evidence, not quest progression and never used to authorize a transaction. */
public final class JeiQuestKnowledge {
    private final Map<UUID, Map<String, KnownQuest>> players = new HashMap<>();

    public record KnownQuest(Set<String> phases, Set<String> milestones, Set<String> claimedMilestones,
                             boolean collection) {
        public static final KnownQuest EMPTY = new KnownQuest(Set.of(), Set.of(), Set.of(), false);
        public KnownQuest {
            phases = Set.copyOf(phases);
            milestones = Set.copyOf(milestones);
            claimedMilestones = Set.copyOf(claimedMilestones);
        }
    }

    public KnownQuest get(UUID player, String quest) {
        return players.getOrDefault(player, Map.of()).getOrDefault(quest, KnownQuest.EMPTY);
    }

    public boolean remember(UUID player, String quest, Set<String> phases, Set<String> milestones,
                            Set<String> claimed, boolean collection) {
        if (phases.isEmpty() && milestones.isEmpty() && claimed.isEmpty()) return false;
        KnownQuest previous = get(player, quest);
        KnownQuest merged = new KnownQuest(union(previous.phases(), phases), union(previous.milestones(), milestones),
                union(previous.claimedMilestones(), claimed), previous.collection() || collection);
        if (previous.equals(merged)) return false;
        players.computeIfAbsent(player, ignored -> new HashMap<>()).put(quest, merged);
        return true;
    }

    /** Reset/abandon without a retained completed/failed state revokes the index as well. */
    public boolean retainQuests(UUID player, Set<String> knownQuestIds) {
        Map<String, KnownQuest> quests = players.get(player);
        if (quests == null) return false;
        boolean changed = quests.keySet().retainAll(knownQuestIds);
        if (quests.isEmpty()) players.remove(player);
        return changed;
    }

    private static Set<String> union(Set<String> left, Set<String> right) {
        Set<String> result = new HashSet<>(left);
        result.addAll(right);
        return result;
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putInt("SchemaVersion", 1);
        CompoundTag playerTags = new CompoundTag();
        players.forEach((player, quests) -> {
            CompoundTag questTags = new CompoundTag();
            quests.forEach((quest, known) -> {
                CompoundTag tag = new CompoundTag();
                tag.put("Phases", strings(known.phases()));
                tag.put("Milestones", strings(known.milestones()));
                tag.put("Claimed", strings(known.claimedMilestones()));
                tag.putBoolean("Collection", known.collection());
                questTags.put(quest, tag);
            });
            playerTags.put(player.toString(), questTags);
        });
        root.put("Players", playerTags);
        return root;
    }

    public static JeiQuestKnowledge load(CompoundTag root) {
        JeiQuestKnowledge state = new JeiQuestKnowledge();
        CompoundTag players = root.getCompound("Players");
        for (String rawPlayer : players.getAllKeys()) {
            UUID player;
            try { player = UUID.fromString(rawPlayer); } catch (IllegalArgumentException ignored) { continue; }
            CompoundTag quests = players.getCompound(rawPlayer);
            for (String quest : quests.getAllKeys()) {
                if (quest.isBlank() || !quests.contains(quest, Tag.TAG_COMPOUND)) continue;
                CompoundTag known = quests.getCompound(quest);
                state.remember(player, quest, readStrings(known, "Phases"), readStrings(known, "Milestones"),
                        readStrings(known, "Claimed"), known.getBoolean("Collection"));
            }
        }
        return state;
    }

    private static ListTag strings(Set<String> values) {
        ListTag result = new ListTag();
        values.stream().sorted().forEach(value -> result.add(StringTag.valueOf(value)));
        return result;
    }

    private static Set<String> readStrings(CompoundTag root, String key) {
        Set<String> result = new HashSet<>();
        ListTag list = root.getList(key, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (!list.getString(i).isBlank()) result.add(list.getString(i));
        return result;
    }
}
