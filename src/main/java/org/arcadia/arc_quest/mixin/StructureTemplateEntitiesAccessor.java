package org.arcadia.arc_quest.mixin;

import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Read the small authored entity list without serializing the template's block palette. */
@Mixin(StructureTemplate.class)
public interface StructureTemplateEntitiesAccessor {
    @Accessor("entityInfoList")
    List<StructureTemplate.StructureEntityInfo> arcq$entities();
}
