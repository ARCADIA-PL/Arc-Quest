package org.arcadia.arc_quest.client.hud.guide;


import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.ponder.EmbeddedPonderScenePanel;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaDefinition;
import org.arcadia.arc_quest.guide.api.GuideMediaType;
import org.arcadia.arc_quest.guide.network.C2SUpdateGuideProgressPacket;
import org.arcadia.arc_quest.guide.network.ClientGuideCache;
import org.arcadia.arc_quest.guide.registry.GuideRegistry;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.jetbrains.annotations.Nullable;


/** 相关处理说明。 */
public final class GuidePopupOverlay implements IGuiOverlay {

    public static final GuidePopupOverlay INSTANCE = new GuidePopupOverlay();

    private final EmbeddedPonderScenePanel ponderPanel = new EmbeddedPonderScenePanel();
    private final GuideReaderPanel reader = new GuideReaderPanel();
    private GuideDefinition guide;
    private ResourceLocation guideId;
    private int pageIndex;
    private boolean markSeenOnClose;
    private boolean closing;
    private float animation;
    private long lastRenderTime;
    private GuidePopupOverlay() {
    }

    public boolean open(ResourceLocation id, int initialPage, boolean markSeen) {
        GuideDefinition definition = GuideRegistry.get(id);
        if (definition == null) return false;
        guide = definition;
        guideId = id;
        pageIndex = Math.max(0, Math.min(initialPage, definition.getPageCount() - 1));
        markSeenOnClose = markSeen;
        closing = false;
        animation = 0f;
        lastRenderTime = 0L;
        reader.reset();
        refreshMediaBinding();
        return true;
    }

    public boolean isActive() {
        return guide != null;
    }

    /** Identity of the guide currently being read, or null when the popup is closed. */
    public ResourceLocation getGuideId() {
        return isActive() ? guideId : null;
    }

    public void tick() {
        if (isActive()) ponderPanel.tick();
    }

    public void clear() {
        clearState();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof InputHostScreen) {
            minecraft.setScreen(null);
        }
    }

    private void clearState() {
        guide = null;
        guideId = null;
        closing = false;
        animation = 0f;
        reader.release();
        ponderPanel.unbind();
    }

    /** 相关处理说明。 */
    public void ensureInputScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (isActive() && minecraft.screen == null) {
            minecraft.setScreen(new InputHostScreen());
        }
    }

    public void close() {
        if (!isActive() || closing) return;
        closing = true;
        if (markSeenOnClose) GuideCompletionClient.completeIfFinalPage(guide, guideId, pageIndex);
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                       int screenWidth, int screenHeight) {
        if (Minecraft.getInstance().screen != null || !isActive()) return;
        HudCursorManager.renderHud(() -> render(null, graphics, screenWidth, screenHeight, partialTick));
    }

    public void render(@Nullable Screen owner, GuiGraphics graphics,
                       int screenWidth, int screenHeight, float partialTick) {
        if (!isActive()) return;
        HudCursorManager.beginFrame();
        try {
            renderPopup(owner, graphics, screenWidth, screenHeight, partialTick);
        } finally {
            HudCursorManager.apply();
        }
    }

    private void renderPopup(@Nullable Screen owner, GuiGraphics graphics,
                             int screenWidth, int screenHeight, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        double mouseX = minecraft.mouseHandler.xpos() * screenWidth / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * screenHeight / minecraft.getWindow().getScreenHeight();
        long now = Util.getMillis();
        if (lastRenderTime == 0L) lastRenderTime = now;
        float deltaTime = Math.min(0.1f, (now - lastRenderTime) / 1000f);
        lastRenderTime = now;
        animation = HudAnimUtil.step(animation, closing ? 0f : 1f, closing ? 5.5f : 4.2f, deltaTime);
        if (closing && animation <= 0.01f) {
            boolean hosted = minecraft.screen instanceof InputHostScreen;
            clearState();
            if (hosted) minecraft.setScreen(DialogueScreen.fromCurrentSession());
            return;
        }

        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, 4600);
            reader.render(owner, graphics, guide, pageIndex, ponderPanel, screenWidth, screenHeight,
                    (int) mouseX, (int) mouseY, partialTick, animation, deltaTime, closing, false);
        } finally { graphics.pose().popPose(); }
    }

    public boolean mouseClicked(double x, double y, int button) {
        if (!isActive()) return false;
        if (closing || button != 0) return true;
        switch (reader.click(x, y, pageIndex, guide.getPageCount(), false)) {
            case CLOSE -> close();
            case PREVIOUS -> changePage(-1);
            case NEXT -> changePage(1);
            case NONE -> {
                if (currentMedia().getType() == GuideMediaType.PONDER)
                    reader.mediaClick(ponderPanel, x, y, button);
            }
            default -> { }
        }
        return true;
    }

    public boolean mouseDragged(double x, double y, int button) {
        if (!isActive()) return false;
        if (!closing && button == 0) reader.drag(y);
        return true;
    }

    public boolean mouseReleased(int button) {
        if (!isActive()) return false;
        if (button == 0) reader.release();
        return true;
    }

    public boolean mouseScrolled(double delta) {
        if (!isActive()) return false;
        if (!closing) reader.wheel(delta);
        return true;
    }

    public boolean keyPressed(int keyCode) {
        if (!isActive()) return false;
        if (closing) return true;
        if (keyCode == 256) close();
        else if (keyCode == 263 || keyCode == 65) changePage(-1);
        else if (keyCode == 262 || keyCode == 68) changePage(1);
        else reader.scrollKey(keyCode);
        return true;
    }

    private void changePage(int direction) {
        int next = Math.max(0, Math.min(guide.getPageCount() - 1, pageIndex + direction));
        if (next == pageIndex) return;
        pageIndex = next;
        reader.reset();
        refreshMediaBinding();
        if (ClientGuideCache.INSTANCE.isUnlocked(guideId)) {
            ClientGuideCache.INSTANCE.applyLocalProgress(guideId, pageIndex);
            ArcQuestNetwork.sendGuideProgress(new C2SUpdateGuideProgressPacket(guideId, pageIndex));
        }
        if (direction > 0) GuideCompletionClient.completeIfFinalPage(guide, guideId, pageIndex);
    }

    private GuideMediaDefinition currentMedia() {
        return guide.getPage(pageIndex).getMedia();
    }

    private void refreshMediaBinding() {
        GuideMediaDefinition media = currentMedia();
        if (media.getType() == GuideMediaType.PONDER && media.getSceneId() != null) {
            ponderPanel.bind(media.getSceneId(), guide.getCategory().getThemeColor(), media.isAutoplay());
        } else {
            ponderPanel.unbind();
        }
    }

    /** 相关处理说明。 */
    private static final class InputHostScreen extends Screen {
        private InputHostScreen() {
            super(Component.empty());
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public void onClose() {
            GuidePopupOverlay.INSTANCE.close();
        }
    }
}
