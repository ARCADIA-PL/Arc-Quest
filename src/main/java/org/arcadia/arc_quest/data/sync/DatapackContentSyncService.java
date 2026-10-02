package org.arcadia.arc_quest.data.sync;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.arcadia.arc_quest.data.sync.network.S2CDatapackContentChunkPacket;
import org.arcadia.arc_quest.data.sync.network.S2CDatapackContentStartPacket;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.io.IOException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.registry.CollectionEntryRegistry;
import org.arcadia.arc_quest.quest.api.CollectionEntryDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.CollectionRecordState;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.logic.CollectionRunDefinitions;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;

public final class DatapackContentSyncService {
    public static final int CHUNK_BYTES = 128 * 1024;
    private static volatile DatapackContentTransfer current = createEmptyTransfer();
    private static volatile DatapackContentSnapshot source = DatapackContentSnapshot.empty(0L);
    private static final int MAX_RECIPIENT_CACHE = 128;
    private static final Map<UUID, RecipientContent> recipients = new LinkedHashMap<>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, RecipientContent> eldest) { return size() > MAX_RECIPIENT_CACHE; }
    };

    private DatapackContentSyncService() {
    }

    public static DatapackContentTransfer prepare(DatapackContentSnapshot snapshot) throws IOException {
        return DatapackContentCodec.encode(snapshot);
    }

    public static void commit(DatapackContentTransfer transfer) {
        try { source = DatapackContentCodec.decode(transfer); }
        catch (IOException exception) { throw new IllegalArgumentException("Invalid prepared content transfer", exception); }
        current = transfer;
        recipients.clear();
    }

    public static DatapackContentTransfer current() {
        return current;
    }

    public static void reset() {
        current = createEmptyTransfer();
        source = DatapackContentSnapshot.empty(0L);
        recipients.clear();
    }

    public static void sendToPlayer(ServerPlayer player) {
        RecipientContent content = forPlayer(player);
        if (content != null) {
            send(PacketDistributor.PLAYER.with(() -> player), content.transfer());
            recipients.put(player.getUUID(), content.sent());
        }
    }

    /** Called after record/state packets. Partial counts do not resend unchanged presentation bytes. */
    public static void syncIfChanged(ServerPlayer player) {
        syncIfChanged(player, null);
    }

    public static void syncIfChanged(ServerPlayer player, Set<ResourceLocation> changedEntryIds) {
        if (player == null) return;
        RecipientContent previous = recipients.get(player.getUUID());
        RecipientContent content = forPlayer(player, changedEntryIds);
        if (content == null) return;
        if (previous == null || !previous.wasSent() || !previous.transfer().contentHash().equals(content.transfer().contentHash())) {
            send(PacketDistributor.PLAYER.with(() -> player), content.transfer());
            recipients.put(player.getUUID(), content.sent());
        }
    }

    public static void clearPlayer(UUID playerId) { recipients.remove(playerId); }

    public static boolean broadcastCurrent() {
        if (ServerLifecycleHooks.getCurrentServer() == null) return false;
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) sendToPlayer(player);
        return true;
    }

    private static RecipientContent forPlayer(ServerPlayer player) {
        return forPlayer(player, null);
    }

    private static RecipientContent forPlayer(ServerPlayer player, Set<ResourceLocation> changedEntryIds) {
        ArcQuestPlayer data = ArcQuestPlayerManager.getOrCreate(player);
        Set<String> known = new LinkedHashSet<>(data.getAllActiveQuests().keySet());
        known.addAll(data.getCompletedQuests()); known.addAll(data.getFailedQuests());
        Set<String> immutableKnown = Set.copyOf(known);
        Map<String, QuestDefinition> selectedRuns = new LinkedHashMap<>();
        Map<String, QuestRuntimeData> selectedRuntimes = new LinkedHashMap<>();
        Map<String, String> definitionScopes = new LinkedHashMap<>();
        for (String id : known) {
            QuestRuntimeData runtime = CollectionContentDisclosure.preferredRuntime(data, id);
            QuestDefinition live = QuestRegistry.getServerDefinition(ResourceLocation.tryParse(id));
            if (runtime == null || !(runtime.hasFrozenDefinitionHash() || runtime.hasCollectionData()
                    || live != null && live.hasCollectionSheets())) continue;
            try {
                QuestDefinition pinned = CollectionRunDefinitions.resolve(player.getServer(), runtime, live);
                selectedRuns.put(id, pinned);
                selectedRuntimes.put(id, runtime);
                String runId = runtime.getCollectionData() == null ? "" : runtime.getCollectionData().getRunId();
                if (runId == null || runId.isBlank()) runId = runtime.getAcceptedAtRealMs() + ":" + runtime.getAcceptedAtTick();
                definitionScopes.put(id, runId + "/" + runtime.getFrozenDefinitionHash());
            } catch (RuntimeException exception) {
                // Omit only this quest; replacing its unavailable old rules with the live version is unsafe.
                selectedRuns.put(id, null); definitionScopes.put(id, "unavailable/" + runtime.getAcceptedAtRealMs());
                ArcQuestLog.warn(ArcQuestLog.Category.QUEST_NETWORK,
                        "Cannot restore collection presentation for quest '{}'; restore its original definition provider: {}", id, exception.getMessage());
            }
        }
        long revision = data.getCollectionRecords().getRevision();
        RecipientContent previous = recipients.get(player.getUUID());
        Map<ResourceLocation, CollectionEntryDefinition> registry = CollectionEntryRegistry.serverSnapshot();
        Set<String> flags = Set.copyOf(data.getAllFlags());
        Map<String, Integer> variables = Map.copyOf(data.getAllVariables());
        Set<String> entryRewardGrants = org.arcadia.arc_quest.quest.logic.CollectionEntryRewardService.disclosureKeys(data);
        boolean metadataChanged = previous == null || previous.transfer().epoch() != source.epoch()
                || previous.registryGeneration() != registry || !previous.knownQuests().equals(immutableKnown)
                || !previous.flags().equals(flags) || !previous.variables().equals(variables)
                || !previous.definitionScopes().equals(definitionScopes)
                || !previous.entryRewardGrants().equals(entryRewardGrants);
        if (!metadataChanged && previous.recordRevision() == revision) return previous;
        Map<ResourceLocation, DisclosureGrant> grants = metadataChanged ? new LinkedHashMap<>() : previous.grants();
        boolean authorizationChanged = metadataChanged;
        Map<ResourceLocation, List<CollectionEntryDefinition>> presentationEntries = new LinkedHashMap<>();
        registry.forEach((id, entry) -> presentationEntries.computeIfAbsent(id, ignored -> new ArrayList<>()).add(entry));
        for (QuestDefinition pinned : selectedRuns.values()) if (pinned != null && pinned.getCollectionConfig() != null) {
            for (CollectionEntryDefinition entry : pinned.getCollectionConfig().getEntries())
                presentationEntries.computeIfAbsent(entry.getEntryId(), ignored -> new ArrayList<>()).add(entry);
        }
        java.util.Collection<ResourceLocation> ids = metadataChanged || changedEntryIds == null
                ? presentationEntries.keySet() : changedEntryIds;
        for (ResourceLocation id : ids) {
            List<CollectionEntryDefinition> entries = presentationEntries.get(id);
            if (entries == null) continue;
            DisclosureGrant grant = DisclosureGrant.of(entries, data.getCollectionRecords());
            if (!grant.equals(grants.put(id, grant))) authorizationChanged = true;
        }
        if (!authorizationChanged) {
            RecipientContent unchanged = new RecipientContent(revision, immutableKnown, previous.snapshot(), previous.transfer(),
                    previous.wasSent(), registry, grants, flags, variables, entryRewardGrants, definitionScopes);
            recipients.put(player.getUUID(), unchanged);
            return unchanged;
        }
        try {
            DatapackContentSnapshot projected = CollectionContentDisclosure.project(source, data.getCollectionRecords(),
                    known::contains, id -> selectedRuns.containsKey(id) ? selectedRuns.get(id)
                            : QuestRegistry.getServerDefinition(net.minecraft.resources.ResourceLocation.tryParse(id)),
                    player, QuestRegistry.getAll(), selectedRuns, selectedRuntimes);
            DatapackContentTransfer transfer = previous != null && previous.snapshot().equals(projected)
                    ? previous.transfer() : DatapackContentCodec.encode(projected);
            RecipientContent content = new RecipientContent(revision, immutableKnown, projected, transfer,
                    previous != null && previous.wasSent() && previous.transfer() == transfer,
                    registry, grants, flags, variables, entryRewardGrants, definitionScopes);
            recipients.put(player.getUUID(), content);
            return content;
        } catch (Exception exception) {
            // Fail closed: never fall back to the shared unfiltered transfer after a projection error.
            ArcQuestLog.error(ArcQuestLog.Category.QUEST_NETWORK, "Cannot authorize collection content for {}", player.getUUID(), exception);
            recipients.remove(player.getUUID());
            return null;
        }
    }

    private record RecipientContent(long recordRevision, Set<String> knownQuests,
                                    DatapackContentSnapshot snapshot, DatapackContentTransfer transfer, boolean wasSent,
                                    Map<ResourceLocation, CollectionEntryDefinition> registryGeneration,
                                    Map<ResourceLocation, DisclosureGrant> grants, Set<String> flags, Map<String, Integer> variables,
                                    Set<String> entryRewardGrants, Map<String, String> definitionScopes) {
        RecipientContent sent() { return new RecipientContent(recordRevision, knownQuests, snapshot, transfer, true,
                registryGeneration, grants, flags, variables, entryRewardGrants, definitionScopes); }
    }

    public record DisclosureGrant(boolean discovered, Set<String> completedResearchSteps, Set<String> allowedBlocks) {
        public static DisclosureGrant of(Collection<CollectionEntryDefinition> entries, CollectionRecordState records) {
            Set<String> steps = new LinkedHashSet<>(), blocks = new LinkedHashSet<>();
            boolean discovered = false;
            int scope = 0;
            for (CollectionEntryDefinition entry : entries) {
                DisclosureGrant grant = of(entry, records);
                discovered |= grant.discovered();
                String prefix = scope++ + "/";
                grant.completedResearchSteps().forEach(step -> steps.add(prefix + step));
                grant.allowedBlocks().forEach(block -> blocks.add(prefix + block));
            }
            return new DisclosureGrant(discovered, Set.copyOf(steps), Set.copyOf(blocks));
        }
        public static DisclosureGrant of(CollectionEntryDefinition entry, CollectionRecordState records) {
            Set<String> steps = new LinkedHashSet<>(), blocks = new LinkedHashSet<>();
            entry.getResearchObjectives().forEach(objective -> {
                if (records.getProgress(entry.getEntryId(), CollectionProgressProjector.researchKey(objective.getObjectiveId()))
                        >= objective.getRequiredCount()) steps.add(objective.getObjectiveId());
            });
            entry.getContent().forEach(block -> { if (CollectionProgressProjector.contentRevealed(entry, block, records)) blocks.add(block.blockId()); });
            return new DisclosureGrant(records.isDiscovered(entry.getEntryId()), Set.copyOf(steps), Set.copyOf(blocks));
        }
    }

    private static void send(PacketDistributor.PacketTarget target, DatapackContentTransfer transfer) {
        byte[] payload = transfer.payloadView();
        int chunkCount = Math.max(1, (payload.length + CHUNK_BYTES - 1) / CHUNK_BYTES);
        ArcQuestNetwork.CHANNEL.send(target,
                new S2CDatapackContentStartPacket(transfer.epoch(), transfer.contentHash(), chunkCount,
                        payload.length, transfer.uncompressedBytes()));
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int start = chunkIndex * CHUNK_BYTES;
            int end = Math.min(payload.length, start + CHUNK_BYTES);
            byte[] chunk = Arrays.copyOfRange(payload, start, end);
            ArcQuestNetwork.CHANNEL.send(target,
                    new S2CDatapackContentChunkPacket(transfer.epoch(), transfer.contentHash(), chunkIndex, chunk));
        }
    }

    private static DatapackContentTransfer createEmptyTransfer() {
        try {
            return DatapackContentCodec.encode(DatapackContentSnapshot.empty(0L));
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
