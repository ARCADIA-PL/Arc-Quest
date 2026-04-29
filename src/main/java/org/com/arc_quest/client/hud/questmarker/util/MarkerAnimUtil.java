package org.com.arc_quest.client.hud.questmarker.util;

public final class MarkerAnimUtil {

    private MarkerAnimUtil() {
    }

    public static float sinePulse(long nowMillis, float speed, float min, float max) {
        float t = (nowMillis / 1000.0f) * speed;
        float s = (float) ((Math.sin(t) + 1.0) * 0.5);
        return min + (max - min) * s;
    }
}