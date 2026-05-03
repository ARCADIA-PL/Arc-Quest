package org.arcadia.arc_quest.mutil.animation;

import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

public interface ArcAnimation {
    void start(ArcGuiElement element, ArcGuiContext context);

    void update(ArcGuiElement element, ArcGuiContext context);

    boolean isFinished();

    void finish(ArcGuiElement element, ArcGuiContext context);
}
