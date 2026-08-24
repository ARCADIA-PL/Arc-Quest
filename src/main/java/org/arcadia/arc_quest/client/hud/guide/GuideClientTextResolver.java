package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.arcadia.arc_quest.client.events.ClientEventHandler;

public final class GuideClientTextResolver {

    private GuideClientTextResolver() {
    }

    public static Component resolve(Component component) {
        if (component == null || !(component.getContents() instanceof TranslatableContents contents)) {
            return component == null ? Component.empty() : component;
        }

        String key = contents.getKey();
        if ("guide.arc_quest.journal_basics.page_1".equals(key)) {
            return Component.translatable(key, highlightedKey(ClientEventHandler.KEY_OPEN_JOURNAL));
        }
        if ("guide.arc_quest.tracking_menu_basics.page_1".equals(key)) {
            return Component.translatable(key, highlightedKey(ClientEventHandler.KEY_OPEN_TRACKING_MENU));
        }
        return component;
    }

    private static Component highlightedKey(KeyMapping mapping) {
        if (mapping == null) return Component.literal("?");
        return mapping.getTranslatedKeyMessage()
                .copy()
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));
    }
}