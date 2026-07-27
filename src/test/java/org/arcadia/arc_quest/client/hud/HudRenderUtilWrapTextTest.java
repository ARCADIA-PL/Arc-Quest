package org.arcadia.arc_quest.client.hud;

import net.minecraft.client.StringSplitter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudRenderUtilWrapTextTest {

    private static final StringSplitter MONOSPACE_SPLITTER = new StringSplitter((codePoint, style) -> 1f);

    @Test
    void wrapsContinuousChineseByMeasuredWidth() {
        String text = "钻石是个好东西可以用来测试";

        List<String> lines = HudRenderUtil.wrapText(text, 5, MONOSPACE_SPLITTER);

        assertEquals(text, String.join("", lines));
        assertTrue(lines.size() > 1);
        assertTrue(lines.stream().allMatch(line -> line.codePointCount(0, line.length()) <= 5));
    }

    @Test
    void wrapsLongWordWithoutSpaces() {
        String text = "abcdefghijklmno";

        List<String> lines = HudRenderUtil.wrapText(text, 4, MONOSPACE_SPLITTER);

        assertEquals(text, String.join("", lines));
        assertTrue(lines.stream().allMatch(line -> line.length() <= 4));
    }
}
