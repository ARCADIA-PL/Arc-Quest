package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.StyledTextUtil;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.component.FadingSearchBox;
import org.arcadia.arc_quest.client.hud.guide.GuideImageLayout;
import org.arcadia.arc_quest.client.hud.guide.GuideImageRenderer;
import org.arcadia.arc_quest.client.hud.quest.icon.*;
import org.arcadia.arc_quest.client.hud.quest.journal.JournalTypes;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScrollbar;
import org.arcadia.arc_quest.client.hud.quest.offer.QuestOfferPanel;
import org.arcadia.arc_quest.client.quest.tracking.ClientQuestTrackingController;
import org.arcadia.arc_quest.client.quest.tracking.QuestTrackingPresentationState;
import org.arcadia.arc_quest.client.quest.collection.CollectionFavoritesStore;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;
import org.arcadia.arc_quest.quest.network.ClientQuestCache;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.quest.network.C2SRequestQuestActionPacket;
import org.arcadia.arc_quest.quest.network.C2SClaimCollectionRewardPacket;
import org.arcadia.arc_quest.quest.network.C2SClaimCollectionEntryRewardPacket;
import org.arcadia.arc_quest.quest.reward.ItemReward;

import java.util.*;

import static org.arcadia.arc_quest.client.hud.quest.journal.detail.CollectionJournalVisuals.*;

