package org.arcadia.arc_quest.client.hud.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GuideClientTextResolverTest {
    @Test
    void plainComponentsKeepTheirIdentityAndNullIsEmpty() {
        Component plain = Component.literal("Normal body").withStyle(ChatFormatting.ITALIC);
        assertSame(plain, GuideClientTextResolver.resolve(plain));
        assertEquals("", GuideClientTextResolver.resolve(null).getString());
    }

    @Test
    void legacyResetRestoresTheContainingStyleAndNativeArgumentsKeepTheirStyles() {
        Component nativeKey = Component.literal("Shift").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        Component source = Component.translatableWithFallback("test.guide.style", "§c§lWarning§r body %s", nativeKey)
                .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC);
        var result = GuideClientTextResolver.resolve(source);
        List<Style> styles = new ArrayList<>();
        List<String> text = new ArrayList<>();
        result.visit((style, part) -> {
            styles.add(style);
            text.add(part);
            return Optional.<Void>empty();
        }, Style.EMPTY);
        assertEquals("Warning body Shift", result.getString());
        Style warning = styles.get(text.indexOf("Warning"));
        Style body = styles.get(text.indexOf(" body "));
        Style key = styles.get(text.indexOf("Shift"));
        assertTrue(warning.isBold());
        assertEquals(ChatFormatting.RED.getColor(), warning.getColor().getValue());
        assertFalse(body.isBold());
        assertTrue(body.isItalic());
        assertEquals(ChatFormatting.AQUA.getColor(), body.getColor().getValue());
        assertTrue(key.isBold());
        assertEquals(ChatFormatting.YELLOW.getColor(), key.getColor().getValue());
    }

    @Test
    void wrappingInsideEmphasisDoesNotMakeTheFollowingBodyBoldOrOverflow() {
        Component source = Component.literal("§a§l" + "A".repeat(24) + "§r" + "b".repeat(40));
        var result = GuideClientTextResolver.resolve(source);
        var splitter = new StringSplitter((codepoint, style) -> style.isBold() ? 10 : 9);
        for (var line : splitter.splitLines(result, 50, Style.EMPTY)) {
            assertTrue(splitter.stringWidth(line) <= 50, "A wrapped styled line exceeded its limit");
            line.visit((style, part) -> {
                if (part.contains("b")) assertFalse(style.isBold(), "Normal body inherited the line's emphasis");
                assertFalse(part.contains("§"));
                return Optional.<Void>empty();
            }, Style.EMPTY);
        }
        assertEquals("A".repeat(24) + "b".repeat(40), result.getString());
    }
}
