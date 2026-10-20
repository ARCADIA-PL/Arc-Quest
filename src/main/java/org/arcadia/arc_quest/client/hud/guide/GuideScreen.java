// file_name: GuideScreen.java
package org.arcadia.arc_quest.client.hud.guide;

import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenSuspension;
import org.arcadia.arc_quest.client.compat.jei.screen.JeiQueryReturn;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.client.config.ArcQuestTextTarget;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.api.GuidePageDefinition;
import org.arcadia.arc_quest.guide.network.C2SUpdateGuideProgressPacket;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.NotNull;


public final class GuideScreen extends Screen implements JeiQueryReturn {

    /** Public lifecycle query for addons presenting effects while this guide is being read. */
    public ResourceLocation getGuideId() {
        return guideId;
    }
    private final JeiScreenSuspension jeiSuspension = new JeiScreenSuspension();
    private java.util.UUID presentationToken;
    private static final java.util.Map<java.util.UUID, java.lang.ref.WeakReference<GuideScreen>> PRESENTATIONS = new java.util.HashMap<>();
    private boolean presentationOpened;
    private boolean presentationClosed;
    private final ResourceLocation guideId;
    private final boolean markSeenOnClose;
    private final EmbeddedPonderScenePanel ponderPanel = new EmbeddedPonderScenePanel();

    private GuideDefinition guide;
    private int currentPage, themeColor;

    private float transitionAlpha = 0f;
    private boolean isClosing = false;
    private float dt = 0f;
    private long lastRenderTime = 0;

    private final GuideReaderPanel reader = new GuideReaderPanel();
    private boolean initialized;
    private boolean settingsOpen;

    public GuideScreen(GuideDefinition guide, int initialPage, boolean markSeenOnClose) {
        super(guide.getTitle());
        this.guideId = guide.getId();
        this.markSeenOnClose = markSeenOnClose;
        this.guide = guide;
        this.currentPage = clampPage(initialPage);
        this.themeColor = guide.getCategory().getThemeColor();
    }

    public static boolean tryOpen(ResourceLocation guideId, int initialPage, boolean markSeenOnClose) {
        GuideDefinition guide = GuideRegistry.get(guideId);
        Minecraft mc = Minecraft.getInstance();
        if (guide == null || mc == null) return false;
        return tryOpen(guideId, initialPage, markSeenOnClose, null);
    }

    public static boolean tryOpen(ResourceLocation guideId, int initialPage, boolean markSeenOnClose, java.util.UUID token) {
        GuideDefinition guide = GuideRegistry.get(guideId);
        Minecraft mc = Minecraft.getInstance();
        if (guide == null || mc == null) return false;
        GuideScreen screen = new GuideScreen(guide, initialPage, markSeenOnClose);
        screen.presentationToken = token;
        if (token != null) PRESENTATIONS.put(token, new java.lang.ref.WeakReference<>(screen));
        mc.setScreen(screen);
        return true;
    }

    @Override
    protected void init() {
        super.init();
        if (presentationToken != null && presentationClosed) {
            minecraft.execute(() -> { if (minecraft.screen == this) minecraft.setScreen(null); });
            return;
        }
        boolean resumed = jeiSuspension.resume() || settingsOpen;
        settingsOpen = false;
        GuideDefinition resolved = GuideRegistry.get(guideId);
        if (resolved == null) { minecraft.setScreen(null); return; }
        boolean replaced = resolved != guide;
        guide = resolved;
        currentPage = clampPage(currentPage);
        themeColor = guide.getCategory().getThemeColor();
        if (!presentationOpened) {
            presentationOpened = true;
            presentation(org.arcadia.arc_quest.api.event.guide.GuidePresentationEvent.Stage.OPENED);
        } else if (resumed) {
            presentation(org.arcadia.arc_quest.api.event.guide.GuidePresentationEvent.Stage.RESUMED);
        }
        if (!initialized || replaced) {
            if (ClientGuideCache.INSTANCE.isUnlocked(guideId)) {
                ClientGuideCache.INSTANCE.applyLocalProgress(guideId, currentPage);
                ArcQuestNetwork.sendGuideProgress(new C2SUpdateGuideProgressPacket(guideId, currentPage));
            }
            reader.reset();
            refreshMediaBinding();
            initialized = true;
        }
        // Screen.init also runs on a window/GUI-scale change. Keep page, scroll,
        // media playback and the server's presentation token alive through it.
        reader.release();
        lastRenderTime = 0;
    }

    @Override
    public void tick() {
        super.tick();
        ponderPanel.tick();
    }

    @Override
    public void removed() {
        if (jeiSuspension.removed() || settingsOpen) presentation(org.arcadia.arc_quest.api.event.guide.GuidePresentationEvent.Stage.SUSPENDED);
        else { ponderPanel.onScreenClosed(); closePresentation(); }
        HudCursorManager.reset();
        super.removed();
    }

