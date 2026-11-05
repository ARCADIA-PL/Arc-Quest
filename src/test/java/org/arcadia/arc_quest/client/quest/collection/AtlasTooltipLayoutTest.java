package org.arcadia.arc_quest.client.quest.collection;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class AtlasTooltipLayoutTest {
    private static AtlasTooltipLayout.Part<String> text(String value) {
        return new AtlasTooltipLayout.Part<>(10, value, true, value);
    }
    @Test void collapsesOnlyEmptySpacingWithoutDeletingInformation() {
        var blocks = AtlasTooltipLayout.blocks(List.of(text(""), text("Lore"), text(""), text(""), text("Stats"), text("")));
        assertEquals(List.of("Lore", "", "Stats"), blocks.stream().map(b -> b.content().text()).toList());
        assertEquals(23, blocks.stream().mapToInt(AtlasPageLayout.Block::height).sum());
    }
    @Test void attributeHeadingAndRowsMoveAsOneSection() {
        var blocks = AtlasTooltipLayout.blocks(List.of(text("Lore"), text(""), text("When in main hand:"), text("Attack damage"), text("Attack speed")));
        var pages = AtlasPageLayout.paginate(blocks, 35);
        assertEquals(2, pages.size());
        assertEquals(List.of("When in main hand:", "Attack damage", "Attack speed"), pages.get(1).stream().map(s -> s.content().text()).toList());
    }
    @Test void nativeImagesStayWithTheirHandednessHeading() {
        var image = new AtlasTooltipLayout.Part<>(16, "", false, "Native Epic Fight stats image");
        var blocks = AtlasTooltipLayout.blocks(List.of(text("Description"), text("One Handed"), image));
        var pages = AtlasPageLayout.paginate(blocks, 30);
        assertEquals(2, pages.size());
        assertEquals("One Handed", pages.get(1).get(0).content().text());
        assertEquals(image, pages.get(1).get(1).content());
    }
    @Test void longParagraphKeepsFinalPairAndPreservesEveryLine() {
        var blocks = AtlasTooltipLayout.blocks(List.of(text("1"), text("2"), text("3"), text("4"), text("5")));
        var pages = AtlasPageLayout.paginate(blocks, 40);
        assertEquals(3, pages.get(0).size());
        assertEquals(List.of("4", "5"), pages.get(1).stream().map(s -> s.content().text()).toList());
        assertEquals(50, pages.stream().flatMap(List::stream).mapToInt(AtlasPageLayout.Slice::height).sum());
    }
}
