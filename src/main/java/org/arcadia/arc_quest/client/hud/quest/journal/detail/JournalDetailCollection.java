package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.guide.GuideImageLayout;
import org.arcadia.arc_quest.client.hud.guide.GuideImageRenderer;
import org.arcadia.arc_quest.client.hud.quest.icon.*;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingController;
import org.arcadia.arc_quest.client.quest.tracking.QuestTrackingPresentationState;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SRequestQuestActionPacket;
import org.arcadia.arc_quest.quest.network.C2SClaimCollectionRewardPacket;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.*;

/** A collection is a real chapter containing specimens, never a phase per specimen. */
public final class JournalDetailCollection {
    private final QuestJournalScreen screen;
    private final LegacyJournalDetailCollection legacy;
    private final CollectionImageViewer imageViewer = new CollectionImageViewer();
    private final List<Action> actions = new ArrayList<>();
    private final List<HudRect> itemHits = new ArrayList<>();
    private final Map<String, List<FormattedCharSequence>> textLines = new LinkedHashMap<>(32, .75f, true);
    private final Map<String, Float> hoverAmounts = new HashMap<>();
    private final Map<String, List<String>> readContentSignatures = new HashMap<>();
    private EditBox search;
    private CollectionJournalState state;
    private CollectionJournalLayout layout;
    private String questId = "", phaseId = "";
    private QuestDefinition definition;
    private QuestRuntimeData runtime;
    private CollectionSheetProgress progress;
    private List<CollectionBindingProgress> filtered = List.of();
    private CollectionSheetProgress filteredProgress;
    private String filterSignature = "";
    private int detailContentHeight, catalogContentHeight;
    private int absX, absY, clipX1, clipY1, clipX2, clipY2;
    private double mouseX, mouseY;
    private boolean interactive, usingSheets;

    public JournalDetailCollection(QuestJournalScreen screen) {
        this.screen = screen;
        this.legacy = new LegacyJournalDetailCollection(screen);
    }

    public static boolean usesSheets(QuestDefinition definition) {
        return definition != null && definition.isCollectionQuest()
                && definition.getAllPhases().stream().anyMatch(PhaseDefinition::hasCollectionSheet);
    }

    public void reset() {
        actions.clear(); itemHits.clear(); hoverAmounts.clear(); textLines.clear();
        legacy.reset(); imageViewer.close();
        state = null; phaseId = ""; questId = ""; search = null;
        readContentSignatures.clear();
        filteredProgress = null; progress = null;
    }

    public String selectedPhaseId() { return phaseId; }
    public boolean selectedPhaseHasSheet() {
        return definition != null && definition.getPhase(phaseId) != null && definition.getPhase(phaseId).hasCollectionSheet();
    }
    public boolean imageOpen() { return imageViewer.isOpen(); }
    public void closeImage() { imageViewer.close(); }
    public void renderImage(GuiGraphics graphics, int mouseX, int mouseY) { imageViewer.render(screen, graphics, mouseX, mouseY); }
    public boolean imageClick(double mouseX, double mouseY, int button) { return imageViewer.click(screen, mouseX, mouseY, button); }

    public void focusJeiPhase(String quest, QuestDefinition def, String phase) {
        if (!usesSheets(def)) { legacy.focusJeiPhase(quest, def, phase); return; }
        if (def.getPhase(phase) != null && def.getPhase(phase).hasCollectionSheet()) phaseId = phase;
    }