    private void presentation(org.arcadia.arc_quest.api.event.guide.GuidePresentationEvent.Stage stage) {
        if (presentationToken != null && minecraft != null && minecraft.getConnection() != null)
            ArcQuestNetwork.CHANNEL.sendToServer(new org.arcadia.arc_quest.guide.network.C2SGuidePresentationPacket(guideId, presentationToken, stage));
    }
    private void closePresentation() {
        if (presentationClosed) return;
        presentationClosed = true;
        if (presentationToken != null) PRESENTATIONS.remove(presentationToken);
        presentation(org.arcadia.arc_quest.api.event.guide.GuidePresentationEvent.Stage.CLOSED);
    }

    public static void cancelPresentation(java.util.UUID token) {
        var ref = PRESENTATIONS.remove(token);
        var screen = ref == null ? null : ref.get();
        if (screen == null) return;
        screen.presentationClosed = true;
        screen.jeiSuspension.cancel();
        var mc = Minecraft.getInstance();
        if (mc.screen == screen) mc.setScreen(null);
    }

    public static void forgetPresentations() { PRESENTATIONS.clear(); }
    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) { }

    @Override
    public void onClose() {
        if (!isClosing) {
            isClosing = true;
            if (markSeenOnClose && minecraft != null && minecraft.player != null) {
                GuideCompletionClient.completeIfFinalPage(guide, guideId, currentPage);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isClosing) return true;
        if (keyCode == 256 || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            onClose(); return true;
        }
        if (keyCode == 263 || keyCode == 65) { previousPage(); return true; }
        if (keyCode == 262 || keyCode == 68) { nextPage(); return true; }
        if (reader.scrollKey(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double delta) {
        if (!isClosing) reader.wheel(delta);
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!isClosing && button == 0) reader.drag(y);
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (isClosing || button != 0) return true;
        switch (reader.click(x, y, currentPage, guide.getPageCount(), true)) {
            case CLOSE -> onClose();
            case PREVIOUS -> previousPage();
            case NEXT -> nextPage();
            case SETTINGS -> {
                settingsOpen = true;
                minecraft.setScreen(new org.arcadia.arc_quest.client.config.ArcQuestTextConfigScreen(
                        this, ArcQuestTextTarget.GUIDE));
            }
            case NONE -> {
                if (currentMedia().getType() == GuideMediaType.PONDER)
                    reader.mediaClick(ponderPanel, x, y, button);
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == 0) reader.release();
        return true;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        JeiScreenIngredients.begin(this, canQueryJei());
        HudCursorManager.beginFrame();
        try {
            if (guide == null) return;
            long now = Util.getMillis();
            dt = lastRenderTime == 0 ? 0 : Math.min(.1f, (now - lastRenderTime) / 1000f);
            lastRenderTime = now;
            transitionAlpha = HudAnimUtil.step(transitionAlpha, isClosing ? 0 : 1,
                    isClosing ? 7 : 5, dt);
            if (isClosing && transitionAlpha <= .01f) {
                if (minecraft.screen == this) minecraft.setScreen(null);
                return;
            }
            reader.render(this, g, guide, currentPage, ponderPanel, width, height,
                    mouseX, mouseY, partialTick, transitionAlpha, dt, isClosing, true);
        } finally { HudCursorManager.apply(); }
    }

    private void nextPage() {
        if (canNext()) {
            currentPage++;
            onPageChange();
            GuideCompletionClient.completeIfFinalPage(guide, guideId, currentPage);
        }
    }
    private void previousPage() { if (canPrev()) { currentPage--; onPageChange(); } }

    // 严格的逻辑判断，确保首页无 PREV，尾页无 NEXT
    private boolean canPrev() { return currentPage > 0; }
    private boolean canNext() { return guide != null && currentPage < guide.getPageCount() - 1; }

    private void onPageChange() {
        ClientGuideCache.INSTANCE.applyLocalProgress(guideId, currentPage);
        ArcQuestNetwork.sendGuideProgress(new C2SUpdateGuideProgressPacket(guideId, currentPage));
        reader.reset();
        refreshMediaBinding();
    }

    private void refreshMediaBinding() {
        GuideMediaDefinition m = currentMedia();
        if (m.getType() == GuideMediaType.PONDER && m.getSceneId() != null)
            ponderPanel.bind(m.getSceneId(), themeColor, m.isAutoplay());
        else if (m.getType() != GuideMediaType.PONDER)
            ponderPanel.unbind();
    }

    private GuidePageDefinition page() { return guide.getPage(currentPage); }
    private GuideMediaDefinition currentMedia() { return page().getMedia(); }
    private int clampPage(int p) { return Math.max(0, Math.min(p, guide.getPageCount() - 1)); }
    public boolean canQueryJei() { return !isClosing && ClientGuideCache.INSTANCE.isUnlocked(guideId); }
    @Override public void prepareJeiQuery() { jeiSuspension.arm(); }
    @Override public void cancelJeiQuery() { jeiSuspension.cancel(); }
    @Override public void abandonJeiQuery() { jeiSuspension.cancel(); ponderPanel.onScreenClosed(); closePresentation(); }
}
