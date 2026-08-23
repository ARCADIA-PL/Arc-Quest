package org.arcadia.arc_quest.questmarker.api;

import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.quest.api.QuestState;

import java.util.Map;
import java.util.Objects;

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
    private final int priority;
    private final Map<String, String> styleHints;
    private final boolean persistent;

    private QuestMarkerData(Builder builder) {
        id = builder.id;
        worldX = builder.worldX;
        worldY = builder.worldY;
        worldZ = builder.worldZ;
        label = builder.label;
        dimension = builder.dimension;

        questId = builder.questId;
        phaseId = builder.phaseId;
        objectiveIndex = builder.objectiveIndex;

        followEntityId = builder.followEntityId;
        followEntityUuid = builder.followEntityUuid;
        followEntityGuid = builder.followEntityGuid;
        attachPoint = builder.attachPoint;

        colorARGB = builder.colorARGB;
        type = builder.type.canonical();
        state = builder.state;
        showDistance = builder.showDistance;
        allowOffscreenArrow = builder.allowOffscreenArrow;
        priority = builder.priority;
        styleHints = Map.copyOf(builder.styleHints);
        persistent = builder.persistent;
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

    public Component getLabelComponent() {
        String translationKey = styleHints.get("labelKey");
        return translationKey == null || translationKey.isBlank()
                ? Component.literal(label)
                : Component.translatable(translationKey);
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

    public int getPriority() {
        return priority;
    }

    public Map<String, String> getStyleHints() {
        return styleHints;
    }

    public String getStyleHint(String key) {
        return styleHints.get(key);
    }

    public boolean isPersistent() {
        return persistent;
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof QuestMarkerData other)) return false;
        return Double.compare(worldX, other.worldX) == 0
                && Double.compare(worldY, other.worldY) == 0
                && Double.compare(worldZ, other.worldZ) == 0
                && objectiveIndex == other.objectiveIndex
                && followEntityId == other.followEntityId
                && colorARGB == other.colorARGB
                && showDistance == other.showDistance
                && allowOffscreenArrow == other.allowOffscreenArrow
                && priority == other.priority
                && persistent == other.persistent
                && Objects.equals(id, other.id)
                && Objects.equals(label, other.label)
                && Objects.equals(dimension, other.dimension)
                && Objects.equals(questId, other.questId)
                && Objects.equals(phaseId, other.phaseId)
                && Objects.equals(followEntityUuid, other.followEntityUuid)
                && Objects.equals(followEntityGuid, other.followEntityGuid)
                && attachPoint == other.attachPoint
                && type == other.type
                && state == other.state
                && Objects.equals(styleHints, other.styleHints);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, worldX, worldY, worldZ, label, dimension, questId, phaseId,
                objectiveIndex, followEntityId, followEntityUuid, followEntityGuid, attachPoint,
                colorARGB, type, state, showDistance, allowOffscreenArrow, priority, styleHints, persistent);
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
        private int priority;
        private Map<String, String> styleHints = Map.of();
        private boolean persistent = true;

        public Builder(String id, double x, double y, double z, String label) {
            this.id = id;
            worldX = x;
            worldY = y;
            worldZ = z;
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
            followEntityId = entityId;
            return this;
        }

        public Builder followEntityUuid(String entityUuid) {
            followEntityUuid = entityUuid == null ? "" : entityUuid;
            return this;
        }

        public Builder followEntityGuid(String entityGuid) {
            followEntityGuid = entityGuid == null ? "" : entityGuid;
            return this;
        }

        public Builder attachPoint(EntityAttachPoint attachPoint) {
            this.attachPoint = attachPoint == null ? EntityAttachPoint.HEAD : attachPoint;
            return this;
        }

        public Builder followEntity(int entityId, EntityAttachPoint attachPoint) {
            followEntityId = entityId;
            this.attachPoint = attachPoint == null ? EntityAttachPoint.HEAD : attachPoint;
            return this;
        }

        public Builder followEntity(int entityId, String entityUuid, EntityAttachPoint attachPoint) {
            followEntityId = entityId;
            followEntityUuid = entityUuid == null ? "" : entityUuid;
            this.attachPoint = attachPoint == null ? EntityAttachPoint.HEAD : attachPoint;
            return this;
        }

        public Builder followEntity(int entityId, String entityUuid, String entityGuid, EntityAttachPoint attachPoint) {
            followEntityId = entityId;
            followEntityUuid = entityUuid == null ? "" : entityUuid;
            followEntityGuid = entityGuid == null ? "" : entityGuid;
            this.attachPoint = attachPoint == null ? EntityAttachPoint.HEAD : attachPoint;
            return this;
        }

        public Builder bindQuestState(QuestState questState) {
            state = QuestMarkerState.fromQuestState(questState);
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
            showDistance = show;
            return this;
        }

        public Builder allowOffscreenArrow(boolean allow) {
            allowOffscreenArrow = allow;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = Math.max(0, priority);
            return this;
        }

        public Builder styleHints(Map<String, String> styleHints) {
            this.styleHints = styleHints == null ? Map.of() : Map.copyOf(styleHints);
            return this;
        }

        public Builder persistent(boolean persistent) {
            this.persistent = persistent;
            return this;
        }

        public QuestMarkerData build() {
            return new QuestMarkerData(this);
        }
    }
}
