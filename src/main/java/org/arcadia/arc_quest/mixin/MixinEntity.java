package org.arcadia.arc_quest.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class MixinEntity {

    @Inject(method = "load", at = @At("TAIL"))
    private void arcQuest$captureDialogueTags(CompoundTag tag, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        CompoundTag persistentData = self.getPersistentData();

        if (tag.contains("ArcQuestDialogueId") && !persistentData.contains("ArcQuestDialogueId")) {
            persistentData.putString("ArcQuestDialogueId", tag.getString("ArcQuestDialogueId"));
        }
        if (tag.contains("ArcQuestNpcId") && !persistentData.contains("ArcQuestNpcId")) {
            persistentData.putString("ArcQuestNpcId", tag.getString("ArcQuestNpcId"));
        }
    }
}