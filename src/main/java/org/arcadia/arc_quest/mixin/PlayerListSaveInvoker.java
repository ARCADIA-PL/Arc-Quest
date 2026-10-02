package org.arcadia.arc_quest.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.level.storage.PlayerDataStorage;

/** Save only the rewarded player, including vanilla inventory and loader attachments. */
@Mixin(PlayerList.class)
public interface PlayerListSaveInvoker {
    @Invoker("save") void arcq$savePlayer(ServerPlayer player);
    @Accessor("playerIo") PlayerDataStorage arcq$playerStorage();
}
