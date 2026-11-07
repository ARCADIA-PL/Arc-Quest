package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalUnreadBadgeRenderer;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScaledTextRenderer;
import org.arcadia.arc_quest.client.hud.component.HudTextMetrics;
import org.arcadia.arc_quest.client.quest.collection.AtlasBrowseStore;
import org.arcadia.arc_quest.client.quest.collection.AtlasCamera;
import org.arcadia.arc_quest.client.quest.collection.AtlasHoverGuard;
import org.arcadia.arc_quest.client.quest.collection.AtlasPageLayout;
import org.arcadia.arc_quest.client.quest.collection.CollectionFavoritesStore;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.data.*;
import org.lwjgl.glfw.GLFW;

/** Native, independently navigable category canvases with an inline inspector. */
final class JournalAtlasCanvas {
  private final QuestJournalScreen screen;
  private final CollectionImageViewer imageViewer;

  private final List<Hit> hits = new ArrayList<>();
  private final List<Node> nodes = new ArrayList<>();
  private final List<AtlasInspectionSnapshot.Category> categoryBadges = new ArrayList<>();
  private final Deque<String> history = new ArrayDeque<>();
  private final Deque<BrowseSnapshot> sourceHistory = new ArrayDeque<>();
  private final CollectionDetailTransition inspectorTransition = new CollectionDetailTransition();
  private final CollectionDetailTransition settingsTransition = new CollectionDetailTransition();
  private final AtlasHoverGuard hoverGuard = new AtlasHoverGuard();

  private record Destination(String quest, String phase, String binding, String variant) {}

  private record BrowseSnapshot(
      Destination destination,
      String stateKey,
      String category,
      String query,
      double x,
      double y,
      double zoom,
      double detailScroll,
      String detailTab,
      int detailPage,
      int detailTabStart) {}

  private BrowseSnapshot pendingBrowseRestore;
  private ItemStack pendingTooltip = ItemStack.EMPTY;
  private List<Component> pendingDetailTooltip = List.of();
  private boolean pendingCanvasTooltip;
  private String renderedTitleHint = "";
  private HudRect renderedTitleHintBounds;
  private String keyboardBinding = "";
  private Object historyConnection;
  private CollectionJournalState state;
  private QuestDefinition definition;
  private CollectionAtlasProfile profile = CollectionAtlasProfile.defaults();
  private CollectionSheetProgress progress;
  private EditBox search;
  private HudRect viewport = new HudRect(0, 0, 0, 0), inspector = new HudRect(0, 0, 0, 0);
  private String quest = "", phase = "", variant = "";
  private int absX, absY, alpha, theme, inspectorHeight, detailViewportHeight;
  private double mouseX, mouseY, dragX, dragY, velocityX, velocityY, detailScroll;
  private boolean dragging, moved, scrollZoom = true, favoritesOnly, groupMenuOpen, filterMenuOpen, showNames, searchOpen;
  private boolean settingsOpen;
  private HudRect settingsBounds = new HudRect(0, 0, 0, 0);
  private double pressX, pressY;
  private String hoverBinding = "";
  private long hoverSince;
  private int categoryStart, recordFilter, groupMenuOffset;
  private double minimumZoom, maximumZoom;
  private HudRect searchBounds = new HudRect(0, 0, 0, 0);
  private Node pressed;
  private long lastFrame = System.nanoTime();
  private String pendingBinding = "", pendingVariant = "";
  private Set<ResourceLocation> favorites = Set.of();
  private String detailTab = "__tooltip";
  private int detailPage, detailPageCount = 1, detailTabStart;
  private List<String> detailVisibleText = List.of();
  private record DetailTab(String id, Component title, String type, List<QuestText> paragraphs, int section, int sectionCount) {}
  @FunctionalInterface private interface PageDraw { void draw(GuiGraphics g, HudRect row, HudRect clip); }

  private record Node(
      CollectionBindingProgress progress,
      CollectionEntryDefinition entry,
      String group,
      String groupTitle,
      double x,
      double y) {}

  private record Hit(HudRect box, Runnable action, String label) {}

  JournalAtlasCanvas(QuestJournalScreen screen, CollectionImageViewer imageViewer) {
    this.screen = screen;
    this.imageViewer = imageViewer;
  }

  void reset() {
    state = null;
    search = null;
    history.clear();
    dragging = false;
    inspectorTransition.reset();
    settingsOpen = false;
    settingsTransition.reset();
  }

  boolean searchFocused() {
    return search != null && search.isFocused();
  }

  void suspend() {
    if (state != null) {
      camera().cancelMotion();
      dragging = false;
      persist();
    }
  }

  private void persist() {
    if (state == null) return;
    state.atlasCameras.forEach(
        (key, c) -> AtlasBrowseStore.putCamera(state.atlasStorageKey + "/" + key, c));
    AtlasBrowseStore.put("scrollZoom", Boolean.toString(scrollZoom));
    AtlasBrowseStore.put("showNames", Boolean.toString(showNames));
    AtlasBrowseStore.save();
  }

  void blurSearch() {
    if (search != null) search.setFocused(false);
  }

  void focus(String binding, String selectedVariant) {
    pendingBinding = binding == null ? "" : binding;
    pendingVariant = selectedVariant == null ? "" : selectedVariant;
  }

  private AtlasCamera camera() {
    return state.atlasCameras.computeIfAbsent(
        state.category, k -> AtlasBrowseStore.camera(state.atlasStorageKey + "/" + k));
  }

  private int color(int rgb) {
    return (alpha << 24) | rgb;
  }

  private static String tr(String key, Object... arguments) {
    return Component.translatable("arc_quest.hud.atlas." + key, arguments).getString();
  }

  private void label(GuiGraphics g, String text, int x, int y, int rgb) {
    AtlasV6Draw.text(g, screen.getFont(), text, x, y, 1, color(rgb));
  }

  private void small(GuiGraphics g, String text, int x, int y, float scale, int rgb) {
    AtlasV6Draw.text(g, screen.getFont(), text, x, y, scale, color(rgb));
  }

  private float readable(float requested) {
    return HudTextMetrics.readableScale(requested, screen.getUiScale() * screen.getMinecraft().getWindow().getGuiScale());
  }

  private void centered(GuiGraphics g, String text, HudRect bounds, float scale, int rgb) {
    JournalScaledTextRenderer.centered(g, screen.getFont(), text, bounds, scale, color(rgb), false);
  }

  private void button(
      GuiGraphics g, int x, int y, int w, String text, boolean active, Runnable action) {
    button(g, x, y, w, text, active, true, ButtonStyle.ACTION, action);
  }

  private enum ButtonStyle { ACTION, CATEGORY, MINI, ICON, QUALITY, TAB, MENU, TOOL, PAGE, HEADER, TRACK }

  private void button(GuiGraphics g, int x, int y, int w, String text, boolean active,
      boolean enabled, ButtonStyle style, Runnable action) {
    int h = style == ButtonStyle.HEADER ? 12 : style == ButtonStyle.MINI || style == ButtonStyle.TOOL ? 16 : style == ButtonStyle.QUALITY ? 17 : style == ButtonStyle.TAB ? 19 : 18;
    HudRect box = new HudRect(x, y, Math.max(12, w), h);
    boolean hovered = enabled && box.contains(mouseX, mouseY);
    if (hovered) screen.requestPointerCursor();
    int previousAlpha = alpha;
    if (!enabled) alpha = alpha * 3 / 10;
    boolean gold = (style == ButtonStyle.ICON || style == ButtonStyle.HEADER) && text.equals("★");
    int accent = gold ? 0xD3AE63 : style == ButtonStyle.TRACK ? 0x77CFA4 : theme;
    if (style == ButtonStyle.TOOL) {
      if (active || hovered) {
        g.fill(x, y, box.right(), box.bottom(), AtlasV6Draw.tint(theme, alpha / 10));
        g.fill(x + 3, box.bottom() - 1, box.right() - 3, box.bottom(), AtlasV6Draw.tint(theme, alpha / 2));
      }
    } else if (style == ButtonStyle.TAB) {
      if (hovered) g.fill(x, y, box.right(), box.bottom(), AtlasV6Draw.tint(theme, alpha / 20));
      if (active) g.fill(x, box.bottom() - 1, box.right(), box.bottom(), color(theme));
    } else if (style == ButtonStyle.QUALITY) {
      g.fill(x, y, box.right(), box.bottom(), AtlasV6Draw.tint(theme, (int) (alpha * (active ? .14 : hovered ? .08 : .025))));
      AtlasV6Draw.frame(g, box, theme, active ? alpha * 3 / 4 : alpha / 5);
    } else if (style == ButtonStyle.PAGE) {
      if (hovered) g.fill(x, y, box.right(), box.bottom(), AtlasV6Draw.tint(theme, alpha / 15));
    } else {
      float fill = style == ButtonStyle.CATEGORY ? .012f : style == ButtonStyle.MENU ? .08f : .015f;
      g.fill(x, y, box.right(), box.bottom(), AtlasV6Draw.tint(active || hovered ? accent : 0xFFFFFF,
          (int) (alpha * (active ? .10 : hovered ? .07 : fill))));
      AtlasV6Draw.frame(g, box, active || hovered ? accent : 0xA0B2BC,
          (int) (alpha * (active ? .55 : hovered ? .50 : .22)));
      if (active && style == ButtonStyle.CATEGORY)
        g.fill(x, box.bottom() - 1, box.right(), box.bottom(), color(theme));
    }
    float scale = readable(style == ButtonStyle.HEADER || style == ButtonStyle.MINI || style == ButtonStyle.TOOL ? .82f : .9f);
    int padding = style == ButtonStyle.HEADER || style == ButtonStyle.PAGE || style == ButtonStyle.ICON ? 2 : 8;
    String clipped = AtlasV6Draw.ellipsis(screen.getFont(), text, Math.max(1, (int) ((w - padding) / scale)));
    if (text.equals("★") || text.equals("☆")) {
      AtlasV6Draw.star(g, x + (w - 9) / 2, y + (h - 9) / 2, 9, text.equals("★") ? 0xFFE2A0 : 0xA0B2BC, alpha);
      if (text.equals("☆")) AtlasV6Draw.star(g, x + (w - 5) / 2, y + (h - 5) / 2, 5, 0x0B131B, alpha);
    } else if (style == ButtonStyle.CATEGORY && text.contains("  ")) {
      String[] parts = text.split("  ", 2);
      float nameScale = readable(.9f), countScale = readable(.8f);
      int countWidth = (int) Math.ceil(screen.getFont().width(parts[1]) * countScale);
      parts[0] = AtlasV6Draw.ellipsis(screen.getFont(), parts[0], (int) (Math.max(8, w - countWidth - 17) / nameScale));
      int nameWidth = (int) Math.ceil(screen.getFont().width(parts[0]) * nameScale);
      int startX = x + (w - nameWidth - countWidth - 7) / 2;
      centered(g, parts[0], new HudRect(startX, y, nameWidth, h), nameScale, active ? theme : 0xD1DAE0);
      centered(g, parts[1], new HudRect(startX + nameWidth + 7, y, countWidth, h), countScale, 0xB0C0CA);
    } else if (style == ButtonStyle.MENU) {
      var point = HudTextMetrics.centered(x, y, w, h, screen.getFont().width(clipped), 8, scale);
      JournalScaledTextRenderer.draw(g, screen.getFont(), clipped, x + 5, point.y(), scale, color(active ? accent : 0xD1DAE0), false);
    } else centered(g, clipped, box, scale, style == ButtonStyle.TRACK ? 0xA5D7BC : active ? accent : 0xD1DAE0);
    alpha = previousAlpha;
    if (enabled) hits.add(new Hit(box, action, text));
  }

