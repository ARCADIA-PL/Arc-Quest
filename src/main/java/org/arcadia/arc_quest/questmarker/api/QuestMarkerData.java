package org.arcadia.arc_quest.questmarker.api;

import org.arcadia.arc_quest.quest.api.QuestState;

/**
 * 任务标记数据模型（API 层）。
 * 支持直接绑定 Quest 运行时语义（questId / phaseId / objectiveIndex）。
 */
public class QuestMarkerData {

    private final String id;
    private final double worldX;
    private final double worldY;
    private final double worldZ;
    private final String label;
    private final String dimension;
    private final String questId;
    private final String phaseId;
    private final int objectiveIndex;
    /**
     * 客户端实体ID，<0 表示非实体跟随。
     */
    private final int followEntityId;
    private final String followEntityUuid;
    private final String followEntityGuid;
    private final EntityAttachPoint attachPoint;
    private final int colorARGB;
    private final QuestMarkerType type;
    private final QuestMarkerState state;
    private final boolean showDistance;
    private final boolean allowOffscreenArrow;

    private QuestMarkerData(Builder builder) {
        this.id = builder.id;
        this.worldX = builder.worldX;
        this.worldY = builder.worldY;
        this.worldZ = builder.worldZ;
        this.label = builder.label;
        this.dimension = builder.dimension;

        this.questId = builder.questId;
        this.phaseId = builder.phaseId;
        this.objectiveIndex = builder.objectiveIndex;

        this.followEntityId = builder.followEntityId;
        this.followEntityUuid = builder.followEntityUuid;
        this.followEntityGuid = builder.followEntityGuid;
        this.attachPoint = builder.attachPoint;

        this.colorARGB = builder.colorARGB;
        this.type = builder.type.canonical();
        this.state = builder.state;
        this.showDistance = builder.showDistance;
        this.allowOffscreenArrow = builder.allowOffscreenArrow;
    }

    public static QuestMarkerData mainQuest(String id, double x, double y, double z, String label) {
        return new Builder(id, x, y, z, label).type(QuestMarkerType.QUEST_MAIN).color(0xFFFFD700).build();
    }

    public static QuestMarkerData sideQuest(String id, double x, double y, double z, String label) {
        return new Builder(id, x, y, z, label).type(QuestMarkerType.QUEST_SIDE).color(0xFF00BFFF).build();
    }

    public static QuestMarkerData npc(String id, double x, double y, double z, String label) {
        return new Builder(id, x, y, z, label).type(QuestMarkerType.NPC_INTERACT).color(0xFF44FF88).build();
    }

    public static QuestMarkerData enemy(String id, double x, double y, double z, String label) {
        return new Builder(id, x, y, z, label).type(QuestMarkerType.ENEMY_TARGET).color(0xFFFF4444).build();
    }

    public static QuestMarkerData location(String id, double x, double y, double z, String label) {
        return new Builder(id, x, y, z, label).type(QuestMarkerType.LOCATION).color(0xFFAAAAAA).build();
    }

    public static QuestMarkerData questObjective(
            String id,
            String questId,
            String phaseId,
            int objectiveIndex,
            double x,
            double y,
            double z,
            String label) {
        return new Builder(id, x, y, z, label)
                .bindQuest(questId)
                .bindPhase(phaseId)
                .bindObjective(objectiveIndex)
                .type(QuestMarkerType.QUEST_OBJECTIVE)
                .state(QuestMarkerState.ACTIVE)
                .color(0xFFFFD700)
                .build();
    }

    public String getId() {
        return id;
    }

    public double getWorldX() {
        return worldX;
    }

    public double getWorldY() {
        return worldY;
    }

    public double getWorldZ() {
        return worldZ;
    }

    public String getLabel() {
        return label;
    }

    public String getDimension() {
        return dimension;
    }

    public String getQuestId() {
        return questId;
    }

    public String getPhaseId() {
        return phaseId;
    }

    public int getObjectiveIndex() {
        return objectiveIndex;
    }

    public int getFollowEntityId() {
        return followEntityId;
    }

    public String getFollowEntityUuid() {
        return followEntityUuid;
    }

    public String getFollowEntityGuid() {
        return followEntityGuid;
    }

    public EntityAttachPoint getAttachPoint() {
        return attachPoint;
    }

    public int getColorARGB() {
        return colorARGB;
    }

    public QuestMarkerType getType() {
        return type;
    }

    public QuestMarkerState getState() {
        return state;
    }

    public boolean isShowDistance() {
        return showDistance;
    }

    public boolean isAllowOffscreenArrow() {
        return allowOffscreenArrow;
    }

