package org.arcadia.arc_quest.client.hud.quest.arcmutil.toast;

import net.minecraft.Util;
import org.arcadia.arc_quest.client.hud.quest.toast.QuestToastManager;

public class ArcQuestToastViewModel {
    public final QuestToastManager.ToastType type;
    public final String text;
    public final String key;
    public final String subtitle;
    public final long startTime;
    public long adjustedStartTime;
    public long lastUpdateTime;
    public String cachedNameStr = "";
    public int cachedNameWidth = -1;

    public ArcQuestToastViewModel(QuestToastManager.ToastType type, String text, String key) {
        this.type = type;
        this.text = text;
        this.key = key;
        this.subtitle = type.getLocalizedPrefix();
        this.startTime = Util.getMillis();
        this.adjustedStartTime = this.startTime;
        this.lastUpdateTime = this.startTime;
    }

    public void tick(boolean frozen) {
        long now = Util.getMillis();
        long dt = now - lastUpdateTime;
        lastUpdateTime = now;
        if (frozen) adjustedStartTime += dt;
    }

    public long elapsed() {
        return Util.getMillis() - adjustedStartTime;
    }

    public boolean isExpired() {
        return elapsed() >= ArcQuestToastElement.ENTER + ArcQuestToastElement.HOLD + ArcQuestToastElement.EXIT;
    }
}
