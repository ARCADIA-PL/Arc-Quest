package org.com.arc_quest.client.gui.gacha;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.gacha.api.GachaShopDefinition;
import org.com.arc_quest.trade.gacha.network.C2SOpenGachaPacket;
import org.com.arc_quest.trade.network.ClientGachaCache;

public class GachaResultRenderer {

    public static final GachaResultRenderer INSTANCE = new GachaResultRenderer();

    private boolean active = false;
    private GachaShopDefinition shopDef;
    private ClientGachaCache.DrawRecord result;

    private float appearAnim = 0f;
    private float particleRot = 0f;

    private GachaResultRenderer() {}

    public void showResult(GachaShopDefinition shopDef, ClientGachaCache.DrawRecord result) {
        this.shopDef = shopDef;
        this.result = result;
        this.active = true;
        this.appearAnim = 0f;
        Minecraft.getInstance().mouseHandler.releaseMouse(); // 释放鼠标供点击
    }

    public boolean isActive() { return active; }

    public void render(GuiGraphics g, int width, int height, float dt) {
        if (!active || result == null) return;

        appearAnim += (1f - appearAnim) * Math.min(1f, dt * 6f);
        particleRot += dt * 30f;

        // 极度夸张的缓出（从 0.2 飙升到 1.1 再弹回 1.0）
        float scale = 1.0f + (1f - HudAnimUtil.easeOutElastic(appearAnim)) * 0.5f;
        int alpha = (int)(255 * Math.min(1f, appearAnim * 2f));

        // 全局背景暗化
        g.fill(0, 0, width, height, (int)(200 * (alpha/255f)) << 24);

        int cx = width / 2;
        int cy = height / 2;

        g.pose().pushPose();
        g.pose().translate(cx, cy, 500f); // 极其靠前
        g.pose().scale(scale, scale, 1f);

        // 寻找目标物品以获取颜色配置
        GachaItem targetItem = null;
        for (GachaItem item : shopDef.getGachaPool().getItems()) {
            if (item.getItemId().equals(result.itemId())) targetItem = item;
        }
        int themeC = targetItem != null ? shopDef.getEffectiveThemeColor(targetItem) : 0xFFFFFF;

        // 巨大光束背景旋转模拟 (可自行替换贴图，此处用代码光晕)
        for(int i=0; i<8; i++) {
            g.pose().pushPose();
            g.pose().mulPose(Axis.ZP.rotationDegrees(particleRot + i * 45f));
            g.fillGradient(-10, -200, 10, 200, HudAnimUtil.withAlpha(themeC, (int)(40 * (alpha/255f))), 0x00000000);
            g.pose().popPose();
        }

        // 中心物品爆点渲染
        if (targetItem != null) {
            g.pose().pushPose();
            g.pose().scale(5.0f, 5.0f, 1f); // 巨大化
            g.renderItem(targetItem.getItemStack(), -8, -12);
            g.pose().popPose();

            String title = targetItem.getItemStack().getHoverName().getString() + " x" + result.actualCount();
            g.drawCenteredString(Minecraft.getInstance().font, title, 0, 70, HudAnimUtil.withAlpha(themeC, alpha));

            if (result.pityTriggered()) {
                g.drawCenteredString(Minecraft.getInstance().font, "【保底触发】", 0, 85, HudAnimUtil.withAlpha(0xFFD700, alpha));
            }
        }

        g.drawCenteredString(Minecraft.getInstance().font, "点击任意位置继续", 0, 130, HudAnimUtil.withAlpha(0x888888, alpha));

        g.pose().popPose();
    }

    public boolean onClick() {
        if (!active || appearAnim < 0.8f) return false;
        active = false;
        
        // 【修复】关闭结算后，无条件向服务端请求打开界面
        // 所有状态校验都在服务端进行，客户端不预判
        Minecraft mc = Minecraft.getInstance();
        
        // 避免重复创建：如果当前已经是 GachaScreen，则无需操作
        if (mc.screen instanceof GachaScreen) {
            return true;
        }
        
        // 发送 C2S 请求，服务端校验后决定是否返回 S2COpenGachaPacket
        ArcQuestNetwork.CHANNEL.sendToServer(new C2SOpenGachaPacket(shopDef.getShopId()));
        return true;
    }
}