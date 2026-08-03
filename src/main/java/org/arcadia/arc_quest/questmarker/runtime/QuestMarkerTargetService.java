package org.arcadia.arc_quest.questmarker.runtime;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.arcadia.arc_quest.questmarker.api.MarkSpec;
import org.arcadia.arc_quest.questmarker.api.MarkTargetResolver;
import org.arcadia.arc_quest.questmarker.api.MarkTargetResolverRegistry;
import org.arcadia.arc_quest.questmarker.api.MarkableObject;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerState;
import org.arcadia.arc_quest.questmarker.api.ResolvedMarkTarget;
import org.slf4j.Logger;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class QuestMarkerTargetService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NPC_ID_KEY = "ArcQuestNpcId";

    private QuestMarkerTargetService() {
    }

    public static QuestMarkerData resolve(String markerId,
                                          String ownerId,
                                          String phaseId,
                                          int objectiveIndex,
                                          MarkSpec spec,
                                          ServerPlayer player,
                                          ServerLevel level) {
        ResolvedMarkTarget target = resolveTarget(spec.target(), player, level);
        if (target == null) return null;
        if (!Double.isFinite(target.x()) || !Double.isFinite(target.y()) || !Double.isFinite(target.z())) return null;

        boolean sameDimension = level.dimension().location().toString().equals(target.dimension());
        if (sameDimension && spec.maxDistance() > 0) {
            double dx = target.x() - player.getX();
            double dy = target.y() - player.getY();
            double dz = target.z() - player.getZ();
            if (dx * dx + dy * dy + dz * dz > (double) spec.maxDistance() * spec.maxDistance()) return null;
        }

        Map<String, String> styleHints = spec.styleHints();
        String label = styleHints.getOrDefault("label", spec.id());
        QuestMarkerData.Builder builder = new QuestMarkerData.Builder(
                markerId, target.x(), target.y(), target.z(), label)
                .dimension(target.dimension())
                .bindQuest(ownerId)
                .type(spec.markerType())
                .state(QuestMarkerState.ACTIVE)
                .color(parseColor(styleHints.get("color"), 0xFFFFFFFF))
                .showDistance(parseBoolean(styleHints.get("showDistance"), true))
                .allowOffscreenArrow(parseBoolean(styleHints.get("allowOffscreenArrow"), true))
                .priority(spec.priority())
                .styleHints(styleHints)
                .persistent(false);

        if (phaseId != null && !phaseId.isBlank()) builder.bindPhase(phaseId);
        if (objectiveIndex >= 0) builder.bindObjective(objectiveIndex);
        if (spec.trackMovingEntity() && target.entityId() >= 0) {
            builder.followEntity(target.entityId(), target.entityUuid(), target.entityGuid(), target.attachPoint());
        }
        return builder.build();
    }

    private static ResolvedMarkTarget resolveTarget(MarkableObject target,
                                                     ServerPlayer player,
                                                     ServerLevel level) {
        if (target instanceof MarkableObject.Pos pos) {
            return ResolvedMarkTarget.position(pos.x() + 0.5, pos.y(), pos.z() + 0.5,
                    level.dimension().location().toString());
        }
        if (target instanceof MarkableObject.DimensionPos pos) {
            return ResolvedMarkTarget.position(pos.x() + 0.5, pos.y(), pos.z() + 0.5,
                    pos.dimension().location().toString());
        }
        if (target instanceof MarkableObject.BlockPosition block) {
            BlockPos pos = block.pos();
            return ResolvedMarkTarget.position(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    level.dimension().location().toString());
        }
        if (target instanceof MarkableObject.EntityByUuid entityByUuid) {
            return fromEntity(level.getEntity(entityByUuid.uuid()), "", QuestMarkerData.EntityAttachPoint.HEAD, level);
        }
        if (target instanceof MarkableObject.EntityByNpcId entityByNpcId) {
            Entity nearest = level.getEntities(player,
                            player.getBoundingBox().inflate(entityByNpcId.searchRadius()),
                            entity -> entityByNpcId.npcId().equals(entity.getPersistentData().getString(NPC_ID_KEY)))
                    .stream()
                    .min((left, right) -> Double.compare(left.distanceToSqr(player), right.distanceToSqr(player)))
                    .orElse(null);
            return fromEntity(nearest, entityByNpcId.npcId(), QuestMarkerData.EntityAttachPoint.HEAD, level);
        }
        if (target instanceof MarkableObject.EntityByTypeNearest entityByType) {
            Entity nearest = level.getEntities(player,
                            player.getBoundingBox().inflate(entityByType.searchRadius()),
                            entity -> entity.getType() == entityByType.type())
                    .stream()
                    .min((left, right) -> Double.compare(left.distanceToSqr(player), right.distanceToSqr(player)))
                    .orElse(null);
            return fromEntity(nearest, "", QuestMarkerData.EntityAttachPoint.HEAD, level);
        }
        if (target instanceof MarkableObject.StructureNearest structure) {
            BlockPos pos = level.findNearestMapStructure(
                    structure.structureTag(), player.blockPosition(), structure.searchRadius(), false);
            return pos == null ? null : ResolvedMarkTarget.position(
                    pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.dimension().location().toString());
        }
        if (target instanceof MarkableObject.CustomResolver custom) {
            MarkTargetResolver resolver = MarkTargetResolverRegistry.get(custom.resolverId());
            if (resolver == null) return null;
            try {
                return resolver.resolve(player, level, custom);
            } catch (RuntimeException exception) {
                LOGGER.error("[QuestMarker] Custom resolver {} failed for player {}",
                        custom.resolverId(), player.getGameProfile().getName(), exception);
                return null;
            }
        }
        return null;
    }

    private static ResolvedMarkTarget fromEntity(Entity entity,
                                                 String guid,
                                                 QuestMarkerData.EntityAttachPoint attachPoint,
                                                 ServerLevel level) {
        if (entity == null || !entity.isAlive()) return null;
        UUID uuid = entity.getUUID();
        return new ResolvedMarkTarget(
                entity.getX(), entity.getY(), entity.getZ(), level.dimension().location().toString(),
                entity.getId(), uuid.toString(), guid, attachPoint);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int parseColor(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.startsWith("#")) normalized = normalized.substring(1);
            if (normalized.startsWith("0x")) normalized = normalized.substring(2);
            long parsed = Long.parseUnsignedLong(normalized, 16);
            if (normalized.length() <= 6) parsed |= 0xFF000000L;
            return (int) parsed;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
