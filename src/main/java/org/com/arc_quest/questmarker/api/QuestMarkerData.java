package org.com.arc_quest.questmarker.api;

/**
 * 任务标记数据模型（API 层）。
 * <p>
 * 渲染层（HUD 和 3D 世界）统一读取此类，附属模组通过 {@link org.com.arc_quest.api.ArcQuestAPI} 注册。
 */
public class QuestMarkerData {

    private final String id;
    private final double worldX;
    private final double worldY;
    private final double worldZ;
    private final String label;
    private final String dimension;
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
        this.colorARGB = builder.colorARGB;
        this.type = builder.type;
        this.state = builder.state;
        this.showDistance = builder.showDistance;
        this.allowOffscreenArrow = builder.allowOffscreenArrow;
    }

    // ── 快捷工厂 ──────────────────────────────────────────

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

    // ── Getter ────────────────────────────────────────────

    public String getId()                  { return id; }
    public double getWorldX()              { return worldX; }
    public double getWorldY()              { return worldY; }
    public double getWorldZ()              { return worldZ; }
    public String getLabel()               { return label; }
    public String getDimension()           { return dimension; }
    public int getColorARGB()              { return colorARGB; }
    public QuestMarkerType getType()       { return type; }
    public QuestMarkerState getState()     { return state; }
    public boolean isShowDistance()        { return showDistance; }
    public boolean isAllowOffscreenArrow() { return allowOffscreenArrow; }

    public boolean isActive() {
        return state == QuestMarkerState.ACTIVE;
    }

    public double distanceTo(double px, double py, double pz) {
        double dx = worldX - px, dy = worldY - py, dz = worldZ - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ── Builder ───────────────────────────────────────────

    public static class Builder {
        private final String id;
        private final double worldX, worldY, worldZ;
        private final String label;
        private String dimension = "minecraft:overworld";
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

        public Builder color(int colorARGB)                        { this.colorARGB = colorARGB; return this; }
        public Builder dimension(String dimension)                  { this.dimension = dimension; return this; }
        public Builder type(QuestMarkerType type)                  { this.type = type; return this; }
        public Builder state(QuestMarkerState state)               { this.state = state; return this; }
        public Builder showDistance(boolean show)                  { this.showDistance = show; return this; }
        public Builder allowOffscreenArrow(boolean allow)          { this.allowOffscreenArrow = allow; return this; }

        public QuestMarkerData build() { return new QuestMarkerData(this); }
    }
}
