package org.arcadia.arc_quest.quest.editor;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.io.DatapackPathResolver;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecResourceLoader2;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class QuestAuthoringEntryLoader {
    private QuestAuthoringEntryLoader() {
    }

    public static CompletableFuture<Set<ResourceLocation>> discoverQuestIdsAsync() {
        return CompletableFuture.supplyAsync(QuestAuthoringEntryLoader::discoverQuestIds);
    }

    public static Set<ResourceLocation> discoverQuestIds() {
        QuestSpecResourceLoader2.LoadReport report = scanQuestSpecs();
        Set<ResourceLocation> questIds = new LinkedHashSet<>();
        for (QuestSpec spec : report.specs().values()) {
            ResourceLocation questId = spec == null ? null : ResourceLocation.tryParse(spec.id);
            if (questId != null) questIds.add(questId);
        }
        return Set.copyOf(questIds);
    }

    public static LoadResult load(ResourceLocation questId) {
        QuestSpecResourceLoader2.LoadReport report = scanQuestSpecs();
        QuestAuthoringEntry result = null;
        for (Map.Entry<Path, QuestSpec> fileEntry : report.specs().entrySet()) {
            QuestSpec spec = fileEntry.getValue();
            ResourceLocation candidateId = spec == null ? null : ResourceLocation.tryParse(spec.id);
            if (!questId.equals(candidateId)) continue;
            if (result != null) {
                return LoadResult.failure("发现重复任务 ID，无法确定编辑目标: " + questId);
            }
            result = new QuestAuthoringEntry(questId, fileEntry.getKey(), spec);
        }
        if (result != null) return LoadResult.success(result);
        if (!report.errors().isEmpty()) {
            var error = report.errors().get(0);
            return LoadResult.failure("读取任务数据包失败（" + error.file() + "): " + error.message());
        }
        return LoadResult.failure("未找到任务数据包文件: " + questId);
    }

    private static QuestSpecResourceLoader2.LoadReport scanQuestSpecs() {
        return new QuestSpecResourceLoader2().loadFromDatapack();
    }

    public record LoadResult(QuestAuthoringEntry entry, String errorMessage) {
        public static LoadResult success(QuestAuthoringEntry entry) {
            return new LoadResult(entry, "");
        }

        public static LoadResult failure(String errorMessage) {
            return new LoadResult(null, errorMessage);
        }

        public boolean successful() {
            return entry != null;
        }
    }
}