    public int render(GuiGraphics graphics, JournalTypes.QuestListEntry entry, QuestDefinition def,
                      QuestRuntimeData rt, int y, int alpha, int theme, double mx, double my,
                      int width, int absoluteX, int absoluteY, int left, int top, int right, int bottom) {
        usingSheets = usesSheets(def);
        if (!usingSheets) return legacy.render(graphics, entry, def, rt, y, alpha, theme, mx, my);
        actions.clear(); itemHits.clear();
        definition = def; runtime = rt; absX = absoluteX; absY = absoluteY;
        clipX1 = left; clipY1 = top; clipX2 = right; clipY2 = bottom;
        mouseX = mx; mouseY = my;
        interactive = screen.canInteractWithObjectiveIcons() && !imageViewer.isOpen()
                && alpha > 8;
        String previousQuest = questId;
        questId = entry.questId();
        var phases = def.getAllPhases().stream().filter(p -> rt == null || rt.isPhaseActive(p.getPhaseId())
                || rt.isPhaseCompleted(p.getPhaseId()) || rt.isPhasePendingManualAdvance(p.getPhaseId())).toList();
        if (phases.isEmpty()) phases = def.getAllPhases().stream().filter(PhaseDefinition::hasCollectionSheet).limit(1).toList();
        if (!previousQuest.equals(questId) || phases.stream().noneMatch(p -> p.getPhaseId().equals(phaseId))) {
            String fallback = phases.stream().filter(p -> rt != null && rt.isPhaseActive(p.getPhaseId())).findFirst().orElse(phases.get(0)).getPhaseId();
            phaseId = CollectionJournalState.phase(screen.getMinecraft().getConnection(), questId,
                    rt == null ? 0 : rt.getAcceptedAtRealMs(), fallback);
            String chosen = phaseId;
            if (phases.stream().noneMatch(p -> p.getPhaseId().equals(chosen))) phaseId = fallback;
        }
        var nextState = CollectionJournalState.get(screen.getMinecraft().getConnection(), questId,
                rt == null ? 0 : rt.getAcceptedAtRealMs(), phaseId);
        if (state != nextState) {
            state = nextState; search = null; filteredProgress = null;
            textLines.clear(); hoverAmounts.clear();
        }
        if (phases.size() > 1) {
            int x = 0;
            for (var phase : phases) {
                Component label = phase.getDisplayName();
                int buttonWidth = Math.min(width, screen.getFont().width(label) + 16);
                if (x > 0 && x + buttonWidth > width) { x = 0; y += 22; }
                final String target = phase.getPhaseId();
                button(graphics, new HudRect(x, y, buttonWidth, 18), label,
                        target.equals(phaseId), alpha, theme, () -> { phaseId = target; state = null; });
                x += buttonWidth + 6;
            }
            y += 26;
        }
        CollectionJournalState.rememberPhase(questId, rt == null ? 0 : rt.getAcceptedAtRealMs(), phaseId);
        if (!selectedPhaseHasSheet()) return y;
        progress = ClientQuestCache.INSTANCE.getCollectionSheetProgress(questId, phaseId);
        if (progress == null) {
            graphics.drawString(screen.getFont(), text("loading"), 0, y, color(0xAAAAAA, alpha), false);
            return y + 16;
        }
        String count = progress.completed() + " / " + progress.target();
        graphics.drawString(screen.getFont(), text("task_progress"), 0, y, color(0xAAAAAA, alpha), false);
        graphics.drawString(screen.getFont(), count, width - screen.getFont().width(count), y,
                color(progress.complete() ? 0x9AD6AA : 0xFFFFFF, alpha), false);
        y += 14;
        bar(graphics, 0, y, width, progress.completed(), progress.target(), theme, alpha);
        y += 9;
        if (progress.candidateTotal() > progress.target()) {
            graphics.drawString(screen.getFont(), text("candidates", progress.candidateTotal()), 0, y,
                    color(0x999999, alpha), false);
            y += 15;
        }
        int overviewWidth = Math.min(width, screen.getFont().width(text("track_overview")) + 16);
        button(graphics, new HudRect(0, y, overviewWidth, 18), text("track_overview"), false, alpha, theme,
                () -> { if (runtime != null && runtime.getState() == QuestState.ACTIVE)
                    ClientQuestTrackingController.INSTANCE.requestCollectionFocus(questId, phaseId, null); });
        y += 25;
        y = renderCategories(graphics, y, width, alpha, theme);

        filter();
        int bodyHeight = Math.max(120, Math.min(320, bottom - absoluteY - y - 50));
        boolean hasSelection = !state.selection.isEmpty();
        boolean detailExpanded = state.expanded && hasSelection
                && (state.selectionMade || width >= CollectionJournalLayout.MIN_CARD_WIDTH * 2 + CollectionJournalLayout.GAP * 2 + 206);
        layout = CollectionJournalLayout.measure(width, y + 25, bodyHeight, detailExpanded);
        int searchWidth = layout.secondLevel() ? width : layout.catalog().width();
        if (!detailExpanded && hasSelection) searchWidth = Math.max(40, searchWidth - screen.getFont().width(text("expand_detail")) - 24);
        renderSearch(graphics, y, searchWidth, alpha);
        if (!layout.secondLevel()) renderCatalog(graphics, layout.catalog(), alpha, theme);
        if (detailExpanded) renderDetail(graphics, layout.detail(), alpha, theme);
        else if (hasSelection) {
            int w = Math.min(width, screen.getFont().width(text("expand_detail")) + 16);
            button(graphics, new HudRect(width - w, y, w, 18), text("expand_detail"), false, alpha, theme,
                    () -> { state.expanded = true; state.selectionMade = true; });
        }
        y = layout.catalog().bottom() + 12;
        y = renderChoices(graphics, y, width, alpha, theme);
        return renderRewardNodes(graphics, y, width, alpha, theme);
    }

