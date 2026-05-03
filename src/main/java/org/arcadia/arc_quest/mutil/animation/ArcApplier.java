package org.arcadia.arc_quest.mutil.animation;

import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiElement;

import java.util.function.Consumer;

public final class ArcApplier {
    private ArcApplier() {
    }

    public static Consumer<Float> opacity(ArcGuiElement element) {
        return element::setOpacity;
    }

    public static Consumer<Float> x(ArcGuiElement element) {
        return value -> element.setX(Math.round(value));
    }

    public static Consumer<Float> y(ArcGuiElement element) {
        return value -> element.setY(Math.round(value));
    }

    public static Consumer<Float> width(ArcGuiElement element) {
        return value -> element.setWidth(Math.round(value));
    }

    public static Consumer<Float> height(ArcGuiElement element) {
        return value -> element.setHeight(Math.round(value));
    }

    public static void applyIfVisible(ArcGuiElement element, ArcGuiContext context, ArcAnimation animation) {
        if (element.isVisible()) {
            animation.update(element, context);
        }
    }
}
