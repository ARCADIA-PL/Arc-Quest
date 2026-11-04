package org.arcadia.arc_quest.quest.spec;

import java.util.*;

/** Mutable authoring DTOs; immutable runtime records are created only by the compiler. */
public class CollectionAtlasProfileSpecData {
    public int schemaVersion = 1;
    public String id = "arc_quest:default";
    public Node node = new Node();
    public Inspector inspector = new Inspector();
    public Zoom zoom = new Zoom();
    public Focus focus = new Focus();
    public Motion motion = new Motion();
    public InputDefaults inputDefaults = new InputDefaults();

    public static class Node {
        public double size = 28;
        public int labelMaxLines = 1;
    }

    public static class Inspector {
        public double preferredWidthFraction = .35,
                minWidth = 176,
                maxWidth = 224,
                minCanvasInteractionWidth = 72;
        public List<String> sections =
                new ArrayList<>(
                        List.of("VARIANTS", "SUBJECT_TOOLTIP", "CONTENT", "SOURCES", "ACTIONS"));
    }

    public static class Zoom {
        public double hardMin = .38,
                baselineMax = 1.05,
                hardMax = 2.4,
                fitMultiplier = .72,
                minOperableNode = 10,
                minOperableSpacing = 12,
                maxNodeSize = 53;
        public String initialView = "FIT_CONSTRAINED";
    }

    public static class Focus {
        public double zoomMultiplier = 1.04, otherGroupAlpha = .22, otherGroupFavoriteAlpha = .46;
        public boolean restoreBrowseSnapshotOnClose = true;
    }

    public static class Motion {
        public double dragThreshold = 3,
                velocitySampleMs = 90,
                maxVelocityPerMs = 1.08,
                inertiaDecayMs = 120,
                maxOverscroll = 27,
                settleDecayMs = 55;
    }

    public static class InputDefaults {
        public String wheelMode = "ZOOM";
        public boolean wasdNavigation = true, reduceMotion = false;
    }
}
