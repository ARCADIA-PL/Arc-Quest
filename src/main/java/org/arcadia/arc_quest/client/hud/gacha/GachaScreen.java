package org.arcadia.arc_quest.client.hud.gacha;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.HudAnimUtil;
import org.arcadia.arc_quest.client.hud.component.HudCursorManager;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache;
import org.arcadia.arc_quest.quest.network.ArcQuestNetwork;
import org.arcadia.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.arcadia.arc_quest.trade.gacha.network.C2SConfirmDrawPacket;
import org.arcadia.arc_quest.trade.gacha.network.C2SDrawGachaPacket;
import org.arcadia.arc_quest.trade.gacha.network.C2SGachaControlPacket;
import org.arcadia.arc_quest.trade.gacha.network.ClientGachaCache;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;

public class GachaScreen extends Screen {
    private static final long DRAW_REQUEST_TIMEOUT = 5000;
    private static final int AUTHORITY_REFRESH_INTERVAL_TICKS = 30;

    private final String shopId;
    private final GachaShopDefinition shopDef;
    private final GachaPreviewPanel previewPanel;
    private final GachaRollerPanel rollerPanel;
    private Phase currentPhase = Phase.PREVIEW;
    private float transitionAnim = 0f;
    private float rollTransitionAnim = 0f;
    private boolean isClosing = false;
    private boolean switchingToResult = false;
    private long lastRenderTime = 0;
    private float dt = 0f;
    private long requestTimestamp = 0;
    private boolean hasPendingDraw = false;
    private int authorityRefreshTicker = 0;

    public GachaScreen(String shopId) {
        super(Component.translatable("arc_quest.gui.gacha.title"));
        this.shopId = shopId;
        shopDef = GachaRegistry.get(shopId);
        previewPanel = new GachaPreviewPanel(this);
        rollerPanel = new GachaRollerPanel(this);
    }

    @Override
    protected void init() {
        super.init();
        if (lastRenderTime == 0) transitionAnim = 0f;
        previewPanel.init(width, height);
        rollerPanel.init(width, height);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (isClosing || shopDef == null || minecraft == null || minecraft.player == null) {
            return;
        }

        if (currentPhase != Phase.PREVIEW) {
            return;
        }

        authorityRefreshTicker++;
        if (authorityRefreshTicker < AUTHORITY_REFRESH_INTERVAL_TICKS) {
            return;
        }
        authorityRefreshTicker = 0;

        ArcQuestNetwork.CHANNEL.sendToServer(C2SGachaControlPacket.sync(shopId));
    }

    public String getShopId() {
        return shopId;
    }

    public GachaShopDefinition getShopDef() {
        return shopDef;
    }

    public GachaPreviewPanel getPreviewPanel() {
        return previewPanel;
    }

    public void startDrawRequest() {
        if (currentPhase != Phase.PREVIEW) {
            ArcQuestLog.debug(ArcQuestLog.Category.HUD, "Draw request ignored: currentPhase={}", currentPhase);
            return;
        }

        ArcQuestLog.info(ArcQuestLog.Category.HUD, "Starting draw request for shop: {}", shopId);
        currentPhase = Phase.WAITING_SERVER;
        hasPendingDraw = true;
        requestTimestamp = System.currentTimeMillis();
        authorityRefreshTicker = 0;
        previewPanel.updateDataSnapshot();

        ArcQuestNetwork.CHANNEL.sendToServer(new C2SDrawGachaPacket(shopId));
        ArcQuestLog.debug(ArcQuestLog.Category.HUD, "C2SDrawGachaPacket sent to server");
    }

    public void triggerRollingAnimation(ClientGachaCache.DrawRecord result) {
        if (currentPhase == Phase.WAITING_SERVER) {
            ArcQuestLog.info(ArcQuestLog.Category.HUD, "Triggering rolling animation for item: {}", result.itemId());
            currentPhase = Phase.ROLLING;
            authorityRefreshTicker = 0;
            rollerPanel.startRoll(result);
        } else {
            ArcQuestLog.warn(ArcQuestLog.Category.HUD, "Cannot trigger animation: currentPhase={}, expected WAITING_SERVER", currentPhase);
        }
    }

    public void onRollFinished(ClientGachaCache.DrawRecord result) {
        if (!switchingToResult) {
            ArcQuestLog.info(ArcQuestLog.Category.HUD, "Roll finished, switching to result renderer for item: {}", result.itemId());
            switchingToResult = true;
            GachaResultRenderer.INSTANCE.showResult(this, shopDef, result);
        } else {
            ArcQuestLog.warn(ArcQuestLog.Category.HUD, "onRollFinished called but already switchingToResult");
        }
    }

    public void confirmDrawAndSync() {
        if (hasPendingDraw) {
            ArcQuestLog.info(ArcQuestLog.Category.HUD, "Confirming draw and syncing for shop: {}", shopId);
            hasPendingDraw = false;
            ArcQuestNetwork.CHANNEL.sendToServer(new C2SConfirmDrawPacket(shopId));
            ArcQuestNetwork.CHANNEL.sendToServer(C2SGachaControlPacket.sync(shopId));
            ArcQuestLog.debug(ArcQuestLog.Category.HUD, "C2SConfirmDrawPacket + immediate SYNC sent, hasPendingDraw=false");
        } else {
            ArcQuestLog.debug(ArcQuestLog.Category.HUD, "confirmDrawAndSync called but no pending draw");
        }
    }

