package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.StringDecomposer;
import org.arcadia.arc_quest.client.events.ClientEventHandler;

import java.util.Optional;

public final class GuideClientTextResolver {

    private GuideClientTextResolver() {
    }

    public static Component resolve(Component component) {
        if (component == null) return Component.empty();
        if (component.getContents() instanceof TranslatableContents contents) {
            String key = contents.getKey();
            if ("guide.arc_quest.journal_basics.page_1".equals(key)) {
                component = Component.translatable(key, highlightedKey(ClientEventHandler.KEY_OPEN_JOURNAL));
            } else if ("guide.arc_quest.tracking_menu_basics.page_1".equals(key)) {
                component = Component.translatable(key, highlightedKey(ClientEventHandler.KEY_OPEN_TRACKING_MENU));
            }
        }
        return expandFormatting(component);
    }

    /**
     * Resolve legacy formatting before wrapping. Otherwise a line starting inside a bold
     * run makes a later reset inherit that line's bold style, widening normal body text.
     * Native component arguments (including real key bindings) keep their own styles.
     */
    private static Component expandFormatting(Component component) {
        if (component.getString().indexOf('\u00a7') < 0) return component;
        MutableComponent result = Component.empty();
        component.visit((style, text) -> {
            if (text.indexOf('\u00a7') < 0) {
                result.append(Component.literal(text).setStyle(style));
                return Optional.<Void>empty();
            }
            StringBuilder run = new StringBuilder();
            Style[] runStyle = {style};
            StringDecomposer.iterateFormatted(text, style, (index, glyphStyle, codepoint) -> {
                if (!glyphStyle.equals(runStyle[0])) {
                    if (!run.isEmpty()) result.append(Component.literal(run.toString()).setStyle(runStyle[0]));
                    run.setLength(0);
                    runStyle[0] = glyphStyle;
                }
                run.appendCodePoint(codepoint);
                return true;
            });
            if (!run.isEmpty()) result.append(Component.literal(run.toString()).setStyle(runStyle[0]));
            return Optional.<Void>empty();
        }, Style.EMPTY);
        return result;
    }

    private static Component highlightedKey(KeyMapping mapping) {
        if (mapping == null) return Component.literal("?");
        return mapping.getTranslatedKeyMessage()
                .copy()
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true));
    }
}
