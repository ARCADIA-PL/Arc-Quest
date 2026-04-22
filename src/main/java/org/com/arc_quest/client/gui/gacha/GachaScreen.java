package org.com.arc_quest.client.gui.gacha;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.dialogue.network.C2SDialogueChoicePacket;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.network.C2SConfirmDrawPacket;
import org.com.arc_quest.trade.gacha.network.C2SDrawGachaPacket;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.com.arc_quest.trade.gacha.network.ClientGachaCache;
import org.slf4j.Logger;

public class GachaScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DRAW_REQUEST_TIMEOUT = 5000;

    private final String shopId;
    private final GachaShopDefinition shopDef;

    public enum Phase { PREVIEW, WAITING_SERVER, ROLLING }
    private Phase currentPhase = Phase.PREVIEW;

    private final GachaPreviewPanel previewPanel;
    private final GachaRollerPanel rollerPanel;

    private float transitionAnim = 0f;
    private float rollTransitionAnim = 0f;

    private boolean isClosing = false;
    private boolean switchingToResult = false;
    private long lastRenderTime = 0;
    private float dt = 0f;

    private long requestTimestamp = 0;

    // ★ 架构核心：全局未决交易追踪器
    private boolean hasPendingDraw = false;

    public GachaScreen(String shopId) {
        super(Component.translatable("arc_quest.gui.gacha.title"));
        this.shopId = shopId;
        this.shopDef = GachaRegistry.get(shopId);
        this.previewPanel = new GachaPreviewPanel(this);
        this.rollerPanel = new GachaRollerPanel(this);
    }

    @Override
    protected void init() {
        super.init();
        if (lastRenderTime == 0) transitionAnim = 0f;
        this.previewPanel.init(width, height);
        this.rollerPanel.init(width, height);
    }

    @Override public void renderBackground(GuiGraphics g) {}
    @Override public boolean isPauseScreen() { return false; }
    public String getShopId() { return shopId; }
    public GachaShopDefinition getShopDef() { return shopDef; }

    public void startDrawRequest() {
        if (currentPhase != Phase.PREVIEW) {
            LOGGER.debug("[Gacha-Client] Draw request ignored: currentPhase={}", currentPhase);
            return;
        }
        
        LOGGER.info("[Gacha-Client] Starting draw request for shop: {}", shopId);
        currentPhase = Phase.WAITING_SERVER;
        this.hasPendingDraw = true;
        this.requestTimestamp = System.currentTimeMillis();
        LOGGER.debug("[Gacha-Client] Phase changed to WAITING_SERVER, hasPendingDraw=true, timestamp={}", requestTimestamp);
        
        ArcQuestNetwork.CHANNEL.sendToServer(new C2SDrawGachaPacket(shopId));
        LOGGER.debug("[Gacha-Client] C2SDrawGachaPacket sent to server");
    }

    public void triggerRollingAnimation(ClientGachaCache.DrawRecord result) {
        if (this.currentPhase == Phase.WAITING_SERVER) {
            LOGGER.info("[Gacha-Client] Triggering rolling animation for item: {}", result.itemId());
            this.currentPhase = Phase.ROLLING;
            this.rollerPanel.startRoll(result);
        } else {
            LOGGER.warn("[Gacha-Client] Cannot trigger animation: currentPhase={}, expected WAITING_SERVER", this.currentPhase);
        }
    }

    public void onRollFinished(ClientGachaCache.DrawRecord result) {
        if (!switchingToResult) {
            LOGGER.info("[Gacha-Client] Roll finished, switching to result renderer for item: {}", result.itemId());
            switchingToResult = true;
            GachaResultRenderer.INSTANCE.showResult(this, shopDef, result);
        } else {
            LOGGER.warn("[Gacha-Client] onRollFinished called but already switching to result");
        }
    }

    // ★ 终极结算同步中心：无论是自然播放完毕，还是意外强制退出，全部走这里！
    public void confirmDrawAndSync() {
        if (hasPendingDraw) {
            LOGGER.info("[Gacha-Client] Confirming draw and syncing for shop: {}", shopId);
            hasPendingDraw = false;
            ArcQuestNetwork.CHANNEL.sendToServer(new C2SConfirmDrawPacket(shopId));
            LOGGER.debug("[Gacha-Client] C2SConfirmDrawPacket sent, hasPendingDraw=false");
            previewPanel.updateDataSnapshot();
            LOGGER.debug("[Gacha-Client] Preview panel data snapshot updated");
        } else {
            LOGGER.debug("[Gacha-Client] confirmDrawAndSync called but no pending draw");
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        transitionAnim = HudAnimUtil.lerp(transitionAnim, isClosing ? 0f : 1f, isClosing ? 0.15f : 0.1f, dt);
        if (isClosing && transitionAnim <= 0.01f) {
            minecraft.setScreen(null);
            return;
        }

        if (shopDef == null) return;

        if (currentPhase == Phase.WAITING_SERVER && System.currentTimeMillis() - requestTimestamp > DRAW_REQUEST_TIMEOUT) {
            currentPhase = Phase.PREVIEW;
            hasPendingDraw = false; // 超时释放锁
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
                return;
            } else {
                switchingToResult = false;
                currentPhase = Phase.PREVIEW;
                rollTransitionAnim = 1.0f;
                previewPanel.updateDataSnapshot(); // 确保安全归位
            }
        }

        if (currentPhase == Phase.PREVIEW || currentPhase == Phase.WAITING_SERVER || rollTransitionAnim > 0) {
            boolean panelWaiting = currentPhase == Phase.WAITING_SERVER || currentPhase == Phase.ROLLING;
            previewPanel.render(g, mx, my, dt, easeProgress, contentScale, panelWaiting, isClosing, rollTransitionAnim);
        }

        if (currentPhase == Phase.ROLLING) {
            rollerPanel.render(g, dt);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (switchingToResult && GachaResultRenderer.INSTANCE.isActive()) {
            return GachaResultRenderer.INSTANCE.mouseClicked();
        }
        if (isClosing || btn != 0) return false;
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
            LOGGER.info("[Gacha-Client] Screen closing: phase={}, hasPendingDraw={}, switchingToResult={}", 
                currentPhase, hasPendingDraw, switchingToResult);
            isClosing = true;

            // 1. 强制熔断动画
            if (currentPhase == Phase.ROLLING && rollerPanel != null && !switchingToResult) {
                LOGGER.info("[Gacha-Client] Forcing roller panel exit animation");
                rollerPanel.onScreenClose();
                // 等待0.35秒让EXIT动画播放完成后再确认
                new Thread(() -> {
                    try { Thread.sleep(350); } catch (InterruptedException e) {}
                    minecraft.execute(this::confirmDrawAndSync);
                }).start();
            }

            // 2. 强制关闭结果弹窗
            if (switchingToResult && GachaResultRenderer.INSTANCE.isActive()) {
                LOGGER.info("[Gacha-Client] Forcing result renderer close and confirm");
                GachaResultRenderer.INSTANCE.forceCloseAndConfirm();
            }

            // 3. 【三阶段终极兜底】如果处于等待、动画中，或有未结算的交易，强制触发保底确认！
            if (hasPendingDraw || currentPhase == Phase.WAITING_SERVER || currentPhase == Phase.ROLLING) {
                LOGGER.info("[Gacha-Client] Emergency fallback: forcing draw confirmation");
                hasPendingDraw = true; // 强行激活锁
                confirmDrawAndSync();  // 强行写入数据，同步本地缓存！
            }

            // 4. 【绝对强同步】只有在以上所有兜底和数据落盘完毕后，才允许向服务器发送恢复对话！
            ArcQuestNetwork.sendDialogueChoice(new C2SDialogueChoicePacket(C2SDialogueChoicePacket.RESTORE_DIALOGUE));
            LOGGER.debug("[Gacha-Client] RESTORE_DIALOGUE packet sent");
        }
    }
}