    private int renderCategories(GuiGraphics g, int y, int width, int alpha, int theme) {
        var categories = definition.getCollectionConfig().getCategories();
        int x = 0;
        int w = screen.getFont().width(text("all", progress.candidateTotal())) + 16;
        button(g, new HudRect(x, y, Math.min(width, w), 18), text("all", progress.candidateTotal()),
                state.category.isEmpty(), alpha, theme, () -> { state.category = ""; state.catalogScroll = 0; });
        x += w + 6;
        for (var category : categories) {
            var counter = progress.categories().stream().filter(c -> c.categoryId().equals(category.getCategoryId())).findFirst().orElse(null);
            if (counter == null) continue;
            Component title = category.getDisplayNameText().resolve(null, QuestTextContext.empty()).copy()
                    .append(" " + counter.completed() + "/" + counter.target());
            w = screen.getFont().width(title) + 16;
            if (x > 0 && x + w > width) { x = 0; y += 23; }
            String id = category.getCategoryId();
            button(g, new HudRect(x, y, Math.min(width, w), 18), title, state.category.equals(id), alpha, theme,
                    () -> { state.category = id; state.catalogScroll = 0; });
            x += w + 6;
        }
        return y + 25;
    }

    private void filter() {
        String signature = state.category + "\n" + state.query;
        if (filteredProgress == progress && filterSignature.equals(signature)) return;
        if (filteredProgress != progress) {
            state.validate(progress.bindings().stream().filter(CollectionBindingProgress::visible)
                    .map(CollectionBindingProgress::bindingId).toList());
        }
        String needle = state.query.strip().toLowerCase(Locale.ROOT);
        filtered = progress.bindings().stream().filter(p -> {
            if (!p.visible()) return false;
            var entry = entry(p);
            if (entry == null) return false;
            if (!state.category.isEmpty() && !state.category.equals(entry.getCategoryId())) return false;
            if (needle.isEmpty()) return true;
            // An unrevealed specimen never contributes its real title, ID or hidden body to search.
            return p.revealed() && (entry.getDisplayName().getString() + " " + entry.getDescription().getString())
                    .toLowerCase(Locale.ROOT).contains(needle);
        }).toList();
        filteredProgress = progress; filterSignature = signature;
    }

    private void renderSearch(GuiGraphics g, int y, int width, int alpha) {
        if (search == null) {
            search = new EditBox(screen.getFont(), 6, y + 4, Math.max(8, width - 12), 14, text("search"));
            search.setBordered(false); search.setMaxLength(80); search.setValue(state.query);
            search.setHint(text("search"));
            search.setResponder(value -> { state.query = value; state.catalogScroll = 0; });
        }
        search.setX(6); search.setY(y + 4); search.setWidth(Math.max(8, width - 12));
        search.setAlpha(alpha / 255f);
        search.setTextColor(color(0xDDDDDD, alpha));
        g.fill(0, y, width, y + 21, color(0xFFFFFF, alpha / 12));
        search.render(g, interactive ? (int) mouseX : -1000, interactive ? (int) mouseY : -1000, 0);
    }

    private void renderCatalog(GuiGraphics g, HudRect box, int alpha, int theme) {
        catalogContentHeight = CollectionJournalLayout.contentHeight(filtered.size(), layout.columns());
        state.catalogScroll = CollectionJournalState.clampScroll(state.catalogScroll, catalogContentHeight, box.height());
        if (filtered.isEmpty()) {
            drawWrapped(g, text("no_results"), box.x() + 6, box.y() + 12, box.width() - 12, 0xAAAAAA, alpha);
            return;
        }
        clip(g, box);
        int[] range = CollectionJournalLayout.visibleRange(filtered.size(), layout.columns(), state.catalogScroll, box.height());
        for (int i = range[0]; i < range[1]; i++) {
            var binding = filtered.get(i);
            var entry = entry(binding);
            if (entry == null) continue;
            int x = box.x() + (i % layout.columns()) * (layout.cardWidth() + CollectionJournalLayout.GAP);
            int y = box.y() + (i / layout.columns()) * (layout.cardHeight() + CollectionJournalLayout.GAP) - (int) state.catalogScroll;
            var card = new HudRect(x, y, layout.cardWidth(), layout.cardHeight());
            if (!visible(card) || y >= box.bottom() || card.bottom() <= box.y()) continue;
            boolean hovered = hit(card) && box.contains(mouseX, mouseY);
            boolean selected = binding.bindingId().equals(state.selection);
            float amount = hoverAmounts.getOrDefault(binding.bindingId(), 0f);
            amount = HudAnimUtil.lerp(amount, hovered ? 1f : 0f, .2f, screen.getDt());
            hoverAmounts.put(binding.bindingId(), amount);
            if (selected || amount > .01f) g.fill(x, y, card.right(), card.bottom(),
                    color(theme, (int) (alpha * (selected ? .12f : .06f * amount))));
            if (selected) g.fill(x + card.width() / 4, card.bottom() - 1,
                    card.right() - card.width() / 4, card.bottom(), color(theme, alpha));
            boolean focused = questId.equals(ClientQuestTrackingController.INSTANCE.trackedQuestId())
                    && phaseId.equals(ClientQuestTrackingController.INSTANCE.trackedPhaseId())
                    && binding.bindingId().equals(QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(questId));
            if (focused) g.fill(card.right() - 7, y + 4, card.right() - 4, y + 7, color(theme, alpha));
            if (binding.revealed()) drawEntryIcon(g, entry, binding, x + (card.width() - 28) / 2, y + 7, 28, alpha, theme, box);
            else g.drawCenteredString(screen.getFont(), Component.literal("?"), x + card.width() / 2, y + 16,
                    color(0x999999, alpha));
            Component title = binding.revealed() ? entry.getDisplayName() : text("unknown_entry");
            var fitted = StyledTextUtil.fitSingleLine(screen.getFont(), title, card.width() - 10);
            g.drawString(screen.getFont(), fitted, x + (card.width() - screen.getFont().width(fitted)) / 2, y + 43,
                    color(binding.revealed() ? 0xEEEEEE : 0x999999, alpha), false);
            Component status = binding.complete() ? text("achieved") : !binding.revealed() ? text("undiscovered") : text("investigating");
            var spec = definition.getPhase(phaseId).getCollectionSheet().getBinding(binding.bindingId());
            if (spec != null && spec.isOptional()) status = status.copy().append(" · ").append(text("optional"));
            var statusLine = StyledTextUtil.fitSingleLine(screen.getFont(), status, card.width() - 10);
            g.drawString(screen.getFont(), statusLine, x + (card.width() - screen.getFont().width(statusLine)) / 2, y + 58,
                    color(binding.complete() ? 0x9AD6AA : 0xAAAAAA, alpha), false);
            if (hovered) screen.requestPointerCursor();
            addAction(card, () -> state.select(binding.bindingId()), box);
        }
        screen.disableScissor(g);
        scrollbar(g, box, catalogContentHeight, state.catalogScroll, alpha);
    }