  int render(
      GuiGraphics g,
      QuestDefinition def,
      CollectionSheetProgress sheet,
      CollectionJournalState browser,
      String questId,
      String phaseId,
      int y,
      int width,
      int height,
      int ax,
      int ay,
      double mx,
      double my,
      int opacity,
      int accent) {
    profile = def.getCollectionConfig().getAtlasProfile();
    if (state != browser) {
      state = browser;
      search = null;
      history.clear();
      detailScroll = 0;
      scrollZoom =
          Boolean.parseBoolean(
              AtlasBrowseStore.read(
                  "scrollZoom",
                  Boolean.toString(profile.inputDefaults().wheelMode().equalsIgnoreCase("ZOOM"))));
      favoritesOnly = state.atlasFavoritesOnly;
      recordFilter = state.atlasRecordFilter;
      showNames = Boolean.parseBoolean(AtlasBrowseStore.read("showNames", "false"));
      variant = state.atlasVariant;
      categoryStart = 0;
      groupMenuOpen = filterMenuOpen = settingsOpen = false;
      settingsTransition.reset();
      searchOpen = false;
      inspectorTransition.reset();
    }
    if (historyConnection != screen.getMinecraft().getConnection()) {
      sourceHistory.clear();
      historyConnection = screen.getMinecraft().getConnection();
    }
    definition = def;
    profile = def.getCollectionConfig().getAtlasProfile();
    progress = sheet;
    quest = questId;
    phase = phaseId;
    absX = ax;
    absY = ay;
    mouseX = mx;
    mouseY = my;
    alpha = opacity;
    theme = accent;
    hits.clear();
    nodes.clear();
    categoryBadges.clear();
    pendingTooltip = ItemStack.EMPTY;
    pendingDetailTooltip = List.of();
    pendingCanvasTooltip = false;
    renderedTitleHint = "";
    renderedTitleHintBounds = null;
    detailElements.clear();
    camera().focusMultiplier(profile.focus().zoomMultiplier());
    long now = System.nanoTime();
    double frameSeconds = Math.max(0, Math.min(.1, (now - lastFrame) / 1e9));
    camera().advance(frameSeconds);
    settingsTransition.advance(settingsOpen, profile.inputDefaults().reduceMotion() ? 1 : (float) frameSeconds);
    if (camera().isAnimating()) suppressHover();
    lastFrame = now;
    // A category strip only. Optional search/filter/group tools open over the canvas via keys.
    var categories = def.getCollectionConfig().getCategories();
    List<String> ids = new ArrayList<>();
    for (var category : categories)
      if (sheet.bindings().stream().anyMatch(p -> p.visible()
          && def.getCollectionConfig().getEntry(p.entryId()) != null
          && category.getCategoryId().equals(def.getCollectionConfig().getEntry(p.entryId()).getCategoryId())))
        ids.add(category.getCategoryId());
    if (ids.isEmpty()) ids.add("");
    if (!ids.contains(state.category)) state.category = ids.get(0);
    categoryStart = Math.max(0, Math.min(ids.size() - 1, categoryStart));
    int stripY = y;
    button(g, 0, stripY, 12, "‹", false, categoryStart > 0, ButtonStyle.ICON,
        () -> categoryStart = Math.max(0, categoryStart - 1));
    int cx = 16, lastCategory = categoryStart - 1;
    for (int i = categoryStart; i < ids.size(); i++) {
      String id = ids.get(i);
      var category = categories.stream().filter(c -> c.getCategoryId().equals(id)).findFirst().orElse(null);
      String name = category == null ? tr("title")
          : category.getDisplayNameText().resolve(null, QuestTextContext.empty()).getString();
      long total = sheet.bindings().stream().filter(p -> p.visible())
          .filter(p -> def.getCollectionConfig().getEntry(p.entryId()).getCategoryId().equals(id)).count();
      long count = sheet.bindings().stream().filter(p -> p.visible() && p.complete())
          .filter(p -> def.getCollectionConfig().getEntry(p.entryId()).getCategoryId().equals(id)).count();
      String text = name + "  " + count + "/" + total;
      int w = Math.min(Math.max(24, width - 32),
          (int) Math.ceil(screen.getFont().width(name) * readable(.9f) + screen.getFont().width(count + "/" + total) * readable(.8f)) + 21);
      if (cx + w > width - 16) break;
      boolean unread = org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE
          .hasUnreadCollectionCategory(quest, phase, id);
      int categoryX = cx;
      button(g, categoryX, stripY, w, text, id.equals(state.category), true, ButtonStyle.CATEGORY, () -> {
        if (!id.equals(state.category)) {
          close(); state.category = id; groupMenuOpen = filterMenuOpen = false;
          camera().discardFocus(); buildNodes(); overview(false); persist();
        }
        // Match the legacy collection tabs: deliberately opening a category acknowledges all
        // unread entries in that category, while simply rendering its default canvas does not.
        org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE
            .markCollectionCategorySeen(quest, phase, id);
      });
      categoryBadges.add(new AtlasInspectionSnapshot.Category(
          id, new HudRect(categoryX, stripY, w, 18), unread));
      if (unread) JournalUnreadBadgeRenderer.draw(g, categoryX + w - 3, stripY + 3, alpha);
      cx += w + 4;
      lastCategory = i;
    }
    button(g, width - 12, stripY, 12, "›", false, lastCategory < ids.size() - 1, ButtonStyle.ICON,
        () -> categoryStart = Math.min(ids.size() - 1, categoryStart + 1));
    y += 22;
    searchBounds = new HudRect(6, y + 6, Math.max(40, width - 12), 18);
    if (search == null) {
      search = new EditBox(screen.getFont(), searchBounds.x() + 12, searchBounds.y() + 5,
          Math.max(16, searchBounds.width() - 15), 12, Component.literal(tr("search")));
      search.setMaxLength(160);
      search.setBordered(false);
      search.setTextColor(0xD1DAE0);
      search.setTextColorUneditable(0x71868F);
      search.setValue(state.query);
      search.setResponder(value -> { state.query = value; camera().cancelMotion(); });
    }
    search.setX(searchBounds.x() + 12);
    search.setY(searchBounds.y() + 5);
    search.setWidth(Math.max(16, searchBounds.width() - 15));
    viewport = new HudRect(0, y, width, Math.max(24, height - y));
    g.fill(0, y, width, viewport.bottom(), AtlasV6Draw.tint(0x101213, alpha * 2 / 5));
    AtlasV6Draw.frame(g, viewport, 0xA0B2BC, alpha / 12);
    buildNodes();
    configureCamera();
    if (!dragging && !nodes.isEmpty())
      camera()
          .settle(
              nodes.stream().mapToDouble(n -> n.x - 14).min().orElse(0),
              nodes.stream().mapToDouble(n -> n.y - 28).min().orElse(0),
              nodes.stream().mapToDouble(n -> n.x + nodeWidth() + 14).max().orElse(100),
              nodes.stream().mapToDouble(n -> n.y + nodeHeight() + 14).max().orElse(100),
              viewport.width(),
              viewport.height(),
              profile.motion().maxOverscroll(),
              profile.motion().settleDecayMs(),
              frameSeconds,
              profile.inputDefaults().reduceMotion());
    if (state.atlasInitialized.add(state.category)
        && profile.zoom().initialView().equalsIgnoreCase("FIT_CONSTRAINED")
        && !AtlasBrowseStore.has(state.atlasStorageKey + "/" + state.category)) overview(false);
    if (!pendingBinding.isEmpty()) {
      var target =
          sheet.bindings().stream()
              .filter(p -> p.visible() && p.bindingId().equals(pendingBinding))
              .findFirst()
              .orElse(null);
      if (target != null) {
        var e = def.getCollectionConfig().getEntry(target.entryId());
        if (e != null) {
          state.category = e.getCategoryId();
          buildNodes();
          nodes.stream()
              .filter(n -> n.progress.bindingId().equals(pendingBinding))
              .findFirst()
              .ifPresent(
                  n -> {
                    if (!state.expanded || !state.selection.equals(n.progress.bindingId()))
                      select(n);
                  });
          variant = pendingVariant;
          state.atlasVariant = variant;
        }
      }
      pendingBinding = "";
      pendingVariant = "";
    }
    if (pendingBrowseRestore != null) {
      var snapshot = pendingBrowseRestore;
      if (snapshot.stateKey.equals(state.atlasStorageKey)) {
        state.query = snapshot.query;
        search.setValue(snapshot.query);
        state.category = snapshot.category;
        camera().cancelMotion();
        camera().x = snapshot.x;
        camera().y = snapshot.y;
        camera().zoom = snapshot.zoom;
        detailScroll = snapshot.detailScroll;
        detailTab = snapshot.detailTab;
        detailPage = snapshot.detailPage;
        detailTabStart = snapshot.detailTabStart;
        buildNodes();
      }
      pendingBrowseRestore = null;
    }
    inspectorTransition.advance(state.expanded, profile.inputDefaults().reduceMotion() ? 1 : (float) frameSeconds);
    int panelWidth = inspectorWidth();
    inspector = new HudRect(viewport.right() - (int) Math.round(panelWidth * inspectorTransition.alpha()),
        viewport.y() + Math.min(profile.inspector().topInset(), viewport.height() / 12), panelWidth,
        Math.max(24, viewport.height() - Math.min(profile.inspector().topInset(), viewport.height() / 12)
            - Math.min(profile.inspector().bottomInset(), viewport.height() / 12)));
    screen.enableScissor(
        g, ax + viewport.x(), ay + viewport.y(), ax + viewport.right(), ay + viewport.bottom());
    // The background dots are fixed to the canvas; nodes and group labels share its camera.
    for (int dy = viewport.y() + 9; dy < viewport.bottom(); dy += 18)
      for (int dx = 9; dx < viewport.right(); dx += 18)
        g.fill(dx, dy, dx + 1, dy + 1, AtlasV6Draw.tint(0xA0B2BC, alpha / 16));
    String query = state.query.trim().toLowerCase(Locale.ROOT);
    Map<String, List<Node>> groups = new LinkedHashMap<>();
    for (Node n : nodes) groups.computeIfAbsent(n.group, k -> new ArrayList<>()).add(n);
    Node focusedNode = nodes.stream().filter(n -> n.progress.bindingId().equals(state.selection)).findFirst().orElse(null);
    for (var group : groups.entrySet()) {
      var members = group.getValue();
      double minX = members.stream().mapToDouble(n -> n.x).min().orElse(0) - 2;
      double minY = members.stream().mapToDouble(n -> n.y).min().orElse(0) - 18;
      double maxX = members.stream().mapToDouble(n -> n.x + nodeWidth()).max().orElse(0) + 6;
      // Shrink with the camera, without inverse scaling or unreadably small headings.
      float headerScale = readable(.86f * (float) camera().zoom);
      String groupTitle = members.get(0).groupTitle;
      int titleWidth = (int) Math.ceil(screen.getFont().width(groupTitle) * headerScale);
      int headingHeight = (int) Math.ceil(8 * headerScale);
      int gx = sx(minX), gy = sy(minY + 18) - headingHeight - 8;
      int headerWidth = Math.max(titleWidth + 20, sx(maxX) - gx);
      boolean dim = (state.expanded && focusedNode != null && !focusedNode.group.equals(group.getKey()))
          || members.stream().noneMatch(this::matches);
      int a = alpha;
      if (dim) alpha = (int) (a * profile.focus().otherGroupAlpha());
      small(g, groupTitle, gx, gy, headerScale, theme);
      centered(g, "" + members.size(), new HudRect(gx + titleWidth + 6, gy, 16, headingHeight), readable(.8f), 0xB0C0CA);
      AtlasV6Draw.hairline(g, gx, gy + headingHeight + 3, headerWidth, theme, alpha / 5);
      alpha = a;
    }
    ItemStack tooltip = ItemStack.EMPTY;
    boolean anyNodeHovered = false;
    for (Node n : nodes) {
      int originalAlpha = alpha;
      boolean favorite = favorites.contains(n.entry.getEntryId());
      if (state.expanded && focusedNode != null && !focusedNode.group.equals(n.group))
        alpha = (int) (alpha * (favorite ? profile.focus().otherGroupFavoriteAlpha() : profile.focus().otherGroupAlpha()));
      if (!n.entry.getPresentation().obtainable(
          org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE::hasFlag, n.progress.discovered()))
        alpha = Math.min(alpha, (int) (originalAlpha * profile.focus().otherGroupAlpha()));
      boolean matches = matches(n);
      if (!matches) alpha = alpha * 15 / 100;
      HudRect box = nodeBox(n);
      if (box.right() < 0 || box.x() > width || box.bottom() < y || box.y() > viewport.bottom()) {
        alpha = originalAlpha; continue;
      }
      int size = box.width();
      boolean selected = n.progress.bindingId().equals(state.selection) && state.expanded;
      boolean hovered = !hoverGuard.blocked() && !camera().isAnimating() && !dragging
          && circleContains(box, mx, my) && (!inspectorTransition.visible(state.expanded) || mx < inspector.x())
          && !(settingsTransition.visible(settingsOpen) && settingsBounds.contains(mx, my));
      AtlasV6Draw.circle(g, box.x() + 1, box.y() + 1, Math.max(4, size - 2), favorite ? 0xD3AE63 : theme,
          (int) (alpha * (selected || hovered ? .24 : favorite ? .19 : .075)));
      if (favorite) AtlasV6Draw.ring(g, box.x() - 1, box.y() - 1, size + 2, .8f, 0xD3AE63, alpha * 4 / 5);
      if (selected || n.progress.bindingId().equals(keyboardBinding)) {
        AtlasV6Draw.ring(g, box.x() - 2, box.y() - 2, size + 4, .8f, theme, alpha);
        AtlasV6Draw.ring(g, box.x() + 1, box.y() + 1, size - 2, .5f, 0xDCE2E5, alpha * 3 / 5);
      }
      ItemStack stack = stack(n);
      int iconSize = Math.max(10, (int) Math.round(size * .78));
      int ix = box.x() + (size - iconSize) / 2, iy = box.y() + (size - iconSize) / 2;
      if (!stack.isEmpty()) {
        renderItem(g, stack, ix, iy, iconSize);
        if (matches) JeiScreenIngredients.collectionSubject(screen, g, quest, phase, n.progress.bindingId(),
            n.entry.getEntryId().toString(), variantFor(n), stack, ix, iy, iconSize, iconSize);
      } else if (n.progress.revealed()) {
        var context = CollectionEntryIcons.context(quest, phase, n.progress.bindingId(), n.entry);
        var visual = screen.getObjectiveIcons().select(context, hovered, false);
        if (visual.available()) visual.render(g, ix, iy, iconSize, alpha / 255f);
        else small(g, "◇", ix + iconSize / 3, iy + iconSize / 3, .9f, 0xA0B2BC);
      } else if (!n.progress.revealed()) small(g, "?", ix + iconSize / 3, iy + iconSize / 3, .9f, 0x71868F);
      if (showNames) {
        String title = n.progress.revealed() ? n.entry.getDisplayName().getString() : tr("undiscovered");
        float captionScale = readable(.82f);
        int textWidth = (int) ((size + 18) / captionScale);
        var captionLines = screen.getFont().split(Component.literal(title), Math.max(8, textWidth));
        int count = Math.min(profile.node().labelMaxLines(), captionLines.size());
        if (profile.node().labelMaxLines() == 1) {
          String caption = AtlasV6Draw.ellipsis(screen.getFont(), title, textWidth);
          centered(g, caption, new HudRect(box.x() - 9, box.bottom() + 3, size + 18, (int) Math.ceil(10 * captionScale)),
              captionScale, favorite ? 0xEED6A2 : 0xD1DAE0);
        } else for (int line = 0; line < count; line++) {
          var caption = captionLines.get(line);
          float captionX = box.x() + (size - screen.getFont().width(caption) * captionScale) / 2f;
          JournalScaledTextRenderer.draw(g, screen.getFont(), caption, captionX, box.bottom() + 3 + line * (float) Math.ceil(11 * captionScale),
              captionScale, color(favorite ? 0xEED6A2 : 0xD1DAE0), false);
        }
      }
      if (favorite) {
        HudRect badge = new HudRect(box.x() + size - 6, box.y() - 4, 10, 10);
        AtlasV6Draw.circle(g, badge.x(), badge.y(), 10, 0x48351B, alpha);
        AtlasV6Draw.ring(g, badge.x(), badge.y(), 10, .6f, 0xD3AE63, alpha);
        AtlasV6Draw.star(g, badge.x() + 1, badge.y() + 1, 8, 0xFFE2A0, alpha);
      }
      if (org.arcadia.arc_quest.quest.logic.profile.collection.CollectionUnreadState.isUnread(
          n.progress, org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE.getCollectionRecord(n.entry.getEntryId())))
        JournalUnreadBadgeRenderer.draw(g, box.right() - 2, box.y() + 2, originalAlpha);
      if (n.progress.complete()) small(g, "✓", box.x() + size - 4, box.y() + size - 4, .85f, 0xA4D3B2);
      alpha = originalAlpha;
      if (matches && hovered) {
        anyNodeHovered = true;
        if (!hoverBinding.equals(n.progress.bindingId())) { hoverBinding = n.progress.bindingId(); hoverSince = now; }
        if (now - hoverSince >= 180_000_000L) tooltip = stack;
      }
    }
    if (!anyNodeHovered) hoverBinding = "";
    if (nodes.stream().noneMatch(this::matches)) {
      String empty = tr("no_matches");
      centered(g, empty, new HudRect(viewport.x(), viewport.y() + viewport.height() / 2 - 18, viewport.width(), 16), readable(.9f), 0xA0B2BC);
      int clearWidth = (int) Math.ceil(screen.getFont().width(tr("clear_filter")) * readable(.9f)) + 12;
      button(g, (viewport.width() - clearWidth) / 2, viewport.y() + viewport.height() / 2,
          clearWidth, tr("clear_filter"), false, this::clearFilters);
    }
    screen.disableScissor(g);
    if (inspectorTransition.visible(state.expanded)) renderInspector(g);
    renderBottom(g);
    renderPopovers(g, groups);
    renderSearch(g);
    renderSearchResults(g);
    if (dragging || searchFocused() || hoverGuard.blocked() || camera().isAnimating())
      JeiScreenIngredients.suppress(screen);
    if (!tooltip.isEmpty() && !searchOpen && !groupMenuOpen && !filterMenuOpen
        && !(settingsTransition.visible(settingsOpen) && settingsBounds.contains(mx, my))
        && (!inspectorTransition.visible(state.expanded) || !inspector.contains(mx, my)))
    {
      pendingTooltip = tooltip.copy();
      pendingCanvasTooltip = true;
    }
    return viewport.bottom();
  }

