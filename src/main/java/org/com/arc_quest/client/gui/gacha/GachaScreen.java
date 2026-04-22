package org.com.arc_quest.client.gui.gacha;

import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.network.C2SDrawGachaPacket;
import org.com.arc_quest.trade.network.ClientGachaCache;
import org.com.arc_quest.trade.gacha.registry.GachaRegistry;
import org.slf4j.Logger;

public class GachaScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long DRAW_REQUEST_TIMEOUT = 5000; // 5秒超时

    private final String shopId;
    private final GachaShopDefinition shopDef;

    // 状态机
    public enum Phase { PREVIEW, WAITING_SERVER, ROLLING }
    private Phase currentPhase = Phase.PREVIEW;

    // 面板组件
    private final GachaPreviewPanel previewPanel;
    private final GachaRollerPanel rollerPanel;

    // 动画状态
    private float transitionAnim = 0f;
    private boolean isClosing = false;
    private long lastRenderTime = 0;
    private float dt = 0f;

    // 监听网络结果的锚点
    private long lastDrawTimeAtOpen = 0;
    private long requestTimestamp = 0; // 请求时间戳（用于超时检测）
    
    // 【优化】日志频率控制
    private long lastLogTime = 0;
    private static final long LOG_INTERVAL_MS = 500; // 每500ms最多输出一次诊断日志

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

        // 【修复】不在这里初始化 lastDrawTimeAtOpen，而是在发送请求时记录
        // 这样可以避免 S2COpenGachaPacket 更新缓存导致的时间戳冲突
        this.lastDrawTimeAtOpen = 0;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    public String getShopId() { return shopId; }
    public GachaShopDefinition getShopDef() { return shopDef; }

    // 供 PreviewPanel 调用：向服务端发送抽奖请求
    public void startDrawRequest() {
        if (currentPhase != Phase.PREVIEW) return;
        
        // 【修复】在发送请求前记录当前最新的抽奖时间戳作为锚点
        var lastResult = ClientGachaCache.INSTANCE.getLastDrawResult(shopId);
        this.lastDrawTimeAtOpen = lastResult != null ? lastResult.drawTime() : 0;
        
        currentPhase = Phase.WAITING_SERVER;
        this.requestTimestamp = System.currentTimeMillis();
        ArcQuestNetwork.CHANNEL.sendToServer(new C2SDrawGachaPacket(shopId));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = Util.getMillis();
        if (lastRenderTime == 0) lastRenderTime = now;
        dt = Math.min((now - lastRenderTime) / 1000f, 0.1f);
        lastRenderTime = now;

        // 全局进退场动画
        transitionAnim = HudAnimUtil.lerp(transitionAnim, isClosing ? 0f : 1f, isClosing ? 0.15f : 0.1f, dt);
        if (isClosing && transitionAnim <= 0.01f) {
            minecraft.setScreen(null);
            return;
        }

        // 暗色遮罩
        g.fill(0, 0, width, height, ((int) (160 * transitionAnim) << 24));

        if (shopDef == null) return;

        // 状态机流转与监听
        checkServerResponse();

        // 渲染分支
        if (currentPhase == Phase.PREVIEW || currentPhase == Phase.WAITING_SERVER) {
            float alpha = isClosing ? transitionAnim : HudAnimUtil.easeOutCubic(transitionAnim);
            previewPanel.render(g, mx, my, dt, alpha, currentPhase == Phase.WAITING_SERVER);
        } else if (currentPhase == Phase.ROLLING) {
            rollerPanel.render(g, mx, my, dt);
            if (rollerPanel.isFinished()) {
                // 滚动结束，唤起 Overlay 结算层，并关闭当前 Screen
                var finalResult = ClientGachaCache.INSTANCE.getLastDrawResult(shopId);
                GachaResultRenderer.INSTANCE.showResult(shopDef, finalResult);
                this.onClose();
            }
        }
    }

    private void checkServerResponse() {
        if (currentPhase != Phase.WAITING_SERVER) return;
        
        // 超时保护：5秒无响应则返回预览界面
        long elapsed = System.currentTimeMillis() - requestTimestamp;
        if (elapsed > DRAW_REQUEST_TIMEOUT) {
            currentPhase = Phase.PREVIEW;
            return;
        }
        
        var latestResult = ClientGachaCache.INSTANCE.getLastDrawResult(shopId);
        var session = ClientGachaCache.INSTANCE.getSession(shopId);
        
        if (latestResult != null && latestResult.drawTime() > lastDrawTimeAtOpen) {
            // 检查是否为失败结果
            String failReason = session.getLastFailReason();
            if (failReason != null) {
                // 抽奖失败，直接返回预览界面
                currentPhase = Phase.PREVIEW;
                return;
            }
            
            // 服务端已返回成功结果，正式进入滚动阶段
            currentPhase = Phase.ROLLING;
            rollerPanel.startRoll(latestResult);
        }
    }

    /**
     * 【修复】由网络包直接触发滚动动画，避免tick轮询的时序问题
     */
    public void triggerRollingAnimation(String itemId, String rarityName, int count, boolean pityTriggered) {
        if (isClosing) return;
        
        // 创建 DrawRecord
        var result = new ClientGachaCache.DrawRecord(itemId, rarityName, count, pityTriggered, System.currentTimeMillis());
        
        // 直接进入 ROLLING 阶段
        currentPhase = Phase.ROLLING;
        rollerPanel.startRoll(result);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (isClosing || btn != 0) return false;
        if (currentPhase == Phase.PREVIEW) {
            return previewPanel.mouseClicked(mx, my);
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (currentPhase == Phase.PREVIEW) return previewPanel.mouseScrolled(delta);
        return false;
    }

    @Override
    public void onClose() {
        if (!isClosing) isClosing = true;
    }
}