    private void drawEntryIcon(GuiGraphics g, CollectionEntryDefinition entry, CollectionBindingProgress binding,
                               int x, int y, int size, int alpha, int theme, HudRect clip) {
        var context = CollectionEntryIcons.context(questId, phaseId, binding.bindingId(), entry);
        var bounds = new HudRect(x - 3, y - 3, size + 6, size + 6);
        boolean hovered = hit(bounds) && clip.contains(mouseX, mouseY);
        var selected = screen.getObjectiveIcons().select(context, hovered, interactive);
        if (!selected.available()) return;
        float hover = screen.getObjectiveIcons().hoverAmount(context, hovered, screen.getDt());
        g.pose().pushPose();
        g.pose().translate(x + size / 2f, y + size / 2f, 0);
        float scale = 1 + hover * .06f;
        g.pose().scale(scale, scale, 1); g.pose().translate(-x - size / 2f, -y - size / 2f, 0);
        selected.render(g, x, y, size, alpha / 255f);
        g.pose().popPose();
        if (!selected.isItem()) return;
        item(g, binding.bindingId(), selected.stack(), bounds, hovered, clip);
    }

    private void item(GuiGraphics g, String binding, ItemStack stack, HudRect box, boolean hovered, HudRect clip) {
        if (!interactive || !visible(box)) return;
        JeiScreenIngredients.collectionItem(screen, g, questId, phaseId, binding, stack,
                box.x(), box.y(), box.width(), box.height());
        var intersection = intersect(box, clip);
        if (intersection.width() > 0 && intersection.height() > 0) itemHits.add(intersection);
        if (hovered) { screen.setHoveredRewardTooltip(stack); screen.requestPointerCursor(); }
    }

