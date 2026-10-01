package org.arcadia.arc_quest.trade.gacha.api;

import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.dialogue.api.CooldownType;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.trade.api.TradeText;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GachaNamedPityVisibilityTest {
    @Test void namedPityCannotSelectAHiddenOutcome() {
        var result = shop(false).performDraw(null, null, 10);
        assertTrue(result.pityTriggered());
        assertNull(result.item(), "No result follows the existing rejection path before payment");
    }
    @Test void namedPityStillSelectsVisibleZeroWeightOutcome() {
        var result = shop(true).performDraw(null, null, 10);
        assertTrue(result.pityTriggered());
        assertEquals("named", result.item().getItemId());
        assertEquals(0, result.item().getBaseWeight());
    }
    @SuppressWarnings("deprecation")
    private static GachaShopDefinition shop(boolean visible) {
        var item = new GachaItem("named", null, null, 0, GachaItem.Rarity.RARE, true) {
            @Override public boolean isVisible(ServerPlayer player, ArcQuestPlayer data) { return visible; }
        };
        return new GachaShopDefinition("test:pity", TradeText.literal("Pity"), null, List.of(),
                new LinkedHashMap<>(), null, false, -1, null, null, new GachaPool(List.of(item)),
                List.of(), CooldownType.NONE, 0, 0, null, -1, null, true, true,
                new PityConfig(10, "named", true), null, null, null, null);
    }
}