    public boolean hasQuestBinding() {
        return questId != null && !questId.isEmpty();
    }

    public boolean hasPhaseBinding() {
        return phaseId != null && !phaseId.isEmpty();
    }

    public boolean hasObjectiveBinding() {
        return objectiveIndex >= 0;
    }

    public boolean hasEntityBinding() {
        return followEntityId >= 0 || hasEntityUuidBinding() || hasEntityGuidBinding();
    }

    public boolean hasEntityUuidBinding() {
        return followEntityUuid != null && !followEntityUuid.isEmpty();
    }

    public boolean hasEntityGuidBinding() {
        return followEntityGuid != null && !followEntityGuid.isEmpty();
    }

    public boolean isActive() {
        return state.isRenderable();
    }

    public double distanceTo(double px, double py, double pz) {
        double dx = worldX - px, dy = worldY - py, dz = worldZ - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public enum EntityAttachPoint {
        HEAD,
        CENTER
    }

    public static class Builder {
        private final String id;
        private final double worldX, worldY, worldZ;
        private final String label;

        private String dimension = "minecraft:overworld";
        private String questId = "";
        private String phaseId = "";
        private int objectiveIndex = -1;

        private int followEntityId = -1;
        private String followEntityUuid = "";
        private String followEntityGuid = "";
        private EntityAttachPoint attachPoint = EntityAttachPoint.HEAD;

        private int colorARGB = 0xFFFFFFFF;
        private QuestMarkerType type = QuestMarkerType.CUSTOM;
        private QuestMarkerState state = QuestMarkerState.ACTIVE;
        private boolean showDistance = true;
        private boolean allowOffscreenArrow = true;

        public Builder(String id, double x, double y, double z, String label) {
            this.id = id;
            this.worldX = x;
            this.worldY = y;
            this.worldZ = z;
            this.label = label;
        }

        public Builder dimension(String dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder bindQuest(String questId) {
            this.questId = questId == null ? "" : questId;
            return this;
        }

        public Builder bindPhase(String phaseId) {
            this.phaseId = phaseId == null ? "" : phaseId;
            return this;
        }

        public Builder bindObjective(int objectiveIndex) {
            this.objectiveIndex = objectiveIndex;
            return this;
        }

        public Builder followEntity(int entityId) {
            this.followEntityId = entityId;
            return this;
        }

        public Builder followEntityUuid(String entityUuid) {
            this.followEntityUuid = entityUuid == null ? "" : entityUuid;
            return this;
        }

        public Builder followEntityGuid(String entityGuid) {
            this.followEntityGuid = entityGuid == null ? "" : entityGuid;
            return this;
        }

        public Builder attachPoint(EntityAttachPoint attachPoint) {
            this.attachPoint = attachPoint == null ? EntityAttachPoint.HEAD : attachPoint;
            return this;
        }

        public Builder followEntity(int entityId, EntityAttachPoint attachPoint) {
            this.followEntityId = entityId;
            this.attachPoint = attachPoint == null ? EntityAttachPoint.HEAD : attachPoint;
            return this;
        }

        public Builder followEntity(int entityId, String entityUuid, EntityAttachPoint attachPoint) {
            this.followEntityId = entityId;
            this.followEntityUuid = entityUuid == null ? "" : entityUuid;
            this.attachPoint = attachPoint == null ? EntityAttachPoint.HEAD : attachPoint;
            return this;
        }

        public Builder followEntity(int entityId, String entityUuid, String entityGuid, EntityAttachPoint attachPoint) {
            this.followEntityId = entityId;
            this.followEntityUuid = entityUuid == null ? "" : entityUuid;
            this.followEntityGuid = entityGuid == null ? "" : entityGuid;
            this.attachPoint = attachPoint == null ? EntityAttachPoint.HEAD : attachPoint;
            return this;
        }

        public Builder bindQuestState(QuestState questState) {
            this.state = QuestMarkerState.fromQuestState(questState);
            return this;
        }

        public Builder color(int colorARGB) {
            this.colorARGB = colorARGB;
            return this;
        }

        public Builder type(QuestMarkerType type) {
            this.type = type == null ? QuestMarkerType.CUSTOM : type;
            return this;
        }

        public Builder state(QuestMarkerState state) {
            this.state = state == null ? QuestMarkerState.ACTIVE : state;
            return this;
        }

        public Builder showDistance(boolean show) {
            this.showDistance = show;
            return this;
        }

        public Builder allowOffscreenArrow(boolean allow) {
            this.allowOffscreenArrow = allow;
            return this;
        }

        public QuestMarkerData build() {
            return new QuestMarkerData(this);
        }
    }
}