    private void renderDetail(GuiGraphics g, HudRect box, int alpha, int theme) {
        var binding = progress.bindings().stream().filter(p -> p.bindingId().equals(state.selection)).findFirst().orElse(null);
        if (binding == null) return;
        var entry = entry(binding);
        if (entry == null) return;
        if (binding.discovered() && !imageViewer.isOpen() && screen.canInteractWithObjectiveIcons()) {
            String key = questId + "/" + phaseId + "/" + binding.bindingId();
            var blocks = binding.content().stream().map(CollectionContentBlock::blockId).toList();
            if (!blocks.equals(readContentSignatures.get(key))) {
                ClientQuestCache.INSTANCE.markCollectionSeen(questId, phaseId, binding.bindingId());
                readContentSignatures.put(key, blocks);
            }
        }
        g.fill(box.x(), box.y(), box.right(), box.bottom(), color(0xFFFFFF, alpha / 20));
        int x = box.x() + 10, width = Math.max(1, box.width() - 20), y = box.y() + 9;
        g.drawString(screen.getFont(), text("entry_archive"), x, y, color(0xAAAAAA, alpha), false);
        int closeWidth = screen.getFont().width(text(layout.secondLevel() ? "back_catalog" : "collapse")) + 10;
        button(g, new HudRect(box.right() - closeWidth - 6, box.y() + 4, closeWidth, 18),
                text(layout.secondLevel() ? "back_catalog" : "collapse"), false, alpha, theme, () -> state.expanded = false);
        HudRect body = new HudRect(x, box.y() + 28, width, Math.max(1, box.height() - 32));
        state.detailScroll = CollectionJournalState.clampScroll(state.detailScroll, detailContentHeight, body.height());
        clip(g, body);
        y = body.y() - (int) state.detailScroll;
        int start = y;
        if (!binding.revealed()) {
            y = drawWrapped(g, text("unknown_entry"), x, y, width, 0xEEEEEE, alpha) + 10;
            y = drawWrapped(g, text("unknown_hint"), x, y, width, 0xAAAAAA, alpha);
        } else {
            drawEntryIcon(g, entry, binding, x, y, 32, alpha, theme, body);
            var icon = screen.getObjectiveIcons().resolve(CollectionEntryIcons.context(questId, phaseId, binding.bindingId(), entry));
            int nameX = icon.available() ? x + 40 : x;
            int nameY = drawWrapped(g, entry.getDisplayName(), nameX, y, x + width - nameX, 0xEEEEEE, alpha);
            y = Math.max(y + 36, nameY + 6);
            Component record = text("record_status", text(binding.researchComplete() ? entry.getResearchObjectives().isEmpty() ? "record_recorded" : "record_researched"
                    : binding.discovered() ? "record_discovered" : "undiscovered"));
            y = drawWrapped(g, record, x, y, width, 0xAAAAAA, alpha) + 10;
            if (!entry.getDescription().getString().isEmpty()) y = drawWrapped(g, entry.getDescription(), x, y, width, 0xCCCCCC, alpha) + 9;
            for (var block : binding.content()) {
                var blockText = block.text().resolve(null, QuestTextContext.empty());
                if (!blockText.getString().isEmpty()) y = drawWrapped(g, blockText, x, y, width, 0xCCCCCC, alpha) + 8;
                GuideMediaDefinition media = block.media();
                if (media != null) {
                    if (GuideImageRenderer.available(media)) {
                        int imageH = Math.max(36, Math.min(150, Math.round(width * (float) media.getHeight() / Math.max(1, media.getWidth()))));
                        var imageBox = new HudRect(x, y, width, imageH);
                        if (visible(imageBox) && imageBox.bottom() > body.y() && imageBox.y() < body.bottom()) {
                            GuideImageRenderer.draw(g, media, x, y, width, imageH,
                                    block.fit() == CollectionMediaFit.COVER ? GuideImageLayout.Fit.COVER : GuideImageLayout.Fit.CONTAIN,
                                    alpha / 255f);
                            if (block.zoomable()) {
                                Component caption = block.caption().resolve(null, QuestTextContext.empty());
                                addAction(imageBox, () -> imageViewer.open(media, caption), body);
                                if (hit(imageBox) && body.contains(mouseX, mouseY)) screen.requestPointerCursor();
                            }
                        }
                        y += imageH + 5;
                    } else y = drawWrapped(g, text("image_missing"), x, y, width, 0x999999, alpha) + 5;
                }
                var caption = block.caption().resolve(null, QuestTextContext.empty());
                if (!caption.getString().isEmpty()) y = drawWrapped(g, caption, x, y, width, 0xAAAAAA, alpha) + 9;
            }
            y += 4;
            g.drawString(screen.getFont(), text("requirements"), x, y, color(0xDDDDDD, alpha), false);
            y += 15;
            int complete = (int) binding.requirements().stream().filter(CollectionRequirementProgress::complete).count();
            bar(g, x, y, width, complete, binding.requirements().size(), theme, alpha);
            y += 9;
            for (var requirement : binding.requirements()) {
                if (requirement.objective() != null && requirement.objective().isHidden()) continue;
                y = requirement(g, binding, requirement, x, y, width, body, alpha, theme) + 9;
            }
            if (!binding.complete() && runtime != null && runtime.isPhaseActive(phaseId)) {
                int w = Math.min(width, screen.getFont().width(text("track_entry")) + 16);
                button(g, new HudRect(x, y, w, 18), text("track_entry"), false, alpha, theme,
                        () -> ClientQuestTrackingController.INSTANCE.requestCollectionFocus(questId, phaseId, binding.bindingId()), body);
                y += 26;
            } else {
                y = drawWrapped(g, text(binding.complete() ? "entry_achieved" : "chapter_inactive"), x, y, width, 0x9AD6AA, alpha) + 10;
            }
            if (!entry.getRelatedItems().isEmpty()) {
                g.fill(x, y, x + width, y + 1, color(0xFFFFFF, alpha / 10)); y += 10;
                g.drawString(screen.getFont(), text("related_items"), x, y, color(0xAAAAAA, alpha), false); y += 15;
                for (var itemId : entry.getRelatedItems()) {
                    var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                    if (item == null) continue;
                    ItemStack stack = item.getDefaultInstance();
                    if (stack.isEmpty()) continue;
                    var iconBox = new HudRect(x, y, 22, 22);
                    if (visible(iconBox) && iconBox.bottom() > body.y() && iconBox.y() < body.bottom()) {
                        boolean hovered = hit(iconBox) && body.contains(mouseX, mouseY);
                        ObjectiveIconAlpha.renderItem(g, stack, x + 2, y + 2, 18, alpha / 255f);
                        item(g, binding.bindingId(), stack, iconBox, hovered, body);
                    }
                    y = Math.max(y + 24, drawWrapped(g, stack.getHoverName(), x + 28, y + 6, width - 28, 0xCCCCCC, alpha) + 7);
                }
            }
        }
        detailContentHeight = y - start + 8;
        screen.disableScissor(g);
        scrollbar(g, body, detailContentHeight, state.detailScroll, alpha);
    }