  private void renderSearch(GuiGraphics g) {
    if (!searchOpen) return;
    g.pose().pushPose(); g.pose().translate(0, 0, 600);
    g.fill(searchBounds.x(), searchBounds.y(), searchBounds.right(), searchBounds.bottom(), color(0x0B131B));
    AtlasV6Draw.frame(g, searchBounds, search.isFocused() ? theme : 0x667782, alpha / 2);
    small(g, "⌕", searchBounds.x() + 3, searchBounds.y() + 4, .9f, 0xA0B2BC);
    if (state.query.isBlank() && !search.isFocused())
      small(g, tr("search"), search.getX(), search.getY(), .82f, 0x71868F);
    search.render(g, (int) mouseX, (int) mouseY, 0);
    hits.add(new Hit(searchBounds, () -> search.setFocused(true), tr("search")));
    g.pose().popPose();
  }

  private void suppressHover() {
    hoverGuard.suppress(mouseX, mouseY);
    hoverBinding = "";
    pendingTooltip = ItemStack.EMPTY;
  }

  void pointerMoved(double x, double y) {
    if (state != null) hoverGuard.pointerMoved(x, y, camera().isAnimating() || dragging);
  }

  private void renderItem(GuiGraphics g, ItemStack stack, int x, int y, int size) {
    org.arcadia.arc_quest.client.hud.quest.QuestIconRenderer.renderIcon(
        g, VisualAsset.of(stack), x, y, size, size, alpha / 255f);
  }

  private boolean matches(Node node) {
    boolean favorite = favorites.contains(node.entry.getEntryId());
    if (favoritesOnly && !favorite || recordFilter == 1 && node.progress.discovered()
        || recordFilter == 2 && !node.progress.discovered()) return false;
    String query = state.query.trim().toLowerCase(Locale.ROOT);
    return query.isBlank() || (node.progress.revealed()
        ? node.entry.getDisplayName().getString() + " " + node.entry.getEntryId() : node.progress.publicClue().getString())
        .toLowerCase(Locale.ROOT).contains(query);
  }

  private void clearFilters() {
    favoritesOnly = state.atlasFavoritesOnly = false;
    recordFilter = state.atlasRecordFilter = 0;
    state.query = ""; search.setValue(""); filterMenuOpen = false;
  }

  private void renderBottom(GuiGraphics g) {
    int gearX = viewport.x() + 5, gearY = viewport.bottom() - 25;
    int overviewWidth = (int) (screen.getFont().width(tr("overview")) * readable(.82f)) + 12;
    String wheel = tr("wheel") + (scrollZoom ? tr("zoom") : tr("scroll"));
    int wheelWidth = (int) (screen.getFont().width(wheel) * readable(.82f)) + 12;
    String expand = screen.isCompactAtlas() ? tr("task_list") : screen.isAtlasCanvasExpanded() ? tr("collapse") : tr("expand");
    int expandWidth = (int) Math.ceil(screen.getFont().width(expand) * readable(.82f)) + 12;
    int naturalWidth = 71 + overviewWidth + wheelWidth + expandWidth;
    int available = Math.max(60, viewport.right() - gearX - 32);
    boolean wrap = available < naturalWidth;
    int width = wrap ? Math.min(available, Math.max(112, Math.max(71 + overviewWidth, wheelWidth + expandWidth))) : naturalWidth;
    int height = wrap ? 44 : 26;
    int left = gearX + 24, top = viewport.bottom() - 5 - height;
    settingsBounds = new HudRect(gearX, top, width + 24, height);
    g.pose().pushPose(); g.pose().translate(0, 0, 680);
    if (settingsTransition.visible(settingsOpen)) {
      int previousAlpha = alpha;
      alpha = Math.round(alpha * settingsTransition.alpha());
      screen.enableScissor(g, absX + left, absY + top, absX + left + width, absY + viewport.bottom() - 4);
      g.pose().pushPose();
      g.pose().translate(-(1 - settingsTransition.alpha()) * width, 0, 0);
      g.fill(left, top, left + width, top + height, AtlasV6Draw.tint(0x0B131B, alpha * 94 / 100));
      AtlasV6Draw.frame(g, new HudRect(left, top, width, height), theme, alpha / 3);
      int x = left + 4, y = top + 5;
      int firstHit = hits.size();
      button(g, x, y, 14, "−", false, camera().zoom > minimumZoom + .001, ButtonStyle.TOOL,
          () -> zoom(1 / 1.12, viewport.width() / 2., viewport.height() / 2.));
      centered(g, Math.round(camera().zoom * 100) + "%", new HudRect(x + 15, y, 29, 16), readable(.8f), 0xB0C0CA);
      button(g, x + 45, y, 14, "+", false, camera().zoom < maximumZoom - .001, ButtonStyle.TOOL,
          () -> zoom(1.12, viewport.width() / 2., viewport.height() / 2.));
      x += 63;
      button(g, x, y, overviewWidth, tr("overview"), false, true, ButtonStyle.TOOL, () -> { close(); overview(); });
      x += overviewWidth;
      if (wrap) { x = left + 4; y += 18; }
      button(g, x, y, wheelWidth, wheel, !scrollZoom, true, ButtonStyle.TOOL, () -> {
        scrollZoom = !scrollZoom; state.atlasScrollZoom = scrollZoom; camera().cancelMotion();
      });
      x += wheelWidth;
      button(g, x, y, expandWidth, expand, screen.isAtlasCanvasExpanded(), true, ButtonStyle.TOOL, screen::toggleAtlasCanvas);
      // Sliding controls do not become clickable until their actual position is settled.
      if (!settingsTransition.interactive(settingsOpen)) hits.subList(firstHit, hits.size()).clear();
      g.pose().popPose(); screen.disableScissor(g);
      alpha = previousAlpha;
    }
    HudRect gear = new HudRect(gearX, gearY, 20, 20);
    boolean hovered = gear.contains(mouseX, mouseY);
    g.fill(gearX, gearY, gear.right(), gear.bottom(), AtlasV6Draw.tint(0x0B131B, alpha * 94 / 100));
    AtlasV6Draw.frame(g, gear, settingsOpen || hovered ? theme : 0xA0B2BC, alpha * (settingsOpen || hovered ? 3 : 1) / 5);
    AtlasV6Draw.gear(g, gearX + 4, gearY + 4, 12, settingsOpen || hovered ? theme : 0xA0B2BC, alpha);
    if (hovered) screen.requestPointerCursor();
    hits.add(new Hit(gear, () -> { settingsOpen = !settingsOpen; suppressHover(); }, tr("settings")));
    g.pose().popPose();
  }

  private void renderPopovers(GuiGraphics g, Map<String, List<Node>> groups) {
    if (!groupMenuOpen && !filterMenuOpen) return;
    g.pose().pushPose(); g.pose().translate(0, 0, 600);
    int width = Math.min(viewport.width(), 156);
    int x = groupMenuOpen ? 0 : viewport.right() - width, y = viewport.y();
    int count = groupMenuOpen ? Math.min(groups.size(), Math.max(1, viewport.height() / 22)) : 6;
    g.fill(x, y, x + width, Math.min(viewport.bottom(), y + count * 22 + 4), color(0x111B23));
    if (groupMenuOpen) {
      for (var group : groups.entrySet().stream().skip(groupMenuOffset).toList()) {
        if (y + 18 > viewport.bottom()) break;
        var members = group.getValue();
        button(g, x + 2, y + 2, width - 4, members.get(0).groupTitle, false, true, ButtonStyle.MENU, () -> {
          close(); groupMenuOpen = false;
          double centerX = (members.stream().mapToDouble(n -> n.x).min().orElse(0)
              + members.stream().mapToDouble(n -> n.x + nodeWidth()).max().orElse(0)) / 2;
          double centerY = (members.stream().mapToDouble(n -> n.y).min().orElse(0)
              + members.stream().mapToDouble(n -> n.y + nodeWidth()).max().orElse(0)) / 2;
          camera().pan(viewport.width() / 2. - centerX * camera().zoom - camera().x,
              viewport.height() / 2. - centerY * camera().zoom - camera().y);
        });
        y += 22;
      }
    } else {
      button(g, x + 2, y + 2, width - 4, tr("all_entries"), !favoritesOnly && recordFilter == 0, true, ButtonStyle.MENU, this::clearFilters);
      for (int mode = 1; mode <= 2; mode++) {
        int chosen = mode;
        button(g, x + 2, y + 2 + mode * 22, width - 4, tr(mode == 1 ? "unrecorded_filter" : "recorded_filter"),
            recordFilter == mode, true, ButtonStyle.MENU, () -> {
              recordFilter = state.atlasRecordFilter = chosen;
              favoritesOnly = state.atlasFavoritesOnly = false; filterMenuOpen = false;
            });
      }
      button(g, x + 2, y + 68, width - 4, tr("favorites"), favoritesOnly, true, ButtonStyle.MENU, () -> {
        favoritesOnly = !favoritesOnly; state.atlasFavoritesOnly = favoritesOnly;
        recordFilter = state.atlasRecordFilter = 0; filterMenuOpen = false;
      });
      button(g, x + 2, y + 90, width - 4, tr("names"), showNames, true, ButtonStyle.MENU, () -> {
        showNames = !showNames; filterMenuOpen = false;
      });
      button(g, x + 2, y + 112, width - 4, tr("clear_filter"), false, true, ButtonStyle.MENU, this::clearFilters);
    }
    g.pose().popPose();
  }

  private void buildNodes() {
    nodes.clear();
    List<CollectionBindingProgress> visible =
        progress.bindings().stream()
            .filter(CollectionBindingProgress::visible)
            .filter(
                p -> {
                  var entry = definition.getCollectionConfig().getEntry(p.entryId());
                  return entry != null && entry.getCategoryId().equals(state.category);
                })
            .toList();
    Set<ResourceLocation> used = new HashSet<>();
    var atlas = definition.getCollectionConfig().getAtlas();
    double bottom = 0;
    if (atlas != null) {
      for (var category : atlas.categories()) {
        if (!category.categoryId().equals(state.category)) continue;
        int groupColumns = columns(category.layoutParameters(), 2);
        double pixelWidth = viewport.width() * screen.getUiScale()
            * Minecraft.getInstance().getWindow().getGuiScale();
        if (category.layoutParameters().containsKey("wideColumns")
            && pixelWidth >= parameter(category.layoutParameters(), "wideMinPixels", 4096))
          groupColumns = (int) parameter(category.layoutParameters(), "wideColumns", groupColumns);
        double groupGapX = parameter(category.layoutParameters(), "gapX", 34);
        double groupGapY = parameter(category.layoutParameters(), "gapY", 34);
        double groupWidth = 64, groupHeight = 64;
        Map<String, org.arcadia.arc_quest.client.quest.collection.AtlasLayoutStrategies.Size> groupSizes = new LinkedHashMap<>();
        for (var group : category.groups()) {
          int cols = columns(group.layoutParameters(), group.columns());
          int count =
              (int)
                  group.nodes().stream()
                      .filter(n -> visible.stream().anyMatch(v -> v.entryId().equals(n.entryId())))
                      .count();
          double gapX = parameter(group.layoutParameters(), "gapX", 14);
          double gapY = parameter(group.layoutParameters(), "gapY", 24);
          String title = group.title().resolve(null, QuestTextContext.empty()).getString();
          // Reserve real space for full headings even at the configured zoom floor.
          double headingWidth = screen.getFont().width(title) * .86 + 26;
          double width = Math.max(headingWidth, Math.min(cols, Math.max(1, count)) * (nodeWidth() + gapX) - gapX + 40);
          double height = Math.max(1, Math.ceil(count / (double) cols)) * (nodeHeight() + gapY) - gapY + 40;
          if (group.layoutParameters().containsKey("cellWidth"))
            width = Math.max(headingWidth, Math.max(parameter(group.layoutParameters(), "minWidth", 0),
                cols * parameter(group.layoutParameters(), "cellWidth", 44)
                    + parameter(group.layoutParameters(), "widthPadding", 0)));
          if (group.layoutParameters().containsKey("cellHeight"))
            height = Math.max(1, Math.ceil(count / (double) cols))
                * parameter(group.layoutParameters(), "cellHeight", 44)
                + parameter(group.layoutParameters(), "heightPadding", 0);
          groupSizes.put(group.groupId(), new org.arcadia.arc_quest.client.quest.collection.AtlasLayoutStrategies.Size(width, height));
          groupWidth = Math.max(groupWidth, width); groupHeight = Math.max(groupHeight, height);
        }
        var groupPositions = category.layoutType().equals(ResourceLocation.parse("arc_quest:group_grid"))
            ? (parameter(category.layoutParameters(), "flowRows", 0) > 0
                ? org.arcadia.arc_quest.client.quest.collection.AtlasLayoutStrategies.flowGrid(
                    category.groups().stream().map(CollectionAtlasDefinition.Group::groupId).toList(), groupSizes,
                    groupColumns, groupGapX, groupGapY)
                : org.arcadia.arc_quest.client.quest.collection.AtlasLayoutStrategies.packedGrid(
                category.groups().stream().map(CollectionAtlasDefinition.Group::groupId).toList(), groupSizes,
                groupColumns, groupGapX, groupGapY))
            : org.arcadia.arc_quest.client.quest.collection.AtlasLayoutStrategies.layout(
                category.layoutType(),
                new org.arcadia.arc_quest.client.quest.collection.AtlasLayoutStrategies.Context(
                    org.arcadia.arc_quest.quest.registry.CollectionAtlasLayoutRegistry.Scope
                        .CATEGORY,
                    category.groups().stream()
                        .map(CollectionAtlasDefinition.Group::groupId)
                        .toList(),
                    groupColumns,
                    groupWidth + groupGapX,
                    groupHeight + groupGapY,
                    category.layoutParameters()));
        int groupIndex = 0;
        for (var group : category.groups()) {
          boolean manualGroup = category.layoutType().getPath().equals("manual");
          boolean manualNode = group.layoutType().getPath().equals("manual");
          int columns = columns(group.layoutParameters(), group.columns());
          double gapX = parameter(group.layoutParameters(), "gapX", 14);
          double gapY = parameter(group.layoutParameters(), "gapY", 24);
          double groupX = group.x() + (manualGroup ? 0 : groupPositions.get(group.groupId()).x());
          double groupY = group.y() + (manualGroup ? 0 : groupPositions.get(group.groupId()).y());
          groupX += parameter(category.layoutParameters(), "originX", 0);
          groupY += parameter(category.layoutParameters(), "originY", 0);
          var nodePositions =
              org.arcadia.arc_quest.client.quest.collection.AtlasLayoutStrategies.layout(
                  group.layoutType(),
                  new org.arcadia.arc_quest.client.quest.collection.AtlasLayoutStrategies.Context(
                      org.arcadia.arc_quest.quest.registry.CollectionAtlasLayoutRegistry.Scope
                          .GROUP,
                      group.nodes().stream()
                          .filter(
                              n -> visible.stream().anyMatch(v -> v.entryId().equals(n.entryId())))
                          .map(n -> n.entryId().toString())
                          .toList(),
                      columns,
                      parameter(group.layoutParameters(), "cellWidth", nodeWidth() + gapX),
                      parameter(group.layoutParameters(), "cellHeight", nodeHeight() + gapY),
                      group.layoutParameters()));
          int index = 0;
          for (var authored : group.nodes()) {
            var projected =
                visible.stream()
                    .filter(v -> v.entryId().equals(authored.entryId()))
                    .findFirst()
                    .orElse(null);
            if (projected == null || !used.add(authored.entryId())) continue;
            double x =
                groupX
                    + authored.x()
                    + (manualNode ? 0 : nodePositions.get(authored.entryId().toString()).x());
            double y =
                groupY
                    + authored.y()
                    + (manualNode ? 0 : nodePositions.get(authored.entryId().toString()).y());
            String title = group.title().resolve(null, QuestTextContext.empty()).getString();
            nodes.add(
                new Node(
                    projected,
                    definition.getCollectionConfig().getEntry(projected.entryId()),
                    group.groupId(),
                    title,
                    x + parameter(group.layoutParameters(), "nodeOriginX", 20)
                        - (parameter(group.layoutParameters(), "centerNodes", 0) > 0 ? nodeWidth() / 2 : 0),
                    y + parameter(group.layoutParameters(), "nodeOriginY", 34)
                        - (parameter(group.layoutParameters(), "centerNodes", 0) > 0 ? nodeWidth() / 2 : 0)));
            bottom = Math.max(bottom, y + nodeHeight() + 64);
            index++;
          }
          groupIndex++;
        }
      }
    }
    int index = 0;
    for (var projected : visible) {
      if (!used.add(projected.entryId())) continue;
      var entry = definition.getCollectionConfig().getEntry(projected.entryId());
      nodes.add(
          new Node(
              projected,
              entry,
              "arc_quest:unassigned",
              tr("other"),
              20 + index % 4 * (nodeWidth() + 14),
              bottom + 34 + index / 4 * (nodeHeight() + 24)));
      index++;
    }
  }

