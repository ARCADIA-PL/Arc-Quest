package org.arcadia.arc_quest.quest.api.migration;

import java.util.*;
import net.minecraft.nbt.*;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;

/**
 * Detached, typed content-migration editor. Never awards progress or rewards. Terminal targets are
 * preserved by default; correcting a historical authoring error requires CORRECT_DECLARED_ALL.
 * Construct from a copy/plan, inspect changes, then let ContentMigrationRegistry commit it.
 */
public final class QuestRuntimeEditor {
    public enum SheetPolicy { EXTEND_ACTIVE, RETAIN_DECLARED_ACTIVE, CORRECT_DECLARED_ALL }
    private final QuestDefinition target;
    private final QuestState state;
    private final CompoundTag tag;
    private final List<String> changes = new ArrayList<>();
    public QuestRuntimeEditor(QuestRuntimeData source, QuestDefinition target) {
        if (!source.getQuestId().equals(target.getId().toString())) throw new IllegalArgumentException("Cannot change quest identity");
        this.target = target; this.state = source.getState(); this.tag = source.serializeNBT();
    }
    public QuestRuntimeEditor definition(String hash, String version) {
        if (hash == null || !hash.matches("[a-f0-9]{64}") || version == null || version.isBlank())
            throw new IllegalArgumentException("A frozen definition hash/version is required");
        tag.putString(QuestRuntimeData.FROZEN_DEFINITION_HASH_KEY, hash);
        tag.putString(QuestRuntimeData.FROZEN_DEFINITION_VERSION_KEY, version);
        changes.add("definition:" + version); return this;
    }
    public QuestRuntimeEditor sheet(String phaseId, SheetPolicy policy) {
        var phase = target.getPhase(phaseId);
        if (phase == null || !phase.hasCollectionSheet()) throw new IllegalArgumentException("Missing target sheet " + phaseId);
        if (state != QuestState.ACTIVE && policy != SheetPolicy.CORRECT_DECLARED_ALL) return this;
        var phases = tag.getCompound("CollectionData").getCompound("Sheets").getCompound("Phases");
        if (!phases.contains(phaseId)) return this;
        var sheet = phases.getCompound(phaseId);
        var ids = new LinkedHashSet<String>();
        if (policy == SheetPolicy.EXTEND_ACTIVE) {
            var previous = sheet.getList("Bindings", Tag.TAG_STRING);
            for (int i = 0; i < previous.size(); i++) ids.add(previous.getString(i));
        }
        int required = 0;
        for (var binding : phase.getCollectionSheet().getBindings()) {
            ids.add(binding.getBindingId()); if (!binding.isOptional()) required++;
        }
        var output = new ListTag(); ids.forEach(id -> output.add(StringTag.valueOf(id)));
        sheet.put("Bindings", output); sheet.putInt("Target", Math.max(1, required));
        if (policy != SheetPolicy.EXTEND_ACTIVE) {
            // Preserve historical reward entitlements, even when their requirements were removed.
            for (String key : List.of("Completed", "PendingOutcomes")) {
                var values = sheet.getCompound(key);
                for (var id : Set.copyOf(values.getAllKeys())) if (!ids.contains(id)) values.remove(id);
            }
        }
        changes.add("sheet:" + phaseId + ":" + policy); return this;
    }
    public QuestRuntimeEditor removeBinding(String phaseId, String bindingId) {
        if (state != QuestState.ACTIVE) return this;
        var phase = tag.getCompound("CollectionData").getCompound("Sheets").getCompound("Phases").getCompound(phaseId);
        phase.getList("Bindings", Tag.TAG_STRING).removeIf(t -> t.getAsString().equals(bindingId));
        phase.getCompound("Completed").remove(bindingId);
        phase.getCompound("PendingOutcomes").remove(bindingId);
        changes.add("remove-binding:" + phaseId + "/" + bindingId); return this;
    }
    public QuestRuntimeEditor reopenActiveSheet(String phaseId) {
        if (state != QuestState.ACTIVE) return this;
        tag.getCompound("CollectionData").getCompound("Sheets").getCompound("Phases").getCompound(phaseId).remove("Settled");
        tag.getList("PendingManualAdvancePhases", Tag.TAG_STRING).removeIf(t -> t.getAsString().equals(phaseId));
        changes.add("reopen-sheet:" + phaseId); return this;
    }
    public QuestRuntimeEditor mapPhase(String from, String to) {
        if (from.equals(to)) return this;
        if (target.getPhase(to) == null) throw new IllegalArgumentException("Missing target phase " + to);
        for (String key : List.of("ActivePhases", "CompletedPhases", "PendingManualAdvancePhases")) {
            var list = tag.getList(key, Tag.TAG_STRING);
            if (list.stream().anyMatch(t -> t.getAsString().equals(to))) throw new IllegalArgumentException("Phase collision " + to);
            for (int i = 0; i < list.size(); i++) if (list.getString(i).equals(from)) list.set(i, StringTag.valueOf(to));
        }
        for (String key : List.of("PhaseProgress", "EffectiveRequiredCounts")) rename(tag.getCompound(key), from, to);
        rename(tag.getCompound("CollectionData").getCompound("Sheets").getCompound("Phases"), from, to);
        changes.add("phase:" + from + "->" + to); return this;
    }
    public QuestRuntimeEditor mapBinding(String phaseId, String from, String to) {
        if (from.equals(to)) return this;
        var definition = target.getPhase(phaseId);
        if (definition == null || !definition.hasCollectionSheet() || definition.getCollectionSheet().getBindings().stream().noneMatch(b -> b.getBindingId().equals(to)))
            throw new IllegalArgumentException("Missing target binding " + to);
        var phase = tag.getCompound("CollectionData").getCompound("Sheets").getCompound("Phases").getCompound(phaseId);
        var bindings = phase.getList("Bindings", Tag.TAG_STRING);
        if (bindings.stream().anyMatch(t -> t.getAsString().equals(to))) throw new IllegalArgumentException("Binding collision " + to);
        for (int i = 0; i < bindings.size(); i++) if (bindings.getString(i).equals(from)) bindings.set(i, StringTag.valueOf(to));
        if (!phase.getCompound("EntryRewards").getCompound(from).isEmpty())
            throw new IllegalArgumentException("Reward-bearing bindings retain their original identity; add a new binding instead");
        for (String key : List.of("Completed", "PendingOutcomes", "EntryRewards")) rename(phase.getCompound(key), from, to);
        changes.add("binding:" + phaseId + "/" + from + "->" + to); return this;
    }
    private static void rename(CompoundTag parent, String from, String to) {
        if (!parent.contains(from)) return;
        if (parent.contains(to)) throw new IllegalArgumentException("Migration key collision " + to);
        parent.put(to, parent.get(from).copy()); parent.remove(from);
    }
    public List<String> changes() { return List.copyOf(changes); }
    public QuestRuntimeData build() { return QuestRuntimeData.deserializeNBT(tag.copy()); }
}
