package org.arcadia.arc_quest.client;

import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.client.hud.dialogue.DialogueScreen;
import org.arcadia.arc_quest.client.hud.gacha.GachaResultRenderer;
import org.arcadia.arc_quest.client.hud.gacha.GachaScreen;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.arcadia.arc_quest.dialogue.network.ClientDialogueCache;
import org.arcadia.arc_quest.trade.gacha.network.ClientGachaCache;
import org.arcadia.arc_quest.trade.network.ClientTradeCache;

/** 客户端主线程收到新玩家会话快照后，旧 UI 不得再发送确认或恢复对话请求。 */
public final class ClientInteractionReset {
    private ClientInteractionReset() { }

    public static void onSessionChanged(long previousEpoch, long nextEpoch) {
        if (previousEpoch <= 0 || nextEpoch <= previousEpoch) return;
        GachaResultRenderer.INSTANCE.discardResult();
        ClientDialogueCache.INSTANCE.clear();
        org.arcadia.arc_quest.client.hud.dialogue.DialogueHistoryPanel.clear();
        ClientTradeCache.INSTANCE.clear();
        ClientGachaCache.INSTANCE.clear();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof DialogueScreen || minecraft.screen instanceof AbstractTradeScreen
                || minecraft.screen instanceof GachaScreen) {
            // setScreen 调用 removed 释放焦点，不调用带有确认/还原副作用的 onClose。
            minecraft.setScreen(null);
        }
    }
}
