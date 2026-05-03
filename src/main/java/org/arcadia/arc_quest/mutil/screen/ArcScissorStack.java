package org.arcadia.arc_quest.mutil.screen;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.perf.ArcGuiProfiler;

import java.util.ArrayDeque;
import java.util.Deque;

public class ArcScissorStack {
    private final Deque<Rect> stack = new ArrayDeque<>();

    public void push(GuiGraphics graphics, int x, int y, int width, int height) {
        Rect next = new Rect(x, y, x + Math.max(0, width), y + Math.max(0, height));
        if (!stack.isEmpty()) next = next.intersect(stack.peek());
        stack.push(next);
        graphics.enableScissor(next.x1, next.y1, next.x2, next.y2);
        ArcGuiProfiler.scissorPushed();
    }

    public void pop(GuiGraphics graphics) {
        if (stack.isEmpty()) return;
        stack.pop();
        ArcGuiProfiler.scissorPopped();
        if (stack.isEmpty()) {
            graphics.disableScissor();
        } else {
            Rect current = stack.peek();
            graphics.enableScissor(current.x1, current.y1, current.x2, current.y2);
        }
    }

    public void clear(GuiGraphics graphics) {
        if (!stack.isEmpty()) {
            stack.clear();
            graphics.disableScissor();
        }
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    private record Rect(int x1, int y1, int x2, int y2) {
        Rect intersect(Rect other) {
            return new Rect(
                    Math.max(x1, other.x1),
                    Math.max(y1, other.y1),
                    Math.min(x2, other.x2),
                    Math.min(y2, other.y2)
            );
        }
    }
}