  private List<CollectionBindingProgress> searchResults() {
    if (state == null || state.query.isBlank()) return List.of();
    String query = state.query.trim().toLowerCase(Locale.ROOT);
    return progress.bindings().stream().filter(CollectionBindingProgress::visible)
        .filter(p -> {
          var entry = definition.getCollectionConfig().getEntry(p.entryId());
          return entry != null && (p.revealed()
              ? (entry.getDisplayName().getString() + " " + entry.getEntryId()).toLowerCase(Locale.ROOT).contains(query)
              : p.publicClue().getString().toLowerCase(Locale.ROOT).contains(query));
        }).limit(5).toList();
  }

  private void openSearchResult(CollectionBindingProgress result) {
    search.setFocused(false);
    searchOpen = false;
    close();
    pendingBinding = result.bindingId();
    pendingVariant = "";
  }

  /** Search the whole authorized sheet, retaining each category's authored layout. */
  private void renderSearchResults(GuiGraphics graphics) {
    if (!searchOpen) return;
    if (!searchFocused() || state.query.isBlank()) return;
    var results = searchResults();
    int width = searchBounds.width(), y = searchBounds.bottom() + 2;
    graphics.pose().pushPose();
    graphics.pose().translate(0, 0, 600);
    for (var result : results) {
      var entry = definition.getCollectionConfig().getEntry(result.entryId());
      String category = definition.getCollectionConfig().getCategories().stream()
          .filter(c -> c.getCategoryId().equals(entry.getCategoryId())).findFirst()
          .map(c -> c.getDisplayNameText().resolve(null, QuestTextContext.empty()).getString()).orElse("");
      String title = (result.revealed() ? entry.getDisplayName().getString() : tr("undiscovered")) + " · " + category;
      button(graphics, searchBounds.x(), y, width,
          screen.getFont().plainSubstrByWidth(title, Math.max(16, width - 12)), false,
          () -> openSearchResult(result));
      y += 20;
    }
    graphics.pose().popPose();
  }

  private void configureCamera() {
    double minX = nodes.stream().mapToDouble(n -> n.x - 14).min().orElse(0);
    double minY = nodes.stream().mapToDouble(n -> n.y - 28).min().orElse(0);
    double maxX = nodes.stream().mapToDouble(n -> n.x + nodeWidth() + 14).max().orElse(nodeWidth());
    double maxY =
        nodes.stream().mapToDouble(n -> n.y + nodeHeight() + 14).max().orElse(nodeHeight());
    double spacing = Double.POSITIVE_INFINITY;
    for (int i = 0; i < nodes.size(); i++)
      for (int j = i + 1; j < nodes.size(); j++) {
        double distance =
            Math.hypot(nodes.get(i).x - nodes.get(j).x, nodes.get(i).y - nodes.get(j).y);
        if (distance > 0) spacing = Math.min(spacing, distance);
      }
    if (!Double.isFinite(spacing)) spacing = 0;
    var limits =
        AtlasCamera.limits(
            viewport.width(),
            viewport.height(),
            maxX - minX,
            maxY - minY,
            profile.node().size(),
            nodeWidth(),
            nodeHeight(),
            spacing,
            profile.zoom());
    minimumZoom = limits.minimum();
    maximumZoom = limits.maximum();
    camera()
        .configure(
            limits.minimum(),
            limits.maximum(),
            1000 / Math.max(25, profile.motion().inertiaDecayMs()));
  }

  private static int columns(Map<String, Double> parameters, int fallback) {
    return Math.max(1, Math.min(64, (int) Math.round(parameter(parameters, "columns", fallback))));
  }

  private static double parameter(Map<String, Double> parameters, String key, double fallback) {
    return Math.max(0, Math.min(4096, parameters.getOrDefault(key, fallback)));
  }

  private double nodeWidth() {
    return profile.node().size();
  }

  private double nodeHeight() {
    // Hidden captions must not keep their otherwise empty rows in the layout.
    return profile.node().size() + (showNames ? profile.node().labelMaxLines() * 8 : 0) + 5;
  }

  private int sx(double x) {
    return (int) Math.round(viewport.x() + camera().x + x * camera().zoom);
  }

  private int sy(double y) {
    return (int) Math.round(viewport.y() + camera().y + y * camera().zoom);
  }

  private static boolean circleContains(HudRect box, double x, double y) {
    double dx = x - box.x() - box.width() / 2., dy = y - box.y() - box.height() / 2.;
    return dx * dx + dy * dy <= box.width() * box.width() / 4.;
  }

  private HudRect nodeBox(Node n) {
    return new HudRect(
        sx(n.x),
        sy(n.y),
        Math.max(12, (int) Math.round(nodeWidth() * camera().zoom)),
        Math.max(12, (int) Math.round(nodeWidth() * camera().zoom)));
  }

  private String variantFor(Node n) {
    var p = n.entry.getPresentation();
    return n.progress.bindingId().equals(state.selection) && !variant.isEmpty()
        ? variant
        : p.defaultVariantId();
  }

  private ItemStack stack(Node n) {
    if (!n.progress.revealed()) return ItemStack.EMPTY;
    var p = n.entry.getPresentation();
    var v =
        p.variants().stream()
            .filter(it -> it.variantId().equals(variantFor(n)))
            .findFirst()
            .orElse(null);
    var subject = v == null ? p.subject() : v.subject();
    return subject == null ? ItemStack.EMPTY : subject.resolveStack();
  }

  private void select(Node n) {
    suppressHover();
    keyboardBinding = n.progress.bindingId();
    boolean keepDetailTab = state.expanded;
    if (state.expanded && !state.selection.equals(n.progress.bindingId()))
      history.push(state.selection);
    state.select(n.progress.bindingId());
    variant = n.entry.getPresentation().defaultVariantId();
    if (!keepDetailTab || detailTabs(n).stream().noneMatch(tab -> tab.id.equals(detailTab))) {
      detailTab = detailTabs(n).get(0).id;
      detailTabStart = 0;
    }
    detailPage = 0;
    org.arcadia.arc_quest.quest.network.ClientCollectionDetails.INSTANCE.clear();
    detailScroll = 0;
    var groupNodes = nodes.stream().filter(it -> it.group.equals(n.group)).toList();
    if (profile.inputDefaults().reduceMotion()) camera().focusRegion(
        groupNodes.stream().mapToDouble(it -> it.x).min().orElse(n.x),
        groupNodes.stream().mapToDouble(it -> it.y - 18).min().orElse(n.y),
        groupNodes.stream().mapToDouble(it -> it.x + nodeWidth()).max().orElse(n.x),
        groupNodes.stream().mapToDouble(it -> it.y + nodeHeight()).max().orElse(n.y),
        n.x, n.y, nodeWidth(), Math.max(44, viewport.width() - inspectorWidth()), viewport.height());
    else camera().focusRegionAnimated(
        groupNodes.stream().mapToDouble(it -> it.x).min().orElse(n.x),
        groupNodes.stream().mapToDouble(it -> it.y - 18).min().orElse(n.y),
        groupNodes.stream().mapToDouble(it -> it.x + nodeWidth()).max().orElse(n.x),
        groupNodes.stream().mapToDouble(it -> it.y + nodeHeight()).max().orElse(n.y),
        n.x, n.y, nodeWidth(), Math.max(44, viewport.width() - inspectorWidth()), viewport.height());
  }

  private int inspectorWidth() {
    return Math.max(
        64,
        (int)
            Math.min(
                viewport.width()
                    - Math.min(
                        profile.inspector().minCanvasInteractionWidth(), viewport.width() * .35),
                Math.max(
                    Math.min(profile.inspector().minWidth(), viewport.width() < 300 ? viewport.width() * .72 : viewport.width() * .36),
                    Math.min(
                        profile.inspector().maxWidth(),
                        viewport.width() * profile.inspector().preferredWidthFraction()))));
  }

  void close() {
    if (state != null) {
      state.expanded = false;
      keyboardBinding = "";
      suppressHover();
      if (profile.focus().restoreBrowseSnapshotOnClose()) {
        if (profile.inputDefaults().reduceMotion()) camera().restore(); else camera().restoreAnimated();
      }
      else camera().discardFocus();
    }
    detailScroll = 0;
  }

  private void overview() {
    overview(true);
  }

  private void overview(boolean animated) {
    configureCamera();
    suppressHover();
    double width = nodes.stream().mapToDouble(n -> n.x + nodeWidth() + 24).max().orElse(200);
    double height = nodes.stream().mapToDouble(n -> n.y + nodeHeight() + 24).max().orElse(150);
    if (animated && !profile.inputDefaults().reduceMotion())
      camera().overviewAnimated(viewport.width(), viewport.height(), width, height, 1.12, Math.min(profile.zoom().hardMax(), 1.51));
    else camera().overview(viewport.width(), viewport.height(), width, height, 1.12, Math.min(profile.zoom().hardMax(), 1.51));
  }

  private void zoom(double factor, double x, double y) {
    suppressHover();
    if (profile.inputDefaults().reduceMotion()) camera().zoomAt(factor, x, y);
    else camera().zoomAtAnimated(factor, x, y);
  }

