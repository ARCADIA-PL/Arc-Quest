package org.arcadia.arc_quest.quest.logic;

import net.minecraft.nbt.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.questplayer.*;
import org.arcadia.arc_quest.questplayer.attachment.ArcQuestAttachments;
import org.arcadia.arc_quest.questplayer.persistence.PlayerNbtFiles;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Durable item mailbox. Inventory and delivery tokens commit together in vanilla player data. */
public final class CollectionRewardDelivery {
    private static final PlayerNbtFiles FILES = new PlayerNbtFiles(4 * 1024 * 1024, 8 * 1024 * 1024);
    private static final Map<Path, CompoundTag> LOADED = new HashMap<>();
    private static final Set<Path> DISABLED = new HashSet<>();
    private static final Map<Path, CompoundTag> RECOVERING = new HashMap<>();
    private static final int MAX_CHUNKS = 4096;
    private static final int AUTHORIZATION_FORMAT = 1;
    private CollectionRewardDelivery() { }

    /** Called only after the service verifies eligibility: this journal is the durable authorization decision. */
    public static void prepare(ServerPlayer player, ArcQuestPlayer data, ResourceLocation entryId, String questId,
                             String runId, String phaseId, String bindingId, CollectionEntryRewardDefinition reward) {
        if (player == null) return;
        Path path = path(player.server, player.getUUID());
        CompoundTag active = RECOVERING.get(path);
        CompoundTag mailbox = (active == null ? load(path) : active).copy(), grants = mailbox.getCompound("Grants");
        String identity = identity(entryId, data.getCollectionRecords().getGeneration(entryId), questId, runId, phaseId, bindingId, reward.rewardId());
        CompoundTag grant = grants.getCompound(identity);
        if (grant.isEmpty()) {
            grant.putInt("AuthorizationFormat", AUTHORIZATION_FORMAT);
            grant.putBoolean("ClaimAuthorized", true);
            grant.putBoolean("ClaimCheckpointed", false);
            grant.putString("EntryId", entryId.toString()); grant.putLong("Generation", data.getCollectionRecords().getGeneration(entryId));
            grant.putString("QuestId", questId); grant.putString("RunId", runId); grant.putString("PhaseId", phaseId);
            grant.putString("BindingId", bindingId); grant.putString("RewardId", reward.rewardId());
            QuestRuntimeData runtime = CollectionEntryRewardService.resolveRuntime(data, questId, runId);
            CompoundTag entitlement = runId == null || runId.isBlank() ? data.getCollectionRecords().getRecord(entryId).getRewardEntitlement(reward.rewardId())
                    : runtime == null ? new CompoundTag() : runtime.getCollectionData().getEntryRewardEntitlement(phaseId, bindingId, reward.rewardId());
            grant.put("Entitlement", entitlement.isEmpty() ? CollectionRewardEntitlement.capture(reward, questId,
                    runtime == null ? "" : runtime.getFrozenDefinitionHash(), phaseId, bindingId, entryId) : entitlement);
            grant.putBoolean("CallbacksAttempted", false);
            ListTag chunks = new ListTag();
            for (IReward part : reward.rewards()) if (part instanceof ItemReward item) {
                ItemStack prototype = new ItemStack(item.getItem());
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item.getItem());
                int remaining = item.getCount(), maximum = prototype.getMaxStackSize();
                if (prototype.isEmpty() || itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId) || maximum <= 0)
                    throw new IllegalArgumentException("Collection reward item is empty or unavailable: " + itemId);
                while (remaining > 0) {
                    if (chunks.size() >= MAX_CHUNKS) throw new IllegalStateException("Collection reward is too large for the delivery mailbox");
                    int amount = Math.min(remaining, maximum); remaining -= amount;
                    CompoundTag chunk = new CompoundTag(); chunk.putUUID("Token", UUID.randomUUID());
                    chunk.put("Stack", new ItemStack(item.getItem(), amount).save(player.registryAccess()));
                    chunks.add(chunk);
                }
            }
            grant.put("Chunks", chunks); grants.put(identity, grant); mailbox.put("Grants", grants);
            write(path, mailbox); // No item side effect is possible before this durable authorization.
            // Reward callbacks may prepare another grant; keep it in the outer recovery's working set.
            if (active != null) active.getCompound("Grants").put(identity, grant.copy());
        }
    }

    public static void grant(ServerPlayer player, ArcQuestPlayer data, ResourceLocation entryId, String questId,
                             String runId, String phaseId, String bindingId, CollectionEntryRewardDefinition reward) {
        if (player == null) return;
        prepare(player, data, entryId, questId, runId, phaseId, bindingId, reward);
        Path path = path(player.server, player.getUUID());
        CompoundTag mailbox = RECOVERING.getOrDefault(path, load(path));
        CompoundTag grant = mailbox.getCompound("Grants").getCompound(identity(entryId, data.getCollectionRecords().getGeneration(entryId),
                questId, runId, phaseId, bindingId, reward.rewardId()));
        markPending(data, grant, true);
        ArcQuestPlayerManager.persistSnapshotSynchronously(player, data);
        grant.putBoolean("ClaimCheckpointed", true);
        write(path, mailbox);
        recover(player, data);
    }

    public static void recover(ServerPlayer player, ArcQuestPlayer data) {
        Path path = path(player.server, player.getUUID());
        if (!Files.exists(path) && !LOADED.containsKey(path)) return;
        if (RECOVERING.containsKey(path)) return;
        CompoundTag mailbox = load(path).copy();
        RECOVERING.put(path, mailbox);
        try { recover(player, data, path, mailbox); }
        finally { RECOVERING.remove(path); }
    }

    private static void recover(ServerPlayer player, ArcQuestPlayer data, Path path, CompoundTag mailbox) {
        CompoundTag grants = mailbox.getCompound("Grants");
        var capability = player.getData(ArcQuestAttachments.PLAYER_DATA);
        capability.retainCollectionDeliveryReceipts(unresolvedTokens(grants));
        Set<UUID> delivered = new HashSet<>();
        boolean changed = false;
        for (String identity : List.copyOf(grants.getAllKeys())) {
            CompoundTag grant = grants.getCompound(identity);
            boolean restoredClaim = false;
            if (!authorized(data, grant)) {
                if (!restoreAuthorizedClaim(data, grant)) { grants.remove(identity); changed = true; continue; }
                restoredClaim = true;
            }
            if (restoredClaim || isDurableAuthorization(grant) && !grant.getBoolean("ClaimCheckpointed")) {
                // A crash may lose the first claim checkpoint. Rebuild only the originally authorized receipt.
                markPending(data, grant, true);
                ArcQuestPlayerManager.persistSnapshotSynchronously(player, data);
                grant.putBoolean("ClaimCheckpointed", true); write(path, mailbox);
            }
            if (!grant.getBoolean("CallbacksAttempted") && !grant.getBoolean("CallbacksReview")) {
                CollectionEntryRewardDefinition definition = null;
                try { definition = CollectionRewardEntitlement.restore(grant.getCompound("Entitlement"), CollectionRunDefinitionStore.get(player.server)); }
                catch (RuntimeException unavailable) {
                    grant.putBoolean("CallbacksReview", true); write(path, mailbox);
                    ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE, "Collection callback provider unavailable; items remain recoverable for {}", grant.getString("RewardId"), unavailable);
                }
                if (definition != null) {
                    grant.putBoolean("CallbacksAttempted", true); write(path, mailbox);
                    var restored = definition;
                    QuestEventSettlement.runReward(player, () -> {
                        for (IReward part : restored.rewards()) if (!(part instanceof ItemReward)) {
                            try { part.grant(player); }
                            catch (RuntimeException failure) {
                                ArcQuestLog.error(ArcQuestLog.Category.QUEST_PROGRESS, "Collection callback failed; automatic replay disabled: {}", restored.rewardId(), failure);
                            }
                        }
                    });
                }
            }
            // A callback may reset this investigation or its permanent record before item delivery.
            if (!authorized(data, grant)) { grants.remove(identity); changed = true; continue; }
            ListTag chunks = grant.getList("Chunks", Tag.TAG_COMPOUND);
            for (int i = 0; i < chunks.size(); i++) {
                CompoundTag chunk = chunks.getCompound(i);
                if (chunk.getBoolean("Done")) continue;
                UUID token = chunk.getUUID("Token");
                if (capability.hasDeliveredCollection(token)) { delivered.add(token); continue; }
                ItemStack stack = ItemStack.parseOptional(player.registryAccess(), chunk.getCompound("Stack"));
                if (stack.isEmpty()) throw new IllegalStateException("Pending collection reward item is missing");
                if (!fits(player.getInventory().items, stack)) continue;
                if (!capability.canRecordDeliveredCollection(token)) continue;
                QuestEventSettlement.runReward(player, () -> {
                    if (!player.getInventory().add(stack) || !stack.isEmpty())
                        throw new IllegalStateException("Reserved collection inventory capacity changed");
                    capability.recordDeliveredCollection(token);
                });
                delivered.add(token);
            }
        }
        if (!delivered.isEmpty()) {
            // Saving the receipt in the same player file as the inventory distinguishes replay from recovery.
            ArcQuestPlayerManager.persistSnapshotSynchronously(player, data);
            var storage = (org.arcadia.arc_quest.mixin.PlayerListSaveInvoker) player.server.getPlayerList();
            // Server-side GameTest players have no connection; PlayerList deliberately skips their save.
            if (player.connection == null) storage.arcq$playerStorage().save(player);
            else storage.arcq$savePlayer(player);
            CompoundTag saved;
            try { saved = FILES.read(player.server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(player.getUUID() + ".dat")); }
            catch (IOException failure) { DISABLED.add(path); throw new IllegalStateException("Cannot verify collection reward inventory save", failure); }
            CompoundTag receipts = saved.getCompound("neoforge:attachments").getCompound("arc_quest:player_data").getCompound("DeliveredCollectionReceipts");
            for (UUID token : delivered) if (!receipts.getBoolean(token.toString())) {
                DISABLED.add(path); throw new IllegalStateException("Collection inventory save lacks its delivery receipt");
            }
            for (String identity : grants.getAllKeys()) for (Tag part : grants.getCompound(identity).getList("Chunks", Tag.TAG_COMPOUND)) {
                CompoundTag chunk = (CompoundTag) part;
                if (delivered.contains(chunk.getUUID("Token"))) { chunk.putBoolean("Done", true); changed = true; }
            }
        }
        for (String identity : List.copyOf(grants.getAllKeys())) {
            CompoundTag grant = grants.getCompound(identity);
            if (grant.getBoolean("CallbacksAttempted") && !grant.getBoolean("CallbacksReview")
                    && grant.getList("Chunks", Tag.TAG_COMPOUND).stream().allMatch(part -> ((CompoundTag) part).getBoolean("Done"))) {
                markPending(data, grant, false); grants.remove(identity); changed = true;
            }
        }
        if (changed) {
            // Keep the old journal recoverable until the cleared pending flags have a durable progress snapshot.
            ArcQuestPlayerManager.persistSnapshotSynchronously(player, data);
            mailbox.put("Grants", grants); write(path, mailbox);
        }
        // Remove tokens only after the journal acknowledges the matching durable inventory commit.
        delivered.forEach(capability::acknowledgeDeliveredCollection);
        capability.retainCollectionDeliveryReceipts(unresolvedTokens(grants));
    }

    private static Set<UUID> unresolvedTokens(CompoundTag grants) {
        Set<UUID> tokens = new HashSet<>();
        for (String identity : grants.getAllKeys()) for (Tag part : grants.getCompound(identity).getList("Chunks", Tag.TAG_COMPOUND)) {
            CompoundTag chunk = (CompoundTag) part;
            if (!chunk.getBoolean("Done")) tokens.add(chunk.getUUID("Token"));
        }
        return tokens;
    }

    static boolean fits(List<ItemStack> inventory, ItemStack reward) {
        long capacity = 0;
        for (ItemStack slot : inventory) {
            if (slot.isEmpty()) capacity += reward.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(slot, reward)) capacity += Math.max(0, Math.min(slot.getMaxStackSize(), 64) - slot.getCount());
            if (capacity >= reward.getCount()) return true;
        }
        return false;
    }
    static String identity(ResourceLocation entry, long generation, String quest, String run, String phase, String binding, String reward) {
        String canonical = run == null || run.isBlank() ? "entry/" + entry + "/" + generation + "/" + reward
                : "run/" + quest + "/" + run + "/" + phase + "/" + binding + "/" + reward;
        return UUID.nameUUIDFromBytes(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
    private static boolean authorized(ArcQuestPlayer data, CompoundTag grant) {
        ResourceLocation entry = ResourceLocation.parse(grant.getString("EntryId"));
        if (grant.getString("RunId").isBlank()) return data.getCollectionRecords().getGeneration(entry) == grant.getLong("Generation")
                && data.getCollectionRecords().isRewardClaimed(entry, grant.getString("RewardId"));
        QuestRuntimeData runtime = CollectionEntryRewardService.resolveRuntime(data, grant.getString("QuestId"), grant.getString("RunId"));
        return runtime != null && runtime.getCollectionData().isEntryRewardClaimed(grant.getString("PhaseId"), grant.getString("BindingId"), grant.getString("RewardId"));
    }
    private static boolean isDurableAuthorization(CompoundTag grant) {
        return grant.getInt("AuthorizationFormat") == AUTHORIZATION_FORMAT && grant.getBoolean("ClaimAuthorized");
    }
    /** An old unmarked journal or a reset generation/run must never fabricate a consumed claim. */
    static boolean restoreAuthorizedClaim(ArcQuestPlayer data, CompoundTag grant) {
        if (!isDurableAuthorization(grant)) return false;
        ResourceLocation entry = ResourceLocation.tryParse(grant.getString("EntryId"));
        String reward = grant.getString("RewardId"), runId = grant.getString("RunId");
        CompoundTag entitlement = grant.getCompound("Entitlement");
        if (entry == null || reward.isBlank() || reward.length() > 128 || entitlement.isEmpty()
                || data.getCollectionRecords().getGeneration(entry) != grant.getLong("Generation")
                || !entry.toString().equals(entitlement.getString("EntryId")) || !reward.equals(entitlement.getString("RewardId"))) return false;
        if (runId.isBlank()) {
            data.getCollectionRecords().snapshotReward(entry, reward, entitlement);
            data.getCollectionRecords().unlockReward(entry, reward);
            data.getCollectionRecords().claimReward(entry, reward);
            markPending(data, grant, true);
            return true;
        }
        QuestRuntimeData runtime = CollectionEntryRewardService.resolveRuntime(data, grant.getString("QuestId"), runId);
        String phase = grant.getString("PhaseId"), binding = grant.getString("BindingId");
        if (runtime == null || !runtime.getCollectionData().getFrozenBindingIds(phase).contains(binding)
                || !runtime.getQuestId().equals(entitlement.getString("QuestId"))
                || !phase.equals(entitlement.getString("PhaseId")) || !binding.equals(entitlement.getString("BindingId"))
                || !runtime.getFrozenDefinitionHash().equals(entitlement.getString("DefinitionHash"))) return false;
        runtime.getCollectionData().snapshotEntryReward(phase, binding, reward, entitlement);
        runtime.getCollectionData().unlockEntryReward(phase, binding, reward);
        runtime.getCollectionData().claimEntryReward(phase, binding, reward);
        markPending(data, grant, true);
        return true;
    }
    private static void markPending(ArcQuestPlayer data, CompoundTag grant, boolean pending) {
        if (grant.getString("RunId").isBlank()) {
            data.getCollectionRecords().setRewardDeliveryPending(ResourceLocation.parse(grant.getString("EntryId")), grant.getString("RewardId"), pending);
            return;
        }
        QuestRuntimeData runtime = CollectionEntryRewardService.resolveRuntime(data, grant.getString("QuestId"), grant.getString("RunId"));
        if (runtime != null) runtime.getCollectionData().setEntryRewardDeliveryPending(grant.getString("PhaseId"), grant.getString("BindingId"), grant.getString("RewardId"), pending);
    }
    private static Path path(MinecraftServer server, UUID player) { return server.getWorldPath(LevelResource.ROOT).resolve("data/arc_quest/collection_deliveries").resolve(player + ".dat").toAbsolutePath().normalize(); }
    private static CompoundTag load(Path path) {
        if (DISABLED.contains(path)) throw new IllegalStateException("Collection delivery journal needs storage repair: " + path);
        return LOADED.computeIfAbsent(path, key -> {
            try { return Files.exists(key) ? FILES.read(key) : new CompoundTag(); }
            catch (IOException failure) { DISABLED.add(key); throw new IllegalStateException("Cannot load collection delivery journal", failure); }
        });
    }
    private static void write(Path path, CompoundTag mailbox) {
        try { FILES.write(path, mailbox); LOADED.put(path, mailbox.copy()); }
        catch (IOException failure) { DISABLED.add(path); throw new IllegalStateException("Collection delivery journal write failed; delivery paused", failure); }
    }
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Path path = path(server, player.getUUID());
            if (DISABLED.contains(path)) continue;
            CompoundTag mailbox = LOADED.get(path);
            if (mailbox == null || mailbox.getCompound("Grants").isEmpty()) continue;
            try { recover(player, ArcQuestPlayerManager.getOrCreate(player)); }
            catch (RuntimeException failure) { DISABLED.add(path); ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE, "Collection reward delivery paused for {}", player.getUUID(), failure); }
        }
    }
    public static void clearRuntime() { LOADED.clear(); DISABLED.clear(); RECOVERING.clear(); }
}
