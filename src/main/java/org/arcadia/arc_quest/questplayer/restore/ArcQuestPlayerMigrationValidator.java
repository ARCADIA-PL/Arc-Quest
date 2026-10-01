package org.arcadia.arc_quest.questplayer.restore;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.dialogue.registry.DialogueRegistry;
import org.arcadia.arc_quest.quest.api.PhaseDefinition;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationBundle;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.questplayer.migration.ArcQuestPlayerMigrationSections;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;
import org.arcadia.arc_quest.trade.gacha.registry.GachaRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ArcQuestPlayerMigrationValidator {

    public ArcQuestPlayerMigrationReport validate(ServerPlayer targetPlayer,
                                                  ArcQuestPlayerMigrationBundle bundle,
                                                  Set<String> sections) {
        List<ArcQuestPlayerMigrationIssue> issues = new ArrayList<>();

        validateMeta(bundle, issues);
        if (sections.isEmpty() || !bundle.getMeta().getExportedSections().containsAll(sections)
                || !bundle.getSections().getAvailableSections().containsAll(sections)) {
            issues.add(new ArcQuestPlayerMigrationIssue(ArcQuestPlayerMigrationSeverity.ERROR,
                    "invalid_sections", "所选区段为空、未导出或不受支持", "meta", String.valueOf(sections)));
        }
        if (issues.isEmpty()) {
            try {
                bundle.getSections().applyTo(new CompoundTag(), sections);
                validateSections(bundle.getSections(), sections, issues);
            } catch (RuntimeException exception) {
                issues.add(new ArcQuestPlayerMigrationIssue(ArcQuestPlayerMigrationSeverity.ERROR,
                        "invalid_section_data", "区段数据无法解析: " + exception.getMessage(), "sections", ""));
            }
        }

        boolean blocking = issues.stream().anyMatch(issue -> issue.severity() == ArcQuestPlayerMigrationSeverity.ERROR);
        return new ArcQuestPlayerMigrationReport(blocking, issues);
    }

    private void validateMeta(ArcQuestPlayerMigrationBundle bundle,
                              List<ArcQuestPlayerMigrationIssue> issues) {
        int sourceVersion = bundle.getMeta().getSourceArcQuestDataVersion();
        if (sourceVersion < 0 || sourceVersion > ArcQuestPlayer.getCurrentDataVersion()) {
            issues.add(new ArcQuestPlayerMigrationIssue(ArcQuestPlayerMigrationSeverity.ERROR,
                    "unsupported_player_data_version", "玩家数据版本不受支持", "meta", String.valueOf(sourceVersion)));
        }
        if (!ArcQuestPlayerMigrationBundle.FORMAT.equals(bundle.getFormat())) {
            issues.add(new ArcQuestPlayerMigrationIssue(
                    ArcQuestPlayerMigrationSeverity.ERROR,
                    "invalid_format",
                    "快照格式不是 arc_quest:player_migration",
                    "meta",
                    bundle.getFormat()
            ));
        }

        if (bundle.getVersion() <= 0 || bundle.getVersion() > ArcQuestPlayerMigrationBundle.VERSION) {
            issues.add(new ArcQuestPlayerMigrationIssue(
                    ArcQuestPlayerMigrationSeverity.ERROR,
                    "invalid_version",
                    "快照版本非法",
                    "meta",
                    String.valueOf(bundle.getVersion())
            ));
        }
    }

    private void validateSections(ArcQuestPlayerMigrationSections sections,
                                  Set<String> selectedSections,
                                  List<ArcQuestPlayerMigrationIssue> issues) {
        if (selectedSections.contains("flagsVars")) {
            validateFlagsVars(sections.getFlagsVars(), issues);
        }
        if (selectedSections.contains("questState")) {
            validateQuestState(sections.getQuestState(), issues);
        }
        if (selectedSections.contains("dialogue")) {
            validateDialogue(sections.getDialogue(), issues);
        }
        if (selectedSections.contains("trade")) {
            validateTrade(sections.getTrade(), issues);
        }
        if (selectedSections.contains("gacha")) {
            validateGacha(sections.getGacha(), issues);
        }
        if (selectedSections.contains("markers")) {
            validateMarkers(sections.getMarkers(), issues);
        }
    }

    private void validateFlagsVars(CompoundTag tag, List<ArcQuestPlayerMigrationIssue> issues) {
        if (!tag.contains("Flags", Tag.TAG_LIST)) {
            issues.add(new ArcQuestPlayerMigrationIssue(
                    ArcQuestPlayerMigrationSeverity.WARNING,
                    "missing_flags",
                    "flagsVars section 缺少 Flags 列表",
                    "flagsVars",
                    "Flags"
            ));
        }
        if (!tag.contains("Variables", Tag.TAG_COMPOUND)) {
            issues.add(new ArcQuestPlayerMigrationIssue(
                    ArcQuestPlayerMigrationSeverity.WARNING,
                    "missing_variables",
                    "flagsVars section 缺少 Variables 结构",
                    "flagsVars",
                    "Variables"
            ));
        }
    }

    private void validateQuestState(CompoundTag tag, List<ArcQuestPlayerMigrationIssue> issues) {
        if (!tag.contains("ActiveQuests", Tag.TAG_LIST)) {
            issues.add(new ArcQuestPlayerMigrationIssue(
                    ArcQuestPlayerMigrationSeverity.WARNING,
                    "missing_active_quests",
                    "questState section 缺少 ActiveQuests 列表",
                    "questState",
                    "ActiveQuests"
            ));
            return;
        }

        ListTag active = tag.getList("ActiveQuests", Tag.TAG_COMPOUND);
        for (int i = 0; i < active.size(); i++) {
            CompoundTag questTag = active.getCompound(i);
            QuestRuntimeData qdata = QuestRuntimeData.deserializeNBT(questTag);
            String questId = qdata.getQuestId();
            QuestDefinition def = QuestRegistry.get(questId);
            if (def == null) {
                issues.add(new ArcQuestPlayerMigrationIssue(
                        ArcQuestPlayerMigrationSeverity.ERROR,
                        "unknown_quest",
                        "任务未在当前 QuestRegistry 中注册",
                        "questState",
                        questId
                ));
                continue;
            }

            for (String phaseId : qdata.getActivePhaseIds()) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null) {
                    issues.add(new ArcQuestPlayerMigrationIssue(
                            ArcQuestPlayerMigrationSeverity.ERROR,
                            "unknown_phase",
                            "活跃阶段未在当前任务定义中找到",
                            "questState",
                            questId + "#" + phaseId
                    ));
                    continue;
                }

                int[] progress = qdata.getAllProgress(phaseId);
                if (progress.length != phase.getObjectives().size()) {
                    issues.add(new ArcQuestPlayerMigrationIssue(
                            ArcQuestPlayerMigrationSeverity.WARNING,
                            "objective_progress_length_mismatch",
                            "阶段目标进度长度与当前任务定义不一致",
                            "questState",
                            questId + "#" + phaseId
                    ));
                }
            }
        }
    }

    private void validateDialogue(CompoundTag tag, List<ArcQuestPlayerMigrationIssue> issues) {
        for (String bucket : List.of("Nodes", "Choices", "Dialogues")) {
            if (!tag.contains(bucket, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag sub = tag.getCompound(bucket);
            for (String key : sub.getAllKeys()) {
                if (bucket.equals("Dialogues")) {
                    String dialogueId = extractDialogueId(key);
                    if (dialogueId != null && DialogueRegistry.INSTANCE.get(dialogueId) == null) {
                        issues.add(new ArcQuestPlayerMigrationIssue(
                                ArcQuestPlayerMigrationSeverity.WARNING,
                                "unknown_dialogue",
                                "对话进度引用了当前 registry 中不存在的 dialogue",
                                "dialogue",
                                dialogueId
                        ));
                    }
                }
            }
        }
    }

    private void validateTrade(CompoundTag tag, List<ArcQuestPlayerMigrationIssue> issues) {
        if (!tag.contains("TradePurchases", Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag purchases = tag.getCompound("TradePurchases");
        for (String shopId : purchases.getAllKeys()) {
            if (TradeRegistry.get(shopId) == null) {
                issues.add(new ArcQuestPlayerMigrationIssue(
                        ArcQuestPlayerMigrationSeverity.WARNING,
                        "unknown_trade_shop",
                        "交易进度引用了当前 registry 中不存在的 shop",
                        "trade",
                        shopId
                ));
            }
        }
    }

    private void validateGacha(CompoundTag tag, List<ArcQuestPlayerMigrationIssue> issues) {
        for (String bucket : List.of("DrawCounts", "PityCounters", "Histories", "DrawCooldowns")) {
            if (!tag.contains(bucket, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag sub = tag.getCompound(bucket);
            for (String shopId : sub.getAllKeys()) {
                if (GachaRegistry.get(shopId) == null) {
                    issues.add(new ArcQuestPlayerMigrationIssue(
                            ArcQuestPlayerMigrationSeverity.WARNING,
                            "unknown_gacha_shop",
                            "抽奖进度引用了当前 registry 中不存在的 shop",
                            "gacha",
                            shopId
                    ));
                }
            }
        }
    }

    private void validateMarkers(CompoundTag tag, List<ArcQuestPlayerMigrationIssue> issues) {
        if (!tag.contains("Markers", Tag.TAG_LIST)) {
            return;
        }
        ListTag markers = tag.getList("Markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < markers.size(); i++) {
            CompoundTag marker = markers.getCompound(i);
            String questId = marker.getString("questId");
            String phaseId = marker.getString("phaseId");
            int objectiveIndex = marker.contains("objectiveIndex", Tag.TAG_INT) ? marker.getInt("objectiveIndex") : -1;

            if (questId == null || questId.isEmpty()) {
                continue;
            }

            QuestDefinition def = QuestRegistry.get(questId);
            if (def == null) {
                issues.add(new ArcQuestPlayerMigrationIssue(
                        ArcQuestPlayerMigrationSeverity.WARNING,
                        "marker_unknown_quest",
                        "marker 绑定了当前 registry 中不存在的任务",
                        "markers",
                        questId
                ));
                continue;
            }

            if (phaseId != null && !phaseId.isEmpty()) {
                PhaseDefinition phase = def.getPhase(phaseId);
                if (phase == null) {
                    issues.add(new ArcQuestPlayerMigrationIssue(
                            ArcQuestPlayerMigrationSeverity.WARNING,
                            "marker_unknown_phase",
                            "marker 绑定了当前任务中不存在的阶段",
                            "markers",
                            questId + "#" + phaseId
                    ));
                    continue;
                }
                if (objectiveIndex >= phase.getObjectives().size()) {
                    issues.add(new ArcQuestPlayerMigrationIssue(
                            ArcQuestPlayerMigrationSeverity.WARNING,
                            "marker_objective_index_out_of_bounds",
                            "marker objectiveIndex 超出当前阶段目标数量",
                            "markers",
                            questId + "#" + phaseId + "@" + objectiveIndex
                    ));
                }
            }
        }
    }

    private static String extractDialogueId(String key) {
        int first = key.indexOf(':');
        if (first < 0) {
            return null;
        }
        int second = key.indexOf(':', first + 1);
        if (second < 0) {
            return key;
        }
        String tail = key.substring(second + 1);
        boolean numeric = !tail.isEmpty() && tail.chars().allMatch(Character::isDigit);
        return numeric ? key.substring(0, second) : key;
    }
}