  private void renderInspector(GuiGraphics graphics) {
    Node selected =
        nodes.stream()
            .filter(n -> n.progress.bindingId().equals(state.selection))
            .findFirst()
            .orElse(null);
    if (selected == null) {
      close();
      inspectorTransition.reset();
      return;
    }
    if (selected.progress.revealed() && inspectorTransition.interactive(state.expanded))
      org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE.markCollectionSeen(
          quest, phase, selected.progress.bindingId());
    int width = inspectorWidth();
    int outerAlpha = alpha, firstHit = hits.size();
    alpha = Math.round(alpha * inspectorTransition.alpha());
    screen.enableScissor(graphics, absX + viewport.x(), absY + viewport.y(), absX + viewport.right(), absY + viewport.bottom());
    JeiScreenIngredients.occlude(
        screen,
        absX + inspector.x(),
        absY + inspector.y(),
        absX + inspector.right(),
        absY + inspector.bottom());
    graphics.pose().pushPose();
    graphics.pose().translate(0, 0, 350);
    AtlasV6Draw.shadow(graphics, inspector, alpha);
    graphics.fill(inspector.x(), inspector.y(), inspector.right(), inspector.bottom(), AtlasV6Draw.tint(0x0B131B, alpha * 99 / 100));
    AtlasV6Draw.frame(graphics, inspector, theme, alpha / 3);
    int x = inspector.x() + 8, y = inspector.y() + 6;
    button(graphics, inspector.right() - 19, y + 2, 12, "×", false, true, ButtonStyle.HEADER, this::close);
    button(graphics, inspector.right() - 35, y + 2, 12,
        favorites.contains(selected.entry.getEntryId()) ? "★" : "☆",
        favorites.contains(selected.entry.getEntryId()), true, ButtonStyle.HEADER, () -> {
          CollectionFavoritesStore.INSTANCE.toggle(selected.entry.getEntryId());
          favorites = CollectionFavoritesStore.INSTANCE.snapshot();
        });
    ItemStack subject = stack(selected);
    int textX = x;
    if (!subject.isEmpty()) {
      renderItem(graphics, subject, x, y + 1, 14);
      if (inspectorTransition.interactive(state.expanded)) JeiScreenIngredients.collectionSubject(screen, graphics, quest, phase, selected.progress.bindingId(),
          selected.entry.getEntryId().toString(), variantFor(selected), subject, x, y + 1, 14, 14);
      if (!hoverGuard.blocked() && inspectorTransition.interactive(state.expanded)
          && new HudRect(x, y, 16, 16).contains(mouseX, mouseY)) pendingTooltip = subject.copy();
      textX += 19;
    } else if (selected.progress.revealed()) {
      var context = CollectionEntryIcons.context(quest, phase, selected.progress.bindingId(), selected.entry);
      var visual = screen.getObjectiveIcons().select(context, false, false);
      if (visual.available()) {
        visual.render(graphics, x, y + 1, 14, alpha / 255f);
        textX += 19;
      }
    }
    Component title = selected.progress.revealed()
        ? (subject.isEmpty() ? selected.entry.getDisplayName() : subject.getHoverName())
        : Component.literal(tr("undiscovered"));
    if (!subject.isEmpty()) {
      var nativeLines = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(screen.getMinecraft(), subject);
      if (!nativeLines.isEmpty()) title = nativeLines.get(0);
    }
    var titleLines = screen.getFont().split(title, Math.max(18, inspector.right() - 47 - textX));
    int titlePitch = 12;
    int titleHeight = Math.max(18, (titleLines.size() - 1) * titlePitch + 8);
    float titleY = y + (titleHeight - ((titleLines.size() - 1) * titlePitch + 8)) / 2f;
    for (var line : titleLines) {
      JournalScaledTextRenderer.draw(graphics, screen.getFont(), line, textX, titleY, 1, color(0xE3EBEF), false);
      titleY += titlePitch;
    }
    Component titleHint = detailTab.equals("__tooltip") && !subject.isEmpty()
        ? org.arcadia.arc_quest.client.quest.collection.AtlasNativeTooltip.titleHint(subject) : Component.empty();
    int headerBottom = y + titleHeight;
    if (!titleHint.getString().isBlank()) {
      float hintScale = readable(.9f);
      int lastWidth = titleLines.isEmpty() ? 0 : screen.getFont().width(titleLines.get(titleLines.size() - 1));
      int hintX = textX + lastWidth + 6;
      float hintRowY = titleY - titlePitch;
      if (hintX + screen.getFont().width(titleHint) * hintScale > inspector.right() - 47) {
        hintX = textX;
        hintRowY = headerBottom + 3;
        headerBottom += (int) Math.ceil(8 * hintScale) + 4;
      }
      float hintY = hintRowY + 8 * (1 - hintScale) / 2f;
      var hintLines = screen.getFont().split(titleHint, Math.max(1, (int) ((inspector.right() - 47f - hintX) / hintScale)));
      int hintPitch = (int) Math.ceil(11 * hintScale);
      for (int i = 0; i < hintLines.size(); i++)
        JournalScaledTextRenderer.draw(graphics, screen.getFont(), hintLines.get(i), hintX, hintY + i * hintPitch,
            hintScale, color(0xB7C7D0), false);
      headerBottom = Math.max(headerBottom, (int) Math.ceil(hintY + (hintLines.size() - 1) * hintPitch + 8 * hintScale));
      renderedTitleHint = titleHint.getString();
      renderedTitleHintBounds = new HudRect(hintX, (int) Math.floor(hintY), (int) Math.ceil(screen.getFont().width(titleHint) * hintScale),
          (int) Math.ceil((hintLines.size() - 1) * hintPitch + 8 * hintScale));
    }
    y = Math.max(y + 18, headerBottom) + 4;
    AtlasV6Draw.hairline(graphics, x, y, inspector.right() - 8 - x, theme, alpha / 3);
    y += 4;
    renderPagedInspector(graphics, selected, x, y, width - 16);
    graphics.pose().popPose();
    screen.disableScissor(graphics);
    if (!inspectorTransition.interactive(state.expanded)) hits.subList(firstHit, hits.size()).clear();
    alpha = outerAlpha;
  }

  private List<DetailTab> detailTabs(Node selected) {
    List<DetailTab> tabs = new ArrayList<>();
    if (!stack(selected).isEmpty()) tabs.add(new DetailTab("__tooltip", Component.literal(tr("page_tooltip")), "TOOLTIP", List.of(), -1, 1));
    var configured = selected.entry.getPresentation().detailPages();
    if (!configured.isEmpty() && selected.progress.revealed()) {
      for (var page : configured) tabs.add(new DetailTab(page.id(), page.title().resolve(null, QuestTextContext.empty()),
          page.type().name(), page.paragraphs(), page.providerSection(), page.providerSectionCount()));
    } else {
      if (!selected.progress.revealed() || !selected.entry.getDescription().getString().isBlank() || !selected.progress.content().isEmpty())
        tabs.add(new DetailTab("__content", Component.literal(tr("page_content")), "CONTENT", List.of(), -1, 1));
      if (profile.inspector().sections().contains("SOURCES") && selected.progress.revealed())
        tabs.add(new DetailTab("__sources", Component.literal(tr("sources")), "SOURCES", List.of(), -1, 1));
      if (selected.progress.revealed() && org.arcadia.arc_quest.quest.registry.CollectionDetailProviderRegistry.contains(selected.entry.getEntryId()))
        tabs.add(new DetailTab("__provider", Component.literal(tr("page_provider")), "PROVIDER", List.of(), -1, 1));
    }
    if (tabs.isEmpty()) tabs.add(new DetailTab("__content", Component.literal(tr("page_content")), "CONTENT", List.of(), -1, 1));
    return tabs;
  }

  private void renderPagedInspector(GuiGraphics g, Node selected, int x, int y, int width) {
    var tabs = detailTabs(selected);
    int selectedIndex = 0;
    for (int i = 0; i < tabs.size(); i++) if (tabs.get(i).id.equals(detailTab)) selectedIndex = i;
    detailTab = tabs.get(selectedIndex).id;
    detailTabStart = Math.max(0, Math.min(detailTabStart, tabs.size() - 1));
    int totalTabWidth = tabs.stream().mapToInt(t -> (int) Math.ceil(screen.getFont().width(t.title) * readable(.9f)) + 12).sum();
    boolean overflowTabs = totalTabWidth > width;
    if (!overflowTabs) detailTabStart = 0;
    int tx = x + (overflowTabs ? 14 : 0), lastTab = detailTabStart - 1;
    if (overflowTabs) button(g, x, y, 12, "‹", false, detailTabStart > 0, ButtonStyle.PAGE, () -> detailTabStart--);
    for (int i = detailTabStart; i < tabs.size(); i++) {
      var tab = tabs.get(i);
      int tw = Math.min(width - (overflowTabs ? 28 : 0), (int) Math.ceil(screen.getFont().width(tab.title) * readable(.9f)) + 10);
      if (tx + tw > x + width - (overflowTabs ? 14 : 0)) break;
      button(g, tx, y, tw, tab.title.getString(), tab.id.equals(detailTab), true, ButtonStyle.TAB,
          () -> { detailTab = tab.id; detailPage = 0; });
      tx += tw + 2; lastTab = i;
    }
    if (overflowTabs) button(g, x + width - 12, y, 12, "›", false, lastTab < tabs.size() - 1, ButtonStyle.PAGE,
        () -> detailTabStart = Math.min(tabs.size() - 1, detailTabStart + 1));
    // A selected tab may be outside the author-controlled long tab strip; keep it reachable.
    y += 24;
    // Quality is persistent navigation, not a document page. Never leave a page containing only
    // selectors when a native tooltip section has to continue on the next page.
    AtlasV6Draw.hairline(g, x, y - 3, width, 0xA0B2BC, alpha / 8);
    y = renderQualityButtons(g, selected, x, y, width);
    HudRect content = new HudRect(x, y, width, Math.max(1, inspector.bottom() - y - 23));
    List<AtlasPageLayout.Block<PageDraw>> blocks = new ArrayList<>();
    var tab = tabs.get(selectedIndex);
    switch (tab.type) {
      case "TOOLTIP" -> tooltipBlocks(blocks, selected, content);
      case "TEXT" -> tab.paragraphs.forEach(t -> textBlocks(blocks, t.resolve(null, QuestTextContext.empty()), content.width(), 0xD1DAE0));
      case "CONTENT" -> contentBlocks(blocks, selected, content.width());
      case "SOURCES" -> sourceBlocks(blocks, selected, content.width());
      case "PROVIDER" -> providerBlocks(blocks, selected, content.width(), tab.section, tab.sectionCount);
      default -> throw new IllegalStateException("Unknown detail page type " + tab.type);
    }
    if (blocks.isEmpty()) textBlocks(blocks, Component.literal(tr("page_empty")), content.width(), 0xA0B2BC);
    var pages = AtlasPageLayout.paginate(blocks, content.height());
    detailPageCount = pages.size();
    detailPage = Math.max(0, Math.min(detailPage, detailPageCount - 1));
    inspectorHeight = 0; detailViewportHeight = content.height();
    for (var slice : pages.get(detailPage)) {
      HudRect clip = new HudRect(content.x(), content.y() + slice.y(), content.width(), slice.height());
      screen.enableScissor(g, absX + clip.x(), absY + clip.y(), absX + clip.right(), absY + clip.bottom());
      int first = hits.size();
      slice.content().draw(g, new HudRect(content.x(), clip.y() - slice.offset(), content.width(), slice.totalHeight()), clip);
      // Partial oversized components can draw, but their clipped controls cannot receive input.
      if (slice.offset() != 0 || slice.height() != slice.totalHeight()) hits.subList(first, hits.size()).clear();
      // Native tooltip components enqueue font vertices directly; finish them under this slice's clip.
      g.flush();
      screen.disableScissor(g);
      inspectorHeight = Math.max(inspectorHeight, slice.y() + slice.height());
    }
    detailVisibleText = List.copyOf(pageText);
    pageText.clear();
    int fy = inspector.bottom() - 20;
    AtlasV6Draw.hairline(g, x, fy - 4, width, 0xA0B2BC, alpha / 7);
    String pageTitle = tab.title.getString() + " · " + (detailPage + 1) + " / " + detailPageCount;
    float footerScale = readable(.88f);
    centered(g, pageTitle, new HudRect(x + 20, fy, width - 40, 18), footerScale, 0xB0C0CA);
    detailElements.add(new AtlasInspectionSnapshot.DetailElement("PAGE_TITLE", new HudRect(x + 20, fy, width - 40, 18), pageTitle));
    int previous = hits.size();
    button(g, x, fy, 16, "‹", false, detailPage > 0, ButtonStyle.PAGE, () -> detailPage--);
    if (hits.size() > previous) renameLastHit(tr("page_previous"));
    previous = hits.size();
    button(g, x + width - 16, fy, 16, "›", false, detailPage + 1 < detailPageCount, ButtonStyle.PAGE, () -> detailPage++);
    if (hits.size() > previous) renameLastHit(tr("page_next"));
  }

  private void renameLastHit(String label) {
    var hit = hits.remove(hits.size() - 1);
    hits.add(new Hit(hit.box, hit.action, label));
  }

  private final List<String> pageText = new ArrayList<>();
  private final List<AtlasInspectionSnapshot.DetailElement> detailElements = new ArrayList<>();

  private void textBlocks(List<AtlasPageLayout.Block<PageDraw>> blocks, Component text, int width, int rgb) {
    if (text.getString().isBlank()) return;
    for (var line : screen.getFont().split(text, Math.max(1, width))) {
      blocks.add(new AtlasPageLayout.Block<>(screen.getFont().lineHeight + 3, (g, row, clip) -> {
        JournalScaledTextRenderer.draw(g, screen.getFont(), line, row.x(), row.y() + 1, 1, color(rgb), false);
        pageText.add(lineToString(line));
      }));
    }
    blocks.add(new AtlasPageLayout.Block<>(4, (g, row, clip) -> {}));
  }

  private static String lineToString(net.minecraft.util.FormattedCharSequence line) {
    StringBuilder text = new StringBuilder();
    line.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
    return text.toString();
  }

  private int renderQualityButtons(GuiGraphics g, Node selected, int x, int y, int width) {
    if (selected.entry.getPresentation().variants().isEmpty()) return y;
    int qualityX = 0;
    for (var option : selected.entry.getPresentation().variants()) {
      String label = option.label().resolve(null, QuestTextContext.empty()).getString();
      int qualityWidth = Math.min(width, (int) Math.ceil(screen.getFont().width(label) * readable(.9f)) + 12);
      if (qualityX > 0 && qualityX + qualityWidth > width) { y += 20; qualityX = 0; }
      button(g, x + qualityX, y, qualityWidth, label, option.variantId().equals(variantFor(selected)), true,
          ButtonStyle.QUALITY, () -> {
            variant = option.variantId(); state.atlasVariant = variant; detailPage = 0; camera().cancelMotion();
          });
      detailElements.add(new AtlasInspectionSnapshot.DetailElement("QUALITY", new HudRect(x + qualityX, y, qualityWidth, 17), label));
      qualityX += qualityWidth + 3;
    }
    return y + 23;
  }

