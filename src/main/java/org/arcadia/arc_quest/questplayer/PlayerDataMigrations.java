package org.arcadia.arc_quest.questplayer;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.arcadia.arc_quest.quest.data.NbtVersionManager;

/** 玩家 NBT 的既有版本演进，仅处理调用方提供的独立副本。 */
final class PlayerDataMigrations {
    private PlayerDataMigrations() { }

    private static final NbtVersionManager VERSION_MANAGER = new NbtVersionManager(
            "arc_quest:player_data", 4, null
    );

    static {
        VERSION_MANAGER.addMigration(0, 1, tag -> {
            if (!tag.contains("Flags", Tag.TAG_LIST)) {
                tag.put("Flags", new ListTag());
            }
        });
        VERSION_MANAGER.addMigration(1, 2, tag -> {
            if (tag.contains("NodeVisitHistory", Tag.TAG_COMPOUND)) {
                tag.putInt("_needs_dialogue_migration", 1);
            } else if (!tag.contains("DialogueProgress", Tag.TAG_COMPOUND)) {
                tag.put("DialogueProgress", new CompoundTag());
            }
        });
        VERSION_MANAGER.addMigration(2, 3, tag -> {
            if (tag.contains("_version", Tag.TAG_INT)) {
                int oldVersion = tag.getInt("_version");
                tag.putInt("_ArcQuestVer", Math.max(oldVersion, 3));
                tag.remove("_version");
            } else {
                tag.putInt("_ArcQuestVer", 3);
            }
            tag.remove("_needs_dialogue_migration");
        });
        VERSION_MANAGER.addMigration(3, 4, root -> {
            if (!root.contains("ActiveQuests", Tag.TAG_LIST)) return;

            ListTag activeList = root.getList("ActiveQuests", Tag.TAG_COMPOUND);
            for (int i = 0; i < activeList.size(); i++) {
                CompoundTag q = activeList.getCompound(i);
                boolean hasNewStruct = q.contains("ActivePhases", Tag.TAG_LIST)
                        || q.contains("CompletedPhases", Tag.TAG_LIST)
                        || q.contains("PhaseProgress", Tag.TAG_COMPOUND);

                if (!hasNewStruct) {
                    String legacyPhaseId = q.getString("PhaseId");
                    int[] legacyProgress = q.contains("Progress", Tag.TAG_INT_ARRAY)
                            ? q.getIntArray("Progress")
                            : new int[0];

                    ListTag activePhases = new ListTag();
                    ListTag completedPhases = new ListTag();
                    CompoundTag phaseProgress = new CompoundTag();

                    if (legacyPhaseId != null && !legacyPhaseId.isEmpty()) {
                        activePhases.add(StringTag.valueOf(legacyPhaseId));
                        phaseProgress.putIntArray(legacyPhaseId, legacyProgress);
                    }

                    q.put("ActivePhases", activePhases);
                    q.put("CompletedPhases", completedPhases);
                    q.put("PhaseProgress", phaseProgress);
                    q.remove("PhaseId");
                    q.remove("Progress");
                    continue;
                }

                ListTag activePhases = q.getList("ActivePhases", Tag.TAG_STRING);
                CompoundTag phaseProgress = q.contains("PhaseProgress", Tag.TAG_COMPOUND)
                        ? q.getCompound("PhaseProgress")
                        : new CompoundTag();

                for (int j = 0; j < activePhases.size(); j++) {
                    String pid = activePhases.getString(j);
                    if (pid != null && !pid.isEmpty() && !phaseProgress.contains(pid, Tag.TAG_INT_ARRAY)) {
                        phaseProgress.putIntArray(pid, new int[0]);
                    }
                }

                q.put("PhaseProgress", phaseProgress);
                if (!q.contains("CompletedPhases", Tag.TAG_LIST)) {
                    q.put("CompletedPhases", new ListTag());
                }
            }
        });
    }

    static void migrate(CompoundTag root) {
        VERSION_MANAGER.migrate(root);
    }

    static void stamp(CompoundTag root) {
        VERSION_MANAGER.setInitialVersion(root);
    }

    static int currentVersion() {
        return VERSION_MANAGER.getCurrentVersion();
    }
}
