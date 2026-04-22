package org.com.arc_quest.client.gui.gacha;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.com.arc_quest.client.gui.HudAnimUtil;
import org.com.arc_quest.quest.network.ArcQuestNetwork;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.gacha.network.C2SConfirmDrawPacket;
import org.com.arc_quest.trade.gacha.network.ClientGachaCache;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GachaRollerPanel {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final GachaScreen parent;
    private int width, height;

    private List<GachaItem> rollingItems = new ArrayList<>();
    private float currentX = 0f;
    private float targetX = 0f;
    private float velocity = 0f;

    private boolean isRolling = false;
    private boolean finished = false;
    private boolean rewardConfirmed = false;  // 【新增】标记奖励是否已确认

    private static final int ITEM_W = 100;
    private static final int ITEM_GAP = 10;
    private int lastPassedIndex = -1;

    public GachaRollerPanel(GachaScreen parent) {
        this.parent = parent;
    }

    public void init(int w, int h) {
        this.width = w;
        this.height = h;
    }

    public void startRoll(ClientGachaCache.DrawRecord result) {
        this.isRolling = true;
        this.finished = false;
        this.rewardConfirmed = false;  // 【新增】重置确认状态
        this.rollingItems.clear();

        // 1. 生成伪随机滚动序列 (总共约 50 个元素)
        List<GachaItem> pool = parent.getShopDef().getGachaPool().getItems();
        if (pool.isEmpty()) {
            return;
        }
        
        Random rand = new Random();
        for (int i = 0; i < 45; i++) {
            rollingItems.add(pool.get(rand.nextInt(pool.size())));
        }

        // 2. 插入最终奖励物品 (倒数第 5 个位置最居中)
        GachaItem targetItem = null;
        for (GachaItem item : pool) {
            if (item.getItemId().equals(result.itemId())) {
                targetItem = item; break;
            }
        }
        if (targetItem == null) {
            targetItem = pool.get(0); // Fallback
        }

        rollingItems.add(targetItem);
        for (int i = 0; i < 5; i++) rollingItems.add(pool.get(rand.nextInt(pool.size())));

        // 3. 计算目标位移 (保证目标物品恰好停在屏幕正中心，加入一点随机偏移以拟真)
        int targetIndex = 45;
        float centerScreenX = width / 2f;
        float itemCenterX = targetIndex * (ITEM_W + ITEM_GAP) + ITEM_W / 2f;
        float randomOffset = (rand.nextFloat() - 0.5f) * (ITEM_W - 10); // 别停得太死板

        this.currentX = 0f;
        this.targetX = itemCenterX - centerScreenX + randomOffset;
        this.velocity = 2000f; // 极高初速
        this.lastPassedIndex = -1; // 重置音效计数器
    }

    public void render(GuiGraphics g, int mx, int my, float dt) {
        if (!isRolling) return;

        // 复杂的阻尼运动模拟
        float distanceLeft = targetX - currentX;
        velocity = distanceLeft * 3.5f; // 动态弹簧阻尼
        if (velocity < 10f && distanceLeft < 2f) {
            velocity = 0f;
            currentX = targetX;
            finished = true;
        }
        currentX += velocity * dt;

        // 播放滚轮“哒哒”音效
        int passedIndex = (int)((currentX + width/2f) / (ITEM_W + ITEM_GAP));
        if (passedIndex != lastPassedIndex && velocity > 20f) {
            float pitch = 1.0f + (velocity / 2000f) * 0.5f; // 速度越快音调越高
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), pitch, 0.3f));
            lastPassedIndex = passedIndex;
        }

        // 渲染视口
        int viewY = height / 2 - ITEM_W / 2;
        g.fill(0, viewY - 10, width, viewY + ITEM_W + 10, 0xAA000000); // 横带底色
        g.fillGradient(0, viewY - 10, width, viewY + ITEM_W + 10, 0x00000000, 0x55FFFFFF); // 环境反光

        g.enableScissor(0, viewY - 10, width, viewY + ITEM_W + 10);

        for (int i = 0; i < rollingItems.size(); i++) {
            float drawX = i * (ITEM_W + ITEM_GAP) - currentX;
            if (drawX + ITEM_W < 0 || drawX > width) continue; // Culling

            GachaItem item = rollingItems.get(i);
            int themeC = parent.getShopDef().getEffectiveThemeColor(item);

            g.fill((int)drawX, viewY, (int)drawX + ITEM_W, viewY + ITEM_W, 0xFF111115);
            HudAnimUtil.drawFrame(g, (int)drawX, viewY, ITEM_W, ITEM_W, 2, 0xFF000000 | themeC);
            g.fillGradient((int)drawX, viewY, (int)drawX + ITEM_W, viewY + ITEM_W, HudAnimUtil.withAlpha(themeC, 60), 0);

            // 渲染大图标
            g.pose().pushPose();
            g.pose().translate(drawX + ITEM_W/2f - 16, viewY + ITEM_W/2f - 16, 0);
            g.pose().scale(2.0f, 2.0f, 1f);
            g.renderItem(item.getItemStack(), 0, 0);
            g.pose().popPose();
        }

        g.disableScissor();

        // 中心瞄准线指示器
        g.fill(width/2 - 2, viewY - 20, width/2 + 2, viewY + ITEM_W + 20, 0xDDFFDD33);
    }

    public boolean isFinished() { return finished; }
    
    /**
     * 【新增】关闭界面时确保奖励已确认（防止玩家ESC关闭导致奖励丢失）。
     */
    public void onScreenClose() {
        if (isRolling && !rewardConfirmed) {
            rewardConfirmed = true;
            ArcQuestNetwork.CHANNEL.sendToServer(new C2SConfirmDrawPacket(parent.getShopId()));
        }
    }
}