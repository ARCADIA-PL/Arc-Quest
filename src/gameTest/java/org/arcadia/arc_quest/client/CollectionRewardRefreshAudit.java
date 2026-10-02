package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.*;
import org.arcadia.arc_quest.data.sync.*;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.builder.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.logic.CollectionSheetService;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.reward.ItemReward;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/** Finite native UI regression. Claim acknowledgements are fixtures, never a server grant claim. */
final class CollectionRewardRefreshAudit {
    private static final ResourceLocation ENTRY = ResourceLocation.parse("arc_quest:audit_refresh_zombie");
    private static final String FIRST = "first_record", RESEARCH = "anatomy_reward", NODE = "manual_survey";
    private Map<String, QuestRuntimeData> oldActive;
    private Map<ResourceLocation, QuestDefinition> oldDefinitions;
    private CollectionRecordState records;
    private net.minecraft.nbt.CompoundTag oldRecords;
    private QuestDefinition source;
    private QuestRuntimeData runtime;
    private CollectionJournalState savedState;
    private double savedScroll, savedCatalogScroll;
    private float savedTransition, savedItemsAlpha;
    private Object savedTab;
    private HudRect claimBounds;
    private String capture;
    private int stage;

    @SuppressWarnings("unchecked") void begin(QuestJournalScreen screen) throws Exception {
        var cache = ClientQuestCache.INSTANCE;
        var active = (Map<String, QuestRuntimeData>) field(cache, "activeQuests");
        oldActive = new LinkedHashMap<>(active);
        oldDefinitions = (Map<ResourceLocation, QuestDefinition>) field(QuestRegistry.class, "clientPresentationRegistry");
        records = (CollectionRecordState) field(cache, "collectionRecords"); oldRecords = records.serializeNBT();
        var specimen = CollectionEntryBuilder.create(ENTRY).category("field").displayName("Public zombie archive")
                .entity(EntityType.ZOMBIE).discover(ObjectiveBuilder.kill(EntityType.ZOMBIE, 1).id("first_defeat"))
                .research(ObjectiveBuilder.kill(EntityType.ZOMBIE, 5).id("anatomy").display("Lifetime zombie study"))
                .discoveryReward(FIRST, new ItemReward(Items.COAL, 1))
                .researchReward(RESEARCH, new ItemReward(Items.IRON_NUGGET, 3))
                .text("reading", "Public specimen notes preserve the current archive scroll while claim receipts update. ".repeat(32));
        var config = CollectionQuestConfigBuilder.create().category("field", "Field").entry(specimen)
                .reward(new CollectionRewardNode(NODE, RewardScope.QUEST, EntryRewardGrantMode.MANUAL,
                        List.of(new ItemReward(Items.EMERALD, 1)), List.of(), CollectionClientAuditFixtures.QUEST)).build();
        source = QuestBuilder.create(CollectionClientAuditFixtures.QUEST).category(QuestCategory.COLLECTION).mode(QuestMode.COLLECTION)
                .displayName("Authorized reward refresh audit").collectionConfig(config).themeColor(0x85C6AE)
                .phase(PhaseBuilder.create("field").autoAdvanceOnComplete(false)
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 3).id("this_run").display("Current survey zombie defeats"))
                        .collectionSheet(CollectionSheetBuilder.create().binding(EntryRequirementBuilder.create("zombie", ENTRY).objective("this_run"))))
                .reward(new ItemReward(Items.DIAMOND, 1)).build();
        records.clear();
        runtime = new QuestRuntimeData(source.getId().toString(), "field", 1, 0, 1780000000000L, 0);
        CollectionSheetService.initialize(source, runtime, records);
        runtime.getCollectionData().markRewardUnlocked(NODE);
        active.clear(); active.put(runtime.getQuestId(), runtime);
        installAuthorized(screen);
    }

    boolean advance(QuestJournalScreen screen) throws Exception {
        var renderer = screen.getDetailPanel().collectionRenderer;
        switch (stage) {
            case 0 -> {
                require(!renderer.detailVisible(), "New audit unexpectedly retained another run's archive");
                var binding = binding();
                require(binding.revealed() && !binding.discovered(), "Public but undiscovered specimen was hidden");
                HudRect card = cardAction(renderer);
                if (card == null) {
                    int top = (int) field(renderer, "clipY1"), bottom = (int) field(renderer, "clipY2");
                    screen.mouseScrolled((screen.getScaledWidth() - 40) * screen.getUiScale(),
                            (top + bottom) / 2d * screen.getUiScale(), 0, -1);
                    return false;
                }
                click(screen, card);
                stage++;
            }
            case 1 -> {
                if (!renderer.detailInteractive()) return false;
                require(hasRenderedText(renderer, "record_public_undiscovered") && hasRenderedText(renderer, "public_archive_hint"),
                        "Public-undiscovered archive did not render its explicit state and explanation");
                var research = progress(renderer, "researchProgress");
                var discovery = progress(renderer, "discoveryProgress");
                require(research.size() == 1 && research.get(0).current() == 0 && research.get(0).target() == 5
                                && research.get(0).objectiveIndex() == -1 && discovery.size() == 1,
                        "Public record progress did not render its real read-only permanent objectives");
                require(binding().entryRewards().isEmpty() && claimAction(renderer) == null,
                        "Undiscovered archive fabricated an undisclosed reward or manual action");
                capture = "11-public-undiscovered"; stage++;
            }
            case 2 -> {
                records.discover(ENTRY); records.increment(ENTRY, "discovery:first_defeat", 1, 1);
                records.increment(ENTRY, "research:anatomy", 3, 5); records.unlockReward(ENTRY, FIRST);
                runtime.setObjectiveProgress("field", 0, 3);
                installAuthorized(screen); stage++;
            }
            case 3 -> {
                var permanent = progress(renderer, "researchProgress");
                require(permanent.size() == 1 && permanent.get(0).current() == 3 && permanent.get(0).target() == 5
                                && !permanent.get(0).complete() && permanent.get(0).objectiveIndex() == -1,
                        "Three current-run defeats falsely completed five permanent research defeats");
                require(binding().complete() && !binding().researchComplete()
                                && binding().requirements().get(0).current() == 3 && binding().requirements().get(0).target() == 3,
                        "Current survey and lifetime research projections were conflated");
                require(hasRenderedText(renderer, "research_locked_hint") && hasRenderedText(renderer, "research_hint"),
                        "Incomplete research did not explain its independent reward threshold");
                require(binding().entryRewards().size() == 1 && binding().entryRewards().get(0).canClaim()
                                && sourceEntry(screen).getRewards().stream().noneMatch(row -> row.rewardId().equals(RESEARCH)),
                        "Locked research item payload leaked or the public ready reward disappeared");
                capture = "12-lifetime-three-of-five"; stage++;
            }
            case 4 -> {
                HudRect jump = rewardJumpAction(renderer);
                require(jump != null && redPixels(screen, jump) > 0, "Ready detail reward entrance has no visible red badge");
                click(screen, jump); stage++;
            }
            case 5 -> {
                claimBounds = claimAction(renderer);
                require(claimBounds != null && claimBounds.height() == 22 && redPixels(screen, claimBounds) > 0,
                        "Ready manual reward did not render the primary claim button and red badge");
                savedState = state(renderer); savedScroll = savedState.detailScroll; savedCatalogScroll = savedState.catalogScroll;
                savedTransition = transition(renderer).alpha();
                savedTab = field(screen.getDetailPanel().rewardsRenderer, "activeTab");
                savedItemsAlpha = (float) field(screen.getDetailPanel().rewardsRenderer, "itemsAlphaAnim");
                require(savedScroll > 0 && savedTransition == 1, "Receipt replacement was not exercised in a settled scrolled archive");
                click(screen, claimBounds);
                require(!records.isRewardClaimed(ENTRY, FIRST), "A disconnected native claim click granted its own receipt");
                capture = "13-visible-primary-claim"; stage++;
            }
            case 6 -> {
                // This explicitly models an acknowledged server receipt, not a network delivery or reward grant.
                require(records.claimReward(ENTRY, FIRST), "Acknowledged receipt fixture could not transition to claimed");
                installAuthorized(screen);
                require(renderer.detailOpen() && state(renderer) == savedState && transition(renderer).alpha() == savedTransition,
                        "Same-epoch authorized definition replacement immediately reset the archive transition");
                stage++;
            }
            case 7 -> {
                require(renderer.detailOpen() && renderer.detailInteractive() && state(renderer) == savedState
                                && savedState.detailScroll == savedScroll && savedState.catalogScroll == savedCatalogScroll
                                && transition(renderer).alpha() == savedTransition,
                        "Claim acknowledgement moved or reopened the settled archive");
                require(field(screen.getDetailPanel().rewardsRenderer, "activeTab") == savedTab
                                && (float) field(screen.getDetailPanel().rewardsRenderer, "itemsAlphaAnim") >= savedItemsAlpha,
                        "Authorized claim refresh restarted reward tabs or their item fade");
                require(binding().entryRewards().get(0).claimed() && !binding().entryRewards().get(0).canClaim()
                                && claimAction(renderer) == null && redPixels(screen, claimBounds) == 0,
                        "Claimed receipt did not remove the native action and badge");
                capture = "14-claim-refresh-no-flicker"; stage++;
            }
            case 8 -> {
                records.increment(ENTRY, "research:anatomy", 2, 5); records.unlockReward(ENTRY, RESEARCH);
                installAuthorized(screen); stage++;
            }
            case 9 -> {
                var permanent = progress(renderer, "researchProgress");
                require(permanent.get(0).current() == 5 && permanent.get(0).complete() && binding().researchComplete()
                                && binding().entryRewards().stream().anyMatch(row -> row.definition().rewardId().equals(RESEARCH)
                                && row.canClaim() && row.definition().rewards().stream().anyMatch(reward -> reward instanceof ItemReward item
                                && item.getItem() == Items.IRON_NUGGET)),
                        "Completed real lifetime research did not expose its authorized iron nugget reward");
                renderer.closeDetail(); stage++;
            }
            case 10 -> {
                if (renderer.detailVisible()) return false;
                require((boolean) field(screen.getDetailPanel().rewardsRenderer, "primaryClaimable"),
                        "Claimable manual survey milestone did not light its reward tab badge");
                runtime.getCollectionData().markRewardClaimed(NODE); installAuthorized(screen); stage++;
            }
            case 11 -> {
                require(!(boolean) field(screen.getDetailPanel().rewardsRenderer, "primaryClaimable"),
                        "Received survey reward left its tab badge lit");
                return true;
            }
            default -> throw new IllegalStateException("Unknown receipt audit stage " + stage);
        }
        return false;
    }

    private void installAuthorized(QuestJournalScreen screen) throws Exception {
        var snapshot = CollectionContentDisclosure.project(DatapackContentSnapshot.empty(1), records,
                runtime.getQuestId()::equals, ignored -> source, null, List.of(source));
        var definition = QuestSpecCompiler.compileClientPresentation(QuestSpecJsonReader.read(
                snapshot.documents(DatapackContentModule.QUEST).get(0)), Map.of());
        QuestRegistry.replaceClientPresentationSnapshot(Map.of(definition.getId(), definition));
        ((Map<?, ?>) field(ClientQuestCache.INSTANCE, "collectionProjections")).clear();
        screen.rebuildEntries();
    }

    @SuppressWarnings("unchecked") void restore(QuestJournalScreen screen) throws Exception {
        var active = (Map<String, QuestRuntimeData>) field(ClientQuestCache.INSTANCE, "activeQuests");
        active.clear(); active.putAll(oldActive); records.readSnapshot(oldRecords);
        QuestRegistry.replaceClientPresentationSnapshot(oldDefinitions);
        ((Map<?, ?>) field(ClientQuestCache.INSTANCE, "collectionProjections")).clear();
        screen.rebuildEntries();
    }

    String takeCapture() { String result = capture; capture = null; return result; }
    private CollectionBindingProgress binding() { return ClientQuestCache.INSTANCE.getCollectionBindingProgress(runtime.getQuestId(), "field", "zombie"); }
    private CollectionEntryDefinition sourceEntry(QuestJournalScreen screen) { return QuestRegistry.get(screen.getSelectedQuestId()).getCollectionConfig().getEntry(ENTRY); }
    private static CollectionJournalState state(JournalDetailCollection renderer) throws Exception { return (CollectionJournalState) field(renderer, "state"); }
    private static CollectionDetailTransition transition(JournalDetailCollection renderer) throws Exception { return (CollectionDetailTransition) field(renderer, "detailTransition"); }
    @SuppressWarnings("unchecked") private static List<CollectionRequirementProgress> progress(Object renderer, String field) throws Exception { return (List<CollectionRequirementProgress>) field(renderer, field); }
    private static boolean hasRenderedText(Object renderer, String key) throws Exception {
        return ((Map<?, ?>) field(renderer, "textLines")).keySet().stream().anyMatch(line -> line.toString().contains(
                Component.translatable("arc_quest.gui.collection." + key).getString()));
    }
    private static HudRect cardAction(Object renderer) throws Exception {
        return findAction(renderer, 80, CollectionBindingProgress.class);
    }
    private static HudRect claimAction(Object renderer) throws Exception {
        return findAction(renderer, 22, CollectionEntryRewardDefinition.class);
    }
    private static HudRect rewardJumpAction(Object renderer) throws Exception {
        for (Object action : (List<?>) field(renderer, "actions")) {
            HudRect bounds = (HudRect) value(action, "box");
            if (bounds.height() == 22 && !captures(value(action, "action"), CollectionEntryRewardDefinition.class)) return absoluteBounds(renderer, bounds);
        }
        return null;
    }
    private static HudRect findAction(Object renderer, int height, Class<?> capture) throws Exception {
        for (Object action : (List<?>) field(renderer, "actions")) {
            HudRect bounds = (HudRect) value(action, "box");
            if (bounds.height() == height && captures(value(action, "action"), capture)) return absoluteBounds(renderer, bounds);
        }
        return null;
    }
    private static HudRect absoluteBounds(Object renderer, HudRect bounds) throws Exception {
        return new HudRect(bounds.x() + (int) field(renderer, "absX"), bounds.y() + (int) field(renderer, "absY"),
                bounds.width(), bounds.height());
    }
    private static boolean captures(Object action, Class<?> type) throws Exception {
        for (Field captured : action.getClass().getDeclaredFields()) {
            captured.setAccessible(true); if (type.isInstance(captured.get(action))) return true;
        }
        return false;
    }
    private static void click(QuestJournalScreen screen, HudRect box) {
        require(screen.mouseClicked((box.x() + box.width() / 2d) * screen.getUiScale(),
                (box.y() + box.height() / 2d) * screen.getUiScale(), 0), "Native archive action did not consume its click");
    }
    private static int redPixels(QuestJournalScreen screen, HudRect box) {
        try (NativeImage image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            double scale = screen.getUiScale() * Minecraft.getInstance().getWindow().getGuiScale();
            int left = Math.max(0, (int) Math.floor(box.x() * scale)), right = Math.min(image.getWidth(), (int) Math.ceil(box.right() * scale));
            int top = Math.max(0, (int) Math.floor(box.y() * scale)), bottom = Math.min(image.getHeight(), (int) Math.ceil(box.bottom() * scale));
            int red = 0;
            for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
                int pixel = image.getPixelRGBA(x, y), r = pixel & 255, g = pixel >>> 8 & 255, b = pixel >>> 16 & 255;
                if (r > 100 && r > g + 20 && r > b + 20) red++;
            }
            return red;
        }
    }
    private static Object value(Object owner, String name) throws Exception { Method method = owner.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(owner); }
    private static Object field(Object owner, String name) throws Exception {
        for (Class<?> type = owner instanceof Class<?> clazz ? clazz : owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(owner instanceof Class<?> ? null : owner); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
