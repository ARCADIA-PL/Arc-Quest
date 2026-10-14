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
    @GameTest(template="empty", batch="arc_quest.objectives", timeoutTicks=100)
    public static void registeredAddonSignalsMatchExactTypeStayPersonalAndFreezeRecipients(GameTestHelper helper) {
        var typeA=org.arcadia.arc_quest.quest.registry.ObjectiveTypeRegistry.register(ResourceLocation.parse("arcq_qa:signal_a"),
                org.arcadia.arc_quest.quest.api.ObjectiveTypeDefinition.builder().counting(true).build());
        var typeB=org.arcadia.arc_quest.quest.registry.ObjectiveTypeRegistry.register(ResourceLocation.parse("arcq_qa:signal_b"),
                org.arcadia.arc_quest.quest.api.ObjectiveTypeDefinition.builder().counting(true).build());
        var target=ResourceLocation.parse("arcq_qa:shared_target");
        var quest=org.arcadia.arc_quest.quest.builder.QuestBuilder.create("arcq_qa:typed_signals")
                .setInitialPhase("first")
                .phase(org.arcadia.arc_quest.quest.builder.PhaseBuilder.create("first")
                        .objective(org.arcadia.arc_quest.quest.builder.ObjectiveBuilder.ofType(typeA,target,1))
                        .thenGoTo("second"))
                .phase(org.arcadia.arc_quest.quest.builder.PhaseBuilder.create("second")
                        .objective(org.arcadia.arc_quest.quest.builder.ObjectiveBuilder.ofType(typeA,target,1))
                        .objective(org.arcadia.arc_quest.quest.builder.ObjectiveBuilder.ofType(typeB,target,1))).build();
        var previous=org.arcadia.arc_quest.quest.registry.QuestRegistry.getDatapackSnapshot();
        var fixtures=new LinkedHashMap<>(previous);
        fixtures.put(quest.getId(),quest);
        org.arcadia.arc_quest.quest.registry.QuestRegistry.replaceDatapackSnapshot(fixtures);
        var a=actor(helper);var b=actor(helper);
        try {
            org.arcadia.arc_quest.quest.logic.QuestProgressHandler.acceptQuest(a,quest.getId().toString());
            org.arcadia.arc_quest.quest.logic.QuestProgressHandler.acceptQuest(b,quest.getId().toString());
            var qa=ArcQuestPlayerManager.getOrCreate(a).getActiveQuest(quest.getId().toString());
            var qb=ArcQuestPlayerManager.getOrCreate(b).getActiveQuest(quest.getId().toString());
            org.arcadia.arc_quest.quest.tracking.QuestEventManager.notifyCustom(a,target);
            helper.assertTrue(qa.isPhaseActive("first"),"CUSTOM signal consumed a registered addon type");
            org.arcadia.arc_quest.quest.tracking.QuestEventManager.notifyObjective(a,typeA,target,1);
            helper.assertTrue(qa.isPhaseActive("second")&&qa.getObjectiveProgress("second",0)==0,"One typed event leaked into its newly activated successor");
            helper.assertTrue(qb.isPhaseActive("first")&&qb.getObjectiveProgress("first",0)==0,"Typed event advanced another player");
            org.arcadia.arc_quest.quest.tracking.QuestEventManager.notifyObjective(a,typeB,target,1);
            helper.assertTrue(qa.getObjectiveProgress("second",0)==0&&qa.getObjectiveProgress("second",1)==1,"Type B consumed Type A with the same target");
            helper.succeed();
        } finally {
            release(a);release(b);
            org.arcadia.arc_quest.quest.registry.QuestRegistry.replaceDatapackSnapshot(previous);
        }
    }
    @GameTest(template="empty", batch="arc_quest.markers", timeoutTicks=100)
    public static void entityMarkerDefaultsUseTranslationKeysAndPreserveExplicitLabels(GameTestHelper helper) {
        var player = actor(helper);
        var level = helper.getLevel();
        var pig = net.minecraft.world.entity.EntityType.PIG.create(level);
        try {
            Objects.requireNonNull(pig).moveTo(helper.absolutePos(new net.minecraft.core.BlockPos(2, 2, 2)), 0, 0);
            pig.setNoAi(true); pig.setNoGravity(true); level.addFreshEntity(pig); player.setPos(pig.position());
            var target = new org.arcadia.arc_quest.questmarker.api.MarkableObject.EntityByUuid(pig.getUUID());
            var automatic = org.arcadia.arc_quest.questmarker.api.MarkSpec.of("regression_entity_name", target);
            var resolved = org.arcadia.arc_quest.questmarker.runtime.QuestMarkerTargetService.resolve(
                    automatic.id(), "regression", null, -1, automatic, player, level);
            helper.assertTrue(resolved != null && "entity.minecraft.pig".equals(resolved.getStyleHints().get("labelKey")),
                    "Native entity target lost its client-side translation key");
            helper.assertTrue(resolved.hasEntityBinding() && resolved.getFollowEntityUuid().equals(pig.getUUID().toString()),
                    "Naming changed the entity binding");
            var translated = org.arcadia.arc_quest.questmarker.api.MarkSpec.translated(
                    "regression_explicit_name", "marker.authored.name", target);
            var explicit = org.arcadia.arc_quest.questmarker.runtime.QuestMarkerTargetService.resolve(
                    translated.id(), "regression", null, -1, translated, player, level);
            helper.assertTrue(explicit != null && "marker.authored.name".equals(explicit.getStyleHints().get("labelKey")),
                    "Entity defaults overrode the authored translation key");
            helper.succeed();
        } finally { if (pig != null) pig.discard(); release(player); }
    }

    @GameTest(template="empty", batch="arc_quest.dialogue", timeoutTicks=100)
    public static void oneTimeTreeSurvivesCloseUntilSuccessfulFinalAction(GameTestHelper helper) {
        var player = actor(helper);
        var manager = org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager.INSTANCE;
        try {
            var failNext = new java.util.concurrent.atomic.AtomicBoolean(true);
            var type = new ResourceLocation("arc_quest", "qa_once_retry");
            DialogueActionTypes.register(type, (actor, session, tag) -> {
                if (failNext.getAndSet(false)) throw new IllegalStateException("QA rejected first commit");
            });
            var tree = org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder.create("arc_quest:qa_one_time_esc")
                    .oneTime().node("intro").say("Intro", "arc_quest:qa_intro").autoNext("commit")
                    .node("commit").say("Commit", "arc_quest:qa_commit")
                    .choice("arc_quest:qa_finish", DialogueText.literal("OK"), choice -> choice
                            .action(new DialogueAction.Custom(type, new CompoundTag()))
                            .action(new DialogueAction.SetFlag("arc_quest:qa_one_time_finished")).close()).build();
            var data = ArcQuestPlayerManager.getOrCreate(player);
            for (int depth = 0; depth < 2; depth++) {
                var session = manager.startDialogue(player, tree);
                helper.assertTrue(session != null, "One-time tree could not reopen");
                if (depth == 1) manager.handleAutoAdvance(player);
                manager.handleClose(player, session.getSessionId(), session.getRevision(), session.getPlayerSessionEpoch());
                helper.assertTrue(!data.hasFlag("arc_quest:qa_one_time_finished"), "Close executed key event");
                data.getDialogueProgress().deserialize(data.getDialogueProgress().serialize());
                helper.assertTrue(!data.getDialogueProgress().hasCompletedDialogue(tree.dialogueId(), tree.dialogueId()), "Close persisted completion");
            }
            manager.startDialogue(player, tree);
            manager.handleAutoAdvance(player);
            manager.handleChoice(player, 0);
            helper.assertTrue(!data.getDialogueProgress().hasCompletedDialogue(tree.dialogueId(), tree.dialogueId()), "Failed final action consumed one-time tree");
            helper.assertTrue(manager.startDialogue(player, tree) != null, "Failed final action could not retry");
            manager.handleAutoAdvance(player);
            manager.handleChoice(player, 0);
            helper.assertTrue(data.hasFlag("arc_quest:qa_one_time_finished"), "Final action missing");
            helper.assertTrue(manager.startDialogue(player, tree) == null, "Completed one-time tree replayed");
            helper.succeed();
        } finally { manager.endDialogue(player); release(player); }
    }

    @GameTest(template="empty", batch="arc_quest.dialogue", timeoutTicks=100)
    public static void automaticTransitionsCheckDestinationGateInsteadOfCurrentVisitedNode(GameTestHelper helper) {
        var player = actor(helper);
        try {
            var tree = org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder.create("arc_quest:qa_auto_gate")
                    .node("start").say("Start", "arc_quest:qa_start").autoNext("once")
                    .node("once").nodeOneTime().say("Once", "arc_quest:qa_once").autoNext("blocked")
                    .node("blocked").nodeOneTime().say("Blocked", "arc_quest:qa_blocked")
                    .autoNext("finish")
                    .node("finish").say("Finish", "arc_quest:qa_finish").build();
            var session = new DialogueSession(player, tree);
            session.autoAdvance();
            helper.assertTrue(session.getCurrentNode().nodeId().equals("once"), "Could not enter first one-time node");
            session.autoAdvance();
            helper.assertTrue(session.getCurrentNode().nodeId().equals("blocked"), "Visited current node prevented leaving it");
            session.end();
            var retry = new DialogueSession(player, tree);
            retry.autoAdvance();
            helper.assertTrue(retry.getCurrentNode().nodeId().equals("start") && !retry.isEnded(), "Auto transition entered an already visited one-time destination");
            helper.assertTrue(!ArcQuestPlayerManager.getOrCreate(player).getDialogueProgress()
                    .hasCompletedDialogue(tree.dialogueId(), tree.dialogueId()), "Blocked transition consumed the tree");
            var terminalTree = org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder.create("arc_quest:qa_auto_terminal")
                    .oneTime().node("intro").say("Intro", "arc_quest:qa_terminal_intro").autoNext("terminal")
                    .node("terminal").say("Terminal", "arc_quest:qa_terminal").build();
            var manager = org.arcadia.arc_quest.dialogue.runtime.DialogueSessionManager.INSTANCE;
            var managed = manager.startDialogue(player, terminalTree);
            manager.handleAutoAdvance(player);
            helper.assertTrue(managed.getCurrentNode().isTerminal() && !managed.isEnded(), "Terminal text did not remain visible");
            manager.handleClose(player, managed.getSessionId(), managed.getRevision(), managed.getPlayerSessionEpoch());
            helper.assertTrue(manager.startDialogue(player, terminalTree) == null, "Acknowledged automatic terminal tree replayed");
            helper.succeed();
        } finally { release(player); }
    }

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

    @GameTest(template="empty", batch="arc_quest.dialogue", timeoutTicks=100)
    public static void builderConditionalTextMatchesTypedConditionsForEachPlayer(GameTestHelper helper) {
        var player=actor(helper);var other=actor(helper);
        try {
            var condition=new DialogueCondition.Any(List.of(
                    new DialogueCondition.All(List.of(new DialogueCondition.HasFlag("qa:a"),
                            new DialogueCondition.Not(new DialogueCondition.HasFlag("qa:b")))),
                    new DialogueCondition.HasFlag("qa:c")));
            var builder=org.arcadia.arc_quest.dialogue.builder.DialogueTreeBuilder.create("arc_quest:qa_branches");
            builder.node("root").say(DialogueText.literal("fallback"),"arc_quest:qa_fallback")
                    .sayIf(condition,DialogueText.literal("branch"),100,"arc_quest:qa_branch")
                    .sayIf(DialogueCondition.HoldItem.inInventory("minecraft:emerald",2),
                            DialogueText.literal("inventory"),200,"arc_quest:qa_inventory");
            var node=builder.build().nodes().get("root");
            var data=ArcQuestPlayerManager.getOrCreate(player);
            var context=org.arcadia.arc_quest.dialogue.runtime.DialogueEvalContext.of(player,null,
                    "qa",data.getDialogueProgress());
            for(int mask=0;mask<8;mask++) {
                for(int bit=0;bit<3;bit++) {
                    String flag="qa:"+(char)('a'+bit);
                    if((mask&(1<<bit))!=0)data.setFlag(flag);else data.removeFlag(flag);
                }
                String actual=org.arcadia.arc_quest.dialogue.runtime.ConditionalTextEvaluator.evaluate(context,
                        node.conditionalTexts(),"fallback");
                helper.assertTrue(actual.equals(condition.test(context)?"branch":"fallback"),
                        "Serialized conditional branch differs from its typed predicate: "+mask);
            }
            player.getInventory().setItem(12,new ItemStack(Items.EMERALD,2));
            helper.assertTrue(org.arcadia.arc_quest.dialogue.runtime.ConditionalTextEvaluator.evaluate(context,
                    node.conditionalTexts(),"fallback").equals("inventory"),"Inventory conditional text lost source/priority");
            data.setFlag("qa:priority");
            var ordered=new java.util.LinkedHashMap<String,org.arcadia.arc_quest.dialogue.api.ConditionalSay>();
            ordered.put("0|HAS_FLAG:qa:priority",org.arcadia.arc_quest.dialogue.api.ConditionalSay.of(
                    "arc_quest:qa_low",DialogueText.literal("low")));
            ordered.put("200|HAS_FLAG:qa:priority",org.arcadia.arc_quest.dialogue.api.ConditionalSay.of(
                    "arc_quest:qa_high",DialogueText.literal("high")));
            var selected=org.arcadia.arc_quest.dialogue.runtime.ConditionalTextEvaluator.evaluateWithIndex(context,ordered,"fallback");
            helper.assertTrue("arc_quest:qa_high".equals(selected.sayId)&&selected.selectedIndex==1&&selected.text.equals("high"),
                    "Native network selection mixed a high-priority say with another branch's identity");
            var otherContext=org.arcadia.arc_quest.dialogue.runtime.DialogueEvalContext.of(other,null,"qa",
                    ArcQuestPlayerManager.getOrCreate(other).getDialogueProgress());
            helper.assertTrue(org.arcadia.arc_quest.dialogue.runtime.ConditionalTextEvaluator.evaluate(otherContext,
                    node.conditionalTexts(),"fallback").equals("fallback"),"Conditional branch leaked to another player");
            helper.succeed();
        } finally { release(player);release(other); }
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
