package org.arcadia.arc_quest.integration.storage;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.fml.ModList;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.inventory.PlayerItemAccess;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;

@PrefixGameTestTemplate(false)
public final class WithoutBeyondDimensionsGameTests {
    @GameTest(template = "jei_empty", templateNamespace = Arc_Quest.MOD_ID, batch = "arc_quest.without_beyond", timeoutTicks = 100)
    public static void optionalStorageAbsentKeepsBackpackPaymentsWorking(GameTestHelper h) {
        h.assertTrue(!ModList.get().isLoaded("beyonddimensions"), "Absent-mod test unexpectedly loaded storage");
        var level = h.getLevel();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "ArcQNoStorage"));
        try {
            player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 5));
            h.assertTrue(PlayerItemAccess.externalIdentity(player).isEmpty(), "Unexpected external source");
            ItemTradeOffer.cost(Items.DIAMOND, 3).execute(player);
            h.assertTrue(player.getInventory().getItem(0).getCount() == 2, "Backpack cost failed without optional mod");
        } finally { player.invalidateCaps(); }
        h.succeed();
    }
}
