package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.quest.collection.AtlasBrowseStore;
import org.arcadia.arc_quest.client.quest.collection.AtlasCamera;
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
  private final Deque<String> history = new ArrayDeque<>();
  private final Deque<BrowseSnapshot> sourceHistory = new ArrayDeque<>();

  private record Destination(String quest, String phase, String binding, String variant) {}

  private record BrowseSnapshot(
      Destination destination,
      String stateKey,
      String category,
      String query,
      double x,
      double y,
      double zoom,
      double detailScroll) {}

  private BrowseSnapshot pendingBrowseRestore;
  private ItemStack pendingTooltip = ItemStack.EMPTY;
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
  private boolean dragging, moved, scrollZoom = true, favoritesOnly, groupMenuOpen, filterMenuOpen, showNames;
  private int categoryStart, recordFilter, groupMenuOffset;
  private double minimumZoom, maximumZoom;
  private HudRect searchBounds = new HudRect(0, 0, 0, 0);
  private Node pressed;
  private long lastFrame = System.nanoTime();
  private String pendingBinding = "", pendingVariant = "";
  private Set<ResourceLocation> favorites = Set.of();

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

  private void button(
      GuiGraphics g, int x, int y, int w, String text, boolean active, Runnable action) {
    button(g, x, y, w, text, active, true, ButtonStyle.ACTION, action);
  }

  private enum ButtonStyle { ACTION, CATEGORY, MINI, ICON, QUALITY, MENU }

  private void button(GuiGraphics g, int x, int y, int w, String text, boolean active,
      boolean enabled, ButtonStyle style, Runnable action) {
    int h = style == ButtonStyle.MINI ? 14 : style == ButtonStyle.QUALITY ? 15 : 18;
    HudRect box = new HudRect(x, y, Math.max(12, w), h);
    boolean hovered = enabled && box.contains(mouseX, mouseY);
    if (hovered) screen.requestPointerCursor();
    int previousAlpha = alpha;
    if (!enabled) alpha = alpha * 3 / 10;
    boolean gold = style == ButtonStyle.ICON && text.equals("★");
    int accent = gold ? 0xD3AE63 : theme;
    if (style == ButtonStyle.QUALITY) {
      if (active || hovered) g.fill(x, y, box.right(), box.bottom(), AtlasV6Draw.tint(theme, (int) (alpha * (active ? .17 : .08))));
      AtlasV6Draw.frame(g, new HudRect(x, box.bottom() - 1, box.width(), 1),
          active ? theme : 0xA0B2BC, active ? alpha : alpha / 4);
    } else {
      float fill = style == ButtonStyle.CATEGORY ? .012f : style == ButtonStyle.MENU ? .08f : .015f;
      g.fill(x, y, box.right(), box.bottom(), AtlasV6Draw.tint(active || hovered ? accent : 0xFFFFFF,
          (int) (alpha * (active ? .10 : hovered ? .07 : fill))));
      AtlasV6Draw.frame(g, box, active || hovered ? accent : 0xA0B2BC,
          (int) (alpha * (active ? .55 : hovered ? .50 : .22)));
      if (active && style == ButtonStyle.CATEGORY)
        g.fill(x, box.bottom() - 1, box.right(), box.bottom(), color(theme));
    }
    float scale = style == ButtonStyle.MINI ? .72f : style == ButtonStyle.QUALITY ? .72f : .82f;
    String clipped = AtlasV6Draw.ellipsis(screen.getFont(), text, (int) Math.ceil((w - 8) / scale));
    int textX = x + Math.max(3, (w - (int) (screen.getFont().width(clipped) * scale)) / 2);
    int textY = y + (h - (int) (screen.getFont().lineHeight * scale)) / 2;
    if (text.equals("★") || text.equals("☆")) {
      AtlasV6Draw.star(g, x + (w - 9) / 2, y + 4, 9, text.equals("★") ? 0xFFE2A0 : 0xA0B2BC, alpha);
      if (text.equals("☆")) AtlasV6Draw.star(g, x + (w - 5) / 2, y + 6, 5, 0x0B131B, alpha);
    } else if (style == ButtonStyle.CATEGORY && text.contains("  ")) {
      String[] parts = text.split("  ", 2);
      int countWidth = (int) Math.ceil(screen.getFont().width(parts[1]) * .65);
      parts[0] = AtlasV6Draw.ellipsis(screen.getFont(), parts[0], (int) (Math.max(8, w - countWidth - 17) / .82));
      int nameWidth = (int) Math.ceil(screen.getFont().width(parts[0]) * .82);
      int startX = x + Math.max(4, (w - nameWidth - countWidth - 7) / 2);
      small(g, parts[0], startX, textY, .82f, active ? theme : 0xD1DAE0);
      small(g, parts[1], startX + nameWidth + 7, textY + 1, .65f, 0xA0B2BC);
    } else small(g, clipped, style == ButtonStyle.MENU ? x + 5 : textX,
        textY, scale, active ? accent : 0xD1DAE0);
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
      groupMenuOpen = filterMenuOpen = false;
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
    pendingTooltip = ItemStack.EMPTY;
    camera().focusMultiplier(profile.focus().zoomMultiplier());
    long now = System.nanoTime();
    double frameSeconds = (now - lastFrame) / 1e9;
    camera().advance(frameSeconds);
    lastFrame = now;
    // V6: a single category strip, then a separate compact search toolbar.
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
          (int) Math.ceil(screen.getFont().width(name) * .82 + screen.getFont().width(count + "/" + total) * .65) + 21);
      if (cx + w > width - 16) break;
      button(g, cx, stripY, w, text, id.equals(state.category), true, ButtonStyle.CATEGORY, () -> {
        close(); state.category = id; groupMenuOpen = filterMenuOpen = false; camera().cancelMotion();
      });
      cx += w + 4;
      lastCategory = i;
    }
    button(g, width - 12, stripY, 12, "›", false, lastCategory < ids.size() - 1, ButtonStyle.ICON,
        () -> categoryStart = Math.min(ids.size() - 1, categoryStart + 1));
    y += 24;
    int searchX = 48, filterWidth = 32;
    searchBounds = new HudRect(searchX, y, Math.max(40, width - searchX - filterWidth - 4), 18);
    if (search == null) {
      search = new EditBox(screen.getFont(), searchBounds.x() + 12, y + 5,
          Math.max(16, searchBounds.width() - 15), 12, Component.literal(tr("search")));
      search.setMaxLength(160);
      search.setBordered(false);
      search.setTextColor(0xD1DAE0);
      search.setTextColorUneditable(0x71868F);
      search.setValue(state.query);
      search.setResponder(value -> { state.query = value; camera().cancelMotion(); });
    }
    search.setX(searchBounds.x() + 12);
    search.setY(y + 5);
    search.setWidth(Math.max(16, searchBounds.width() - 15));
    g.fill(searchBounds.x(), y, searchBounds.right(), y + 18, AtlasV6Draw.tint(0x090D14, alpha * 2 / 5));
    AtlasV6Draw.frame(g, searchBounds, search.isFocused() ? theme : 0x667782, alpha * 2 / 5);
    small(g, "⌕", searchBounds.x() + 3, y + 4, .9f, 0xA0B2BC);
    if (state.query.isBlank() && !search.isFocused())
      small(g, AtlasV6Draw.ellipsis(screen.getFont(), tr("search"), (int) ((searchBounds.width() - 15) / .82)),
          search.getX(), y + 5, .82f, 0x71868F);
    search.render(g, (int) mx, (int) my, 0);
    hits.add(new Hit(searchBounds, () -> { search.setFocused(true); groupMenuOpen = filterMenuOpen = false; }, tr("search")));
    button(g, 0, y, 44, tr("group_menu"), groupMenuOpen,
        () -> { groupMenuOpen = !groupMenuOpen; groupMenuOffset = 0; filterMenuOpen = false; blurSearch(); });
    button(g, width - filterWidth, y, filterWidth, tr("filter"), favoritesOnly || recordFilter != 0 || filterMenuOpen,
        () -> { filterMenuOpen = !filterMenuOpen; groupMenuOpen = false; blurSearch(); });
    y += 24;
    viewport = new HudRect(0, y, width, Math.max(24, height - y - 34));
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
        && !AtlasBrowseStore.has(state.atlasStorageKey + "/" + state.category)) overview();
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
        camera().x = snapshot.x;
        camera().y = snapshot.y;
        camera().zoom = snapshot.zoom;
        detailScroll = snapshot.detailScroll;
        buildNodes();
      }
      pendingBrowseRestore = null;
    }
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
      int gx = sx(minX), gy = sy(minY), gr = sx(maxX);
      boolean dim = (state.expanded && focusedNode != null && !focusedNode.group.equals(group.getKey()))
          || members.stream().noneMatch(this::matches);
      int a = alpha;
      if (dim) alpha = (int) (a * profile.focus().otherGroupAlpha());
      small(g, members.get(0).groupTitle, gx, gy, .82f, theme);
      int titleWidth = (int) (screen.getFont().width(members.get(0).groupTitle) * .82);
      small(g, "" + members.size(), gx + titleWidth + 6, gy + 1, .7f, 0xA0B2BC);
      g.fill(gx, gy + 11, Math.max(gx + titleWidth + 15, gr), gy + 12, AtlasV6Draw.tint(theme, alpha / 5));
      alpha = a;
    }
    ItemStack tooltip = ItemStack.EMPTY;
    for (Node n : nodes) {
      int originalAlpha = alpha;
      boolean favorite = favorites.contains(n.entry.getEntryId());
      if (state.expanded && focusedNode != null && !focusedNode.group.equals(n.group))
        alpha = (int) (alpha * (favorite ? profile.focus().otherGroupFavoriteAlpha() : profile.focus().otherGroupAlpha()));
      boolean matches = matches(n);
      if (!matches) alpha = alpha * 15 / 100;
      HudRect box = nodeBox(n);
      if (box.right() < 0 || box.x() > width || box.bottom() < y || box.y() > viewport.bottom()) {
        alpha = originalAlpha; continue;
      }
      int size = box.width();
      boolean selected = n.progress.bindingId().equals(state.selection) && state.expanded;
      boolean hovered = box.contains(mx, my) && (!state.expanded || mx < inspector.x());
      AtlasV6Draw.plate(g, box.x() + 1, box.y() + 1, Math.max(4, size - 2), favorite ? 0xD3AE63 : theme,
          (int) (alpha * (selected || hovered ? .24 : favorite ? .19 : .075)));
      if (favorite) AtlasV6Draw.frame(g, new HudRect(box.x() - 1, box.y() - 1, size + 2, size + 2), 0xD3AE63, alpha * 4 / 5);
      if (selected || n.progress.bindingId().equals(keyboardBinding)) {
        AtlasV6Draw.frame(g, new HudRect(box.x() - 2, box.y() - 2, size + 4, size + 4), theme, alpha);
        AtlasV6Draw.frame(g, new HudRect(box.x() + 1, box.y() + 1, size - 2, size - 2), 0xDCE2E5, alpha * 3 / 5);
      }
      ItemStack stack = stack(n);
      int iconSize = Math.max(10, (int) Math.round(size * .87));
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
        float captionScale = .70f;
        int textWidth = (int) ((size + 18) / captionScale);
        var captionLines = screen.getFont().split(Component.literal(title), Math.max(8, textWidth));
        int count = Math.min(profile.node().labelMaxLines(), captionLines.size());
        if (profile.node().labelMaxLines() == 1) {
          String caption = AtlasV6Draw.ellipsis(screen.getFont(), title, textWidth);
          int tw = (int) (screen.getFont().width(caption) * captionScale);
          small(g, caption, box.x() + (size - tw) / 2, box.y() + size + 3, captionScale, favorite ? 0xEED6A2 : 0xD1DAE0);
        } else for (int line = 0; line < count; line++) {
          var caption = captionLines.get(line);
          int tw = (int) (screen.getFont().width(caption) * captionScale);
          g.pose().pushPose();
          g.pose().translate(box.x() + (size - tw) / 2, box.y() + size + 3 + line * 8, 0);
          g.pose().scale(captionScale, captionScale, 1);
          g.drawString(screen.getFont(), caption, 0, 0, color(favorite ? 0xEED6A2 : 0xD1DAE0), false);
          g.pose().popPose();
        }
      }
      if (favorite) {
        HudRect badge = new HudRect(box.x() + size - 6, box.y() - 4, 10, 10);
        g.fill(badge.x(), badge.y(), badge.right(), badge.bottom(), color(0x48351B));
        AtlasV6Draw.frame(g, badge, 0xD3AE63, alpha);
        AtlasV6Draw.star(g, badge.x() + 1, badge.y() + 1, 8, 0xFFE2A0, alpha);
      }
      if (n.progress.complete()) small(g, "✓", box.x() + size - 4, box.y() + size - 4, .85f, 0xA4D3B2);
      alpha = originalAlpha;
      if (matches && hovered) tooltip = stack;
    }
    if (nodes.stream().noneMatch(this::matches)) {
      String empty = tr("no_matches");
      small(g, empty, Math.max(4, (viewport.width() - (int) (screen.getFont().width(empty) * .82)) / 2),
          viewport.y() + viewport.height() / 2 - 14, .82f, 0xA0B2BC);
      int clearWidth = (int) (screen.getFont().width(tr("clear_filter")) * .82) + 12;
      button(g, (viewport.width() - clearWidth) / 2, viewport.y() + viewport.height() / 2,
          clearWidth, tr("clear_filter"), false, this::clearFilters);
    }
    screen.disableScissor(g);
    renderBottom(g);
    if (state.expanded) renderInspector(g);
    renderPopovers(g, groups);
    renderSearchResults(g);
    if (dragging || searchFocused()) JeiScreenIngredients.suppress(screen);
    if (!tooltip.isEmpty() && !groupMenuOpen && !filterMenuOpen && (!state.expanded || !inspector.contains(mx, my))) pendingTooltip = tooltip.copy();
    return viewport.bottom() + 34;
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
    int y = viewport.bottom() + 5, x = 0;
    // The journal's ornamental close control overlaps the lower-right corner of this workspace.
    int textRight = Math.max(0, viewport.right() - 28);
    button(g, x, y, 16, "−", false, camera().zoom > minimumZoom + .001, ButtonStyle.MINI,
        () -> camera().zoomAt(1 / 1.12, viewport.width() / 2., viewport.height() / 2.));
    small(g, Math.round(camera().zoom * 100) + "%", x + 20, y + 6, .72f, 0xA0B2BC);
    x += 49;
    button(g, x, y, 16, "+", false, camera().zoom < maximumZoom - .001, ButtonStyle.MINI,
        () -> camera().zoomAt(1.12, viewport.width() / 2., viewport.height() / 2.));
    x += 20;
    int overviewWidth = (int) (screen.getFont().width(tr("overview")) * .82) + 8;
    button(g, x, y, overviewWidth, tr("overview"), false, true, ButtonStyle.MINI, () -> { close(); overview(); });
    x += overviewWidth + 4;
    String wheel = scrollZoom ? tr("zoom") : tr("scroll");
    int wheelWidth = (int) (screen.getFont().width(tr("wheel") + wheel) * .82) + 8;
    button(g, x, y, wheelWidth, tr("wheel") + wheel, !scrollZoom, true, ButtonStyle.MINI, () -> {
      scrollZoom = !scrollZoom; state.atlasScrollZoom = scrollZoom; camera().cancelMotion();
    });
    x += wheelWidth + 4;
    String expand = screen.isCompactAtlas() ? tr("task_list") : screen.isAtlasCanvasExpanded() ? tr("collapse") : tr("expand");
    int expandWidth = (int) Math.ceil(screen.getFont().width(expand) * .82) + 10;
    if (x + expandWidth <= viewport.right())
      button(g, x, y, expandWidth, expand, screen.isAtlasCanvasExpanded(), true, ButtonStyle.MINI, screen::toggleAtlasCanvas);
    x += expandWidth + 8;
    var hovered = nodes.stream().filter(this::matches).filter(n -> nodeBox(n).contains(mouseX, mouseY))
        .filter(n -> !state.expanded || mouseX < inspector.x()).findFirst()
        .orElseGet(() -> nodes.stream().filter(n -> n.progress.bindingId().equals(state.selection)).findFirst().orElse(null));
    if (hovered != null && !screen.isCompactAtlas() && textRight - x > 36) {
      String name = AtlasV6Draw.ellipsis(screen.getFont(), hovered.entry.getDisplayName().getString(),
          (int) ((textRight - x) / .70));
      small(g, name, textRight - (int) (screen.getFont().width(name) * .70), y + 4, .70f, 0xA0B2BC);
    }
    int hintY = y + 23;
    String categoryName = definition.getCollectionConfig().getCategories().stream()
        .filter(c -> c.getCategoryId().equals(state.category)).findFirst()
        .map(c -> c.getDisplayNameText().resolve(null, QuestTextContext.empty()).getString()).orElse("");
    String counts = AtlasV6Draw.ellipsis(screen.getFont(),
        tr("matching", categoryName, nodes.stream().filter(this::matches).count(), nodes.size()), (int) (textRight / .65));
    int countsWidth = (int) (screen.getFont().width(counts) * .65);
    small(g, counts, textRight - countsWidth, hintY, .65f, 0x71868F);
    if (!screen.isCompactAtlas() && textRight - countsWidth > 80)
      small(g, AtlasV6Draw.ellipsis(screen.getFont(), tr("controls"), (int) ((textRight - countsWidth - 12) / .65)),
          0, hintY, .65f, 0x71868F);
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
        double groupGapX = parameter(category.layoutParameters(), "gapX", 34);
        double groupGapY = parameter(category.layoutParameters(), "gapY", 34);
        double groupWidth = 64, groupHeight = 64;
        for (var group : category.groups()) {
          int cols = columns(group.layoutParameters(), group.columns());
          int count =
              (int)
                  group.nodes().stream()
                      .filter(n -> visible.stream().anyMatch(v -> v.entryId().equals(n.entryId())))
                      .count();
          double gapX = parameter(group.layoutParameters(), "gapX", 14);
          double gapY = parameter(group.layoutParameters(), "gapY", 24);
          groupWidth =
              Math.max(groupWidth, Math.min(cols, Math.max(1, count)) * (nodeWidth() + gapX));
          groupHeight =
              Math.max(groupHeight, Math.ceil(count / (double) cols) * (nodeHeight() + gapY) + 34);
        }
        var groupPositions =
            org.arcadia.arc_quest.client.quest.collection.AtlasLayoutStrategies.layout(
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
                      nodeWidth() + gapX,
                      nodeHeight() + gapY,
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
                    x + 20,
                    y + 34));
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
    close();
    pendingBinding = result.bindingId();
    pendingVariant = "";
    camera().cancelMotion();
  }

  /** Search the whole authorized sheet, retaining each category's authored layout. */
  private void renderSearchResults(GuiGraphics graphics) {
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
    return profile.node().size() + profile.node().labelMaxLines() * 8 + 5;
  }

  private int sx(double x) {
    return (int) Math.round(viewport.x() + camera().x + x * camera().zoom);
  }

  private int sy(double y) {
    return (int) Math.round(viewport.y() + camera().y + y * camera().zoom);
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
    camera().cancelMotion();
    keyboardBinding = n.progress.bindingId();
    if (state.expanded && !state.selection.equals(n.progress.bindingId()))
      history.push(state.selection);
    state.select(n.progress.bindingId());
    variant = n.entry.getPresentation().defaultVariantId();
    detailScroll = 0;
    var groupNodes = nodes.stream().filter(it -> it.group.equals(n.group)).toList();
    double centerX = (groupNodes.stream().mapToDouble(it -> it.x).min().orElse(n.x)
        + groupNodes.stream().mapToDouble(it -> it.x + nodeWidth()).max().orElse(n.x)) / 2;
    double centerY = (groupNodes.stream().mapToDouble(it -> it.y).min().orElse(n.y)
        + groupNodes.stream().mapToDouble(it -> it.y + nodeWidth()).max().orElse(n.y)) / 2;
    camera().focusRegion(
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
      if (profile.focus().restoreBrowseSnapshotOnClose()) camera().restore();
      else camera().discardFocus();
    }
    detailScroll = 0;
  }

  private void overview() {
    configureCamera();
    camera()
        .overview(
            viewport.width(),
            viewport.height(),
            nodes.stream().mapToDouble(n -> n.x + nodeWidth() + 24).max().orElse(200),
            nodes.stream().mapToDouble(n -> n.y + nodeHeight() + 24).max().orElse(150),
            1,
            Math.min(profile.zoom().hardMax(), 1.35));
  }

  private void renderInspector(GuiGraphics graphics) {
    Node selected =
        nodes.stream()
            .filter(n -> n.progress.bindingId().equals(state.selection))
            .findFirst()
            .orElse(null);
    if (selected == null) {
      close();
      return;
    }
    int width = inspectorWidth();
    inspector = new HudRect(viewport.right() - width, viewport.y(), width, viewport.height());
    JeiScreenIngredients.occlude(
        screen,
        absX + inspector.x(),
        absY + inspector.y(),
        absX + inspector.right(),
        absY + inspector.bottom());
    graphics.pose().pushPose();
    graphics.pose().translate(0, 0, 350);
    graphics.fill(inspector.x(), inspector.y(), inspector.right(), inspector.bottom(), AtlasV6Draw.tint(0x0B131B, alpha * 96 / 100));
    AtlasV6Draw.frame(graphics, inspector, theme, alpha / 3);
    int x = inspector.x() + 8, y = inspector.y() + 7;
    button(graphics, inspector.right() - 23, y, 16, "×", false, true, ButtonStyle.ICON, this::close);
    button(graphics, inspector.right() - 43, y, 16,
        favorites.contains(selected.entry.getEntryId()) ? "★" : "☆",
        favorites.contains(selected.entry.getEntryId()), true, ButtonStyle.ICON, () -> {
          CollectionFavoritesStore.INSTANCE.toggle(selected.entry.getEntryId());
          favorites = CollectionFavoritesStore.INSTANCE.snapshot();
        });
    ItemStack subject = stack(selected);
    int textX = x;
    if (!subject.isEmpty()) {
      renderItem(graphics, subject, x, y + 1, 14);
      JeiScreenIngredients.collectionSubject(screen, graphics, quest, phase, selected.progress.bindingId(),
          selected.entry.getEntryId().toString(), variantFor(selected), subject, x, y + 1, 14, 14);
      if (new HudRect(x, y, 16, 16).contains(mouseX, mouseY)) pendingTooltip = subject.copy();
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
    var titleLines = screen.getFont().split(title, (int) (Math.max(18, inspector.right() - 47 - textX) / .85));
    int titleY = y + 4;
    for (var line : titleLines) {
      graphics.pose().pushPose();
      graphics.pose().translate(textX, titleY, 0); graphics.pose().scale(.85f, .85f, 1);
      graphics.drawString(screen.getFont(), line, 0, 0, color(0xE3EBEF), false);
      graphics.pose().popPose();
      titleY += 10;
    }
    y += Math.max(18, titleLines.size() * 10 + 4) + 6;
    graphics.fill(x, y, inspector.right() - 8, y + 1, AtlasV6Draw.tint(theme, alpha / 3));
    y += 6;
    HudRect body = new HudRect(x, y, width - 16, Math.max(1, inspector.bottom() - y - 18));
    screen.enableScissor(
        graphics, absX + body.x(), absY + body.y(), absX + body.right(), absY + body.bottom());
    int contentY = y - (int) detailScroll;
    if (!sourceHistory.isEmpty()) {
      clippedButton(graphics, body, x, contentY, Math.min(body.width(),
          (int) (screen.getFont().width("‹ " + tr("back")) * .72) + 10),
          "‹ " + tr("back"), false, ButtonStyle.MINI, this::back);
      contentY += 19;
    }
    var group = nodes.stream().filter(n -> n.group.equals(selected.group)).toList();
    String status = selected.groupTitle + " · " + (group.indexOf(selected) + 1) + "/" + group.size()
        + " · " + (selectedRecorded(selected) ? "✓ " + tr(selected.entry.getPresentation().variants().isEmpty()
            ? "recorded" : "variant_recorded") : tr(selected.entry.getPresentation().variants().isEmpty()
            ? "unrecorded" : "variant_unrecorded"));
    contentY = wrappedSmall(graphics, Component.literal(status), body, contentY, .70f, 0xA0B2BC) + 3;
    Set<String> rendered = new HashSet<>();
    // Variants remain reachable even when an author omits the optional section name.
    List<String> sections = new ArrayList<>(profile.inspector().sections());
    if (!sections.contains("VARIANTS")) sections.add(0, "VARIANTS");
    if (!sections.contains("SUBJECT_TOOLTIP") && !sections.contains("TOOLTIP"))
      sections.add(Math.min(1, sections.size()), "SUBJECT_TOOLTIP");
    for (String section : sections) {
      if (!rendered.add(section)) continue;
      contentY =
          switch (section) {
            case "VARIANTS" -> renderVariants(graphics, selected, body, contentY);
            case "SUBJECT_TOOLTIP", "TOOLTIP" -> renderSubjectTooltip(graphics, selected, body, contentY);
            case "CONTENT" -> renderContent(graphics, selected, body, contentY);
            case "CONTENT_FALLBACK" -> hasDisclosedSource(selected) ? contentY
                : renderContent(graphics, selected, body, contentY);
            case "SOURCES" -> renderSources(graphics, selected, body, contentY);
            case "ACTIONS" -> renderTrackingActions(graphics, selected, body, contentY);
            default -> contentY;
          };
    }
    inspectorHeight = contentY + (int) detailScroll - y;
    detailViewportHeight = body.height();
    detailScroll = Math.max(0, Math.min(detailScroll, inspectorHeight - detailViewportHeight));
    screen.disableScissor(graphics);
    if (inspectorHeight > body.height()) {
      int trackHeight = body.height();
      int thumbHeight = Math.max(8, trackHeight * trackHeight / inspectorHeight);
      int thumbY = body.y() + (int) ((trackHeight - thumbHeight) * detailScroll / Math.max(1, inspectorHeight - trackHeight));
      graphics.fill(inspector.right() - 3, thumbY, inspector.right() - 2, thumbY + thumbHeight, AtlasV6Draw.tint(0xA0B2BC, alpha / 2));
    }
    graphics.fill(x, inspector.bottom() - 17, inspector.right() - 8, inspector.bottom() - 16, AtlasV6Draw.tint(0xA0B2BC, alpha / 7));
    small(graphics, "W A S D · " + tr("same_group"), x, inspector.bottom() - 11, .63f, 0x71868F);
    String escape = "Esc " + tr("return");
    small(graphics, escape, inspector.right() - 8 - (int) (screen.getFont().width(escape) * .63),
        inspector.bottom() - 11, .63f, 0x71868F);
    graphics.pose().popPose();
  }

  private boolean selectedRecorded(Node node) {
    var choice = node.entry.getPresentation().variants().stream()
        .filter(v -> v.variantId().equals(variantFor(node))).findFirst().orElse(null);
    if (choice == null || choice.outcomeId().isBlank()) return node.progress.discovered();
    var record = org.arcadia.arc_quest.quest.network.ClientQuestCache.INSTANCE.getCollectionRecord(node.entry.getEntryId());
    return record != null && record.hasOutcome(choice.outcomeId());
  }

  private int wrappedSmall(GuiGraphics graphics, Component text, HudRect clip, int y, float scale, int rgb) {
    for (var line : screen.getFont().split(text, Math.max(1, (int) (clip.width() / scale)))) {
      if (y + screen.getFont().lineHeight * scale > clip.y() && y < clip.bottom()) {
        graphics.pose().pushPose(); graphics.pose().translate(clip.x(), y, 0); graphics.pose().scale(scale, scale, 1);
        graphics.drawString(screen.getFont(), line, 0, 0, color(rgb), false); graphics.pose().popPose();
      }
      y += (int) Math.ceil(screen.getFont().lineHeight * scale * 1.4);
    }
    return y;
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

  private int renderVariants(GuiGraphics graphics, Node selected, HudRect body, int y) {
    int x = body.x();
    for (var option : selected.entry.getPresentation().variants()) {
      String label = option.label().resolve(null, QuestTextContext.empty()).getString();
      int width = Math.min(body.width(), (int) Math.ceil(screen.getFont().width(label) * .72) + 10);
      if (x > body.x() && x + width > body.right()) {
        x = body.x();
        y += 18;
      }
      String id = option.variantId();
      clippedButton(
          graphics,
          body,
          x,
          y,
          width,
          label,
          id.equals(variant),
          ButtonStyle.QUALITY,
          () -> {
            variant = id;
            state.atlasVariant = id;
            detailScroll = 0;
            camera().cancelMotion();
          });
      x += width + 3;
    }
    return selected.entry.getPresentation().variants().isEmpty() ? y : y + 18;
  }

  /** Render the real element's native tooltip inside the inspector, including mod components. */
  private int renderSubjectTooltip(GuiGraphics graphics, Node selected, HudRect body, int y) {
    ItemStack subject = stack(selected);
    if (subject.isEmpty()) return y;
    var font = net.minecraftforge.client.ForgeHooksClient.getTooltipFont(subject, screen.getFont());
    var lines = net.minecraft.client.gui.screens.Screen.getTooltipFromItem(screen.getMinecraft(), subject);
    // The real stack name is already in the inspector header; keep its actual native body.
    var bodyLines = lines.size() > 1 ? lines.subList(1, lines.size()).stream()
        .filter(line -> !line.getString().isBlank()).toList() : List.<Component>of();
    var components = net.minecraftforge.client.ForgeHooksClient.gatherTooltipComponents(
        subject, bodyLines, subject.getTooltipImage(), 0, (int) ((body.width() - 12) / .80) + 16, body.height(), font);
    if (components.isEmpty()) return y;
    int tooltipHeight = 6;
    for (var component : components) tooltipHeight += (int) Math.ceil(component.getHeight() * .80) + 1;
    graphics.fill(body.x(), y, body.right(), y + tooltipHeight, AtlasV6Draw.tint(0x060B12, alpha / 3));
    graphics.fill(body.x(), y, body.x() + 1, y + tooltipHeight, AtlasV6Draw.tint(theme, alpha / 3));
    y += 3;
    for (var component : components) {
      if (y + component.getHeight() >= body.y() && y < body.bottom()) {
        graphics.pose().pushPose();
        graphics.pose().translate(body.x() + 6, y, 0);
        graphics.pose().scale(.80f, .80f, 1);
        component.renderText(font, 0, 0, graphics.pose().last().pose(), graphics.bufferSource());
        component.renderImage(font, 0, 0, graphics);
        graphics.pose().popPose();
      }
      y += (int) Math.ceil(component.getHeight() * .80) + 1;
    }
    return y + 4;
  }

  private int renderContent(GuiGraphics graphics, Node selected, HudRect body, int y) {
    y =
        paragraph(
            graphics,
            selected.progress.revealed()
                ? selected.entry.getDescription()
                : selected.progress.publicClue(),
            body,
            y);
    for (var block : selected.progress.content()) {
      y = paragraph(graphics, block.text().resolve(null, QuestTextContext.empty()), body, y);
      var media = block.media();
      if (media == null
          || !org.arcadia.arc_quest.client.hud.guide.GuideImageRenderer.available(media)) continue;
      int height =
          Math.max(
              32, Math.min(112, body.width() * media.getHeight() / Math.max(1, media.getWidth())));
      if (y + height > body.y() && y < body.bottom()) {
        org.arcadia.arc_quest.client.hud.guide.GuideImageRenderer.draw(
            graphics,
            media,
            body.x(),
            y,
            body.width(),
            height,
            block.fit() == CollectionMediaFit.COVER
                ? org.arcadia.arc_quest.client.hud.guide.GuideImageLayout.Fit.COVER
                : org.arcadia.arc_quest.client.hud.guide.GuideImageLayout.Fit.CONTAIN,
            alpha / 255f);
        if (block.zoomable()) {
          int top = Math.max(y, body.y()), bottom = Math.min(y + height, body.bottom());
          hits.add(
              new Hit(
                  new HudRect(body.x(), top, body.width(), bottom - top),
                  () ->
                      imageViewer.open(
                          media, block.caption().resolve(null, QuestTextContext.empty())),
                  tr("view_image")));
        }
      }
      y += height + 6;
      y = paragraph(graphics, block.caption().resolve(null, QuestTextContext.empty()), body, y);
    }
    return y;
  }

  private int renderSources(GuiGraphics graphics, Node selected, HudRect body, int y) {
    Set<ResourceLocation> seen = new HashSet<>();
    boolean headingDrawn = false;
    String heading = tr(selected.entry.getSubjectKind() == CollectionSubjectKind.CUSTOM ? "possible_findings" : "sources");
    for (var sourceSet : definition.getCollectionConfig().getSourceSets()) {
      for (var relation :
          sourceSet.relations().stream()
              .sorted(Comparator.comparingInt(CollectionSourceSetDefinition.Relation::sortOrder))
              .toList()) {
        boolean incoming = relation.targetEntryId().equals(selected.entry.getEntryId());
        if (!seen.add(relation.relationId())
            || incoming && !relation.variantIds().isEmpty()
                && !relation.variantIds().contains(variantFor(selected))) continue;
        ResourceLocation endpoint =
            incoming
                ? relation.sourceEntryId()
                : relation.sourceEntryId().equals(selected.entry.getEntryId())
                    ? relation.targetEntryId()
                    : null;
        if (endpoint == null) continue;
        Destination resolved = findDestination(endpoint);
        if (resolved == null) continue;
        Destination destination = incoming || relation.variantIds().isEmpty() ? resolved
            : new Destination(resolved.quest, resolved.phase, resolved.binding, relation.variantIds().get(0));
        var target = org.arcadia.arc_quest.quest.registry.QuestRegistry.get(destination.quest);
        var entry =
            target == null || target.getCollectionConfig() == null
                ? null
                : target.getCollectionConfig().getEntry(endpoint);
        if (entry == null) continue;
        String label =
            (incoming ? tr("source_prefix") : tr("contents_prefix"))
                + entry.getDisplayName().getString();
        if (!headingDrawn) {
          if (y + 10 > body.y() && y < body.bottom()) small(graphics, heading, body.x(), y, .76f, theme);
          y += 11;
          headingDrawn = true;
        }
        int rowHeight = 29;
        if (y + rowHeight > body.y() && y < body.bottom()) {
          HudRect row = new HudRect(body.x(), y, body.width(), rowHeight);
          boolean hovered = row.contains(mouseX, mouseY);
          graphics.fill(row.x(), row.y(), row.right(), row.bottom(), AtlasV6Draw.tint(hovered ? theme : 0xFFFFFF, alpha / (hovered ? 12 : 65)));
          AtlasV6Draw.frame(graphics, row, hovered ? theme : 0xA0B2BC, alpha / (hovered ? 2 : 5));
          var context = CollectionEntryIcons.context(destination.quest, destination.phase, destination.binding, entry);
          var visual = screen.getObjectiveIcons().select(context, row.contains(mouseX, mouseY), false);
          if (visual.available()) visual.render(graphics, row.x() + 5, y + 7, 14, alpha / 255f);
          int textX = row.x() + 24;
          small(graphics, AtlasV6Draw.ellipsis(screen.getFont(), entry.getDisplayName().getString(),
              (int) (Math.max(8, body.width() - 43) / .80)), textX, y + 5, .80f, 0xE3EBEF);
          String method = relation.method();
          String methodKey = "arc_quest.hud.atlas.method_" + method.toLowerCase(Locale.ROOT);
          String methodText = net.minecraft.locale.Language.getInstance().has(methodKey)
              ? Component.translatable(methodKey).getString()
              : relation.description().resolve(null, QuestTextContext.empty()).getString();
          var qualityEntry = incoming ? selected.entry : entry;
          String shownVariant = incoming ? variantFor(selected) : destination.variant;
          String qualityLabel = qualityEntry.getPresentation().variants().stream()
              .filter(v -> v.variantId().equals(shownVariant))
              .map(v -> v.label().resolve(null, QuestTextContext.empty()).getString()).findFirst().orElse("");
          if (!qualityLabel.isBlank()) methodText += " · " + qualityLabel;
          small(graphics, AtlasV6Draw.ellipsis(screen.getFont(), methodText,
              (int) (Math.max(8, body.width() - 43) / .68)), textX, y + 18, .68f, 0xA0B2BC);
          int top = Math.max(y, body.y()), bottom = Math.min(y + rowHeight, body.bottom());
          hits.add(new Hit(new HudRect(body.x(), top, body.width(), bottom - top), () -> navigate(destination), label));
          small(graphics, "›", row.right() - 10, y + 11, .9f, 0xD1DAE0);
        }
        y += rowHeight + 3;
      }
    }
    if (!headingDrawn) {
      if (y + 10 > body.y() && y < body.bottom()) small(graphics, heading, body.x(), y, .76f, theme);
      y += 13;
      y = wrappedSmall(graphics, Component.literal(tr(selected.entry.getPresentation().variants().isEmpty()
          ? "no_sources" : "no_variant_sources")), body, y, .76f, 0xA0B2BC) + 6;
    }
    return y;
  }

  /** Authors can use cards in preference to duplicate acquisition prose, retaining a text fallback. */
  private boolean hasDisclosedSource(Node selected) {
    for (var set : definition.getCollectionConfig().getSourceSets())
      for (var relation : set.relations()) {
        boolean incoming = relation.targetEntryId().equals(selected.entry.getEntryId());
        if (incoming && !relation.variantIds().isEmpty()
            && !relation.variantIds().contains(variantFor(selected))) continue;
        var endpoint = incoming ? relation.sourceEntryId()
            : relation.sourceEntryId().equals(selected.entry.getEntryId()) ? relation.targetEntryId() : null;
        if (endpoint != null && findDestination(endpoint) != null) return true;
      }
    return false;
  }

  private int renderTrackingActions(GuiGraphics graphics, Node selected, HudRect body, int y) {
    if (selected.entry.getSubjectKind() != CollectionSubjectKind.CUSTOM) return y;
    var marker = org.arcadia.arc_quest.client.hud.questmarker.QuestMarkerManager.INSTANCE
        .get(org.arcadia.arc_quest.quest.logic.CollectionSourceTrackingService.MARKER_ID);
    boolean tracked = marker != null && marker.getLabel().equals(selected.entry.getDisplayName().getString());
    String text = tr(tracked ? "cancel_tracking" : "track_location");
    graphics.fill(body.x(), y + 2, body.right(), y + 3, AtlasV6Draw.tint(0xA0B2BC, alpha / 7));
    y += 9;
    clippedButton(graphics, body, body.x(), y, Math.min(body.width(),
        (int) Math.ceil(screen.getFont().width(text) * .82) + 12), text, tracked,
        () -> track(selected, "", tracked));
    return y + 23;
  }

  private int paragraph(GuiGraphics graphics, Component text, HudRect body, int y) {
    if (text.getString().isBlank()) return y;
    for (var line : screen.getFont().split(text, (int) (body.width() / .85))) {
      if (y + screen.getFont().lineHeight > body.y() && y < body.bottom())
        { graphics.pose().pushPose(); graphics.pose().translate(body.x(), y, 0); graphics.pose().scale(.85f, .85f, 1); graphics.drawString(screen.getFont(), line, 0, 0, color(0xAAB4BF), false); graphics.pose().popPose(); }
      y += (int) (screen.getFont().lineHeight * .85) + 2;
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
    if (!pendingTooltip.isEmpty() && !dragging && !searchFocused())
      graphics.renderTooltip(screen.getFont(), pendingTooltip, mouseX, mouseY);
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
            detailScroll));
    if (!screen.openAtlasDestination(
        destination.quest, destination.phase, destination.binding, destination.variant))
      sourceHistory.pop();
  }

  void loadFavorites() {
    favorites = CollectionFavoritesStore.INSTANCE.snapshot();
  }

  boolean click(double x, double y) {
    if (state == null) return false;
    camera().cancelMotion();
    for (int i = hits.size() - 1; i >= 0; i--)
      if (hits.get(i).box.contains(x, y)) {
        hits.get(i).action.run();
        persist();
        screen.playClick();
        return true;
      }
    if (groupMenuOpen || filterMenuOpen) { groupMenuOpen = filterMenuOpen = false; return true; }
    if (search != null) {
      search.setFocused(searchBounds.contains(x, y));
      if (search.isFocused()) return search.mouseClicked(x, y, 0);
    }
    if (state.expanded && inspector.contains(x, y)) return true;
    if (!viewport.contains(x, y)) return false;
    camera().cancelMotion();
    dragging = true;
    moved = false;
    dragX = x;
    dragY = y;
    pressed = nodes.stream().filter(this::matches).filter(n -> nodeBox(n).contains(x, y)).findFirst().orElse(null);
    return true;
  }

  boolean drag(double x, double y, int button) {
    if (!dragging || button != 0) return false;
    double dx = x - dragX, dy = y - dragY;
    if (Math.abs(dx) + Math.abs(dy) > profile.motion().dragThreshold()) moved = true;
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
    if (!dragging) return false;
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
    camera().cancelMotion();
    if (state.expanded && inspector.contains(x, y)) {
      detailScroll =
          Math.max(
              0,
              Math.min(
                  Math.max(0, inspectorHeight - detailViewportHeight), detailScroll - delta * 24));
      return true;
    }
    if (scrollZoom) camera().zoomAt(Math.pow(1.12, delta), x - viewport.x(), y - viewport.y());
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
        return true;
      }
      return search.keyPressed(key, scan, mods);
    }
    if (state == null) return false;
    camera().cancelMotion();
    switch (key) {
      case GLFW.GLFW_KEY_ESCAPE:
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
        camera().zoomAt(1.12, viewport.width() / 2., viewport.height() / 2.);
        return true;
      case GLFW.GLFW_KEY_MINUS:
      case GLFW.GLFW_KEY_KP_SUBTRACT:
        camera().zoomAt(1 / 1.12, viewport.width() / 2., viewport.height() / 2.);
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
        nodes.stream()
            .map(
                n ->
                    new AtlasInspectionSnapshot.Node(
                        n.progress.bindingId(), n.entry.getEntryId().toString(), nodeBox(n)))
            .toList(),
        hits.stream().map(h -> new AtlasInspectionSnapshot.Control(h.label, h.box)).toList(),
        state.expanded ? inspectorHeight : 0, state.expanded ? detailViewportHeight : 0);
  }

  boolean character(char c, int mods) {
    return searchFocused() && search.charTyped(c, mods);
  }
}