/** A collection is a real chapter containing specimens, never a phase per specimen. */
public final class JournalDetailCollection {
    private final QuestJournalScreen screen;
    private final LegacyJournalDetailCollection legacy;
    private final CollectionImageViewer imageViewer = new CollectionImageViewer();
    private final CollectionDetailTransition detailTransition = new CollectionDetailTransition();
    private final CollectionTrackDwell trackDwell = new CollectionTrackDwell();
    private static final String FAVORITES_CATEGORY = "\u0000favorites";
    private final JournalScrollbar catalogScrollbar = new JournalScrollbar(3, 18);
    private final JournalScrollbar detailScrollbar = new JournalScrollbar(3, 18);
    private final List<Action> actions = new ArrayList<>();
    private final List<HudRect> itemHits = new ArrayList<>();
    private final Map<String, List<FormattedCharSequence>> textLines = new LinkedHashMap<>(32, .75f, true);
    private final Map<String, Float> hoverAmounts = new HashMap<>();
    private final Map<String, List<String>> readContentSignatures = new HashMap<>();
    private FadingSearchBox search;
    private HudRect searchBounds = new HudRect(0, 0, 0, 0);
    private HudRect modalBounds = new HudRect(0, 0, 0, 0);
    private HudRect detailViewport = new HudRect(0, 0, 0, 0);
    private CollectionJournalState state;
    private CollectionJournalLayout layout;
    private String questId = "", phaseId = "";
    private QuestDefinition definition;
    private QuestRuntimeData runtime;
    private CollectionSheetProgress progress;
    private List<CollectionBindingProgress> filtered = List.of();
    private CollectionSheetProgress filteredProgress;
    private CollectionSheetProgress favoritesProgress;
    private long favoritesRevision = -1;
    private long visibleFavorites;
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
        detailTransition.reset(); trackDwell.reset();
        catalogScrollbar.mouseReleased(0); detailScrollbar.mouseReleased(0);
        state = null; phaseId = ""; questId = ""; search = null;
        readContentSignatures.clear();
        filteredProgress = null; favoritesProgress = null; progress = null;
    }

    public String selectedPhaseId() { return phaseId; }
    public boolean selectedPhaseHasSheet() {
        return definition != null && definition.getPhase(phaseId) != null && definition.getPhase(phaseId).hasCollectionSheet();
    }
    public boolean imageOpen() { return imageViewer.isOpen(); }
    public boolean searchFocused() { return usingSheets && search != null && search.isFocused() && !detailVisible() && !imageOpen(); }
    public void blurSearch() { if (search != null) search.setFocused(false); }
    public void closeImage() { imageViewer.close(); }
    public void renderImage(GuiGraphics graphics, int mouseX, int mouseY) { imageViewer.render(screen, graphics, mouseX, mouseY); }
    public boolean imageClick(double mouseX, double mouseY, int button) { return imageViewer.click(screen, mouseX, mouseY, button); }

    public boolean detailOpen() {
        return usingSheets && state != null && state.expanded && state.selectionMade && !state.selection.isEmpty()
                && selectedPhaseHasSheet();
    }

    public boolean detailVisible() { return usingSheets && detailTransition.visible(detailOpen()); }
    public boolean detailInteractive() { return detailTransition.interactive(detailOpen()); }
    public void advanceDetailTransition(float seconds) { detailTransition.advance(detailOpen(), seconds); }

    public void resetBrowserOnOpen() {
        CollectionJournalState.resetBrowserOnOpen(screen.getMinecraft().getConnection());
        if (search != null) { search.setValue(""); search.setFocused(false); }
        detailTransition.reset(); trackDwell.reset();
        imageViewer.close(); actions.clear(); itemHits.clear(); hoverAmounts.clear();
        filteredProgress = null;
    }

    public void closeDetail() {
        if (state != null) state.expanded = false;
        detailScrollbar.mouseReleased(0);
        imageViewer.close();
        interactive = false;
        actions.clear(); itemHits.clear();
    }

    /** Called after the ordinary journal panels, before offer/history/image modal layers. */
    public void renderModal(GuiGraphics graphics, int logicalWidth, int logicalHeight, int mx, int my) {
        if (!detailVisible() || progress == null) return;
        actions.clear(); itemHits.clear();
        absX = 0; absY = 0;
        var settled = CollectionJournalLayout.modal(logicalWidth, logicalHeight);
        modalBounds = new HudRect(settled.x(), settled.y() + detailTransition.offsetY(), settled.width(), settled.height());
        clipX1 = modalBounds.x(); clipY1 = modalBounds.y();
        clipX2 = modalBounds.right(); clipY2 = modalBounds.bottom();
        mouseX = mx; mouseY = my;
        int alpha = Math.round(255 * screen.getEffectiveAlpha() * detailTransition.alpha());
        interactive = detailInteractive() && screen.canInteractWithObjectiveIcons() && !imageViewer.isOpen() && alpha > 8;
        JeiScreenIngredients.modal(screen, interactive);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350);
        graphics.fill(0, 0, logicalWidth, logicalHeight, color(0x050A0D, alpha * 3 / 4));
        renderDetail(graphics, modalBounds, alpha, screen.getCurrentThemeColor());
        graphics.pose().popPose();
    }

    /** Topmost details consume background input while JEI retains the dedicated item clicks. */
    public boolean detailClick(double x, double y, int button) {
        if (!detailVisible()) return false;
        if (!detailInteractive() || !interactive || imageViewer.isOpen()) return true;
        if (button == 0) {
            if (!modalBounds.contains(x, y)) { closeDetail(); screen.playClick(); return true; }
            var scroll = detailScrollbar.mouseClicked(x, y, CollectionJournalLayout.scrollbarTrack(detailViewport),
                    8, detailContentHeight, state.detailScroll);
            if (scroll.consumed()) { state.detailScroll = scroll.scrollOffset(); return true; }
            mouseClicked(x, y);
        }
        return true;
    }

    public boolean detailDrag(double x, double y, int button) {
        if (!detailVisible()) return false;
        if (detailInteractive() && interactive && button == 0 && !imageViewer.isOpen()) {
            var scroll = detailScrollbar.mouseDragged(y, CollectionJournalLayout.scrollbarTrack(detailViewport),
                    detailContentHeight, state.detailScroll);
            if (scroll.consumed()) state.detailScroll = scroll.scrollOffset();
        }
        return true;
    }

    public boolean detailRelease(int button) {
        if (!detailVisible()) return false;
        detailScrollbar.mouseReleased(button);
        return true;
    }

    public boolean detailScroll(double x, double y, double delta) {
        if (!detailVisible()) return false;
        if (detailInteractive() && interactive && !imageViewer.isOpen() && modalBounds.contains(x, y))
            state.detailScroll = CollectionJournalState.clampScroll(state.detailScroll - delta * 24,
                    detailContentHeight, detailViewport.height());
        return true;
    }

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
        interactive = screen.canInteractWithObjectiveIcons() && !imageViewer.isOpen() && !detailVisible()
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
            textLines.clear(); hoverAmounts.clear(); trackDwell.reset(); detailTransition.reset();
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
        graphics.drawString(screen.getFont(), text("task_progress"), 0, y + 2, color(MUTED, alpha), false);
        graphics.drawString(screen.getFont(), count, width - screen.getFont().width(count), y + 2,
                color(progress.complete() ? COMPLETE : TEXT, alpha), false);
        y += 15;
        bar(graphics, 0, y, width, progress.completed(), progress.target(), theme, alpha);
        y += 7;
        y = renderCategories(graphics, y, width, alpha, theme);

        filter();
        // Keep the catalog height independent of the enclosing panel's animated scroll offset.
        int bodyHeight = Math.max(CollectionJournalLayout.CARD_HEIGHT,
                Math.min(360, bottom - top - y - 46));
        layout = CollectionJournalLayout.measure(width, y + 26, bodyHeight, false);
        renderSearch(graphics, y, width, alpha, theme);
        renderCatalog(graphics, layout.catalog(), alpha, theme);
        y = layout.catalog().bottom() + 12;
        y = renderChoices(graphics, y, width, alpha, theme);
        return renderRewardNodes(graphics, y, width, alpha, theme);
    }

    private int renderCategories(GuiGraphics g, int y, int width, int alpha, int theme) {
        var categories = definition.getCollectionConfig().getCategories();
        List<Component> labels = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        labels.add(text("all", progress.candidateTotal())); ids.add("");
        long favoriteCount = visibleFavoriteCount();
        if (favoriteCount > 0) {
            labels.add(text("favorites").copy().append(" " + favoriteCount)); ids.add(FAVORITES_CATEGORY);
        } else if (FAVORITES_CATEGORY.equals(state.category)) {
            state.category = ""; state.catalogScroll = 0;
        }
        for (var category : categories) {
            var counter = progress.categories().stream().filter(c -> c.categoryId().equals(category.getCategoryId())).findFirst().orElse(null);
            if (counter == null) continue;
            labels.add(category.getDisplayNameText().resolve(null, QuestTextContext.empty()).copy()
                    .append(" " + counter.completed() + "/" + counter.target()));
            ids.add(category.getCategoryId());
        }
        int totalWidth = labels.stream().mapToInt(label -> screen.getFont().width(label) + 20).sum();
        boolean paged = totalWidth > width;
        int left = paged ? 22 : 0, right = paged ? Math.max(left + 1, width - 22) : width;
        int available = Math.max(1, right - left);
        state.categoryOffset = paged ? Math.max(0, Math.min(state.categoryOffset, labels.size() - 1)) : 0;
        int x = left, end = state.categoryOffset;
        while (end < labels.size()) {
            Component title = labels.get(end);
            int w = Math.min(available, screen.getFont().width(title) + 16);
            if (x > left && x + w > right) break;
            String id = ids.get(end);
            button(g, new HudRect(x, y, w, 18), title, state.category.equals(id), alpha, theme,
                    () -> { state.category = id; state.catalogScroll = 0; });
            x += w + 4; end++;
        }
        if (paged) {
            int previous = Math.max(0, state.categoryOffset - 1), used = 0;
            for (int i = state.categoryOffset - 1; i >= 0; i--) {
                int w = Math.min(available, screen.getFont().width(labels.get(i)) + 16);
                if (used > 0 && used + w + 4 > available) break;
                previous = i; used += w + 4;
            }
            final int previousPage = previous, nextPage = end;
            if (state.categoryOffset > 0)
                button(g, new HudRect(0, y, 18, 18), Component.literal("‹"), false, alpha, theme,
                        () -> state.categoryOffset = previousPage);
            if (end < labels.size())
                button(g, new HudRect(width - 18, y, 18, 18), Component.literal("›"), false, alpha, theme,
                        () -> state.categoryOffset = nextPage);
        }
        return y + 22;
    }

    private long visibleFavoriteCount() {
        long revision = CollectionFavoritesStore.INSTANCE.revision();
        if (favoritesProgress != progress || favoritesRevision != revision) {
            visibleFavorites = progress.bindings().stream().filter(CollectionBindingProgress::visible)
                    .filter(p -> CollectionFavoritesStore.INSTANCE.isFavorite(p.entryId())).count();
            favoritesProgress = progress; favoritesRevision = revision;
        }
        return visibleFavorites;
    }

    private void filter() {
        String signature = state.category + "\n" + state.query + "\n" + CollectionFavoritesStore.INSTANCE.revision();
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
            if (FAVORITES_CATEGORY.equals(state.category)) {
                if (!CollectionFavoritesStore.INSTANCE.isFavorite(p.entryId())) return false;
            } else if (!state.category.isEmpty() && !state.category.equals(entry.getCategoryId())) return false;
            if (needle.isEmpty()) return true;
            // An unrevealed specimen never contributes its real title, ID or hidden body to search.
            return p.revealed() && (entry.getDisplayName().getString() + " " + entry.getDescription().getString())
                    .toLowerCase(Locale.ROOT).contains(needle);
        }).sorted(Comparator.comparing(p -> !CollectionFavoritesStore.INSTANCE.isFavorite(p.entryId()))).toList();
        filteredProgress = progress; filterSignature = signature;
    }

    private void renderSearch(GuiGraphics g, int y, int width, int alpha, int theme) {
        if (search == null) {
            search = new FadingSearchBox(screen.getFont(), 24, y + 6, Math.max(8, width - 32), 14, text("search"));
            search.setBordered(false); search.setMaxLength(80); search.setValue(state.query);
            search.setHint(text("search"));
            search.setResponder(value -> { state.query = value; state.catalogScroll = 0; });
        }
        searchBounds = new HudRect(0, y, width, 20);
        search.setX(24); search.setY(y + 6); search.setWidth(Math.max(8, width - 32));
        search.setAlpha(alpha / 255f);
        search.setTextColor(color(TEXT, alpha));
        search.setCursorColor(theme);
        softRect(g, searchBounds, color(0x33454F, alpha / 3));
        g.fill(7, y + 19, Math.max(7, width - 7), y + 20,
                color(search.isFocused() ? theme : MUTED, alpha / (search.isFocused() ? 2 : 8)));
        CollectionJournalVisuals.search(g, 8, y + 5, search.isFocused() ? theme : MUTED, alpha);
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
        String hoveredTrack = null;
        for (int i = range[0]; i < range[1]; i++) {
            var binding = filtered.get(i);
            var entry = entry(binding);
            if (entry == null) continue;
            int x = box.x() + (i % layout.columns()) * (layout.cardWidth() + CollectionJournalLayout.GAP);
            int y = box.y() + (i / layout.columns()) * (layout.cardHeight() + CollectionJournalLayout.GAP) - (int) state.catalogScroll;
            var card = new HudRect(x, y, layout.cardWidth(), layout.cardHeight());
            if (!visible(card) || y >= box.bottom() || card.bottom() <= box.y()) continue;
            addAction(card, () -> { state.select(binding.bindingId()); trackDwell.reset(); if (search != null) search.setFocused(false); }, box);
            boolean hovered = hit(card) && box.contains(mouseX, mouseY);
            boolean selected = binding.bindingId().equals(state.selection);
            float amount = hoverAmounts.getOrDefault(binding.bindingId(), 0f);
            amount = HudAnimUtil.lerp(amount, hovered ? 1f : 0f, .2f, screen.getDt());
            hoverAmounts.put(binding.bindingId(), amount);
            specimen(g, card, selected, amount, theme, alpha);
            var bookmarkBox = new HudRect(x + 2, y + 2, 18, 18);
            boolean favorite = CollectionFavoritesStore.INSTANCE.isFavorite(binding.entryId());
            boolean bookmarkHovered = hit(bookmarkBox) && box.contains(mouseX, mouseY);
            if (bookmarkHovered) softRect(g, bookmarkBox, color(theme, alpha / 10));
            bookmark(g, x + 7, y + 6, favorite, favorite || bookmarkHovered ? theme : FAINT, alpha);
            addAction(bookmarkBox, () -> { CollectionFavoritesStore.INSTANCE.toggle(binding.entryId()); trackDwell.reset(); }, box);
            boolean focused = questId.equals(ClientQuestTrackingController.INSTANCE.trackedQuestId())
                    && phaseId.equals(ClientQuestTrackingController.INSTANCE.trackedPhaseId())
                    && binding.bindingId().equals(QuestTrackingPresentationState.INSTANCE.collectionBindingIdFor(questId));
            if (focused) focus(g, card.right() - 14, y + 7, theme, alpha);
            if (binding.revealed()) drawEntryIcon(g, entry, binding, x + (card.width() - 28) / 2, y + 8, 28, alpha, theme, box);
            else {
                iconPlate(g, x + (card.width() - 38) / 2, y + 3, 38, MUTED, alpha, amount);
                g.drawCenteredString(screen.getFont(), Component.literal("?"), x + card.width() / 2, y + 16,
                        color(FAINT, alpha));
            }
            Component title = binding.revealed() ? entry.getDisplayName().copy().withStyle(ChatFormatting.BOLD) : text("unknown_entry");
            var fitted = StyledTextUtil.fitSingleLine(screen.getFont(), title, card.width() - 16);
            g.drawString(screen.getFont(), fitted, x + (card.width() - screen.getFont().width(fitted)) / 2, y + 44,
                    color(binding.revealed() ? TEXT : MUTED, alpha), false);
            Component status = binding.complete() ? text("achieved") : !binding.revealed() ? text("undiscovered") : text("investigating");
            var spec = definition.getPhase(phaseId).getCollectionSheet().getBinding(binding.bindingId());
            if (spec != null && spec.isOptional()) status = status.copy().append(" · ").append(text("optional"));
            boolean canTrack = canTrack(binding);
            Component quickLabel = text(focused ? "quick_tracked" : "quick_track");
            var statusLine = StyledTextUtil.fitSingleLine(screen.getFont(), status, card.width() - 32);
            int statusWidth = Math.min(card.width() - 8, Math.max(screen.getFont().width(statusLine),
                    canTrack ? Math.max(screen.getFont().width(text("quick_track")),
                            screen.getFont().width(text("quick_tracked"))) : 0) + 24);
            int statusX = x + (card.width() - statusWidth) / 2, statusY = card.bottom() - 23;
            var statusBox = new HudRect(statusX, statusY, statusWidth, 16);
            boolean statusHovered = canTrack && hit(statusBox) && box.contains(mouseX, mouseY);
            if (statusHovered) hoveredTrack = binding.bindingId();
            boolean ready = statusHovered && trackDwell.ready(binding.bindingId());
            if (ready) statusLine = StyledTextUtil.fitSingleLine(screen.getFont(), quickLabel, statusWidth - 24);
            int statusColor = binding.complete() ? COMPLETE : binding.revealed() ? MUTED : FAINT;
            if (ready) statusColor = theme;
            softRect(g, statusBox, color(statusColor, alpha / (ready ? 6 : 11)));
            if (ready) focus(g, statusX + 6, statusY + 3, statusColor, alpha);
            else status(g, statusX + 6, statusY + 3, binding.complete(), statusColor, alpha);
            g.drawString(screen.getFont(), statusLine, statusX + 18, statusY + 3, color(statusColor, alpha), false);
            if (canTrack) addAction(statusBox, () -> {
                if (trackDwell.ready(binding.bindingId())) ClientQuestTrackingController.INSTANCE
                        .requestCollectionFocus(questId, phaseId, binding.bindingId());
            }, box);
            if (hovered) screen.requestPointerCursor();
        }
        trackDwell.update(hoveredTrack, screen.getDt());
        screen.disableScissor(g);
        HudRect track = CollectionJournalLayout.scrollbarTrack(box);
        catalogScrollbar.render(g, track, catalogContentHeight, state.catalogScroll, alpha / 255f, MUTED);
        if (interactive) catalogScrollbar.requestPointer(mouseX, mouseY, track, 8, catalogContentHeight, state.catalogScroll);
    }

    private void drawEntryIcon(GuiGraphics g, CollectionEntryDefinition entry, CollectionBindingProgress binding,
                               int x, int y, int size, int alpha, int theme, HudRect clip) {
        var context = CollectionEntryIcons.context(questId, phaseId, binding.bindingId(), entry);
        var bounds = new HudRect(x - 3, y - 3, size + 6, size + 6);
        boolean hovered = hit(bounds) && clip.contains(mouseX, mouseY);
        var selected = screen.getObjectiveIcons().select(context, hovered, interactive);
        if (!selected.available()) return;
        float hover = screen.getObjectiveIcons().hoverAmount(context, hovered, screen.getDt());
        iconPlate(g, x - 4, y - 4, size + 8, theme, alpha, hover);
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
        if (binding.discovered() && interactive) {
            String key = questId + "/" + phaseId + "/" + binding.bindingId();
            var blocks = binding.content().stream().map(CollectionContentBlock::blockId).toList();
            if (!blocks.equals(readContentSignatures.get(key))) {
                ClientQuestCache.INSTANCE.markCollectionSeen(questId, phaseId, binding.bindingId());
                readContentSignatures.put(key, blocks);
            }
        }
        panel(g, box, theme, alpha);
        int x = box.x() + 12, width = Math.max(1, box.width() - 24), y = box.y() + 9;
        int closeWidth = Math.min(Math.max(1, width / 2),
                screen.getFont().width(text("back_catalog")) + 10);
        int closeX = box.right() - closeWidth - 6;
        boolean canTrack = canTrack(binding);
        int trackWidth = canTrack ? Math.min(Math.max(1, width / 2), screen.getFont().width(text("track_entry")) + 10) : 0;
        int trackX = closeX - trackWidth - 6;
        var archiveTitle = StyledTextUtil.fitSingleLine(screen.getFont(), text("entry_archive"),
                Math.max(1, (canTrack ? trackX : closeX) - x - 8));
        g.drawString(screen.getFont(), archiveTitle, x, y, color(MUTED, alpha), false);
        button(g, new HudRect(closeX, box.y() + 4, closeWidth, 18),
                text("back_catalog"), false, alpha, theme, this::closeDetail);
        if (canTrack) button(g, new HudRect(trackX, box.y() + 4, trackWidth, 18), text("track_entry"),
                false, alpha, theme, () -> ClientQuestTrackingController.INSTANCE.requestCollectionFocus(questId, phaseId, binding.bindingId()));
        g.fill(x, box.y() + 25, x + width, box.y() + 26, color(MUTED, alpha / 10));
        HudRect body = CollectionJournalLayout.detailBody(box);
        detailViewport = body;
        x = body.x(); width = body.width();
        state.detailScroll = CollectionJournalState.clampScroll(state.detailScroll, detailContentHeight, body.height());
        clip(g, body);
        y = body.y() - (int) state.detailScroll;
        int start = y;
        if (!binding.revealed()) {
            y = drawWrapped(g, text("unknown_entry"), x, y, width, TEXT, alpha) + 12;
            y = drawWrapped(g, text("unknown_hint"), x, y, width, MUTED, alpha);
        } else {
            drawEntryIcon(g, entry, binding, x + 4, y + 4, 32, alpha, theme, body);
            var icon = screen.getObjectiveIcons().resolve(CollectionEntryIcons.context(questId, phaseId, binding.bindingId(), entry));
            int nameX = icon.available() ? x + 48 : x;
            int nameY = drawWrapped(g, entry.getDisplayName().copy().withStyle(ChatFormatting.BOLD),
                    nameX, y + 3, x + width - nameX, TEXT, alpha);
            Component record = text("record_status", text(binding.researchComplete() ? entry.getResearchObjectives().isEmpty() ? "record_recorded" : "record_researched"
                    : binding.discovered() ? "record_discovered" : "undiscovered"));
            int recordEnd = drawWrapped(g, record, nameX, nameY + 3, x + width - nameX, MUTED, alpha);
            y = Math.max(y + 44, recordEnd + 10);
            if (!entry.getDescription().getString().isEmpty()) y = drawWrapped(g, entry.getDescription(), x, y, width, TEXT, alpha) + 12;
            for (var block : binding.content()) {
                var blockText = block.text().resolve(null, QuestTextContext.empty());
                if (!blockText.getString().isEmpty()) y = drawWrapped(g, blockText, x, y, width, TEXT, alpha) + 9;
                GuideMediaDefinition media = block.media();
                if (media != null) {
                    if (GuideImageRenderer.available(media)) {
                        int imageWidth = Math.max(1, width - 8);
                        int imageH = Math.max(36, Math.min(112, Math.round(imageWidth * (float) media.getHeight() / Math.max(1, media.getWidth()))));
                        var imageBox = new HudRect(x, y, width, imageH + 8);
                        if (visible(imageBox) && imageBox.bottom() > body.y() && imageBox.y() < body.bottom()) {
                            softRect(g, imageBox, color(theme, alpha / 18));
                            GuideImageRenderer.draw(g, media, x + 4, y + 4, imageWidth, imageH,
                                    block.fit() == CollectionMediaFit.COVER ? GuideImageLayout.Fit.COVER : GuideImageLayout.Fit.CONTAIN,
                                    alpha / 255f);
                            if (block.zoomable()) {
                                Component caption = block.caption().resolve(null, QuestTextContext.empty());
                                addAction(imageBox, () -> imageViewer.open(media, caption), body);
                                if (hit(imageBox) && body.contains(mouseX, mouseY)) {
                                    screen.requestPointerCursor();
                                    var zoom = new HudRect(imageBox.right() - 22, imageBox.bottom() - 22, 17, 17);
                                    softRect(g, zoom, color(0x111D25, alpha * 4 / 5));
                                    CollectionJournalVisuals.search(g, zoom.x() + 3, zoom.y() + 3, TEXT, alpha);
                                }
                            }
                        }
                        y += imageH + 14;
                    } else y = drawWrapped(g, text("image_missing"), x, y, width, FAINT, alpha) + 5;
                }
                var caption = block.caption().resolve(null, QuestTextContext.empty());
                if (!caption.getString().isEmpty()) y = drawWrapped(g, caption, x, y, width, MUTED, alpha) + 12;
            }
            int complete = (int) binding.requirements().stream().filter(CollectionRequirementProgress::complete).count();
            g.fill(x, y, x + width, y + 1, color(MUTED, alpha / 10));
            y += 11;
            String requirementCount = complete + " / " + binding.requirements().size();
            g.drawString(screen.getFont(), text("requirements"), x, y, color(TEXT, alpha), false);
            g.drawString(screen.getFont(), requirementCount, x + width - screen.getFont().width(requirementCount), y,
                    color(binding.complete() ? COMPLETE : MUTED, alpha), false);
            y += 16;
            bar(g, x, y, width, complete, binding.requirements().size(), theme, alpha);
            y += 12;
            for (var requirement : binding.requirements()) {
                if (requirement.objective() != null && requirement.objective().isHidden()) continue;
                y = requirement(g, binding, requirement, x, y, width, body, alpha, theme) + 9;
            }
            if (!canTrack) {
                y = drawWrapped(g, text(binding.complete() ? "entry_achieved" : "chapter_inactive"), x, y, width, COMPLETE, alpha) + 10;
            }
            y = renderEntryRewards(g, binding, x, y, width, body, alpha, theme);
            if (!entry.getRelatedItems().isEmpty()) {
                g.fill(x, y, x + width, y + 1, color(0xFFFFFF, alpha / 10)); y += 10;
                g.drawString(screen.getFont(), text("related_items"), x, y, color(MUTED, alpha), false); y += 16;
                for (var itemId : entry.getRelatedItems()) {
                    var item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
                    if (item == null) continue;
                    ItemStack stack = item.getDefaultInstance();
                    if (stack.isEmpty()) continue;
                    var iconBox = new HudRect(x, y, 22, 22);
                    if (visible(iconBox) && iconBox.bottom() > body.y() && iconBox.y() < body.bottom()) {
                        boolean hovered = hit(iconBox) && body.contains(mouseX, mouseY);
                        if (hovered) softRect(g, new HudRect(x, y, width, 24), color(theme, alpha / 14));
                        ObjectiveIconAlpha.renderItem(g, stack, x + 2, y + 2, 18, alpha / 255f);
                        item(g, binding.bindingId(), stack, iconBox, hovered, body);
                    }
                    y = Math.max(y + 27, drawWrapped(g, stack.getHoverName(), x + 28, y + 6, width - 28, TEXT, alpha) + 7);
                }
            }
        }
        detailContentHeight = y - start + 8;
        screen.disableScissor(g);
        HudRect track = CollectionJournalLayout.scrollbarTrack(body);
        detailScrollbar.render(g, track, detailContentHeight, state.detailScroll, alpha / 255f, MUTED);
        if (interactive) detailScrollbar.requestPointer(mouseX, mouseY, track, 8, detailContentHeight, state.detailScroll);
    }

    private int renderEntryRewards(GuiGraphics g, CollectionBindingProgress binding, int x, int y, int width,
                                   HudRect body, int alpha, int theme) {
        if (binding.entryRewards().isEmpty()) return y;
        g.fill(x, y, x + width, y + 1, color(MUTED, alpha / 10)); y += 11;
        g.drawString(screen.getFont(), text("entry_rewards"), x, y, color(TEXT, alpha), false); y += 18;
        // Progress and reward definitions here are already scoped and filtered by the server projection.
        for (var projected : binding.entryRewards()) {
            var rewardDefinition = projected.definition();
            String triggerKey = switch (rewardDefinition.trigger()) {
                case DISCOVERED -> "entry_reward_discovered";
                case RESEARCH_COMPLETE -> "entry_reward_research";
                case BINDING_COMPLETE -> "entry_reward_binding";
            };
            if (rewardDefinition.trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE
                    && !projected.sourceRunId().isEmpty() && runtime != null && runtime.getCollectionData() != null
                    && !projected.sourceRunId().equals(runtime.getCollectionData().getRunId())) triggerKey = "entry_reward_previous_run";
            String statusKey = projected.claimed() ? "claimed" : !projected.unlocked() ? "locked"
                    : rewardDefinition.grantMode() == EntryRewardGrantMode.AUTO ? "entry_reward_auto" : "claim";
            Component statusText = text(statusKey);
            int statusWidth = Math.min(Math.max(1, width / 2), screen.getFont().width(statusText) + 14);
            int headerY = y;
            int headerEnd = drawWrapped(g, text(triggerKey), x, y, Math.max(1, width - statusWidth - 10), MUTED, alpha);
            var claimBox = new HudRect(x + width - statusWidth, headerY - 3, statusWidth, 18);
            if (projected.canClaim()) {
                String targetQuest = questId, targetPhase = phaseId;
                String targetRun = projected.sourceRunId();
                button(g, claimBox, statusText, false, alpha, theme, () -> {
                    if (screen.getMinecraft().getConnection() != null)
                        ArcQuestNetwork.sendClaimCollectionEntryReward(C2SClaimCollectionEntryRewardPacket.of(
                                targetQuest, targetRun, targetPhase, binding.bindingId(), rewardDefinition.rewardId()));
                }, body);
            } else drawWrapped(g, statusText, claimBox.x() + 5, headerY, Math.max(1, statusWidth - 10),
                    projected.claimed() ? COMPLETE : FAINT, alpha);
            y = Math.max(headerEnd + 7, headerY + 20);
            for (var reward : rewardDefinition.rewards()) {
                if (reward instanceof ItemReward itemReward) {
                    ItemStack stack = new ItemStack(itemReward.getItem(), itemReward.getCount());
                    var iconBox = new HudRect(x, y, 22, 22);
                    if (visible(iconBox) && iconBox.bottom() > body.y() && iconBox.y() < body.bottom()) {
                        boolean hovered = hit(iconBox) && body.contains(mouseX, mouseY);
                        if (hovered) softRect(g, new HudRect(x, y, width, 24), color(theme, alpha / 14));
                        ObjectiveIconAlpha.renderItem(g, stack, x + 2, y + 2, 18, alpha / 255f);
                        if (interactive && (projected.unlocked() || projected.claimed())) {
                            JeiScreenIngredients.collectionEntryRewardItem(screen, g, questId, phaseId,
                                    binding.bindingId(), rewardDefinition.rewardId(), stack, x, y, 22, 22);
                            var bounded = intersect(iconBox, body);
                            if (bounded.width() > 0 && bounded.height() > 0) itemHits.add(bounded);
                        }
                        if (hovered) { screen.setHoveredRewardTooltip(stack); screen.requestPointerCursor(); }
                    }
                    Component rewardName = stack.getHoverName().copy();
                    if (itemReward.getCount() > 1) rewardName = rewardName.copy().append(" ×" + itemReward.getCount());
                    y = Math.max(y + 27, drawWrapped(g, rewardName, x + 28, y + 6,
                            Math.max(1, width - 28), TEXT, alpha) + 7);
                } else y = drawWrapped(g, Component.literal(reward.describe()), x, y + 4, width, TEXT, alpha) + 7;
            }
            y += 8;
        }
        return y + 4;
    }

    private int requirement(GuiGraphics g, CollectionBindingProgress binding, CollectionRequirementProgress req,
                            int x, int y, int width, HudRect body, int alpha, int theme) {
        if (req.objective() == null) {
            String count = req.current() + "/" + req.target();
            int cw = screen.getFont().width(count) + 8;
            status(g, x + 1, y + 1, req.complete(), req.complete() ? COMPLETE : FAINT, alpha);
            int end = drawWrapped(g, req.label(), x + 14, y, Math.max(1, width - cw - 14), req.complete() ? COMPLETE : TEXT, alpha);
            g.drawString(screen.getFont(), count, x + width - screen.getFont().width(count), y,
                    color(MUTED, alpha), false);
            return end;
        }
        var context = new ObjectiveIconContext(questId, phaseId, req.objectiveIndex(), req.objective(),
                req.current(), req.target(), ObjectiveIconsClient.generation());
        var rowLayout = ObjectiveRowRenderer.layout(screen, context, width, false);
        // Offscreen requirement rows contribute their measured height without item render/JEI work.
        if (y + rowLayout.height() <= body.y() || y >= body.bottom()) return y + rowLayout.height();
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
        String key = width + "/" + text.hashCode() + "/" + text.getString() + "/" + net.minecraft.locale.Language.getInstance().hashCode();
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
    private boolean canTrack(CollectionBindingProgress binding) {
        return binding.revealed() && !binding.complete() && runtime != null && runtime.isPhaseActive(phaseId)
                && binding.requirements().stream().anyMatch(p -> !p.complete() && p.objective() != null && !p.objective().isHidden());
    }
    private void button(GuiGraphics g, HudRect rect, Component label, boolean selected, int alpha, int theme, Runnable action, HudRect clip) {
        boolean hover = hit(rect) && clip.contains(mouseX, mouseY);
        softRect(g, rect, color(selected || hover ? theme : 0x637B87, alpha / (selected ? 5 : hover ? 11 : 22)));
        var line = StyledTextUtil.fitSingleLine(screen.getFont(), label, Math.max(1, rect.width() - 10));
        g.drawString(screen.getFont(), line, rect.x() + 5, rect.y() + 5, color(selected || hover ? TEXT : MUTED, alpha), false);
        if (selected) g.fill(rect.x() + 4, rect.bottom() - 1, rect.right() - 4, rect.bottom(), color(theme, alpha));
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
        g.fill(x, y, x + w, y + 3, color(MUTED, alpha / 9));
        int fill = target <= 0 ? 0 : Math.max(0, Math.min(w, (int) (w * (double) current / target)));
        if (fill > 0) g.fill(x, y, x + fill, y + 3, color(theme, alpha * 4 / 5));
    }
    public boolean mouseClicked(double x, double y) {
        if (!usingSheets) return legacy.mouseClicked(x, y);
        if (!interactive || x + absX < clipX1 || x + absX >= clipX2
                || y + absY < clipY1 || y + absY >= clipY2) return false;
        if (!detailVisible() && layout != null) {
            var scroll = catalogScrollbar.mouseClicked(x, y, CollectionJournalLayout.scrollbarTrack(layout.catalog()),
                    8, catalogContentHeight, state.catalogScroll);
            if (scroll.consumed()) { state.catalogScroll = scroll.scrollOffset(); return true; }
        }
        // Icon clicks cannot select a specimen, submit an offer or change the tracking focus.
        if (JeiScreenIngredients.isRuntimeAvailable() && itemHits.stream().anyMatch(b -> b.contains(x, y))) return true;
        if (search != null && !detailVisible()) {
            boolean inside = searchBounds.contains(x, y);
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
    public boolean mouseDraggedAbsolute(double x, double y, int button) {
        if (!usingSheets || !interactive || state == null || layout == null || button != 0 || detailVisible()) return false;
        var scroll = catalogScrollbar.mouseDragged(y - absY, CollectionJournalLayout.scrollbarTrack(layout.catalog()),
                catalogContentHeight, state.catalogScroll);
        if (scroll.consumed()) state.catalogScroll = scroll.scrollOffset();
        return scroll.consumed();
    }
    public boolean mouseReleased(int button) { return catalogScrollbar.mouseReleased(button); }
    public boolean mouseScrolled(double x, double y, double delta) {
        if (!usingSheets || !interactive || state == null || layout == null || imageViewer.isOpen() || detailVisible()) return false;
        var catalog = layout.catalog();
        if (new HudRect(catalog.x(), catalog.y(), catalog.width() + CollectionJournalLayout.SCROLLBAR_GUTTER,
                catalog.height()).contains(x, y)) {
            state.catalogScroll = CollectionJournalState.clampScroll(state.catalogScroll - delta * 24, catalogContentHeight, layout.catalog().height());
            return true;
        }
        return false;
    }
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (detailVisible()) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { closeDetail(); return true; }
            return true;
        }
        if (!usingSheets || search == null || !search.isFocused()) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { search.setFocused(false); return true; }
        search.keyPressed(key, scan, modifiers);
        return true;
    }
    public boolean charTyped(char character, int modifiers) {
        if (detailVisible()) return true;
        return usingSheets && search != null && search.isFocused() && search.charTyped(character, modifiers);
    }
    private record Action(HudRect box, Runnable action) {}
}
