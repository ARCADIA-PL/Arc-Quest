package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;

import java.util.List;

/** Assertions compare captured pixels; screenshots alone never count as a visual pass. */
final class ObjectiveIconRenderRegression {
    private double opaqueEnergy;
    private List<ObjectiveIconPixelAudit.Sample> parentClean, parentHighZ, modalClean;

    void nativeOpaque(NativeImage image, ObjectiveIconAuditJournal screen) {
        opaqueEnergy = iconEnergy(image, screen);
        ObjectiveIconClientAudit.check(opaqueEnergy > 2, "Native journal baseline icon has no visible pixels");
        parentClean = modalSamples(image, screen);
        ObjectiveIconClientAudit.LOG.info("{} NATIVE_ICON_OPAQUE energy={} rect={}",
                ObjectiveIconClientAudit.MARKER, opaqueEnergy, screen.iconRect());
    }
    void nativeClosing(NativeImage image, ObjectiveIconAuditJournal screen) {
        ObjectiveIconClientAudit.check(screen.closing() && Math.abs(screen.getEffectiveAlpha() - .5) < .001,
                "Native journal screenshot is not an actual half-opacity closing frame");
        double faded = iconEnergy(image, screen), ratio = faded / opaqueEnergy;
        ObjectiveIconClientAudit.LOG.info("{} NATIVE_ICON_CLOSING energy={} ratio={} rect={}",
                ObjectiveIconClientAudit.MARKER, faded, ratio, screen.iconRect());
        ObjectiveIconClientAudit.check(ratio > .18 && ratio < .82,
                "Native objective row did not pass closing alpha to its icon: ratio=" + ratio);
    }
    private static double iconEnergy(NativeImage image, ObjectiveIconAuditJournal screen) {
        var icon = screen.iconRect();
        var sample = ObjectiveIconPixelAudit.sample(image, screen.width, screen.height, icon);
        var background = ObjectiveIconPixelAudit.sample(image, screen.width, screen.height,
                new ObjectiveIconPixelAudit.Rect(icon.x() - 4 * screen.getUiScale(), icon.y() + 4 * screen.getUiScale(),
                        screen.getUiScale(), screen.getUiScale()));
        return sample.energy(background.pixels()[0]);
    }
    void parentHighZ(NativeImage image, ObjectiveIconAuditJournal screen) { parentHighZ = modalSamples(image, screen); }
    void modalClean(NativeImage image, ObjectiveIconAuditJournal screen) { modalClean = modalSamples(image, screen); }
    void modalHighZ(NativeImage image, ObjectiveIconAuditJournal screen) {
        ObjectiveIconClientAudit.check(parentClean != null && parentHighZ != null && modalClean != null,
                "Modal pixel comparisons lack a control capture");
        var stressed = modalSamples(image, screen);
        String[] names = {"panel body", "slider", "action buttons"};
        for (int region = 0; region < names.length; region++) {
            int[] base = parentClean.get(region).pixels(), control = parentHighZ.get(region).pixels();
            int[] clean = modalClean.get(region).pixels(), actual = stressed.get(region).pixels();
            ObjectiveIconClientAudit.check(base.length == control.length && clean.length == actual.length
                    && base.length == clean.length, "Modal comparison dimensions changed");
            int hits = 0, leaks = 0;
            double sum = 0, maximum = 0;
            for (int pixel = 0; pixel < base.length; pixel++) {
                // Inspect only locations where the actual high-Z parent draw changed the control.
                if (ObjectiveIconPixelAudit.difference(base[pixel], control[pixel]) < 40) continue;
                hits++;
                double difference = ObjectiveIconPixelAudit.difference(clean[pixel], actual[pixel]);
                sum += difference;
                maximum = Math.max(maximum, difference);
                if (difference > 22) leaks++;
            }
            ObjectiveIconClientAudit.LOG.info("{} MODAL_PIXELS region={} stressPixels={} mean={} max={} leaked={}",
                    ObjectiveIconClientAudit.MARKER, names[region], hits, sum / Math.max(1, hits), maximum, leaks);
            ObjectiveIconClientAudit.check(hits >= 8, "High-Z parent control did not reach " + names[region]);
            // The panel is deliberately translucent: small source-over differences are correct.
            ObjectiveIconClientAudit.check(sum / hits < 10 && maximum < 38 && leaks <= hits / 50,
                    "Parent high-Z text/items leak through modal " + names[region]);
        }
    }
    private static List<ObjectiveIconPixelAudit.Sample> modalSamples(NativeImage image, ObjectiveIconAuditJournal screen) {
        return screen.modalProbeRects().stream()
                .map(rect -> ObjectiveIconPixelAudit.sample(image, screen.width, screen.height, rect)).toList();
    }
}
