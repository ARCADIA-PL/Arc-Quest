package org.arcadia.arc_quest.quest.logic;

import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.questplayer.*;
import org.arcadia.arc_quest.questplayer.capability.ArcQuestCapabilities;
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
    private static final int MAX_CHUNKS = 4096;
    private CollectionRewardDelivery() { }

    public static void grant(ServerPlayer player, ArcQuestPlayer data, ResourceLocation entryId, String questId,
                             String runId, String phaseId, String bindingId, CollectionEntryRewardDefinition reward) {
        if (player == null) return;
        Path path = path(player.server, player.getUUID());
        CompoundTag mailbox = load(path), grants = mailbox.getCompound("Grants");
        String identity = identity(entryId, data.getCollectionRecords().getGeneration(entryId), questId, runId, phaseId, bindingId, reward.rewardId());
        CompoundTag grant = grants.getCompound(identity);
        if (grant.isEmpty()) {
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
                int remaining = item.getCount(), maximum = new ItemStack(item.getItem()).getMaxStackSize();
                while (remaining > 0) {
                    if (chunks.size() >= MAX_CHUNKS) throw new IllegalStateException("Collection reward is too large for the delivery mailbox");
                    int amount = Math.min(remaining, maximum); remaining -= amount;
                    CompoundTag chunk = new CompoundTag(); chunk.putUUID("Token", UUID.randomUUID());
                    chunk.put("Stack", new ItemStack(item.getItem(), amount).save(new CompoundTag()));
                    chunks.add(chunk);
                }
            }
            grant.put("Chunks", chunks); grants.put(identity, grant); mailbox.put("Grants", grants);
            write(path, mailbox); // No item side effect is possible before this durable authorization.
        }
        markPending(data, grant, true);
        ArcQuestPlayerManager.persistSnapshotSynchronously(player, data);
        recover(player, data);
    }

    public static void recover(ServerPlayer player, ArcQuestPlayer data) {
        Path path = path(player.server, player.getUUID());
        if (!Files.exists(path) && !LOADED.containsKey(path)) return;
        CompoundTag mailbox = load(path), grants = mailbox.getCompound("Grants");
        var capability = player.getCapability(ArcQuestCapabilities.PLAYER_DATA).resolve()
                .orElseThrow(() -> new IllegalStateException("Missing collection delivery capability"));
        Set<UUID> delivered = new HashSet<>();
        boolean changed = false;
        for (String identity : List.copyOf(grants.getAllKeys())) {
            CompoundTag grant = grants.getCompound(identity);
            if (!authorized(data, grant)) { grants.remove(identity); changed = true; continue; }
            if (!grant.getBoolean("CallbacksAttempted")) {
                var definition = CollectionRewardEntitlement.restore(grant.getCompound("Entitlement"), CollectionRunDefinitionStore.get(player.server));
                grant.putBoolean("CallbacksAttempted", true); write(path, mailbox);
                QuestEventSettlement.runReward(player, () -> {
                    for (IReward part : definition.rewards()) if (!(part instanceof ItemReward)) {
                        try { part.grant(player); }
                        catch (RuntimeException failure) {
                            ArcQuestLog.error(ArcQuestLog.Category.QUEST_PROGRESS, "Collection callback failed; automatic replay disabled: {}", definition.rewardId(), failure);
                        }
                    }
                });
            }
            ListTag chunks = grant.getList("Chunks", Tag.TAG_COMPOUND);
            for (int i = 0; i < chunks.size(); i++) {
                CompoundTag chunk = chunks.getCompound(i);
                if (chunk.getBoolean("Done")) continue;
                UUID token = chunk.getUUID("Token");
                if (capability.hasDeliveredCollection(token)) { delivered.add(token); continue; }
                ItemStack stack = ItemStack.of(chunk.getCompound("Stack"));
                if (stack.isEmpty()) throw new IllegalStateException("Pending collection reward item is missing");
                if (!fits(player.getInventory().items, stack)) continue;
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
            CompoundTag receipts = saved.getCompound("ForgeCaps").getCompound("arc_quest:player_data").getCompound("DeliveredCollectionReceipts");
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
            if (grant.getList("Chunks", Tag.TAG_COMPOUND).stream().allMatch(part -> ((CompoundTag) part).getBoolean("Done"))) {
                markPending(data, grant, false); grants.remove(identity); changed = true;
            }
        }
        if (changed) { mailbox.put("Grants", grants); write(path, mailbox); ArcQuestPlayerManager.persistSnapshotSynchronously(player, data); }
        // Remove tokens only after the journal acknowledges the matching durable inventory commit.
        delivered.forEach(capability::acknowledgeDeliveredCollection);
    }

    static boolean fits(List<ItemStack> inventory, ItemStack reward) {
        long capacity = 0;
        for (ItemStack slot : inventory) {
            if (slot.isEmpty()) capacity += reward.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(slot, reward)) capacity += Math.max(0, Math.min(slot.getMaxStackSize(), 64) - slot.getCount());
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
        try { FILES.write(path, mailbox); }
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
    public static void clearRuntime() { LOADED.clear(); DISABLED.clear(); }
}
