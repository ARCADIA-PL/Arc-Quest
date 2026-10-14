package org.arcadia.arc_quest.testing;

import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.*;
import org.arcadia.arc_quest.dialogue.api.*;
import org.arcadia.arc_quest.dialogue.registry.DialogueActionTypes;
import org.arcadia.arc_quest.dialogue.runtime.DialogueSession;
import org.arcadia.arc_quest.questplayer.*;
import org.arcadia.arc_quest.questplayer.capability.*;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.arcadia.arc_quest.trade.runtime.TradeTransactionCoordinator;
import java.util.*;

/** Real Forge capability dispatch and native transactions; actor fixtures are not connected clients. */
@GameTestHolder("arc_quest")
@PrefixGameTestTemplate(false)
public final class FrameworkLifecycleGameTests {
    @GameTest(template="empty", batch="arc_quest.capability", timeoutTicks=100)
    public static void invalidatedHandleCannotReviveButDispatcherCanQuerySameData(GameTestHelper helper) {
        var player = actor(helper);
        try {
            var old = player.getCapability(ArcQuestCapabilities.PLAYER_DATA);
            var data = old.resolve().orElseThrow();
            var snapshot = new CompoundTag(); snapshot.putString("proof", "retained");
            data.replaceSnapshot(snapshot);
            player.invalidateCaps();
            helper.assertTrue(!old.isPresent(), "Previously retained capability survived entity invalidation");
            player.reviveCaps();
            var next = player.getCapability(ArcQuestCapabilities.PLAYER_DATA).resolve().orElseThrow();
            helper.assertTrue(next == data && next.snapshot().getString("proof").equals("retained"),
                    "Entity revival lost or replaced its capability data");
            helper.assertTrue(!old.isPresent(), "Revival made a stale handle usable");
            helper.succeed();
        } finally { release(player); }
    }

    @GameTest(template="empty", batch="arc_quest.dialogue", timeoutTicks=100)
    public static void customFailureEndsNativeSessionBeforeRewardAndTransition(GameTestHelper helper) {
        var player = actor(helper);
        try {
            var type = new ResourceLocation("arc_quest", "qa_rejected_action");
            DialogueActionTypes.register(type, (actor, session, tag) -> { throw new IllegalStateException("QA rejected action"); });
            var text = DialogueText.literal("QA");
            var choice = new DialogueChoice("choose", text, "next", List.of(), List.of(
                    new DialogueAction.Custom(type, new CompoundTag()),
                    new DialogueAction.SetFlag("arc_quest:qa_must_not_be_awarded")),
                    true, 0, CooldownType.NONE, 0, 0, null, null, List.of());
            var root = new DialogueNode("root", text, text, Map.of(), List.of(choice), null, 0);
            var next = new DialogueNode("next", text, text, Map.of(), List.of(), null, 0);
            var tree = new DialogueTree("arc_quest:qa_failure", text, "root", Map.of("root", root, "next", next), null);
            var session = new DialogueSession(player, tree);
            session.choose(0);
            helper.assertTrue(session.isEnded(), "Failed custom action left the native session open");
            helper.assertTrue(session.getCurrentNode().nodeId().equals("root"), "Failed custom action advanced the node");
            helper.assertTrue(!ArcQuestPlayerManager.getOrCreate(player).hasFlag("arc_quest:qa_must_not_be_awarded"),
                    "Failed custom action ran a later reward");
            helper.succeed();
        } finally { release(player); }
    }

    @GameTest(template="empty", batch="arc_quest.trade", timeoutTicks=100)
    public static void nativeItemRewardRejectsFullCreativeInventoryAndRollsBackAllCosts(GameTestHelper helper) {
        var player = actor(helper);
        try {
            player.setGameMode(GameType.CREATIVE);
            for (int slot=0; slot<player.getInventory().items.size(); slot++)
                player.getInventory().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
            player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 2));
            var template = new ItemStack(Items.IRON_SWORD);
            template.getOrCreateTag().putString("origin", "framework_trade");
            var reward = new ItemTradeOffer(template, 1, false, null);
            var coordinator = new TradeTransactionCoordinator();
            var inventoryBefore = player.getInventory().save(new net.minecraft.nbt.ListTag());
            boolean dialogueRejected = false;
            try { new DialogueAction.GiveItem("minecraft:iron_sword", 1).execute(player); }
            catch (IllegalStateException expected) { dialogueRejected = true; }
            helper.assertTrue(dialogueRejected && inventoryBefore.equals(player.getInventory().save(new net.minecraft.nbt.ListTag())),
                    "Native dialogue silently consumed or partially delivered a full-inventory reward");
            var costs = List.<org.arcadia.arc_quest.trade.api.ITradeOffer>of(ItemTradeOffer.cost(Items.EMERALD, 1));
            var failed = coordinator.execute(player, costs, List.of(reward));
            helper.assertTrue(!failed.succeeded() && failed.fullyReversible() && failed.rollbackSucceeded(),
                    "Rejected delivery did not roll back the native transaction");
            helper.assertTrue(inventoryBefore.equals(player.getInventory().save(new net.minecraft.nbt.ListTag())),
                    "Failed transaction changed inventory");
            player.getInventory().setItem(1, ItemStack.EMPTY);
            var retried = coordinator.execute(player, costs, List.of(reward));
            helper.assertTrue(retried.succeeded() && player.getInventory().countItem(Items.EMERALD)==1
                    && player.getInventory().countItem(Items.IRON_SWORD)==1, "Retry did not charge and deliver exactly once");
            helper.assertTrue(player.getInventory().getItem(1).getTag().getString("origin").equals("framework_trade"),
                    "Reward lost its configured NBT");
            helper.succeed();
        } finally { release(player); }
    }

    private static ServerPlayer actor(GameTestHelper helper) {
        var level=helper.getLevel();
        var player=new ServerPlayer(level.getServer(),level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ArcQRegression"));
        player.connection=new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),player);
        ArcQuestPlayerManager.getOrCreate(player);
        return player;
    }

    private static void release(ServerPlayer player) {
        ArcQuestPlayerManager.unload(player.getUUID());
        PlayerSessionEpochManager.endSession(player.getUUID());
        player.invalidateCaps();
    }
}
