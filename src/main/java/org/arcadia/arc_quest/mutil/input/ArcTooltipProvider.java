package org.arcadia.arc_quest.mutil.input;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.util.List;

public interface ArcTooltipProvider {
    @Nullable
    List<Component> getTooltipLines();
}