    private int requirement(GuiGraphics g, CollectionBindingProgress binding, CollectionRequirementProgress req,
                            int x, int y, int width, HudRect body, int alpha, int theme) {
        if (req.objective() == null) {
            String count = req.current() + "/" + req.target();
            int cw = screen.getFont().width(count) + 8;
            int end = drawWrapped(g, Component.literal(req.complete() ? "✓ " : "○ ").append(req.label()),
                    x, y, Math.max(1, width - cw), req.complete() ? 0x9AD6AA : 0xCCCCCC, alpha);
            g.drawString(screen.getFont(), count, x + width - screen.getFont().width(count), y,
                    color(0xAAAAAA, alpha), false);
            return end;
        }
        var context = new ObjectiveIconContext(questId, phaseId, req.objectiveIndex(), req.objective(),
                req.current(), req.target(), ObjectiveIconsClient.generation());
        var rowLayout = ObjectiveRowRenderer.layout(screen, context, width, false);
        var area = new ObjectiveRowRenderer.Area(x, y, width, absX + x, absY + y,
                absX + (int) mouseX, absY + (int) mouseY,
                Math.max(clipX1, absX + body.x()), Math.max(clipY1, absY + body.y()),
                Math.min(clipX2, absX + body.right()), Math.min(clipY2, absY + body.bottom()));
        var result = ObjectiveRowRenderer.render(screen, g, context, area, false, interactive,
                req.target() <= 0 ? 0 : (float) req.current() / req.target(), theme, alpha,
                (gg, x1, y1, x2, y2) -> screen.enableScissor(gg, x1, y1, x2, y2));
        if (rowLayout.iconSize() > 0 && visible(new HudRect(x, y, rowLayout.iconSize(), rowLayout.iconSize()))) {
            var selected = screen.getObjectiveIcons().select(context, area.containsMouse(rowLayout.height()), interactive);
            if (selected.isItem()) item(g, binding.bindingId(), selected.stack(),
                    new HudRect(x, y, rowLayout.iconSize(), rowLayout.iconSize()),
                    hit(new HudRect(x, y, rowLayout.iconSize(), rowLayout.iconSize())), body);
        }
        if (result.canSubmit()) addAction(new HudRect(x + rowLayout.textX(), y,
                width - rowLayout.textX(), result.height()), () -> QuestOfferPanel.trigger(questId, phaseId, req.objectiveIndex()), body);
        return y + result.height();
    }

    private CollectionEntryDefinition entry(CollectionBindingProgress binding) {
        return definition.getCollectionConfig().getEntry(binding.entryId());
    }

    private int renderChoices(GuiGraphics g, int y, int width, int alpha, int theme) {
        var phase = definition.getPhase(phaseId);
        if (!JournalDetailPanel.shouldShowBranchChoices(definition, runtime, phaseId)) return y;
        y = drawWrapped(g, Component.translatable("arc_quest.gui.journal.section.choose_path"), 0, y, width, 0xDDCC99, alpha) + 5;
        for (int i = 0; i < phase.getChoices().size(); i++) {
            var choice = phase.getChoices().get(i);
            if (choice.getVisibleCondition() != null && !choice.getVisibleCondition().testClient(
                    ClientQuestCache.INSTANCE.getCompletedQuestsAsRL(), ClientQuestCache.INSTANCE.getAllFlags(),
                    ClientQuestCache.INSTANCE.getAllVariables())) continue;
            int choiceIndex = i;
            int height = Math.max(22, screen.getFont().split(choice.getDisplayText(), Math.max(1, width - 12)).size()
                    * (screen.getFont().lineHeight + 2) + 10);
            var box = new HudRect(0, y, width, height);
            boolean hover = hit(box);
            g.fill(0, y, width, y + height, color(theme, alpha / (hover ? 6 : 12)));
            drawWrapped(g, choice.getDisplayText(), 6, y + 5, width - 12, 0xDDDDDD, alpha);
            addAction(box, () -> {
                if (screen.getMinecraft().getConnection() != null)
                    ArcQuestNetwork.sendQuestAction(C2SRequestQuestActionPacket.choose(questId, phaseId, choiceIndex));
            }, new HudRect(clipX1 - absX, clipY1 - absY, clipX2 - clipX1, clipY2 - clipY1));
            if (hover) screen.requestPointerCursor();
            y += height + 5;
        }
        return y + 8;
    }

