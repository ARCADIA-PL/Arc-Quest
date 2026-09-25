package org.arcadia.arc_quest.trade.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.arcadia.arc_quest.trade.offer.CommandTradeOffer;
import org.arcadia.arc_quest.trade.offer.CompositeTradeOffer;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TradeOfferUpdateSignatureTest {
    @Test
    void dynamicPriceUsesCurrentCountInsteadOfPreview() {
        int[] count = {4};
        var tag = signatureTag();
        var offer = new ItemTradeOffer(tag, ignored -> count[0], 1, true, null);
        String first = offer.getUpdateSignature(null);
        count[0] = 6;
        assertNotEquals(first, offer.getUpdateSignature(null));
        assertEquals(1, offer.getDisplayAmount());
    }

    @Test
    void equivalentRebuiltOffersHaveStableSignatures() {
        var tag = signatureTag();
        var first = new ItemTradeOffer(tag, 4, true, null);
        var second = new ItemTradeOffer(tag, 4, true, null);
        assertEquals(first.getUpdateSignature(null), second.getUpdateSignature(null));
    }

    @Test
    void commandAndCompositeChangesAreDetectedWithSameDisplayText() {
        Component label = Component.literal("Gift");
        var first = new CommandTradeOffer("give {player} apple 1", label);
        var second = new CommandTradeOffer("give {player} apple 2", label);
        assertNotEquals(first.getUpdateSignature(null), second.getUpdateSignature(null));
        var bundleOne = new CompositeTradeOffer(List.of(first), label);
        var bundleTwo = new CompositeTradeOffer(List.of(second), label);
        assertNotEquals(bundleOne.getUpdateSignature(null), bundleTwo.getUpdateSignature(null));
    }

    private static TagKey<Item> signatureTag() {
        // 签名只使用标签 ID，不做注册表查找；保持此测试独立于 Forge 启动器和事件总线转换。
        return new TagKey<>(null, ResourceLocation.fromNamespaceAndPath("minecraft", "planks"));
    }
}
