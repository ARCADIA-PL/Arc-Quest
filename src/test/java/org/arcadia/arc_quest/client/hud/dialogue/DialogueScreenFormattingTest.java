package org.arcadia.arc_quest.client.hud.dialogue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

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
}
