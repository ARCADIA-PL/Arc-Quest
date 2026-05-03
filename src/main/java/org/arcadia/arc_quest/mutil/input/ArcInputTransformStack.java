package org.arcadia.arc_quest.mutil.input;

import java.util.ArrayDeque;
import java.util.Deque;

public class ArcInputTransformStack {
    private final Deque<Transform> stack = new ArrayDeque<>();
    private Transform current = new Transform(0, 0, 1f, 1f);

    public void push(float translateX, float translateY, float scaleX, float scaleY) {
        stack.push(current);
        current = new Transform(
                current.translateX + translateX * current.scaleX,
                current.translateY + translateY * current.scaleY,
                current.scaleX * scaleX,
                current.scaleY * scaleY
        );
    }

    public void pop() {
        if (!stack.isEmpty()) current = stack.pop();
    }

    public double toLocalX(double globalX) {
        return (globalX - current.translateX) / current.scaleX;
    }

    public double toLocalY(double globalY) {
        return (globalY - current.translateY) / current.scaleY;
    }

    public void clear() {
        stack.clear();
        current = new Transform(0, 0, 1f, 1f);
    }

    private record Transform(float translateX, float translateY, float scaleX, float scaleY) {
    }
}