    public void onDrawFailedAndReturnToPreview() {
        if (currentPhase == Phase.WAITING_SERVER) {
            ArcQuestLog.info(ArcQuestLog.Category.HUD, "Draw failed, returning to preview: shop={}", shopId);
            currentPhase = Phase.PREVIEW;
            hasPendingDraw = false;
            requestTimestamp = 0;
            authorityRefreshTicker = 0;
            previewPanel.updateDataSnapshot();
        } else {
            previewPanel.updateDataSnapshot();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        HudCursorManager.beginFrame();
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        transitionAnim = HudAnimUtil.lerp(transitionAnim, isClosing ? 0f : 1f, isClosing ? 0.15f : 0.1f, dt);
        if (isClosing && transitionAnim <= 0.01f) {
            minecraft.setScreen(null);
            HudCursorManager.apply();
            return;
        }

        if (shopDef == null) {
            HudCursorManager.apply();
            return;
        }

        if (currentPhase == Phase.WAITING_SERVER && System.currentTimeMillis() - requestTimestamp > DRAW_REQUEST_TIMEOUT) {
            currentPhase = Phase.PREVIEW;
            hasPendingDraw = false;
            authorityRefreshTicker = 0;
        }

        if (currentPhase == Phase.ROLLING) {
            rollTransitionAnim = Math.min(1.0f, rollTransitionAnim + dt * 2.8f);
        } else {
            rollTransitionAnim = Math.max(0.0f, rollTransitionAnim - dt * 3.5f);
        }

        float easeProgress = isClosing ? HudAnimUtil.easeInCubic(transitionAnim) : HudAnimUtil.easeOutCubic(transitionAnim);
        float contentScale = isClosing ? transitionAnim : (0.8f + 0.2f * easeProgress);

        if (switchingToResult) {
            if (GachaResultRenderer.INSTANCE.isActive()) {
                GachaResultRenderer.INSTANCE.render(g, width, height, dt);
                HudCursorManager.apply();
                return;
            } else {
                switchingToResult = false;
                currentPhase = Phase.PREVIEW;
                rollTransitionAnim = 1.0f;
                authorityRefreshTicker = 0;
                previewPanel.updateDataSnapshot();
            }
        }

        if (currentPhase == Phase.PREVIEW || currentPhase == Phase.WAITING_SERVER || rollTransitionAnim > 0) {
            boolean panelWaiting = currentPhase == Phase.WAITING_SERVER || currentPhase == Phase.ROLLING;
            previewPanel.render(g, mx, my, dt, easeProgress, contentScale, panelWaiting, isClosing, rollTransitionAnim);
        }

        if (currentPhase == Phase.ROLLING) {
            rollerPanel.render(g, dt);
        }
        HudCursorManager.apply();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (switchingToResult && GachaResultRenderer.INSTANCE.isActive()) {
            return GachaResultRenderer.INSTANCE.mouseClicked();
        }
        if (isClosing || btn != 0) return false;

        if (currentPhase == Phase.ROLLING) {
            return rollerPanel.mouseClicked();
        }

        if (currentPhase == Phase.PREVIEW) return previewPanel.mouseClicked(mx, my);
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (currentPhase == Phase.PREVIEW) return previewPanel.mouseScrolled(delta);
        return false;
    }

    @Override
    public void onClose() {
        if (!isClosing) {
            ArcQuestLog.info(ArcQuestLog.Category.HUD, "Screen closing: phase={}, hasPendingDraw={}, switchingToResult={}",
                    currentPhase, hasPendingDraw, switchingToResult);
            isClosing = true;

            if (currentPhase == Phase.ROLLING && rollerPanel != null && !switchingToResult) {
                ArcQuestLog.info(ArcQuestLog.Category.HUD, "Forcing roller panel exit animation");
                rollerPanel.onScreenClose();
            }

            if (switchingToResult && GachaResultRenderer.INSTANCE.isActive()) {
                ArcQuestLog.info(ArcQuestLog.Category.HUD, "Forcing result renderer close and confirm");
                GachaResultRenderer.INSTANCE.forceCloseAndConfirm();
            } else if (hasPendingDraw || currentPhase == Phase.WAITING_SERVER || currentPhase == Phase.ROLLING) {
                ArcQuestLog.info(ArcQuestLog.Category.HUD, "Emergency fallback: forcing draw confirmation");
                hasPendingDraw = true;
                confirmDrawAndSync();
            }

            ArcQuestNetwork.sendDialogueChoice(ClientDialogueCache.INSTANCE.createRestorePacket());
            ArcQuestLog.debug(ArcQuestLog.Category.HUD, "RESTORE_DIALOGUE packet sent");
        }
    }

    @Override
    public void removed() {
        HudCursorManager.reset();
        super.removed();
    }

    public enum Phase {PREVIEW, WAITING_SERVER, ROLLING}
}
