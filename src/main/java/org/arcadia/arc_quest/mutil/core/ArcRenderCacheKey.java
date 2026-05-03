package org.arcadia.arc_quest.mutil.core;

import java.util.Objects;

public class ArcRenderCacheKey {
    private long signature = Long.MIN_VALUE;
    private int width = -1;
    private int height = -1;

    public boolean matches(long signature, int width, int height) {
        return this.signature == signature && this.width == width && this.height == height;
    }

    public void update(long signature, int width, int height) {
        this.signature = signature;
        this.width = width;
        this.height = height;
    }

    public void invalidate() {
        signature = Long.MIN_VALUE;
        width = -1;
        height = -1;
    }

    public static long signature(Object... values) {
        return Objects.hash(values);
    }
}
