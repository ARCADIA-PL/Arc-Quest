package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.util.thread.EffectiveSide;
import org.arcadia.arc_quest.quest.api.*;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Shared knowledge definitions. Snapshot replacement validates conflicts before publication. */
public final class CollectionEntryRegistry {
    private static final Map<ResourceLocation, CollectionEntryDefinition> CODE = new LinkedHashMap<>();
    private static volatile Map<ResourceLocation, CollectionEntryDefinition> entries = Map.of();
    @Nullable private static volatile Map<ResourceLocation, CollectionEntryDefinition> clientEntries;
    private CollectionEntryRegistry() {}

    public static synchronized void register(CollectionEntryDefinition definition) {
        Objects.requireNonNull(definition, "collection entry");
        CollectionEntryDefinition previous = CODE.get(definition.getEntryId());
        checkConflict(previous, definition);
        checkConflict(entries.get(definition.getEntryId()), definition);
        if (previous == null) CODE.put(definition.getEntryId(), definition);
        LinkedHashMap<ResourceLocation, CollectionEntryDefinition> merged = new LinkedHashMap<>(entries);
        checkConflict(merged.get(definition.getEntryId()), definition);
        merged.putIfAbsent(definition.getEntryId(), definition);
        entries = Map.copyOf(merged);
    }

    /** Called with the successful, merged Quest snapshot after a datapack reload. */
    public static synchronized void replaceQuestSnapshot(Collection<QuestDefinition> quests) {
        LinkedHashMap<ResourceLocation, CollectionEntryDefinition> merged = new LinkedHashMap<>(CODE);
        for (QuestDefinition quest : quests) {
            CollectionQuestConfig config = quest.getCollectionConfig();
            if (config == null) continue;
            for (CollectionEntryDefinition definition : config.getEntries()) {
                checkConflict(merged.get(definition.getEntryId()), definition);
                merged.putIfAbsent(definition.getEntryId(), definition);
            }
        }
        entries = Map.copyOf(merged);
    }

    public static synchronized void replaceClientPresentationSnapshot(Collection<QuestDefinition> quests) {
        LinkedHashMap<ResourceLocation, CollectionEntryDefinition> merged = new LinkedHashMap<>();
        for (QuestDefinition quest : quests) {
            if (quest.getCollectionConfig() == null) continue;
            for (CollectionEntryDefinition entry : quest.getCollectionConfig().getEntries()) {
                // The same shared entry can be redacted differently by its task context; keep the disclosed version.
                merged.merge(entry.getEntryId(), entry, (oldEntry, nextEntry) ->
                        disclosureScore(nextEntry) > disclosureScore(oldEntry) ? nextEntry : oldEntry);
            }
        }
        clientEntries = Map.copyOf(merged);
    }

    public static synchronized void clearClientPresentationSnapshot() { clientEntries = null; }
    private static int disclosureScore(CollectionEntryDefinition entry) {
        if (entry.getDisplayName().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && "arc_quest.collection.entry.unknown".equals(text.getKey())) return -1;
        return (entry.getSubjectId() != null || entry.getItemTag() != null ? 4 : 0)
                + (entry.getIcon().mode() != org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec.Mode.NONE ? 2 : 0)
                + Math.min(8, entry.getContent().size());
    }
    private static Map<ResourceLocation, CollectionEntryDefinition> current() {
        var client = clientEntries;
        if (EffectiveSide.get() == LogicalSide.CLIENT) return client == null ? Map.of() : client;
        return entries;
    }

    @Nullable public static CollectionEntryDefinition get(ResourceLocation entryId) { return current().get(entryId); }
    @Nullable public static CollectionEntryDefinition get(String entryId) {
        ResourceLocation id = entryId == null ? null : ResourceLocation.tryParse(entryId);
        return id == null ? null : get(id);
    }
    public static Collection<CollectionEntryDefinition> all() { return current().values(); }
    public static Map<ResourceLocation, CollectionEntryDefinition> snapshot() { return current(); }
    @Nullable public static CollectionEntryDefinition getServerEntry(ResourceLocation entryId) { return entries.get(entryId); }
    public static Map<ResourceLocation, CollectionEntryDefinition> serverSnapshot() { return entries; }
    public static synchronized void clear() { CODE.clear(); entries = Map.of(); clientEntries = null; }