    private int renderRewardNodes(GuiGraphics g, int y, int width, int alpha, int theme) {
        var config = definition.getCollectionConfig();
        if (!config.isAllowManualRewardClaim()) return y;
        var nodes = new ArrayList<>(config.getQuestRewardNodes());
        for (var category : config.getCategories()) if (state.category.isEmpty() || state.category.equals(category.getCategoryId()))
            nodes.addAll(category.getRewardNodes());
        nodes.removeIf(node -> node.getGrantMode() != EntryRewardGrantMode.MANUAL);
        if (nodes.isEmpty()) return y;
        g.drawString(screen.getFont(), text("milestone_rewards"), 0, y, color(0xCCCCCC, alpha), false); y += 16;
        var clip = new HudRect(clipX1 - absX, clipY1 - absY, clipX2 - clipX1, clipY2 - clipY1);
        for (var node : nodes) {
            boolean claimed = ClientQuestCache.INSTANCE.isCollectionRewardClaimed(questId, node.getRewardNodeId());
            boolean unlocked = ClientQuestCache.INSTANCE.isCollectionRewardUnlocked(questId, node.getRewardNodeId());
            int rowY = y;
            int buttonWidth = Math.min(70, screen.getFont().width(text("claim")) + 16);
            int rewardWidth = Math.max(24, width - buttonWidth - 12), x = 0;
            for (var reward : node.getRewards()) {
                int cellWidth = reward instanceof ItemReward ? 26 : Math.min(rewardWidth, screen.getFont().width(reward.describe()) + 6);
                if (x > 0 && x + cellWidth > rewardWidth) { x = 0; y += 26; }
                if (reward instanceof ItemReward itemReward) {
                    ItemStack stack = new ItemStack(itemReward.getItem(), itemReward.getCount());
                    var box = new HudRect(x, y, 24, 24);
                    if (visible(box)) {
                        ObjectiveIconAlpha.renderItem(g, stack, x + 3, y + 1, 18, alpha / 255f);
                        if (itemReward.getCount() > 1) {
                            String count = Integer.toString(itemReward.getCount());
                            g.drawString(screen.getFont(), count, x + 24 - screen.getFont().width(count), y + 15, color(0xDDDDDD, alpha), false);
                        }
                        if (interactive && (unlocked || claimed)) {
                            JeiScreenIngredients.collectionRewardItem(screen, g, questId, node.getRewardNodeId(), stack,
                                    x, y, 24, 24);
                            itemHits.add(intersect(box, clip));
                        }
                        if (hit(box)) { screen.setHoveredRewardTooltip(stack); screen.requestPointerCursor(); }
                    }
                } else drawWrapped(g, Component.literal(reward.describe()), x, y + 5, cellWidth, 0xBBBBBB, alpha);
                x += cellWidth + 4;
            }
            String label = claimed ? "claimed" : unlocked ? "claim" : "locked";
            var button = new HudRect(width - buttonWidth, rowY + 2, buttonWidth, 18);
            if (!claimed && unlocked) button(g, button, text(label), false, alpha, theme, () -> {
                if (screen.getMinecraft().getConnection() != null)
                    ArcQuestNetwork.sendClaimCollectionReward(C2SClaimCollectionRewardPacket.of(questId, node.getRewardNodeId()));
            });
            else g.drawString(screen.getFont(), text(label), button.x() + 5, button.y() + 5, color(0x888888, alpha), false);
            y += 29;
        }
        return y + 6;
    }

    private int drawWrapped(GuiGraphics g, Component text, int x, int y, int width, int col, int alpha) {
        String key = width + "/" + text.getString() + "/" + net.minecraft.locale.Language.getInstance().hashCode();
        var lines = textLines.computeIfAbsent(key, ignored -> screen.getFont().split(text, Math.max(1, width)));
        while (textLines.size() > 256) textLines.remove(textLines.keySet().iterator().next());
        for (var line : lines) {
            if (y + screen.getFont().lineHeight > clipY1 - absY && y < clipY2 - absY)
                g.drawString(screen.getFont(), line, x, y, color(col, alpha), false);
            y += screen.getFont().lineHeight + 2;
        }
        return y;
    }