  private void tooltipBlocks(List<AtlasPageLayout.Block<PageDraw>> blocks, Node selected, HudRect content) {
    int width=content.width(), availableHeight=content.height();
    var subject = stack(selected);
    var font = net.minecraftforge.client.ForgeHooksClient.getTooltipFont(subject, screen.getFont());
    var nativeParts = org.arcadia.arc_quest.client.quest.collection.AtlasNativeTooltip.gather(screen.getMinecraft(), subject, font, width,
        new org.arcadia.arc_quest.client.quest.collection.AtlasRenderContext(font, Math.max(1,width-12), Math.max(1,availableHeight),
            screen.getUiScale()*screen.getMinecraft().getWindow().getGuiScale(), alpha, theme,
            new org.arcadia.arc_quest.client.quest.collection.AtlasRenderContext.Rect(content.x(),content.y(),content.width(),content.height()),
            java.util.List.of(new org.arcadia.arc_quest.client.quest.collection.AtlasRenderContext.Rect(inspector.x(),inspector.y(),inspector.width(),inspector.height()))));
    var nativeBlocks = org.arcadia.arc_quest.client.quest.collection.AtlasTooltipLayout.blocks(nativeParts);
    int occupied = blocks.stream().mapToInt(AtlasPageLayout.Block::height).sum();
    int naturalHeight = nativeBlocks.stream().mapToInt(AtlasPageLayout.Block::height).sum();
    float scale = org.arcadia.arc_quest.client.quest.collection.AtlasTooltipLayout.fitScale(naturalHeight, Math.max(1, availableHeight - occupied));
    if (scale < 1) {
      blocks.add(new AtlasPageLayout.Block<>(Math.max(1, availableHeight - occupied), (g, row, clip) -> {
        g.fill(row.x(), row.y(), row.right(), row.bottom(), AtlasV6Draw.tint(0x060B12, alpha / 3));
        g.fill(row.x(), row.y(), row.x() + 1, row.bottom(), AtlasV6Draw.tint(theme, alpha / 3));
        g.pose().pushPose(); g.pose().translate(row.x() + 6, row.y(), 0); g.pose().scale(scale, scale, 1);
        int nativeY = 0;
        for (var block : nativeBlocks) {
          var part = block.content();
          if (!part.blank()) {
            var component = part.content();
            g.pose().pushPose(); g.pose().translate(0, nativeY, 0);
            renderTooltipComponent(g, font, component); g.pose().popPose();
          }
          pageText.add(part.text()); nativeY += block.height();
        }
        g.pose().popPose();
      }));
      return;
    }
    for (var nativeBlock : nativeBlocks) {
      var part = nativeBlock.content();
      var component = part.content();
      blocks.add(new AtlasPageLayout.Block<>(Math.max(1, (int) Math.ceil(nativeBlock.height() * scale)), (g, row, clip) -> {
        if (!(component instanceof org.arcadia.arc_quest.client.quest.collection.AtlasNativeTooltip.StyledComponent)) {
          g.fill(row.x(), row.y(), row.right(), row.bottom(), AtlasV6Draw.tint(0x060B12, alpha / 3));
          g.fill(row.x(), row.y(), row.x() + 1, row.bottom(), AtlasV6Draw.tint(theme, alpha / 3));
        }
        if (!part.blank()) {
          g.pose().pushPose(); g.pose().translate(row.x() + 6, row.y(), 0); g.pose().scale(scale, scale, 1);
          renderTooltipComponent(g, font, component); g.pose().popPose();
        }
        pageText.add(part.text().isEmpty() ? "tooltip-component:" + component.getClass().getSimpleName() + ":" + component.getHeight() : part.text());
      }, nativeBlock.keepWithNext()));
    }
  }

  private void renderTooltipComponent(GuiGraphics g, net.minecraft.client.gui.Font font,
      net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent component) {
    if (component instanceof org.arcadia.arc_quest.client.quest.collection.AtlasNativeTooltip.StyledComponent styled)
      styled.renderInAtlas(font, g, alpha, theme);
    else {
      component.renderText(font, 0, 0, g.pose().last().pose(), g.bufferSource());
      component.renderImage(font, 0, 0, g);
    }
  }

  private void contentBlocks(List<AtlasPageLayout.Block<PageDraw>> blocks, Node selected, int width) {
    textBlocks(blocks, selected.progress.revealed() ? selected.entry.getDescription() : selected.progress.publicClue(), width, 0xD1DAE0);
    for (var block : selected.progress.content()) {
      textBlocks(blocks, block.text().resolve(null, QuestTextContext.empty()), width, 0xD1DAE0);
      var media = block.media();
      if (media != null && org.arcadia.arc_quest.client.hud.guide.GuideImageRenderer.available(media)) {
        int height = Math.max(32, Math.min(112, width * media.getHeight() / Math.max(1, media.getWidth())));
        blocks.add(new AtlasPageLayout.Block<>(height + 6, (g, row, clip) -> {
          org.arcadia.arc_quest.client.hud.guide.GuideImageRenderer.draw(g, media, row.x(), row.y(), row.width(), height,
              block.fit() == CollectionMediaFit.COVER ? org.arcadia.arc_quest.client.hud.guide.GuideImageLayout.Fit.COVER
                  : org.arcadia.arc_quest.client.hud.guide.GuideImageLayout.Fit.CONTAIN, alpha / 255f);
          if (block.zoomable()) hits.add(new Hit(row, () -> imageViewer.open(media,
              block.caption().resolve(null, QuestTextContext.empty())), tr("view_image")));
        }));
      }
      textBlocks(blocks, block.caption().resolve(null, QuestTextContext.empty()), width, 0xA0B2BC);
    }
    if (profile.inspector().sections().contains("ACTIONS") && selected.entry.getSubjectKind() == CollectionSubjectKind.CUSTOM)
      blocks.add(new AtlasPageLayout.Block<>(32, (g, row, clip) -> renderTrackingActions(g, selected, clip, row.y())));
  }

  private void providerBlocks(List<AtlasPageLayout.Block<PageDraw>> blocks, Node selected, int width, int index, int count) {
    var cache = org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE;
    var details = org.arcadia.arc_quest.quest.network.ClientCollectionDetails.INSTANCE;
    var data = cache.getCollectionData(quest);
    if (data != null && state.expanded && cache.getPlayerSessionEpoch() > 0) {
      var key = new org.arcadia.arc_quest.quest.network.ClientCollectionDetails.Key(cache.getPlayerSessionEpoch(),
          cache.getDatapackReloadEpoch(), quest, phase, selected.progress.bindingId(), data.getRunId(), variantFor(selected));
      var request = details.request(key, net.minecraft.Util.getMillis());
      if (request != null) org.arcadia.arc_quest.quest.network.ArcQuestNetwork.CHANNEL.sendToServer(request);
    }
    var doc = details.document();
    if (doc == null) { textBlocks(blocks, Component.translatable("arc_quest.collection.detail.loading"), width, 0xA0B2BC); return; }
    if (doc.sections().isEmpty() || index >= doc.sections().size()) {
      textBlocks(blocks, Component.translatable("arc_quest.collection.detail.unavailable"), width, 0xA0B2BC); return;
    }
    var sections = index < 0 ? doc.sections() : doc.sections().subList(index,
        count == 0 ? doc.sections().size() : Math.min(doc.sections().size(), index + count));
    for (var section : sections) {
      int sectionStart = blocks.size();
      boolean location = section.layout() == CollectionDetailDocument.Layout.LOCATION_INFO;
      if (section.layout() == CollectionDetailDocument.Layout.UPGRADE_PATH)
        blocks.add(new AtlasPageLayout.Block<>(6, (g, row, clip) -> AtlasV6Draw.hairline(g, row.x(), row.y(), row.width(), theme, alpha / 8)));
      if (location) detailText(blocks, section.status(), width, .86f, 0xA8CEBA, 6);
      else detailStatus(blocks, section.status(), width);
      detailText(blocks, section.title(), width, .92f, theme, 5);
      if (section.layout() == CollectionDetailDocument.Layout.LARGE_ITEM_GRID
          || section.layout() == CollectionDetailDocument.Layout.VALUABLE_ITEM_GRID
          || section.layout() == CollectionDetailDocument.Layout.ITEM_CARDS
          || section.layout() == CollectionDetailDocument.Layout.SOURCE_CARDS) {
        detailRichItems(blocks, section.lines(), selected, width, section.layout());
      } else if (section.layout() == CollectionDetailDocument.Layout.UPGRADE_PATH) {
        detailUpgradePath(blocks, section.lines(), selected, width);
      } else if (section.layout() == CollectionDetailDocument.Layout.ITEM_GRID) {
        detailItemGrid(blocks, section.lines(), selected, width);
      } else for (var line : section.lines()) {
        int lineStart = blocks.size();
        if (line.alternatives().isEmpty() && line.required() == 0) {
          detailText(blocks, line.label(), width, 1f, 0xD1DCE2, 5);
          continue;
        }
        Component label = line.required() > 0 ? Component.translatable("arc_quest.collection.detail.quantity", line.label(), line.required()) : line.label();
        int iconWidth = line.alternatives().isEmpty() ? 0 : 25;
        var labelLines = screen.getFont().split(label, Math.max(1, width - iconWidth - 2));
        int rowHeight = Math.max(iconWidth == 0 ? 12 : 23, labelLines.size() * 10 + 3);
        blocks.add(new AtlasPageLayout.Block<>(rowHeight, (g, row, clip) -> {
          int textY = row.y() + Math.max(1, (row.height() - labelLines.size() * 10) / 2);
          for (var wrapped : labelLines) {
            g.drawString(screen.getFont(), wrapped, row.x() + iconWidth, textY, color(0xD1DAE0), false);
            pageText.add(lineToString(wrapped)); textY += 10;
          }
          if (!line.alternatives().isEmpty()) {
            var item = line.alternatives().get((int) (net.minecraft.Util.getMillis() / 1500 % line.alternatives().size()));
            renderItem(g, item, row.x() + 1, row.y() + Math.max(1, (row.height() - 18) / 2), 18);
            if (inspectorTransition.interactive(state.expanded) && row.y() >= clip.y() && row.y() + 22 <= clip.bottom())
              JeiScreenIngredients.collectionDetailItem(screen, g, quest, phase, selected.progress.bindingId(), item, row.x(), row.y(), 22, 22);
            if (!hoverGuard.blocked() && clip.contains(mouseX, mouseY) && new HudRect(row.x(), row.y(), 22, 22).contains(mouseX, mouseY)) pendingTooltip = item.copy();
          }
        }));
        if (line.owned() >= 0 && line.required() > 0) textBlocks(blocks,
            Component.translatable("arc_quest.collection.detail.have_need", line.owned(), line.required()), width,
            line.owned() >= line.required() ? 0x87CF9D : 0xE38B86);
        keepDetailBlocksTogether(blocks, lineStart);
      }
      detailText(blocks, section.hint(), width, .84f, 0xAABCC7, 5);
      if (location && selected.entry.getSubjectKind() == CollectionSubjectKind.CUSTOM)
        blocks.add(new AtlasPageLayout.Block<>(29, (g, row, clip) -> renderTrackingActions(g, selected, row, row.y())));
      if (section.layout() != CollectionDetailDocument.Layout.ROWS && !location) {
        if (blocks.size() > sectionStart + 1) {
          var first = blocks.get(sectionStart);
          blocks.set(sectionStart, new AtlasPageLayout.Block<>(first.height(), first.content(), 1));
        }
      } else keepDetailBlocksTogether(blocks, sectionStart);
    }
  }

  /** A small, wrapped paragraph with a measured height. Never silently truncate provider content. */
  private void detailText(List<AtlasPageLayout.Block<PageDraw>> blocks, Component text, int width,
      float scale, int rgb, int gap) {
    if (text.getString().isBlank()) return;
    float textScale = readable(scale);
    var lines = screen.getFont().split(text, Math.max(1, (int) (width / textScale)));
    int pitch = (int) Math.ceil((screen.getFont().lineHeight + 3) * textScale);
    blocks.add(new AtlasPageLayout.Block<>(lines.size() * pitch + gap, (g, row, clip) -> {
      int y = row.y();
      for (var line : lines) {
        JournalScaledTextRenderer.draw(g, screen.getFont(), line, row.x(), y + 1, textScale, color(rgb), false);
        pageText.add(lineToString(line)); y += pitch;
      }
    }));
  }

  private void detailStatus(List<AtlasPageLayout.Block<PageDraw>> blocks, Component text, int width) {
    if (text.getString().isBlank()) return;
    int rgb = text.getStyle().getColor() == null ? 0xA3CBB7 : text.getStyle().getColor().getValue();
    float textScale = readable(.9f);
    int pitch = (int) Math.ceil(11 * textScale);
    var lines = screen.getFont().split(text, Math.max(1, (int) ((width - 12) / textScale)));
    blocks.add(new AtlasPageLayout.Block<>(lines.size() * pitch + 12, (g, row, clip) -> {
      int bottom = row.bottom() - 6;
      detailElements.add(new AtlasInspectionSnapshot.DetailElement("STATUS", new HudRect(row.x(), row.y(), row.width(), bottom - row.y()), text.getString()));
      g.fill(row.x(), row.y(), row.right(), bottom, AtlasV6Draw.tint(rgb, alpha / 28));
      g.fill(row.x(), row.y(), row.x() + 1, bottom, AtlasV6Draw.tint(rgb, alpha / 2));
      float y = row.y() + (bottom - row.y() - (lines.size() - 1) * pitch - 8 * textScale) / 2f;
      for (var line : lines) {
        JournalScaledTextRenderer.draw(g, screen.getFont(), line, row.x() + 5, y, textScale, color(rgb), false);
        pageText.add(lineToString(line)); y += pitch;
      }
    }));
  }

  private void detailItemGrid(List<AtlasPageLayout.Block<PageDraw>> blocks,
      List<org.arcadia.arc_quest.quest.api.CollectionDetailDocument.Line> items, Node selected, int width) {
    int columns = Math.max(1, width / 26);
    for (int start = 0; start < items.size(); start += columns) {
      var rowItems = items.subList(start, Math.min(items.size(), start + columns));
      blocks.add(new AtlasPageLayout.Block<>(26, (g, row, clip) -> {
        for (int col = 0; col < rowItems.size(); col++) {
          var item = rowItems.get(col).alternatives().stream().findFirst().orElse(ItemStack.EMPTY);
          if (item.isEmpty()) continue;
          int x = row.x() + col * 26;
          var cell = new HudRect(x, row.y(), 23, 23);
          g.fill(x, row.y(), x + 23, row.y() + 23, AtlasV6Draw.tint(0x060B12, alpha / 2));
          AtlasV6Draw.frame(g, cell, theme, alpha / 4);
          renderItem(g, item, x + 2, row.y() + 2, 19);
          pageText.add(item.getHoverName().getString());
          if (inspectorTransition.interactive(state.expanded) && row.y() >= clip.y() && cell.bottom() <= clip.bottom())
            JeiScreenIngredients.collectionDetailItem(screen, g, quest, phase, selected.progress.bindingId(), item, x, row.y(), 23, 23);
          if (!hoverGuard.blocked() && clip.contains(mouseX, mouseY) && cell.contains(mouseX, mouseY)) pendingTooltip = item.copy();
        }
      }));
    }
  }

  private static void keepDetailBlocksTogether(List<AtlasPageLayout.Block<PageDraw>> blocks, int start) {
    if (start >= blocks.size()) return;
    var first = blocks.get(start);
    blocks.set(start, new AtlasPageLayout.Block<>(first.height(), first.content(), blocks.size() - start - 1));
  }

