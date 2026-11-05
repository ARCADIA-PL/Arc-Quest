package org.arcadia.arc_quest.integration.storage;

import com.wintercogs.beyonddimensions.api.dimensionnet.rule.ExtractPlan;
import com.wintercogs.beyonddimensions.api.dimensionnet.rule.UnifiedStorageRules;
import com.wintercogs.beyonddimensions.api.storage.key.impl.ItemStackKey;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.inventory.PlayerItemAccess;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import static org.arcadia.arc_quest.integration.storage.BeyondDimensionsGameTests.run;

/** The extraction-rule API only exists in the newer storage release. */
@PrefixGameTestTemplate(false)
public final class BeyondDimensionsRuleGameTests {
    @GameTest(template = "jei_empty", templateNamespace = Arc_Quest.MOD_ID, batch = "arc_quest.beyond_storage", timeoutTicks = 100)
    public static void extractionRulesCanRejectPaymentWithoutConsumingBackpack(GameTestHelper h) {
        run(h, f -> {
            var ruleId = ResourceLocation.parse("arc_quest:storage/test_reject");
            var key = new ItemStackKey(new ItemStack(Items.DIAMOND));
            f.put(new ItemStack(Items.DIAMOND), 4);
            f.player().getInventory().setItem(0, new ItemStack(Items.DIAMOND, 1));
            UnifiedStorageRules.extract(ruleId, key, request -> request.net() == f.net() ? ExtractPlan.reject() : ExtractPlan.allow());
            try {
                boolean rejected = false;
                try { ItemTradeOffer.cost(Items.DIAMOND, 3).execute(f.player()); }
                catch (IllegalStateException expected) { rejected = true; }
                h.assertTrue(rejected && f.amount(new ItemStack(Items.DIAMOND)) == 4
                        && f.player().getInventory().getItem(0).getCount() == 1, "Payment bypassed extraction rule");
            } finally { UnifiedStorageRules.removeExtract(ruleId); }
        });
    }

    @GameTest(template = "jei_empty", templateNamespace = Arc_Quest.MOD_ID, batch = "arc_quest.beyond_storage", timeoutTicks = 100)
    public static void failedTradeRefundsSecondaryResourcesChargedByExtractionRule(GameTestHelper h) {
        run(h, f -> {
            var ruleId = ResourceLocation.parse("arc_quest:storage/test_secondary_cost");
            var diamond = new ItemStackKey(new ItemStack(Items.DIAMOND));
            var emerald = new ItemStackKey(new ItemStack(Items.EMERALD));
            f.put(new ItemStack(Items.DIAMOND), 4); f.put(new ItemStack(Items.EMERALD), 9);
            UnifiedStorageRules.extract(ruleId, diamond, request -> request.net() == f.net()
                    ? ExtractPlan.allow().take(emerald, 2) : ExtractPlan.allow());
            try {
                var payment = PlayerItemAccess.remove(f.player(), stack -> stack.is(Items.DIAMOND), 2, false);
                h.assertTrue(f.amount(new ItemStack(Items.DIAMOND)) == 2 && f.amount(new ItemStack(Items.EMERALD)) < 9,
                        "Rule did not execute its secondary cost");
                payment.rollback();
                h.assertTrue(f.amount(new ItemStack(Items.DIAMOND)) == 4 && f.amount(new ItemStack(Items.EMERALD)) == 9,
                        "Refund lost extraction-rule secondary resources");
            } finally { UnifiedStorageRules.removeExtract(ruleId); }
        });
    }
}
