package org.arcadia.arc_quest.client.hud.questmarker;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务标记投影的相机基向量扩展点。
 * 例如骑乘实体可以提供自己的 forward、left 和 up 向量。
 */
public final class MarkerCameraBasisRegistry {
    private static final List<Provider> PROVIDERS = new CopyOnWriteArrayList<>();

    private MarkerCameraBasisRegistry() {
    }

    public static void register(Provider provider) {
        if (provider != null) PROVIDERS.add(provider);
    }

    public static Basis resolve(Camera camera, Basis fallback) {
        for (Provider provider : PROVIDERS) {
            if (provider.applies(camera)) {
                Basis basis = provider.resolve(camera, fallback);
                if (basis != null) return basis.normalized(fallback);
            }
        }
        return fallback;
    }

    public record Basis(Vec3 forward, Vec3 left, Vec3 up) {
        public Basis normalized(Basis fallback) {
            Vec3 safeForward = normalizeOr(forward, fallback.forward());
            Vec3 safeLeft = normalizeOr(left, fallback.left());
            Vec3 safeUp = normalizeOr(up, fallback.up());
            return new Basis(safeForward, safeLeft, safeUp);
        }

        private static Vec3 normalizeOr(Vec3 value, Vec3 fallback) {
            if (value == null || value.lengthSqr() < 1.0E-6D) return fallback;
            return value.normalize();
        }
    }

    @FunctionalInterface
    public interface Provider {
        boolean applies(Camera camera);

        default Basis resolve(Camera camera, Basis fallback) {
            return fallback;
        }
    }
}