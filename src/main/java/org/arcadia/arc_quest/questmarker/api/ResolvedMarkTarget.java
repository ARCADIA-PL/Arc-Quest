package org.arcadia.arc_quest.questmarker.api;

public record ResolvedMarkTarget(
        double x,
        double y,
        double z,
        String dimension,
        int entityId,
        String entityUuid,
        String entityGuid,
        QuestMarkerData.EntityAttachPoint attachPoint
) {
    public ResolvedMarkTarget {
        dimension = dimension == null || dimension.isBlank() ? "minecraft:overworld" : dimension;
        entityUuid = entityUuid == null ? "" : entityUuid;
        entityGuid = entityGuid == null ? "" : entityGuid;
        attachPoint = attachPoint == null ? QuestMarkerData.EntityAttachPoint.HEAD : attachPoint;
    }

    public static ResolvedMarkTarget position(double x, double y, double z, String dimension) {
        return new ResolvedMarkTarget(x, y, z, dimension, -1, "", "", QuestMarkerData.EntityAttachPoint.HEAD);
    }
}
