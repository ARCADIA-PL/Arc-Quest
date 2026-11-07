package org.arcadia.arc_quest.quest.api.migration;

import java.util.*;
import java.util.function.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.QuestProgressHandler;
import org.arcadia.arc_quest.quest.network.QuestSyncCoordinator;
import org.arcadia.arc_quest.questplayer.*;

/**
 * Server-thread content SPI. Exact hashes authorize a migration, never version labels alone.
 * Edits run on detached copies. Commit swaps the run and backup receipt in one ArcQ snapshot;
 * it has no authority over inventories/world files and never replays quest lifecycle rewards.
 */
public final class ContentMigrationRegistry {
    public static final ContentMigrationRegistry INSTANCE = new ContentMigrationRegistry();
    public enum Scope { ACTIVE, ARCHIVE, PAUSED }
    public enum RestoreResult { RESTORED, ABSENT, CHANGED_SINCE_MIGRATION, ALREADY_RESTORED }
    public record Definition(ResourceLocation id, ResourceLocation questId, Set<String> sourceHashes,
                             QuestDefinition target, String targetHash, String targetVersion,
                             Set<Scope> scopes, Consumer<QuestRuntimeEditor> edit) {
        public Definition {
            Objects.requireNonNull(id); Objects.requireNonNull(questId); Objects.requireNonNull(target); Objects.requireNonNull(edit);
            sourceHashes = Set.copyOf(sourceHashes); scopes = Set.copyOf(scopes);
            if (!questId.equals(target.getId()) || sourceHashes.contains(targetHash)
                    || sourceHashes.stream().anyMatch(s -> !s.matches("[a-f0-9]{64}"))) throw new IllegalArgumentException("Invalid migration identity/whitelist");
        }
    }
    public static final class Plan {
        private final Definition definition;
        private final QuestRuntimeData before, after;
        private final Scope scope;
        private final List<String> changes;
        private Plan(Definition definition, QuestRuntimeData before, QuestRuntimeData after, Scope scope, List<String> changes) {
            this.definition = definition; this.before = before.copy(); this.after = after.copy(); this.scope = scope; this.changes = List.copyOf(changes);
        }
        public List<String> changes() { return changes; }
        public String runId() { return identity(before); }
        public ResourceLocation migrationId() { return definition.id(); }
        public QuestRuntimeData previewCopy() { return after.copy(); }
        private String receiptKey() { return definition.id() + "/" + runId(); }
    }
    private record Registration(ResourceLocation quest, Set<String> versions, Function<MinecraftServer, Definition> factory) {}
    private final Map<ResourceLocation, Registration> factories = new LinkedHashMap<>();
    private ContentMigrationRegistry() {}
    public synchronized void register(ResourceLocation id, Function<MinecraftServer, Definition> factory) {
        register(id, null, Set.of(), factory);
    }
    /** Version filtering is a cheap factory prefilter only; authorization still requires full hashes. */
    public synchronized void register(ResourceLocation id, ResourceLocation quest, Set<String> sourceVersions,
            Function<MinecraftServer, Definition> factory) {
        if (factories.putIfAbsent(Objects.requireNonNull(id), new Registration(quest, Set.copyOf(sourceVersions), Objects.requireNonNull(factory))) != null)
            throw new IllegalArgumentException("Duplicate content migration " + id);
    }
    public Optional<Plan> preview(Definition definition, QuestRuntimeData run, Scope scope) {
        if (run == null || !run.getQuestId().equals(definition.questId().toString())
                || !definition.scopes().contains(scope) || !definition.sourceHashes().contains(run.getFrozenDefinitionHash())) return Optional.empty();
        var editor = new QuestRuntimeEditor(run, definition.target()).definition(definition.targetHash(), definition.targetVersion());
        definition.edit().accept(editor);
        var next = editor.build();
        if (run.getState() != next.getState() || !identity(run).equals(identity(next)))
            throw new IllegalStateException("Migration changed lifecycle identity");
        return Optional.of(new Plan(definition, run, next, scope, editor.changes()));
    }
    private static String identity(QuestRuntimeData run) {
        return run.hasCollectionData() ? run.getCollectionData().getRunId()
                : "legacy/" + run.getAcceptedAtRealMs() + "/" + run.getAcceptedAtTick() + "/" + run.getQuestId();
    }
    public boolean commit(ArcQuestPlayer data, Plan plan) {
        var current = switch(plan.scope) { case ACTIVE -> data.getActiveQuest(plan.before.getQuestId());
            case ARCHIVE -> data.getCollectionArchives().get(plan.before.getQuestId(), plan.runId());
            case PAUSED -> data.getRuntimeManagement().pausedRun(plan.before.getQuestId()); };
        if (data.getRuntimeManagement().contains(plan.receiptKey())) return false;
        if (current == null || !current.serializeNBT().equals(plan.before.serializeNBT())) throw new IllegalStateException("Stale migration preview");
        // Validate backup capacity before any live mutation. Both fields persist in the same player document.
        var journal = new org.arcadia.arc_quest.quest.data.RuntimeManagementState();
        journal.restore(data.getRuntimeManagement().snapshot());
        journal.record(plan.receiptKey(), plan.before.serializeNBT(), plan.after.serializeNBT());
        if (plan.scope == Scope.ACTIVE) data.addActiveQuest(plan.after.copy());
        else if(plan.scope==Scope.ARCHIVE)data.getCollectionArchives().replace(plan.after);
        else journal.pauseRun(plan.before.getQuestId(),plan.after,journal.pauseReason(plan.before.getQuestId()));
        data.getRuntimeManagement().restore(journal.snapshot());
        return true;
    }
    /** Explicit recovery only: refuses to discard any gameplay performed after migration. */
    public RestoreResult restore(ArcQuestPlayer data, ResourceLocation migrationId, String questId, String runId) {
        String key = migrationId + "/" + runId;
        var receipt = data.getRuntimeManagement().receipt(key);
        if (receipt.isEmpty()) return RestoreResult.ABSENT;
        if (receipt.getBoolean("Restored")) return RestoreResult.ALREADY_RESTORED;
        var before = receipt.getCompound("Before"); var after = receipt.getCompound("After");
        if (!questId.equals(before.getString("QuestId"))) return RestoreResult.ABSENT;
        var active = data.getActiveQuest(questId);
        boolean isActive = active != null && identity(active).equals(runId);
        var paused=data.getRuntimeManagement().pausedRun(questId);
        boolean isPaused=!isActive&&paused!=null&&!paused.hasCollectionData()&&identity(paused).equals(runId);
        var current = isActive ? active : isPaused ? paused : data.getCollectionArchives().get(questId, runId);
        if (current == null || !current.serializeNBT().equals(after)) return RestoreResult.CHANGED_SINCE_MIGRATION;
        var restored = QuestRuntimeData.deserializeNBT(before);
        if (isActive) data.addActiveQuest(restored);
        else if(isPaused)data.getRuntimeManagement().pauseRun(questId,restored,data.getRuntimeManagement().pauseReason(questId));
        else data.getCollectionArchives().replace(restored);
        data.getRuntimeManagement().markRestored(key);
        return RestoreResult.RESTORED;
    }
    public int reconcile(MinecraftServer server, ArcQuestPlayer data, ResourceLocation id) {
        if (!server.isSameThread()) throw new IllegalStateException("Migrations require server thread");
        var registration = factories.get(id);
        if (registration == null) throw new IllegalArgumentException("Unknown content migration " + id);
        if (registration.quest() != null) {
            var candidates = new ArrayList<>(data.getCollectionArchives().allRuns(registration.quest().toString()));
            var activeRun = data.getActiveQuest(registration.quest().toString());
            if (activeRun != null) candidates.add(activeRun);
            var pausedRun=data.getRuntimeManagement().pausedRun(registration.quest().toString());
            if(pausedRun!=null&&!pausedRun.hasCollectionData())candidates.add(pausedRun);
            if (candidates.stream().noneMatch(r -> registration.versions().isEmpty() || registration.versions().contains(r.getFrozenDefinitionVersion()))) return 0;
        }
        var definition = registration.factory().apply(server);
        if (definition == null) return 0;
        if (!definition.id().equals(id)) throw new IllegalArgumentException("Migration factory identity mismatch");
        var beforeBatch = data.serializeNBT();
        try {
        int count = 0;
        var active = preview(definition, data.getActiveQuest(definition.questId().toString()), Scope.ACTIVE);
        boolean activeChanged = active.isPresent() && commit(data, active.get());
        if (activeChanged) count++;
        var pausedRun=data.getRuntimeManagement().pausedRun(definition.questId().toString());
        if(pausedRun!=null&&!pausedRun.hasCollectionData()) {
            var pausedPlan=preview(definition,pausedRun,Scope.PAUSED);
            if(pausedPlan.isPresent()&&commit(data,pausedPlan.get()))count++;
        }
        for (var run : data.getCollectionArchives().allRuns(definition.questId().toString())) {
            // A captured ACTIVE copy of the current run is kept equivalent, not migrated twice.
            var current = data.getActiveQuest(run.getQuestId());
            if (current != null && current.hasCollectionData() && current.getCollectionData().getRunId().equals(run.getCollectionData().getRunId())) {
                if (activeChanged) data.getCollectionArchives().replace(current);
                continue;
            }
            var plan = preview(definition, run, Scope.ARCHIVE);
            if (plan.isPresent() && commit(data, plan.get())) count++;
        }
        return count;
        } catch (RuntimeException failure) { data.deserializeNBT(beforeBatch); throw failure; }
    }
    public int reconcile(ServerPlayer player, ResourceLocation id) {
        var data = ArcQuestPlayerManager.getOrCreate(player);
        int changed = reconcile(player.server, data, id);
        if (changed > 0) {
            QuestProgressHandler.rebuildTrackingIndex(player, data);
            ArcQuestPlayerManager.persistSnapshotSynchronously(player, data);
            QuestSyncCoordinator.syncFullDataAndPush(player, data);
        }
        return changed;
    }
}