    private static void checkConflict(CollectionEntryDefinition previous, CollectionEntryDefinition next) {
        if (previous != null && previous != next && !equivalent(previous, next)) {
            throw new IllegalArgumentException("Conflicting shared collection entry: " + next.getEntryId());
        }
    }

    /** Compare the immutable public definition rather than object identity of compiled objectives. */
    public static boolean equivalent(CollectionEntryDefinition left, CollectionEntryDefinition right) {
        return left.getEntryId().equals(right.getEntryId())
                && left.getCategoryId().equals(right.getCategoryId())
                && left.getDisplayName().equals(right.getDisplayName())
                && left.getDescription().equals(right.getDescription())
                && left.getSubjectKind() == right.getSubjectKind()
                && Objects.equals(left.getSubjectId(), right.getSubjectId())
                && Objects.equals(left.getItemTag(), right.getItemTag())
                && left.getIcon().equals(right.getIcon())
                && left.getRelatedItems().equals(right.getRelatedItems())
                && left.getVisibilityMode() == right.getVisibilityMode()
                && left.getHiddenPresentationMode() == right.getHiddenPresentationMode()
                && left.getSortOrder() == right.getSortOrder()
                && left.isResearchAfterDiscovery() == right.isResearchAfterDiscovery()
                && sameConditions(left, right)
                && sameObjectives(left.getDiscoveryObjectives(), right.getDiscoveryObjectives())
                && sameObjectives(left.getResearchObjectives(), right.getResearchObjectives())
                && sameContent(left.getContent(), right.getContent());
    }

    private static boolean sameConditions(CollectionEntryDefinition left, CollectionEntryDefinition right) {
        if (left.getRecordConditions().isEmpty() && right.getRecordConditions().isEmpty()) return true;
        if (left.getRecordConditionSignature() != null && right.getRecordConditionSignature() != null) {
            return left.getRecordConditionSignature().equals(right.getRecordConditionSignature());
        }
        return left.getRecordConditions().equals(right.getRecordConditions());
    }

    private static boolean sameObjectives(List<ObjectiveEntry> left, List<ObjectiveEntry> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            ObjectiveEntry a = left.get(i), b = right.get(i);
            if (!a.getObjectiveId().equals(b.getObjectiveId()) || !a.getType().equals(b.getType())
                    || !a.getTargetId().equals(b.getTargetId()) || a.getRequiredCount() != b.getRequiredCount()
                    || !a.getDisplayText().equals(b.getDisplayText()) || a.isHidden() != b.isHidden()
                    || a.isOptional() != b.isOptional() || !a.getExtraData().equals(b.getExtraData())
                    || !a.getIcon().equals(b.getIcon()) || !a.getRelatedMarks().equals(b.getRelatedMarks())) return false;
        }
        return true;
    }

    private static boolean sameContent(List<CollectionContentBlock> left, List<CollectionContentBlock> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            var a = left.get(i); var b = right.get(i);
            if (!a.blockId().equals(b.blockId()) || !a.text().resolve(null, QuestTextContext.empty()).equals(b.text().resolve(null, QuestTextContext.empty()))
                    || !a.caption().resolve(null, QuestTextContext.empty()).equals(b.caption().resolve(null, QuestTextContext.empty()))
                    || a.fit() != b.fit() || a.zoomable() != b.zoomable() || a.reveal() != b.reveal()
                    || !a.revealStepId().equals(b.revealStepId())) return false;
            var am = a.media(); var bm = b.media();
            if (am == null || bm == null) { if (am != bm) return false; }
            else if (am.getType() != bm.getType() || !Objects.equals(am.getTexture(), bm.getTexture())
                    || !Objects.equals(am.getSceneId(), bm.getSceneId()) || am.getWidth() != bm.getWidth()
                    || am.getHeight() != bm.getHeight() || am.isAutoplay() != bm.isAutoplay() || am.isLoop() != bm.isLoop()) return false;
        }
        return true;
    }
}
