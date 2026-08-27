package org.arcadia.arc_quest.client.hud.dialogue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueScreenFormattingTest {

    @Test
    void typewriterPrefixKeepsNestedComponentStyle() {
        Component text = Component.translatableWithFallback(
                "arc_quest.test.dialogue_style", "%s",
                Component.literal("目标").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));

        FormattedCharSequence prefix = DialogueScreen.prefix(text.getVisualOrderText(), 2);
        Style[] styles = new Style[2];
        int[] codePoints = new int[2];
        int[] count = {0};
        assertTrue(prefix.accept((position, style, codePoint) -> {
            if (count[0] < styles.length) {
                styles[count[0]] = style;
                codePoints[count[0]] = codePoint;
            }
            count[0]++;
            return true;
        }));

        assertEquals(2, count[0]);
        assertEquals('目', codePoints[0]);
        assertEquals('标', codePoints[1]);
        assertTrue(styles[0].isBold());
        assertEquals(ChatFormatting.LIGHT_PURPLE.getColor(), styles[0].getColor().getValue());
        assertEquals(styles[0], styles[1]);
    }

    @Test
    void wrappedLineNormalizationAcceptsLegacyStrings() {
        List<FormattedCharSequence> lines = DialogueScreen.normalizeWrappedLines(
                List.of("Legacy dialogue line"));

        assertEquals(1, lines.size());
        assertEquals("Legacy dialogue line", plainText(lines.get(0)));
    }

    @Test
    void wrappedLineNormalizationKeepsFormattedStyles() {
        FormattedCharSequence styled = Component.literal("Styled")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .getVisualOrderText();

        FormattedCharSequence normalized = DialogueScreen.normalizeWrappedLines(List.of(styled)).get(0);
        Style[] firstStyle = new Style[1];
        normalized.accept((position, style, codePoint) -> {
            firstStyle[0] = style;
            return false;
        });

        assertTrue(firstStyle[0].isBold());
        assertEquals(ChatFormatting.AQUA.getColor(), firstStyle[0].getColor().getValue());
    }

    private static String plainText(FormattedCharSequence sequence) {
        StringBuilder text = new StringBuilder();
        sequence.accept((position, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }
}
