package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
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
import org.arcadia.arc_quest.quest.network.C2SClaimCollectionEntryRewardPacket;
import org.arcadia.arc_quest.quest.logic.profile.collection.CollectionProgressProjector;
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
    private final CollectionCatalogOrder catalogOrder = new CollectionCatalogOrder();
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
    private Map<net.minecraft.resources.ResourceLocation, List<CollectionBindingProgress>> specimenGroups = Map.of();
    private CollectionSheetProgress filteredProgress;
    private CollectionSheetProgress favoritesProgress;
    private long favoritesRevision = -1;
    private long visibleFavorites;
    private String filterSignature = "";
    private String browserFilter = "";
    private Language filterLanguage;
    private int detailContentHeight, catalogContentHeight;
    private CollectionBindingProgress closingBinding;
    private CollectionEntryDefinition closingEntry;
    private Set<String> closingOutcomeIds = Set.of();
    private boolean closingCanTrack;
    private String closingRunId = "";
    private int lastDetailTheme;
    private CollectionBindingProgress recordProgressBinding;
    private CollectionEntryDefinition recordProgressEntry;
    private List<CollectionRequirementProgress> discoveryProgress = List.of(), researchProgress = List.of();
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
        catalogOrder.reset();
        catalogScrollbar.mouseReleased(0); detailScrollbar.mouseReleased(0);
        state = null; phaseId = ""; questId = ""; search = null;
        readContentSignatures.clear();
        filteredProgress = null; favoritesProgress = null; progress = null;
        filterLanguage = null;
        specimenGroups = Map.of();
        recordProgressBinding = null; recordProgressEntry = null;
        closingBinding = null; closingEntry = null; closingOutcomeIds = Set.of();
        discoveryProgress = researchProgress = List.of();
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
        catalogOrder.reset();
        imageViewer.close(); actions.clear(); itemHits.clear(); hoverAmounts.clear();
        filteredProgress = null;
    }

    public void closeDetail() {
        if (state != null) {
            closingBinding = progress == null ? null : progress.binding(state.selection);
            closingEntry = closingBinding == null ? null : entry(closingBinding);
            var record = closingEntry == null ? null : ClientQuestCache.INSTANCE.getCollectionRecord(closingEntry.getEntryId());
            closingOutcomeIds = record == null ? Set.of() : Set.copyOf(record.getOutcomeIds());
            closingCanTrack = closingBinding != null && canTrack(closingBinding);
            closingRunId = runtime != null && runtime.getCollectionData() != null ? runtime.getCollectionData().getRunId() : "";
            state.expanded = false;
        }
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
        // Minecraft's Font treats alpha 0..3 as an unspecified color and replaces it with 255.
        // Keep the modal input barrier, but never draw its near-transparent terminal frames.
        if (!CollectionDetailTransition.shouldDraw(alpha)) return;
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350);
        graphics.fill(0, 0, logicalWidth, logicalHeight, color(0x050A0D, alpha * 3 / 4));
        renderDetail(graphics, modalBounds, alpha, !detailOpen() && closingBinding != null ? lastDetailTheme : screen.getCurrentThemeColor());
        graphics.flush();
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
            catalogOrder.reset();
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
        if (catalogOrder.configure(definition.getCollectionConfig(), definition.getPhase(phaseId).getCollectionSheet()))
            filterSignature = "";
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

        // Keep the catalog height independent of the enclosing panel's animated scroll offset.
        int bodyHeight = Math.max(CollectionJournalLayout.CARD_HEIGHT,
                Math.min(360, bottom - top - y - 46));
        int previousColumns = layout == null ? 0 : layout.columns();
        layout = CollectionJournalLayout.measure(width, y + 26, bodyHeight, false);
        filter(previousColumns);
        renderSearch(graphics, y, width, alpha, theme);
        renderCatalog(graphics, layout.catalog(), alpha, theme);
        y = layout.catalog().bottom() + 12;
        y = renderChoices(graphics, y, width, alpha, theme);
        return y;
    }

    public String selectedRewardCategory() {
        return state == null || FAVORITES_CATEGORY.equals(state.category) ? "" : state.category;
    }

    private int renderCategories(GuiGraphics g, int y, int width, int alpha, int theme) {
        var categories = catalogOrder.categories();
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
                    .filter(p -> CollectionFavoritesStore.INSTANCE.isFavorite(p.entryId()))
                    .map(CollectionBindingProgress::entryId).distinct().count();
            favoritesProgress = progress; favoritesRevision = revision;
        }
        return visibleFavorites;
    }

    private void filter(int previousColumns) {
        String signature = state.category + "\n" + state.query + "\n" + CollectionFavoritesStore.INSTANCE.revision();
        Language language = Language.getInstance();
        if (filteredProgress == progress && filterSignature.equals(signature) && filterLanguage == language) return;
        String nextBrowserFilter = state.category + "\n" + state.query;
        boolean preserveScroll = filteredProgress != null && browserFilter.equals(nextBrowserFilter);
        var previousEntries = filtered;
        if (filteredProgress != progress) {
            state.validate(progress.bindings().stream().filter(CollectionBindingProgress::visible)
                    .map(CollectionBindingProgress::bindingId).toList());
        }
        String needle = state.query.strip().toLowerCase(Locale.ROOT);
        catalogOrder.refresh(progress, nextBrowserFilter, CollectionFavoritesStore.INSTANCE::snapshot);
        filtered = progress.bindings().stream().filter(p -> {
            if (!p.visible()) return false;
            var entry = entry(p);
            if (entry == null) return false;
            if (FAVORITES_CATEGORY.equals(state.category)) {
                if (!CollectionFavoritesStore.INSTANCE.isFavorite(p.entryId())) return false;
            } else if (!state.category.isEmpty() && !state.category.equals(entry.getCategoryId())) return false;
            if (needle.isEmpty()) return true;
            // An unrevealed specimen never contributes its real title, ID or hidden body to search.
            String searchable = p.revealed() ? entry.getDisplayName().getString() + " " + entry.getDescription().getString()
                    : p.hasPublicClue() ? p.publicClue().getString() : "";
            return searchable.toLowerCase(Locale.ROOT).contains(needle);
        }).toList();
        // One object has one card even when this phase contains several investigations of it.
        Map<net.minecraft.resources.ResourceLocation, List<CollectionBindingProgress>> specimens = new LinkedHashMap<>();
        for (var candidate : filtered) specimens.computeIfAbsent(candidate.entryId(), ignored -> new ArrayList<>()).add(candidate);
        specimens.replaceAll((entryId, group) -> catalogOrder.sortBindings(group, CollectionBindingProgress::bindingId));
        specimenGroups = specimens;
        filtered = specimens.values().stream().map(group -> group.stream().filter(p -> !p.complete()).findFirst()
                .orElse(group.get(0))).toList();
        filtered = catalogOrder.sort(filtered, CollectionBindingProgress::entryId);
        if (preserveScroll) state.catalogScroll = CollectionCatalogOrder.preserveScroll(previousEntries, filtered,
                CollectionBindingProgress::entryId, state.catalogScroll, previousColumns, layout.columns(),
                CollectionJournalLayout.CARD_HEIGHT + CollectionJournalLayout.GAP);
        browserFilter = nextBrowserFilter;
        filteredProgress = progress; filterSignature = signature;
        filterLanguage = language;
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
            addAction(card, () -> { state.select(binding.bindingId()); state.chooseInitialDetailView(canTrack(binding));
                trackDwell.reset(); if (search != null) search.setFocused(false); }, box);
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
            var claimable = specimenGroups.getOrDefault(binding.entryId(), List.of(binding)).stream()
                    .filter(CollectionRewardFeedback::canClaim).findFirst().orElse(null);
            boolean canClaim = claimable != null;
            if (focused) focus(g, card.right() - (canClaim ? 24 : 14), y + 7, theme, alpha);
            if (canClaim) {
                CollectionRewardVisuals.dot(g, card.right() - 8, y + 9, alpha);
                addAction(new HudRect(card.right() - 18, y, 18, 18), () -> {
                    state.select(claimable.bindingId()); state.chooseInitialDetailView(canTrack(claimable));
                    state.firstRewards = claimable.entryRewards().stream().anyMatch(p -> p.canClaim()
                            && p.definition().trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE);
                    if (search != null) search.setFocused(false);
                }, box);
            }
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
            Component status = binding.complete() ? text("achieved") : !binding.revealed() ? text("undiscovered")
                    : !binding.discovered() ? text("record_public_undiscovered") : text("investigating");
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
            float switchAmount = canTrack ? trackDwell.appearance(binding.bindingId()) : 0;
            var quickLine = StyledTextUtil.fitSingleLine(screen.getFont(), quickLabel, statusWidth - 24);
            int statusColor = binding.complete() ? COMPLETE : binding.revealed() ? MUTED : FAINT;
            int tint = HudAnimUtil.lerpColor(statusColor, theme, switchAmount);
            softRect(g, statusBox, color(tint, Math.round(alpha * (.09f + .13f * switchAmount))));
            int idleAlpha = Math.round(alpha * (1 - switchAmount)), trackAlpha = Math.round(alpha * switchAmount);
            if (idleAlpha > 3) {
                status(g, statusX + 6, statusY + 3, binding.complete(), statusColor, idleAlpha);
                g.drawString(screen.getFont(), statusLine, statusX + 18,
                        statusY + 3 - Math.round(switchAmount * 2), color(statusColor, idleAlpha), false);
            }
            if (trackAlpha > 3) {
                focus(g, statusX + 6, statusY + 3, theme, trackAlpha);
                g.drawString(screen.getFont(), quickLine, statusX + 18,
                        statusY + 5 - Math.round(switchAmount * 2), color(theme, trackAlpha), false);
                g.fill(statusX + 5, statusBox.bottom() - 1, statusBox.right() - 5, statusBox.bottom(),
                        color(theme, Math.round(trackAlpha * .65f)));
            }
            if (canTrack) addAction(statusBox, () -> ClientQuestTrackingController.INSTANCE
                    .requestCollectionFocus(questId, phaseId, binding.bindingId()), box);
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
        float hover = screen.getObjectiveIcons().hoverAmount(context, hovered,
                !detailOpen() && closingBinding != null ? 0 : screen.getDt());
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
        var binding = !detailOpen() && closingBinding != null ? closingBinding
                : progress.bindings().stream().filter(p -> p.bindingId().equals(state.selection)).findFirst().orElse(null);
        if (binding == null) return;
        var entry = !detailOpen() && closingEntry != null ? closingEntry : entry(binding);
        if (entry == null) return;
        if (detailOpen()) { closingBinding = null; closingEntry = null; lastDetailTheme = theme; }
        if (binding.discovered() && state.archiveView && interactive) {
            String key = questId + "/" + phaseId + "/" + binding.bindingId();
            var blocks = binding.content().stream().map(CollectionContentBlock::blockId).toList();
            if (!blocks.equals(readContentSignatures.get(key))) {
                ClientQuestCache.INSTANCE.markCollectionSeen(questId, phaseId, binding.bindingId());
                readContentSignatures.put(key, blocks);
            }
        }
        state.chooseInitialDetailView(detailCanTrack(binding));
        panel(g, box, theme, alpha);
        var workspace = CollectionDetailWorkspace.measure(box, !binding.entryRewards().isEmpty());
        var identity = workspace.identity();
        int closeX = box.right() - 25;
        button(g, new HudRect(closeX, box.y() + 6, 18, 18), Component.literal("×"),
                false, alpha, theme, this::closeDetail);
        int nameX = identity.x();
        if (binding.revealed()) {
            var icon = screen.getObjectiveIcons().resolve(CollectionEntryIcons.context(questId, phaseId, binding.bindingId(), entry));
            if (icon.available()) {
                drawEntryIcon(g, entry, binding, identity.x() + 2, identity.y() + 2, 28, alpha, theme, identity);
                nameX += 40;
            }
        }
        Component name = binding.revealed() ? entry.getDisplayName().copy().withStyle(ChatFormatting.BOLD) : text("unknown_entry");
        var title = StyledTextUtil.fitSingleLine(screen.getFont(), name, Math.max(1, closeX - nameX - 8));
        g.drawString(screen.getFont(), title, nameX, identity.y() + 3, color(TEXT, alpha), false);
        Component record = binding.revealed() ? text(binding.discovered() ? "record_recorded" : "record_public_undiscovered")
                : text("undiscovered");
        g.drawString(screen.getFont(), StyledTextUtil.fitSingleLine(screen.getFont(), record,
                Math.max(1, identity.right() - nameX)), nameX, identity.y() + 17, color(MUTED, alpha), false);

        var tabs = workspace.tabs();
        boolean canTrack = detailCanTrack(binding);
        int trackWidth = canTrack ? Math.min(tabs.width() / 3, screen.getFont().width(text("track_entry")) + 12) : 0;
        int tabWidth = Math.max(1, (tabs.width() - trackWidth - (canTrack ? 12 : 0)) / 2);
        tabWidth = Math.min(tabWidth, Math.max(screen.getFont().width(text("view_investigation")),
                screen.getFont().width(text("view_archive"))) + 16);
        button(g, new HudRect(tabs.x(), tabs.y(), tabWidth, tabs.height()), text("view_investigation"),
                !state.archiveView, alpha, theme, () -> switchDetailView(false));
        button(g, new HudRect(tabs.x() + tabWidth + 4, tabs.y(), tabWidth, tabs.height()), text("view_archive"),
                state.archiveView, alpha, theme, () -> switchDetailView(true));
        if (canTrack) button(g, new HudRect(tabs.right() - trackWidth, tabs.y(), trackWidth, tabs.height()), text("track_entry"),
                false, alpha, theme, () -> ClientQuestTrackingController.INSTANCE.requestCollectionFocus(questId, phaseId, binding.bindingId()));

        HudRect body = workspace.viewport();
        detailViewport = body;
        state.detailScroll = CollectionJournalState.clampScroll(state.detailScroll, detailContentHeight, body.height());
        clip(g, body);
        int y = body.y() - (int) state.detailScroll, start = y;
        if (!binding.revealed()) {
            y = drawWrapped(g, text("unknown_entry"), body.x(), y, body.width(), TEXT, alpha) + 12;
            y = drawWrapped(g, binding.hasPublicClue() ? binding.publicClue() : text("unknown_hint"),
                    body.x(), y, body.width(), MUTED, alpha);
        } else if (state.archiveView) {
            y = renderArchive(g, binding, entry, body.x(), y, body.width(), body, alpha, theme);
        } else {
            y = drawWrapped(g, text(entry.isUnifiedGameplay() ? "current_investigation_hint" : "legacy_investigation_hint"),
                    body.x(), y, body.width(), MUTED, alpha) + 8;
            y = renderBindingSelector(g, binding, body.x(), y, body.width(), body, alpha, theme);
            y = renderRequirements(g, binding, body.x(), y, body.width(), body, alpha, theme);
        }
        detailContentHeight = y - start + 8;
        if (state.archiveView) state.archiveContentHeight = detailContentHeight;
        else state.investigationContentHeight = detailContentHeight;
        screen.disableScissor(g);
        HudRect track = CollectionJournalLayout.scrollbarTrack(body);
        detailScrollbar.render(g, track, detailContentHeight, state.detailScroll, alpha / 255f, MUTED);
        if (interactive) detailScrollbar.requestPointer(mouseX, mouseY, track, 8, detailContentHeight, state.detailScroll);
        renderRewardStrip(g, binding, entry, workspace.rewards(), alpha, theme);
    }

    private void switchDetailView(boolean archive) {
        state.showArchive(archive);
        detailContentHeight = archive ? state.archiveContentHeight : state.investigationContentHeight;
        detailScrollbar.mouseReleased(0);
    }

    private int renderBindingSelector(GuiGraphics g, CollectionBindingProgress selected, int x, int y,
                                      int width, HudRect clip, int alpha, int theme) {
        var alternatives = specimenGroups.getOrDefault(selected.entryId(), List.of(selected));
        if (alternatives.size() < 2) return y;
        int index = 0;
        for (int i = 0; i < alternatives.size(); i++) if (alternatives.get(i).bindingId().equals(selected.bindingId())) index = i;
        Component label = text("investigation_variant", index + 1, alternatives.size());
        g.drawString(screen.getFont(), StyledTextUtil.fitSingleLine(screen.getFont(), label, Math.max(1, width - 44)),
                x, y + 5, color(MUTED, alpha), false);
        var previous = alternatives.get(Math.max(0, index - 1));
        var next = alternatives.get(Math.min(alternatives.size() - 1, index + 1));
        button(g, new HudRect(x + width - 38, y, 16, 18), Component.literal("‹"), false, alpha, theme,
                () -> selectInvestigation(previous), clip);
        button(g, new HudRect(x + width - 18, y, 16, 18), Component.literal("›"), false, alpha, theme,
                () -> selectInvestigation(next), clip);
        return y + 25;
    }

    private void selectInvestigation(CollectionBindingProgress binding) {
        state.select(binding.bindingId());
        state.chooseInitialDetailView(true);
        detailContentHeight = 0;
        detailScrollbar.mouseReleased(0);
    }

    private int renderArchive(GuiGraphics g, CollectionBindingProgress binding, CollectionEntryDefinition entry,
                              int x, int y, int width, HudRect body, int alpha, int theme) {
        if (!binding.discovered()) y = drawWrapped(g, text("public_archive_hint"), x, y, width, MUTED, alpha) + 10;
        if (!entry.getDescription().getString().isEmpty()) y = drawWrapped(g, entry.getDescription(), x, y, width, TEXT, alpha) + 12;
        y = renderRecordProgress(g, binding, entry, x, y, width, alpha, theme);
        if (entry.isUnifiedGameplay() && !entry.getOutcomes().isEmpty()) {
            y = drawWrapped(g, text("archive_outcomes"), x, y, width, MUTED, alpha) + 6;
            var record = ClientQuestCache.INSTANCE.getCollectionRecord(entry.getEntryId());
            for (var outcome : entry.getOutcomes()) {
                boolean attained = !detailOpen() && closingBinding != null
                        ? closingOutcomeIds.contains(outcome.outcomeId()) : record != null && record.hasOutcome(outcome.outcomeId());
                status(g, x + 1, y + 1, attained, attained ? COMPLETE : FAINT, alpha);
                y = drawWrapped(g, outcome.getDisplayName(), x + 14, y, Math.max(1, width - 14),
                        attained ? COMPLETE : MUTED, alpha) + 7;
            }
            y += 5;
        }
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
        return y;
    }

    private void renderRewardStrip(GuiGraphics g, CollectionBindingProgress binding, CollectionEntryDefinition entry,
                                   HudRect strip, int alpha, int theme) {
        if (binding.entryRewards().isEmpty() || strip.height() <= 0) return;
        clip(g, strip);
        int x = strip.x(), y = strip.y(), width = strip.width();
        boolean hasCurrent = binding.entryRewards().stream().anyMatch(p -> p.definition().trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE);
        boolean hasFirst = binding.entryRewards().stream().anyMatch(p -> p.definition().trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE);
        if (!hasCurrent) state.firstRewards = true;
        if (!hasFirst) state.firstRewards = false;
        g.fill(x, y, strip.right(), y + 1, color(MUTED, alpha / 8)); y += 5;
        int tabWidth = Math.min(Math.max(1, (width - 58) / 2), Math.max(screen.getFont().width(text("reward_current")),
                screen.getFont().width(text("reward_first"))) + 18);
        int firstX = x;
        if (hasCurrent) {
            button(g, new HudRect(x, y, tabWidth, 16), text("reward_current"), !state.firstRewards,
                    alpha, theme, () -> state.firstRewards = false, strip);
            if (binding.entryRewards().stream().anyMatch(p -> p.canClaim() && p.definition().trigger() == CollectionEntryRewardTrigger.BINDING_COMPLETE))
                CollectionRewardVisuals.dot(g, x + tabWidth - 3, y + 2, alpha);
            firstX += tabWidth + 4;
        }
        if (hasFirst) {
            button(g, new HudRect(firstX, y, tabWidth, 16), text("reward_first"), state.firstRewards,
                    alpha, theme, () -> state.firstRewards = true, strip);
            if (binding.entryRewards().stream().anyMatch(p -> p.canClaim() && p.definition().trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE))
                CollectionRewardVisuals.dot(g, firstX + tabWidth - 3, y + 2, alpha);
        }
        var rewards = binding.entryRewards().stream().filter(p -> (p.definition().trigger() != CollectionEntryRewardTrigger.BINDING_COMPLETE)
                == state.firstRewards).toList();
        int claimWidth = Math.min(Math.max(1, width / 3), Math.max(screen.getFont().width(text("claim_reward")),
                Math.max(screen.getFont().width(text("claimed")), screen.getFont().width(text("entry_reward_delivery_pending")))) + 16);
        int itemCapacity = Math.max(1, (width - claimWidth - 10) / 25);
        var pages = CollectionRewardStripPages.pages(rewards.stream().map(p -> p.definition().rewards().size()).toList(),
                rewards.stream().map(p -> p.definition().rewards().stream().anyMatch(r -> !(r instanceof ItemReward))).toList(), itemCapacity);
        int pageIndex = CollectionRewardStripPages.clamp(state.firstRewards ? state.firstRewardPage : state.currentRewardPage, pages.size());
        if (state.firstRewards) state.firstRewardPage = pageIndex; else state.currentRewardPage = pageIndex;
        if (pages.isEmpty()) { screen.disableScissor(g); return; }
        if (pages.size() > 1) {
            int previous = Math.max(0, pageIndex - 1), next = Math.min(pages.size() - 1, pageIndex + 1);
            button(g, new HudRect(strip.right() - 38, y, 16, 16), Component.literal("‹"), false, alpha, theme,
                    () -> setRewardPage(previous), strip);
            button(g, new HudRect(strip.right() - 18, y, 16, 16), Component.literal("›"), false, alpha, theme,
                    () -> setRewardPage(next), strip);
        }
        var page = pages.get(pageIndex);
        var projected = rewards.get(page.rewardIndex());
        var rewardDefinition = projected.definition();
        Component condition = switch (rewardDefinition.trigger()) {
            case DISCOVERED -> text("entry_reward_discovered");
            case RESEARCH_COMPLETE -> text("legacy_reward_research");
            case OUTCOME -> text("reward_outcome_condition", entry.getOutcome(rewardDefinition.outcomeId()) == null
                    ? text("archive_outcomes") : entry.getOutcome(rewardDefinition.outcomeId()).getDisplayName());
            case BINDING_COMPLETE -> text(!projected.sourceRunId().isEmpty() && !projected.sourceRunId().equals(detailRunId())
                    ? "entry_reward_previous_run" : "entry_reward_binding");
        };
        if (projected.deliveryPending()) condition = text("entry_reward_delivery_hint");
        y += 19;
        String pageCount = pages.size() > 1 ? (pageIndex + 1) + "/" + pages.size() : "";
        int pageCountWidth = screen.getFont().width(pageCount);
        g.drawString(screen.getFont(), StyledTextUtil.fitSingleLine(screen.getFont(), condition,
                Math.max(1, width - pageCountWidth - 8)), x, y, color(MUTED, alpha), false);
        if (!pageCount.isEmpty()) g.drawString(screen.getFont(), pageCount, strip.right() - pageCountWidth, y, color(FAINT, alpha), false);
        y += 13;
        var claimBox = new HudRect(strip.right() - claimWidth, y - 1, claimWidth, 22);
        if (projected.canClaim()) claimButton(g, claimBox, text("claim_reward"), alpha, theme, () -> claimEntryReward(binding, projected), strip);
        else {
            String statusKey = projected.deliveryPending() ? "entry_reward_delivery_pending" : projected.claimed() ? "claimed" : !projected.unlocked() ? "locked" : "entry_reward_auto";
            var statusText = StyledTextUtil.fitSingleLine(screen.getFont(), text(statusKey), claimWidth);
            g.drawString(screen.getFont(), statusText, claimBox.x() + (claimWidth - screen.getFont().width(statusText)) / 2,
                    y + 5, color(projected.deliveryPending() ? theme : projected.claimed() ? COMPLETE : FAINT, alpha), false);
        }
        for (int index = page.itemStart(); index < page.itemEnd(); index++) {
            var reward = rewardDefinition.rewards().get(index);
            if (reward instanceof ItemReward itemReward) {
                ItemStack stack = new ItemStack(itemReward.getItem(), itemReward.getCount());
                int itemX = x + (index - page.itemStart()) * 25;
                var iconBox = new HudRect(itemX, y - 1, 22, 22);
                boolean hovered = hit(iconBox) && strip.contains(mouseX, mouseY);
                if (hovered) softRect(g, iconBox, color(theme, alpha / 10));
                ObjectiveIconAlpha.renderItem(g, stack, itemX + 2, y + 1, 18, alpha / 255f);
                if (itemReward.getCount() > 1) {
                    String count = Integer.toString(itemReward.getCount());
                    g.pose().pushPose(); g.pose().translate(0, 0, 160);
                    g.drawString(screen.getFont(), count, itemX + 22 - screen.getFont().width(count), y + 13, color(TEXT, alpha), true);
                    g.pose().popPose();
                }
                if (interactive) {
                    JeiScreenIngredients.collectionEntryRewardItem(screen, g, questId, phaseId, binding.bindingId(),
                            rewardDefinition.rewardId(), stack, iconBox.x(), iconBox.y(), iconBox.width(), iconBox.height());
                    var bounded = intersect(iconBox, strip);
                    if (bounded.width() > 0 && bounded.height() > 0) itemHits.add(bounded);
                }
                if (hovered) { screen.setHoveredRewardTooltip(stack); screen.requestPointerCursor(); }
            } else {
                g.drawString(screen.getFont(), StyledTextUtil.fitSingleLine(screen.getFont(), reward.describeComponent(),
                        Math.max(1, width - claimWidth - 12)), x, y + 5, color(TEXT, alpha), false);
                break;
            }
        }
        screen.disableScissor(g);
    }

    private void setRewardPage(int page) {
        if (state.firstRewards) state.firstRewardPage = page;
        else state.currentRewardPage = page;
    }

    private void claimEntryReward(CollectionBindingProgress binding, CollectionEntryRewardProgress projected) {
        var currentBinding = ClientQuestCache.INSTANCE.getCollectionBindingProgress(questId, phaseId, binding.bindingId());
        boolean available = currentBinding != null && currentBinding.entryRewards().stream().anyMatch(row -> row.canClaim()
                && row.definition().rewardId().equals(projected.definition().rewardId()) && row.sourceRunId().equals(projected.sourceRunId())
                && row.sourcePhaseId().equals(projected.sourcePhaseId()) && row.sourceBindingId().equals(projected.sourceBindingId()));
        if (available && screen.getMinecraft().getConnection() != null)
            ArcQuestNetwork.sendClaimCollectionEntryReward(C2SClaimCollectionEntryRewardPacket.of(
                    questId, projected.sourceRunId(), projected.sourcePhaseId().isBlank() ? phaseId : projected.sourcePhaseId(),
                    projected.sourceBindingId().isBlank() ? binding.bindingId() : projected.sourceBindingId(), projected.definition().rewardId()));
    }

    private int renderRecordProgress(GuiGraphics g, CollectionBindingProgress binding, CollectionEntryDefinition entry,
                                     int x, int y, int width, int alpha, int theme) {
        if (recordProgressBinding != binding || recordProgressEntry != entry) {
            var record = ClientQuestCache.INSTANCE.getCollectionRecord(entry.getEntryId());
            discoveryProgress = binding.discovered() ? List.of() : CollectionProgressProjector.discoveryProgress(entry, record);
            researchProgress = entry.isUnifiedGameplay() ? List.of() : CollectionProgressProjector.researchProgress(entry, record);
            recordProgressBinding = binding; recordProgressEntry = entry;
        }
        if (!discoveryProgress.isEmpty())
            y = recordProgress(g, text("discovery_progress"), discoveryProgress, x, y, width, alpha, theme);
        if (!researchProgress.isEmpty()) {
            y = recordProgress(g, text("legacy_investigation"), researchProgress, x, y, width, alpha, theme);
            if (!binding.researchComplete())
                y = drawWrapped(g, text("research_locked_hint"), x + 2, y, Math.max(1, width - 4), MUTED, alpha) + 10;
        }
        return y;
    }

    private int recordProgress(GuiGraphics g, Component title, List<CollectionRequirementProgress> rows,
                               int x, int y, int width, int alpha, int theme) {
        g.fill(x, y, x + width, y + 1, color(MUTED, alpha / 10)); y += 10;
        y = drawWrapped(g, title, x, y, width, TEXT, alpha) + 7;
        // These are public permanent objectives, with no run index, submission action or run JEI registration.
        for (var row : rows) {
            String count = row.current() + " / " + row.target();
            int countWidth = screen.getFont().width(count);
            status(g, x + 1, y + 1, row.complete(), row.complete() ? COMPLETE : FAINT, alpha);
            Component label = row.label();
            if (row.optional()) label = label.copy().append(" · ").append(text("optional"));
            int end = drawWrapped(g, label, x + 14, y, Math.max(1, width - countWidth - 22),
                    row.complete() ? COMPLETE : TEXT, alpha);
            g.drawString(screen.getFont(), count, x + width - countWidth, y,
                    color(row.complete() ? COMPLETE : MUTED, alpha), false);
            bar(g, x + 14, end + 3, Math.max(1, width - 14), row.current(), row.target(), theme, alpha);
            y = end + 12;
        }
        return y + 4;
    }

    private int renderRequirements(GuiGraphics g, CollectionBindingProgress binding, int x, int y, int width,
                                   HudRect body, int alpha, int theme) {
        var requirements = binding.requirements().stream()
                .filter(row -> row.objective() == null || !row.objective().isHidden()).toList();
        int complete = (int) requirements.stream().filter(CollectionRequirementProgress::complete).count();
        g.fill(x, y, x + width, y + 1, color(MUTED, alpha / 10)); y += 11;
        String count = complete + " / " + requirements.size();
        g.drawString(screen.getFont(), text("requirements"), x, y, color(TEXT, alpha), false);
        g.drawString(screen.getFont(), count, x + width - screen.getFont().width(count), y,
                color(binding.complete() ? COMPLETE : MUTED, alpha), false);
        y += 16;
        bar(g, x, y, width, complete, requirements.size(), theme, alpha); y += 12;
        for (var row : requirements) y = requirement(g, binding, row, x, y, width, body, alpha, theme) + 9;
        if (!detailCanTrack(binding))
            y = drawWrapped(g, text(binding.complete() ? "entry_achieved" : "chapter_inactive"),
                    x, y, width, COMPLETE, alpha) + 10;
        return y + 4;
    }

    private void claimButton(GuiGraphics g, HudRect box, Component label, int alpha, int theme,
                             Runnable action, HudRect clip) {
        boolean hovered = hit(box) && clip.contains(mouseX, mouseY);
        if (visible(box)) {
            CollectionRewardVisuals.claimButton(g, screen.getFont(), box, label, hovered, theme, alpha);
            CollectionRewardVisuals.dot(g, box.x() + 2, box.y() + 2, alpha);
        }
        if (hovered) screen.requestPointerCursor();
        addAction(box, action, clip);
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
        return ClientQuestTrackingController.INSTANCE.canTrackCollectionBinding(questId, phaseId, binding.bindingId());
    }
    private boolean detailCanTrack(CollectionBindingProgress binding) {
        return !detailOpen() && closingBinding != null ? closingCanTrack : canTrack(binding);
    }
    private String detailRunId() {
        return !detailOpen() && closingBinding != null ? closingRunId
                : runtime != null && runtime.getCollectionData() != null ? runtime.getCollectionData().getRunId() : "";
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
