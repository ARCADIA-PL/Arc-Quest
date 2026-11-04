package org.arcadia.arc_quest.quest.api;

import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record CollectionAtlasProfile(
        int schemaVersion,
        ResourceLocation id,
        Node node,
        Inspector inspector,
        Zoom zoom,
        Focus focus,
        Motion motion,
        InputDefaults inputDefaults) {
    public record Node(double size, int labelMaxLines) {}

    public record Inspector(
            double preferredWidthFraction,
            double minWidth,
            double maxWidth,
            double minCanvasInteractionWidth,
            List<String> sections) {
        public Inspector {
            sections = List.copyOf(sections);
        }
    }

    public record Zoom(
            double hardMin,
            double baselineMax,
            double hardMax,
            double fitMultiplier,
            double minOperableNode,
            double minOperableSpacing,
            double maxNodeSize,
            String initialView) {}

    public record Focus(
            double zoomMultiplier,
            double otherGroupAlpha,
            double otherGroupFavoriteAlpha,
            boolean restoreBrowseSnapshotOnClose) {}

    public record Motion(
            double dragThreshold,
            double velocitySampleMs,
            double maxVelocityPerMs,
            double inertiaDecayMs,
            double maxOverscroll,
            double settleDecayMs) {}

    public record InputDefaults(String wheelMode, boolean wasdNavigation, boolean reduceMotion) {}

    public CollectionAtlasProfile {
        if (schemaVersion != 1
                || id == null
                || node == null
                || inspector == null
                || zoom == null
                || focus == null
                || motion == null
                || inputDefaults == null)
            throw new IllegalArgumentException("Invalid atlas profile");
        double[] values = {
            node.size(),
            inspector.preferredWidthFraction(),
            inspector.minWidth(),
            inspector.maxWidth(),
            inspector.minCanvasInteractionWidth(),
            zoom.hardMin(),
            zoom.baselineMax(),
            zoom.hardMax(),
            zoom.fitMultiplier(),
            zoom.minOperableNode(),
            zoom.minOperableSpacing(),
            zoom.maxNodeSize(),
            focus.zoomMultiplier(),
            focus.otherGroupAlpha(),
            focus.otherGroupFavoriteAlpha(),
            motion.dragThreshold(),
            motion.velocitySampleMs(),
            motion.maxVelocityPerMs(),
            motion.inertiaDecayMs(),
            motion.maxOverscroll(),
            motion.settleDecayMs()
        };
        for (double v : values)
            if (!Double.isFinite(v) || v < 0)
                throw new IllegalArgumentException("Invalid profile number");
        if (node.size() < 8
                || node.size() > 128
                || node.labelMaxLines() < 1
                || node.labelMaxLines() > 8
                || zoom.hardMin() <= 0
                || zoom.hardMax() < zoom.hardMin()
                || zoom.hardMax() > 8
                || inspector.preferredWidthFraction() < 0.1
                || inspector.preferredWidthFraction() > 0.8
                || inspector.maxWidth() < inspector.minWidth())
            throw new IllegalArgumentException("Atlas profile bounds conflict");
    }

    public static CollectionAtlasProfile defaults() {
        return new CollectionAtlasProfile(
                1,
                ResourceLocation.parse("arc_quest:default"),
                new Node(28, 1),
                new Inspector(
                        .35,
                        176,
                        224,
                        72,
                        List.of("VARIANTS", "SUBJECT_TOOLTIP", "CONTENT", "SOURCES", "ACTIONS")),
                new Zoom(.38, 1.05, 2.4, .72, 10, 12, 53, "FIT_CONSTRAINED"),
                new Focus(1.04, .22, .46, true),
                new Motion(3, 90, 1.08, 120, 27, 55),
                new InputDefaults("ZOOM", true, false));
    }
}