  /** Display-only item cells, optionally linked to an authorized, revealed collection entry. */
  private void detailRichItems(List<AtlasPageLayout.Block<PageDraw>> blocks,
      List<CollectionDetailDocument.Line> items, Node selected, int width, CollectionDetailDocument.Layout layout) {
    boolean source = layout == CollectionDetailDocument.Layout.SOURCE_CARDS;
    boolean cards = source || layout == CollectionDetailDocument.Layout.ITEM_CARDS;
    boolean valuable = layout == CollectionDetailDocument.Layout.VALUABLE_ITEM_GRID;
    int columns = source ? 1 : cards ? (width >= 130 ? 2 : 1) : Math.max(1, width / 18);
    int cellWidth = width / columns;
    for (int start = 0; start < items.size(); start += columns) {
      var rowItems = items.subList(start, Math.min(items.size(), start + columns));
      int textWidth = Math.max(12, cellWidth - (source ? 35 : 28));
      float titleScale = readable(.92f), subtitleScale = readable(.84f);
      int titlePitch = (int) Math.ceil(11 * titleScale), subtitlePitch = (int) Math.ceil(11 * subtitleScale);
      int rowHeight = cards ? 31 : 19;
      if (cards) for (var line : rowItems) {
        int titleCount = screen.getFont().split(line.label(), (int) (textWidth / titleScale)).size();
        int subtitleCount = line.details().isEmpty() ? 0 : screen.getFont().split(line.details().get(0), (int) (textWidth / subtitleScale)).size();
        int textHeight = (titleCount - 1) * titlePitch + (int) Math.ceil(8 * titleScale);
        if (subtitleCount > 0) textHeight += 3 + (subtitleCount - 1) * subtitlePitch + (int) Math.ceil(8 * subtitleScale);
        rowHeight = Math.max(rowHeight, textHeight + 12);
      }
      int measuredHeight = rowHeight;
      blocks.add(new AtlasPageLayout.Block<>(rowHeight, (g, row, clip) -> {
        for (int col = 0; col < rowItems.size(); col++) {
          var line = rowItems.get(col);
          var item = line.alternatives().stream().findFirst().orElse(ItemStack.EMPTY);
          int x = row.x() + col * cellWidth;
          var cell = new HudRect(x, row.y(), cellWidth - 2, measuredHeight - 2);
          detailElements.add(new AtlasInspectionSnapshot.DetailElement(layout.name(), cell, line.label().getString()));
          boolean hovered = !hoverGuard.blocked() && clip.contains(mouseX, mouseY) && cell.contains(mouseX, mouseY);
          int itemSize = cards ? (source ? 12 : 14) : 15;
          int itemX = x + (cards ? 3 : Math.max(1, (cell.width() - itemSize) / 2));
          int itemY = row.y() + (cell.height() - itemSize) / 2;
          if (cards) {
            g.fill(x, row.y(), cell.right(), cell.bottom(), AtlasV6Draw.tint(hovered ? theme : 0xFFFFFF, hovered ? alpha / 12 : alpha / 70));
            if (source) {
              g.fill(x, row.y(), x + 1, cell.bottom(), AtlasV6Draw.tint(theme, alpha / 3));
              AtlasV6Draw.hairline(g, x, cell.bottom() - 1, cell.width(), 0xA0B2BC, alpha / 8);
            } else AtlasV6Draw.frame(g, cell, hovered ? theme : 0xA0B2BC, hovered ? alpha / 2 : alpha / 7);
          } else {
            g.fill(x, row.y(), cell.right(), cell.bottom(), AtlasV6Draw.tint(hovered ? 0x293C47 : valuable ? 0x25251E : 0x15212A, alpha));
            AtlasV6Draw.frame(g, cell, hovered ? theme : valuable ? 0xBC9A5D : 0x677A88, alpha / (hovered ? 1 : 3));
            g.fill(x + 1, row.y() + 1, cell.right() - 1, row.y() + 2, AtlasV6Draw.tint(0x03090E, alpha / 2));
          }
          if (!item.isEmpty()) renderItem(g, item, itemX, itemY, itemSize);
          pageText.add(line.label().getString());
          if (cards) {
            var labelLines = screen.getFont().split(line.label(), (int) (textWidth / titleScale));
            var secondary = line.details().isEmpty() ? List.<net.minecraft.util.FormattedCharSequence>of()
                : screen.getFont().split(line.details().get(0), (int) (textWidth / subtitleScale));
            float textHeight = (labelLines.size() - 1) * titlePitch + 8 * titleScale;
            if (!secondary.isEmpty()) textHeight += 3 + (secondary.size() - 1) * subtitlePitch + 8 * subtitleScale;
            float ty = row.y() + (cell.height() - textHeight) / 2f;
            int textX = x + (source ? 21 : 22);
            for (var wrapped : labelLines) {
              JournalScaledTextRenderer.draw(g, screen.getFont(), wrapped, textX, ty, titleScale, color(0xD9E3E9), false);
              ty += titlePitch;
            }
            ty += 8 * titleScale - titlePitch + 3;
            for (var wrapped : secondary) {
              JournalScaledTextRenderer.draw(g, screen.getFont(), wrapped, textX, ty, subtitleScale, color(0xAABCC7), false);
              ty += subtitlePitch;
            }
            if (source && line.link() != null && findDestination(line.link().entry()) != null)
              centered(g, "›", new HudRect(cell.right() - 12, cell.y(), 12, cell.height()), 1, theme);
          }
          if (hovered) {
            pendingTooltip = item.copy();
            if (!line.details().isEmpty()) {
              var tip = new ArrayList<Component>(); tip.add(line.label()); tip.addAll(line.details());
              pendingDetailTooltip = List.copyOf(tip);
            }
          }
          if (inspectorTransition.interactive(state.expanded) && cell.y() >= clip.y() && cell.bottom() <= clip.bottom()) {
            Destination destination = line.link() == null ? null : findDestination(line.link().entry());
            if (destination != null) {
              var linked = new Destination(destination.quest, destination.phase, destination.binding, line.link().variant());
              hits.add(new Hit(cell, () -> navigate(linked), line.label().getString()));
            }
            if (!item.isEmpty()) JeiScreenIngredients.collectionDetailItem(screen, g, quest, phase,
                selected.progress.bindingId(), item, itemX, itemY, itemSize, itemSize);
          }
        }
      }));
    }
  }

  private void detailUpgradePath(List<AtlasPageLayout.Block<PageDraw>> blocks,
      List<CollectionDetailDocument.Line> items, Node selected, int width) {
    if (items.size() != 2) { detailRichItems(blocks, items, selected, width, CollectionDetailDocument.Layout.ITEM_CARDS); return; }
    float textScale = readable(.9f);
    int firstWidth = Math.min((width - 20) / 2, 28 + (int) Math.ceil(screen.getFont().width(items.get(0).label()) * textScale));
    int secondWidth = Math.min(width - firstWidth - 20, 28 + (int) Math.ceil(screen.getFont().width(items.get(1).label()) * textScale));
    var firstLines = screen.getFont().split(items.get(0).label(), Math.max(1, (int) ((firstWidth - 26) / textScale)));
    var secondLines = screen.getFont().split(items.get(1).label(), Math.max(1, (int) ((secondWidth - 26) / textScale)));
    int pitch = (int) Math.ceil(11 * textScale);
    int height = Math.max(24, (Math.max(firstLines.size(), secondLines.size()) - 1) * pitch + (int) Math.ceil(8 * textScale) + 10);
    blocks.add(new AtlasPageLayout.Block<>(height + 5, (g, row, clip) -> {
      for (int i = 0; i < 2; i++) {
        var line = items.get(i);
        var item = line.alternatives().stream().findFirst().orElse(ItemStack.EMPTY);
        int x = row.x() + (i == 0 ? 0 : firstWidth + 20);
        var cell = new HudRect(x, row.y(), i == 0 ? firstWidth : secondWidth, height);
        detailElements.add(new AtlasInspectionSnapshot.DetailElement("UPGRADE_PATH", cell, line.label().getString()));
        if (i == 0) {
          g.fill(x, row.y(), cell.right(), cell.bottom(), AtlasV6Draw.tint(0xFFFFFF, alpha / 80));
          AtlasV6Draw.frame(g, cell, theme, alpha / 5);
        }
        int itemY = cell.y() + (height - 16) / 2;
        if (!item.isEmpty()) renderItem(g, item, x + 3, itemY, 16);
        var lines = i == 0 ? firstLines : secondLines;
        float ty = cell.y() + (height - (lines.size() - 1) * pitch - 8 * textScale) / 2f;
        for (var wrapped : lines) {
          JournalScaledTextRenderer.draw(g, screen.getFont(), wrapped, x + 23, ty, textScale, color(i == 0 ? 0xC5D4DD : 0xE0D3F3), false);
          ty += pitch;
        }
        pageText.add(line.label().getString());
        if (i == 0) centered(g, "→", new HudRect(cell.right(), cell.y(), 20, height), textScale, 0x9AADBA);
        if (!hoverGuard.blocked() && clip.contains(mouseX, mouseY) && cell.contains(mouseX, mouseY)) {
          pendingTooltip = item.copy();
          if (!line.details().isEmpty()) { var tip = new ArrayList<Component>(); tip.add(line.label()); tip.addAll(line.details()); pendingDetailTooltip = List.copyOf(tip); }
        }
        if (inspectorTransition.interactive(state.expanded) && cell.y() >= clip.y() && cell.bottom() <= clip.bottom()) {
          if (line.link() != null) {
            var destination = findDestination(line.link().entry());
            if (destination != null) hits.add(new Hit(cell, () -> {
              if (line.link().entry().equals(selected.entry.getEntryId())) {
                variant = line.link().variant(); state.atlasVariant = variant; detailPage = 0;
              } else navigate(new Destination(destination.quest, destination.phase, destination.binding, line.link().variant()));
            }, line.label().getString()));
          }
          if (!item.isEmpty()) JeiScreenIngredients.collectionDetailItem(screen, g, quest, phase, selected.progress.bindingId(), item, x + 3, itemY, 16, 16);
        }
      }
    }));
  }

  private void sourceBlocks(List<AtlasPageLayout.Block<PageDraw>> blocks, Node selected, int width) {
    Set<ResourceLocation> seen = new HashSet<>();
    for (var set : definition.getCollectionConfig().getSourceSets()) for (var relation : set.relations().stream()
        .sorted(Comparator.comparingInt(CollectionSourceSetDefinition.Relation::sortOrder)).toList()) {
      boolean incoming = relation.targetEntryId().equals(selected.entry.getEntryId());
      if (!seen.add(relation.relationId()) || incoming && !relation.variantIds().isEmpty() && !relation.variantIds().contains(variantFor(selected))) continue;
      ResourceLocation endpoint = incoming ? relation.sourceEntryId()
          : relation.sourceEntryId().equals(selected.entry.getEntryId()) ? relation.targetEntryId() : null;
      if (endpoint == null) continue;
      var resolved = findDestination(endpoint);
      if (resolved == null) continue;
      var destination = incoming || relation.variantIds().isEmpty() ? resolved
          : new Destination(resolved.quest, resolved.phase, resolved.binding, relation.variantIds().get(0));
      var def = org.arcadia.arc_quest.quest.registry.QuestRegistry.get(destination.quest);
      var entry = def == null || def.getCollectionConfig() == null ? null : def.getCollectionConfig().getEntry(endpoint);
      if (entry == null) continue;
      var cache = org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE;
      boolean navigable = org.arcadia.arc_quest.client.hud.quest.journal.JournalQuestVisibilityRegistry.isVisible(destination.quest)
          && (cache.getAllActiveQuests().containsKey(destination.quest) || cache.getCompletedQuests().contains(destination.quest) || cache.getFailedQuests().contains(destination.quest));
      String methodKey = "arc_quest.hud.atlas.method_" + relation.method().toLowerCase(Locale.ROOT);
      Component method = net.minecraft.locale.Language.getInstance().has(methodKey) ? Component.translatable(methodKey)
          : relation.description().resolve(null, QuestTextContext.empty());
      textBlocks(blocks, entry.getDisplayName(), width, theme);
      textBlocks(blocks, method, width, 0xA0B2BC);
      if (navigable) blocks.add(new AtlasPageLayout.Block<>(23, (g, row, clip) -> clippedButton(g, clip, row.x(), row.y(),
          Math.min(row.width(), (int) (screen.getFont().width(entry.getDisplayName()) * .82) + 16),
          tr("source_prefix") + entry.getDisplayName().getString(), false, () -> navigate(destination))));
    }
    if (blocks.isEmpty()) textBlocks(blocks, Component.literal(tr(selected.entry.getPresentation().variants().isEmpty()
        ? "no_sources" : "no_variant_sources")), width, 0xA0B2BC);
  }

  private boolean selectedRecorded(Node node) {
    var choice = node.entry.getPresentation().variants().stream()
        .filter(v -> v.variantId().equals(variantFor(node))).findFirst().orElse(null);
    if (choice == null || choice.outcomeId().isBlank()) return node.progress.discovered();
    var record = org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE.getCollectionRecord(node.entry.getEntryId());
    return record != null && record.hasOutcome(choice.outcomeId());
  }

  private void back() {
    if (!sourceHistory.isEmpty()) {
      BrowseSnapshot saved = sourceHistory.pop();
      pendingBrowseRestore = saved;
      Destination target = saved.destination;
      if (!screen.openAtlasDestination(target.quest, target.phase, target.binding, target.variant))
        pendingBrowseRestore = null;
    } else if (!history.isEmpty()) {
      String id = history.pop();
      nodes.stream()
          .filter(n -> n.progress.bindingId().equals(id))
          .findFirst()
          .ifPresent(this::select);
    }
  }

  private int renderTrackingActions(GuiGraphics graphics, Node selected, HudRect body, int y) {
    if (selected.entry.getSubjectKind() != CollectionSubjectKind.CUSTOM) return y;
    var marker = org.arcadia.arc_quest.client.hud.questmarker.QuestMarkerManager.INSTANCE
        .get(org.arcadia.arc_quest.quest.logic.CollectionSourceTrackingService.MARKER_ID);
    boolean tracked = marker != null && marker.getLabel().equals(selected.entry.getDisplayName().getString());
    String text = tr(tracked ? "cancel_tracking" : "track_location");
    graphics.fill(body.x(), y + 2, body.right(), y + 3, AtlasV6Draw.tint(0xA0B2BC, alpha / 7));
    y += 9;
    clippedButton(graphics, body, body.x(), y, body.width(), text, tracked, ButtonStyle.TRACK,
        () -> track(selected, "", tracked));
    return y + 23;
  }

