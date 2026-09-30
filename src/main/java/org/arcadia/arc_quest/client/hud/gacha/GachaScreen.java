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
import org.arcadia.arc_quest.client.compat.jei.screen.JeiScreenIngredients;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

public class GachaScreen extends Screen {
    private static final long DRAW_REQUEST_TIMEOUT = 5000;
    private static final int AUTHORITY_REFRESH_INTERVAL_TICKS = 30;

    private final String shopId;
    private final GachaShopDefinition shopDef;
    private final GachaPreviewPanel previewPanel;
    private final GachaRollerPanel rollerPanel;
    private final Consumer<Object> packetSender;
    private final LongSupplier requestClock;
    private final GachaDrawRequestState drawRequest = new GachaDrawRequestState();
    private Phase currentPhase = Phase.PREVIEW;
    private float transitionAnim = 0f;
    private float rollTransitionAnim = 0f;
    private boolean isClosing = false;
    private boolean switchingToResult = false;
    private long lastRenderTime = 0;
    private float dt = 0f;
    private int authorityRefreshTicker = 0;

    public GachaScreen(String shopId) {
        this(shopId, packet -> ArcQuestNetwork.CHANNEL.sendToServer(packet), Util::getMillis);
    }

    GachaScreen(String shopId, Consumer<Object> packetSender, LongSupplier requestClock) {
        super(Component.translatable("arc_quest.gui.gacha.title"));
        this.shopId = shopId;
        this.packetSender = packetSender;
        this.requestClock = requestClock;
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
        if (drawRequest.pollSlowNotice(requestClock.getAsLong(), DRAW_REQUEST_TIMEOUT)) {
            minecraft.gui.setOverlayMessage(Component.translatableWithFallback(
                    "arc_quest.gui.gacha.waiting_server", "Still waiting for the server. The draw has not been resent."), false);
            packetSender.accept(C2SGachaControlPacket.sync(shopId));
        }

        if (currentPhase != Phase.PREVIEW) {
            return;
        }

        authorityRefreshTicker++;
        if (authorityRefreshTicker < AUTHORITY_REFRESH_INTERVAL_TICKS) {
            return;
        }
        authorityRefreshTicker = 0;

        packetSender.accept(C2SGachaControlPacket.sync(shopId));
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

    public boolean canQueryJei() {
        return currentPhase == Phase.PREVIEW && !drawRequest.pending() && !switchingToResult
                && !isClosing && rollTransitionAnim <= 0.001f && shopDef != null;
    }

    public void startDrawRequest() {
        if (currentPhase != Phase.PREVIEW || isClosing || !drawRequest.start(requestClock.getAsLong())) {
            ArcQuestLog.debug(ArcQuestLog.Category.HUD, "Draw request ignored: currentPhase={}", currentPhase);
            return;
        }

        ArcQuestLog.info(ArcQuestLog.Category.HUD, "Starting draw request for shop: {}", shopId);
        currentPhase = Phase.WAITING_SERVER;
        authorityRefreshTicker = 0;
        previewPanel.updateDataSnapshot();

        packetSender.accept(new C2SDrawGachaPacket(shopId));
        ArcQuestLog.debug(ArcQuestLog.Category.HUD, "C2SDrawGachaPacket sent to server");
    }

    public void triggerRollingAnimation(ClientGachaCache.DrawRecord result) {
        if (currentPhase == Phase.WAITING_SERVER && drawRequest.acceptResult()) {
            ArcQuestLog.info(ArcQuestLog.Category.HUD, "Triggering rolling animation for item: {}", result.itemId());
            currentPhase = Phase.ROLLING;
            authorityRefreshTicker = 0;
            rollerPanel.startRoll(result);
        } else {
            ArcQuestLog.warn(ArcQuestLog.Category.HUD, "Cannot trigger animation: currentPhase={}, expected WAITING_SERVER", currentPhase);
        }
    }

    public void onRollFinished(ClientGachaCache.DrawRecord result) {
        if (currentPhase == Phase.ROLLING && !isClosing && drawRequest.pending() && !switchingToResult) {
            ArcQuestLog.info(ArcQuestLog.Category.HUD, "Roll finished, switching to result renderer for item: {}", result.itemId());
            switchingToResult = true;
            GachaResultRenderer.INSTANCE.showResult(this, shopDef, result);
        } else {
            ArcQuestLog.debug(ArcQuestLog.Category.HUD, "Ignoring stale roll completion: phase={}, closing={}, pending={}, switchingToResult={}",
                    currentPhase, isClosing, drawRequest.pending(), switchingToResult);
        }
    }

    public void confirmDrawAndSync() {
        if (drawRequest.confirm()) {
            ArcQuestLog.info(ArcQuestLog.Category.HUD, "Confirming draw and syncing for shop: {}", shopId);
            packetSender.accept(new C2SConfirmDrawPacket(shopId));
            packetSender.accept(C2SGachaControlPacket.sync(shopId));
            ArcQuestLog.debug(ArcQuestLog.Category.HUD, "C2SConfirmDrawPacket + immediate SYNC sent, hasPendingDraw=false");
        } else {
            ArcQuestLog.debug(ArcQuestLog.Category.HUD, "confirmDrawAndSync called but no pending draw");
        }
    }

    public void onDrawFailedAndReturnToPreview() {
        if (currentPhase == Phase.WAITING_SERVER && drawRequest.fail()) {
            ArcQuestLog.info(ArcQuestLog.Category.HUD, "Draw failed, returning to preview: shop={}", shopId);
            currentPhase = Phase.PREVIEW;
            authorityRefreshTicker = 0;
            previewPanel.updateDataSnapshot();
        } else {
            previewPanel.updateDataSnapshot();
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        JeiScreenIngredients.begin(this, canQueryJei());
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
                    currentPhase, drawRequest.pending(), switchingToResult);
            isClosing = true;

            if (currentPhase == Phase.ROLLING && rollerPanel != null && !switchingToResult) {
                ArcQuestLog.info(ArcQuestLog.Category.HUD, "Forcing roller panel exit animation");
                rollerPanel.onScreenClose();
            }

            if (switchingToResult && GachaResultRenderer.INSTANCE.isActive()) {
                ArcQuestLog.info(ArcQuestLog.Category.HUD, "Forcing result renderer close and confirm");
                GachaResultRenderer.INSTANCE.forceCloseAndConfirm();
            } else if (drawRequest.pending() || currentPhase == Phase.WAITING_SERVER || currentPhase == Phase.ROLLING) {
                ArcQuestLog.info(ArcQuestLog.Category.HUD, "Emergency fallback: forcing draw confirmation");
                confirmDrawAndSync();
            }

            packetSender.accept(ClientDialogueCache.INSTANCE.createRestorePacket());
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