    private void button(GuiGraphics g, HudRect rect, Component label, boolean selected, int alpha, int theme, Runnable action) {
        button(g, rect, label, selected, alpha, theme, action, new HudRect(clipX1 - absX, clipY1 - absY, clipX2 - clipX1, clipY2 - clipY1));
    }
    private void button(GuiGraphics g, HudRect rect, Component label, boolean selected, int alpha, int theme, Runnable action, HudRect clip) {
        boolean hover = hit(rect) && clip.contains(mouseX, mouseY);
        if (selected || hover) g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), color(theme, alpha / (selected ? 5 : 10)));
        var line = StyledTextUtil.fitSingleLine(screen.getFont(), label, Math.max(1, rect.width() - 10));
        g.drawString(screen.getFont(), line, rect.x() + 5, rect.y() + 5, color(selected || hover ? 0xEEEEEE : 0xBBBBBB, alpha), false);
        if (selected) g.fill(rect.x(), rect.bottom() - 1, rect.right(), rect.bottom(), color(theme, alpha));
        if (hover) screen.requestPointerCursor();
        addAction(rect, action, clip);
    }
    private void addAction(HudRect box, Runnable action, HudRect clip) {
        if (!interactive) return;
        var bounded = intersect(box, clip);
        bounded = intersect(bounded, new HudRect(clipX1 - absX, clipY1 - absY, clipX2 - clipX1, clipY2 - clipY1));
        if (bounded.width() > 0 && bounded.height() > 0) actions.add(new Action(bounded, action));
    }
    private static HudRect intersect(HudRect a, HudRect b) {
        int x = Math.max(a.x(), b.x()), y = Math.max(a.y(), b.y());
        return new HudRect(x, y, Math.max(0, Math.min(a.right(), b.right()) - x), Math.max(0, Math.min(a.bottom(), b.bottom()) - y));
    }
    private void clip(GuiGraphics g, HudRect box) { screen.enableScissor(g, absX + box.x(), absY + box.y(), absX + box.right(), absY + box.bottom()); }
    private boolean hit(HudRect box) {
        return interactive && box.contains(mouseX, mouseY)
                && mouseX + absX >= clipX1 && mouseX + absX < clipX2
                && mouseY + absY >= clipY1 && mouseY + absY < clipY2;
    }
    private boolean visible(HudRect box) { return box.right() + absX > clipX1 && box.x() + absX < clipX2 && box.bottom() + absY > clipY1 && box.y() + absY < clipY2; }
    private static int color(int rgb, int a) { return HudAnimUtil.withAlpha(rgb, a); }
    public static Component text(String key, Object... args) { return Component.translatable("arc_quest.gui.collection." + key, args); }
    private static void bar(GuiGraphics g, int x, int y, int w, int current, int target, int theme, int alpha) {
        g.fill(x, y, x + w, y + 2, color(0xFFFFFF, alpha / 9));
        int fill = target <= 0 ? 0 : Math.max(0, Math.min(w, (int) (w * (double) current / target)));
        if (fill > 0) g.fill(x, y, x + fill, y + 2, color(theme, alpha));
    }
    private void scrollbar(GuiGraphics g, HudRect box, int content, double scroll, int alpha) {
        if (content <= box.height()) return;
        int h = Math.max(10, (int) ((long) box.height() * box.height() / content));
        int top = box.y() + (int) ((box.height() - h) * scroll / (content - box.height()));
        g.fill(box.right() - 2, top, box.right(), top + h, color(0xFFFFFF, alpha / 4));
    }

    public boolean mouseClicked(double x, double y) {
        if (!usingSheets) return legacy.mouseClicked(x, y);
        if (!interactive || x + absX < clipX1 || x + absX >= clipX2
                || y + absY < clipY1 || y + absY >= clipY2) return false;
        // Icon clicks cannot select a specimen, submit an offer or change the tracking focus.
        if (JeiScreenIngredients.isRuntimeAvailable() && itemHits.stream().anyMatch(b -> b.contains(x, y))) return true;
        if (search != null) {
            boolean inside = x >= search.getX() - 6 && x < search.getX() + search.getWidth() + 6
                    && y >= search.getY() - 4 && y < search.getY() + 17;
            search.setFocused(inside);
            if (inside) { search.mouseClicked(x, y, 0); return true; }
        }
        for (int i = actions.size() - 1; i >= 0; i--) if (actions.get(i).box().contains(x, y)) {
            actions.get(i).action().run(); screen.playClick(); return true;
        }
        return false;
    }
    public boolean mouseClickedAbsolute(double x, double y) { return mouseClicked(x - absX, y - absY); }
    public boolean mouseScrolledAbsolute(double x, double y, double delta) { return mouseScrolled(x - absX, y - absY, delta); }
    public boolean mouseScrolled(double x, double y, double delta) {
        if (!usingSheets || state == null || layout == null || imageViewer.isOpen()) return false;
        if (layout.detail().contains(x, y)) {
            state.detailScroll = CollectionJournalState.clampScroll(state.detailScroll - delta * 24, detailContentHeight, Math.max(1, layout.detail().height() - 32));
            return true;
        }
        if (!layout.secondLevel() && layout.catalog().contains(x, y)) {
            state.catalogScroll = CollectionJournalState.clampScroll(state.catalogScroll - delta * 24, catalogContentHeight, layout.catalog().height());
            return true;
        }
        return false;
    }
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!usingSheets || search == null || !search.isFocused()) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { search.setFocused(false); return true; }
        search.keyPressed(key, scan, modifiers);
        return true;
    }
    public boolean charTyped(char character, int modifiers) {
        return usingSheets && search != null && search.isFocused() && search.charTyped(character, modifiers);
    }
    private record Action(HudRect box, Runnable action) {}
}