  private int paragraph(GuiGraphics graphics, Component text, HudRect body, int y) {
    if (text.getString().isBlank()) return y;
    for (var line : screen.getFont().split(text, body.width())) {
      if (y + screen.getFont().lineHeight > body.y() && y < body.bottom())
        { graphics.pose().pushPose(); graphics.pose().translate(body.x(), y, 0); graphics.drawString(screen.getFont(), line, 0, 0, color(0xAAB4BF), false); graphics.pose().popPose(); }
      y += screen.getFont().lineHeight + 2;
    }
    return y + 5;
  }

  private void clippedButton(
      GuiGraphics graphics,
      HudRect clip,
      int x,
      int y,
      int width,
      String label,
      boolean active,
      Runnable action) {
    clippedButton(graphics, clip, x, y, width, label, active, ButtonStyle.ACTION, action);
  }

  private void clippedButton(GuiGraphics graphics, HudRect clip, int x, int y, int width,
      String label, boolean active, ButtonStyle style, Runnable action) {
    int h = style == ButtonStyle.QUALITY ? 15 : style == ButtonStyle.MINI ? 14 : 18;
    if (y + h <= clip.y() || y >= clip.bottom()) return;
    button(graphics, x, y, width, label, active, true, style, action);
    Hit hit = hits.remove(hits.size() - 1);
    int top = Math.max(y, clip.y()), bottom = Math.min(y + h, clip.bottom());
    hits.add(
        new Hit(
            new HudRect(
                Math.max(x, clip.x()),
                top,
                Math.min(x + width, clip.right()) - Math.max(x, clip.x()),
                bottom - top),
            hit.action,
            label));
  }

  void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    if (!pendingDetailTooltip.isEmpty() && !dragging && !searchOpen && !hoverGuard.blocked()
        && !camera().isAnimating() && !groupMenuOpen && !filterMenuOpen) {
      screen.renderTooltipLines(graphics, pendingDetailTooltip, mouseX, mouseY);
      return;
    }
    if (!pendingTooltip.isEmpty() && !dragging && !searchOpen && !hoverGuard.blocked()
        && !camera().isAnimating() && !groupMenuOpen && !filterMenuOpen)
      if (pendingCanvasTooltip && inspectorTransition.visible(state.expanded))
        screen.renderTooltipLeftOf(graphics, pendingTooltip, mouseX, mouseY, absX + inspector.x() - 4);
      else screen.renderTooltip(graphics, pendingTooltip, mouseX, mouseY);
  }

  private void track(Node selected, String relationId, boolean clear) {
    var cache = org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE;
    var data = cache.getCollectionData(quest);
    if (data == null) return;
    org.arcadia.arc_quest.quest.network.ArcQuestNetwork.CHANNEL.sendToServer(
        new org.arcadia.arc_quest.quest.network.C2STrackCollectionSourcePacket(
            cache.getPlayerSessionEpoch(),
            quest,
            phase,
            selected.progress.bindingId(),
            data.getRunId(),
            relationId,
            clear));
  }

  private Destination findDestination(ResourceLocation entryId) {
    for (var def : org.arcadia.arc_quest.quest.registry.QuestRegistry.getAll())
      if (def.getCollectionConfig() != null)
        for (var p : def.getAllPhases())
          if (p.hasCollectionSheet())
            for (var b : p.getCollectionSheet().getBindings())
              if (b.getEntryId().equals(entryId)) {
                var projected =
                    org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE
                        .getCollectionBindingProgress(
                            def.getId().toString(), p.getPhaseId(), b.getBindingId());
                if (projected != null && projected.visible() && projected.revealed())
                  return new Destination(
                      def.getId().toString(), p.getPhaseId(), b.getBindingId(), "");
              }
    return null;
  }

  private void navigate(Destination destination) {
    state.atlasVariant = variant;
    persist();
    sourceHistory.push(
        new BrowseSnapshot(
            new Destination(quest, phase, state.selection, variant),
            state.atlasStorageKey,
            state.category,
            state.query,
            camera().x,
            camera().y,
            camera().zoom,
            detailScroll,
            detailTab,
            detailPage,
            detailTabStart));
    if (!screen.openAtlasDestination(
        destination.quest, destination.phase, destination.binding, destination.variant))
      sourceHistory.pop();
  }

  void loadFavorites() {
    favorites = CollectionFavoritesStore.INSTANCE.snapshot();
  }

  boolean click(double x, double y) {
    if (state == null) return false;
    for (int i = hits.size() - 1; i >= 0; i--)
      if (hits.get(i).box.contains(x, y)) {
        hits.get(i).action.run();
        persist();
        screen.playClick();
        return true;
      }
    if (settingsOpen && !settingsBounds.contains(x, y)) settingsOpen = false;
    if (groupMenuOpen || filterMenuOpen) { groupMenuOpen = filterMenuOpen = false; return true; }
    if (search != null) {
      search.setFocused(searchOpen && searchBounds.contains(x, y));
      if (search.isFocused()) return search.mouseClicked(x, y, 0);
    }
    if (inspectorTransition.visible(state.expanded) && inspector.contains(x, y)) return true;
    if (!viewport.contains(x, y)) return false;
    dragging = true;
    moved = false;
    dragX = pressX = x;
    dragY = pressY = y;
    velocityX = velocityY = 0;
    pressed = nodes.stream().filter(this::matches).filter(n -> circleContains(nodeBox(n), x, y)).findFirst().orElse(null);
    return true;
  }

  boolean middleClick(double x, double y) {
    if (state == null || !viewport.contains(x, y)
        || inspectorTransition.visible(state.expanded) && inspector.contains(x, y)) return false;
    dragging = false; pressed = null;
    close(); overview(); persist();
    return true;
  }

  boolean drag(double x, double y, int button) {
    if (!dragging || button != 0) return false;
    double dx = x - dragX, dy = y - dragY;
    if (Math.hypot(x - pressX, y - pressY) > profile.motion().dragThreshold()) moved = true;
    if (!moved) return true;
    suppressHover();
    camera().pan(dx, dy);
    double cap = profile.motion().maxVelocityPerMs() * 1000;
    velocityX =
        Math.max(
            -cap, Math.min(cap, dx * 1000 / Math.max(16, profile.motion().velocitySampleMs())));
    velocityY =
        Math.max(
            -cap, Math.min(cap, dy * 1000 / Math.max(16, profile.motion().velocitySampleMs())));
    dragX = x;
    dragY = y;
    return true;
  }

  boolean release(int button) {
    if (!dragging || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
    dragging = false;
    if (!moved && pressed != null) select(pressed);
    else if (!moved && state.expanded) close();
    else if (moved && !profile.inputDefaults().reduceMotion()) {
      camera().velocityX = velocityX;
      camera().velocityY = velocityY;
    }
    pressed = null;
    persist();
    return true;
  }

  boolean scroll(double x, double y, double delta) {
    if (state == null || !viewport.contains(x, y)) return false;
    if (groupMenuOpen && x < Math.min(viewport.width(), 156)) {
      int groups = (int) nodes.stream().map(n -> n.group).distinct().count();
      int visible = Math.max(1, viewport.height() / 22);
      groupMenuOffset = Math.max(0, Math.min(Math.max(0, groups - visible),
          groupMenuOffset - (int) Math.signum(delta)));
      return true;
    }
    if (inspectorTransition.visible(state.expanded) && inspector.contains(x, y)) {
      return true; // Pages consume wheel input; it must not move the canvas behind them.
    }
    suppressHover();
    if (scrollZoom != net.minecraft.client.gui.screens.Screen.hasControlDown()) zoom(Math.pow(1.12, Math.max(-100, Math.min(100, delta))), x - viewport.x(), y - viewport.y());
    else camera().pan(0, delta * 28);
    persist();
    return true;
  }

  boolean key(int key, int scan, int mods) {
    if (searchFocused()) {
      if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
        searchResults().stream().findFirst().ifPresent(this::openSearchResult);
        return true;
      }
      if (key == GLFW.GLFW_KEY_ESCAPE) {
        blurSearch();
        searchOpen = false;
        return true;
      }
      return search.keyPressed(key, scan, mods);
    }
    if (state == null) return false;
    if (key == GLFW.GLFW_KEY_SLASH || key == GLFW.GLFW_KEY_F && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
      searchOpen = true; search.setFocused(true); groupMenuOpen = filterMenuOpen = false;
      return true;
    }
    if (key == GLFW.GLFW_KEY_G) {
      groupMenuOpen = !groupMenuOpen; groupMenuOffset = 0; filterMenuOpen = false; return true;
    }
    if (key == GLFW.GLFW_KEY_F) {
      filterMenuOpen = !filterMenuOpen; groupMenuOpen = false; return true;
    }
    if (key == GLFW.GLFW_KEY_LEFT && (mods & GLFW.GLFW_MOD_ALT) != 0) { back(); return true; }
    switch (key) {
      case GLFW.GLFW_KEY_ESCAPE:
        if (settingsOpen) { settingsOpen = false; return true; }
        if (groupMenuOpen || filterMenuOpen || searchOpen) {
          groupMenuOpen = filterMenuOpen = searchOpen = false; blurSearch(); return true;
        }
        if (state.expanded) {
          close();
          return true;
        }
        return false;
      case GLFW.GLFW_KEY_W:
        if (!profile.inputDefaults().wasdNavigation()) return false;
        if (state.expanded) return spatial(0, -1, true);
        camera().pan(0, 30);
        return true;
      case GLFW.GLFW_KEY_S:
        if (!profile.inputDefaults().wasdNavigation()) return false;
        if (state.expanded) return spatial(0, 1, true);
        camera().pan(0, -30);
        return true;
      case GLFW.GLFW_KEY_A:
        if (!profile.inputDefaults().wasdNavigation()) return false;
        if (state.expanded) return spatial(-1, 0, true);
        camera().pan(30, 0);
        return true;
      case GLFW.GLFW_KEY_D:
        if (!profile.inputDefaults().wasdNavigation()) return false;
        if (state.expanded) return spatial(1, 0, true);
        camera().pan(-30, 0);
        return true;
      case GLFW.GLFW_KEY_LEFT:
        return spatial(-1, 0);
      case GLFW.GLFW_KEY_RIGHT:
        return spatial(1, 0);
      case GLFW.GLFW_KEY_UP:
        return spatial(0, -1);
      case GLFW.GLFW_KEY_DOWN:
        return spatial(0, 1);
      case GLFW.GLFW_KEY_ENTER:
      case GLFW.GLFW_KEY_KP_ENTER:
        nodes.stream()
            .filter(n -> n.progress.bindingId().equals(keyboardBinding))
            .findFirst()
            .ifPresent(this::select);
        return !keyboardBinding.isEmpty();
      case GLFW.GLFW_KEY_HOME:
        close();
        overview();
        return true;
      case GLFW.GLFW_KEY_EQUAL:
      case GLFW.GLFW_KEY_KP_ADD:
        zoom(1.12, viewport.width() / 2., viewport.height() / 2.);
        return true;
      case GLFW.GLFW_KEY_MINUS:
      case GLFW.GLFW_KEY_KP_SUBTRACT:
        zoom(1 / 1.12, viewport.width() / 2., viewport.height() / 2.);
        return true;
      default:
        return false;
    }
  }

  private boolean spatial(int dx, int dy) {
    return spatial(dx, dy, false);
  }

  private boolean spatial(int dx, int dy, boolean selectInGroup) {
    if (nodes.isEmpty()) return false;
    Node origin =
        nodes.stream()
            .filter(
                n ->
                    n.progress
                        .bindingId()
                        .equals(keyboardBinding.isEmpty() ? state.selection : keyboardBinding))
            .findFirst()
            .orElse(nodes.get(0));
    Node next =
        nodes.stream()
            .filter(n -> !selectInGroup || n.group.equals(origin.group))
            .filter(n -> (n.x - origin.x) * dx + (n.y - origin.y) * dy > 0)
            .min(
                Comparator.comparingDouble(
                    n -> {
                      double along = (n.x - origin.x) * dx + (n.y - origin.y) * dy;
                      double across = (n.x - origin.x) * dy - (n.y - origin.y) * dx;
                      return along + Math.abs(across) * 2;
                    }))
            .orElse(origin);
    keyboardBinding = next.progress.bindingId();
    if (selectInGroup) {
      if (next != origin) select(next);
      return true;
    }
    HudRect box = nodeBox(next);
    int usable = state.expanded ? inspector.x() : viewport.right();
    double shiftX =
        box.x() < viewport.x()
            ? viewport.x() - box.x() + 8
            : box.right() > usable ? usable - box.right() - 8 : 0;
    double shiftY =
        box.y() < viewport.y()
            ? viewport.y() - box.y() + 8
            : box.bottom() > viewport.bottom() ? viewport.bottom() - box.bottom() - 8 : 0;
    camera().pan(shiftX, shiftY);
    return true;
  }

  AtlasInspectionSnapshot inspection() {
    if (state == null) return AtlasInspectionSnapshot.empty();
    var c = camera();
    return new AtlasInspectionSnapshot(
        absX,
        absY,
        screen.getUiScale(),
        viewport,
        state.expanded ? inspector : null,
        state.category,
        state.selection,
        variant,
        state.query,
        c.x,
        c.y,
        c.zoom,
        c.destinationZoom(),
        c.isAnimating(),
        inspectorTransition.alpha(),
        hoverGuard.blocked(),
        !pendingTooltip.isEmpty(),
        nodes.stream()
            .map(
                n ->
                    new AtlasInspectionSnapshot.Node(
                        n.progress.bindingId(), n.entry.getEntryId().toString(), nodeBox(n),
                        n.entry.getPresentation().obtainable(org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE::hasFlag, n.progress.discovered()),
                        n.progress.discovered(),
                        org.arcadia.arc_quest.quest.logic.profile.collection.CollectionUnreadState.isUnread(
                            n.progress, org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE.getCollectionRecord(n.entry.getEntryId()))))
            .toList(),
        List.copyOf(categoryBadges),
        hits.stream().map(h -> new AtlasInspectionSnapshot.Control(h.label, h.box)).toList(),
        state.expanded ? inspectorHeight : 0, state.expanded ? detailViewportHeight : 0,
        state.expanded ? detailTab : "", state.expanded ? detailPage : 0,
        state.expanded ? detailPageCount : 0, state.expanded ? detailVisibleText : List.of(),
        renderedTitleHint, renderedTitleHintBounds, List.copyOf(detailElements));
  }

  boolean character(char c, int mods) {
    return searchFocused() && search.charTyped(c, mods);
  }
}
