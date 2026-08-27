package org.arcadia.arc_quest.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.editor.QuestAuthoringEntryLoader;
import org.arcadia.arc_quest.quest.editor.QuestAuthoringSnapshotRegistry;
import org.arcadia.arc_quest.quest.editor.QuestEditorSessionService;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.LinkedHashSet;
import java.util.Set;

public final class QuestEditorCommands {
    private QuestEditorCommands() { }

    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree() {
        return Commands.literal("editor")
                .then(Commands.literal("create")
                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                .executes(context -> {
                                    QuestEditorSessionService.INSTANCE.create(context.getSource().getPlayerOrException(),
                                            ResourceLocationArgument.getId(context, "quest_id"));
                                    return 1;
                                })))
                .then(Commands.literal("delete")
                        .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                                .executes(context -> {
                                    QuestEditorSessionService.INSTANCE.delete(context.getSource().getPlayerOrException(),
                                            ResourceLocationArgument.getId(context, "quest_id"));
                                    return 1;
                                })))
                .then(Commands.literal("duplicate")
                        .then(Commands.argument("source_id", ResourceLocationArgument.id())
                                .then(Commands.argument("target_id", ResourceLocationArgument.id())
                                        .executes(context -> {
                                            QuestEditorSessionService.INSTANCE.duplicate(context.getSource().getPlayerOrException(),
                                                    ResourceLocationArgument.getId(context, "source_id"),
                                                    ResourceLocationArgument.getId(context, "target_id"));
                                            return 1;
                                        }))))
                .then(Commands.literal("open")
                .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                        .suggests((context, builder) -> {
                            Set<ResourceLocation> questIds = new LinkedHashSet<>(
                                    QuestAuthoringSnapshotRegistry.getDatapackSnapshot().keySet());
                            questIds.addAll(QuestRegistry.getDatapackSnapshot().keySet());
                            if (!questIds.isEmpty()) {
                                return ArcQuestSuggestionUtil.suggest(
                                        questIds.stream().map(ResourceLocation::toString).toList(),
                                        builder, QuestEditorCommands::questTooltip);
                            }
                            return QuestAuthoringEntryLoader.discoverQuestIdsAsync()
                                    .thenCompose(ids -> ArcQuestSuggestionUtil.suggest(
                                            ids.stream().map(ResourceLocation::toString).toList(),
                                            builder, QuestEditorCommands::questTooltip));
                        })
                        .executes(context -> {
                            QuestEditorSessionService.INSTANCE.open(context.getSource().getPlayerOrException(),
                                    ResourceLocationArgument.getId(context, "quest_id"));
                            return 1;
                        })));
    }

    private static Component questTooltip(String id) {
        QuestDefinition definition = QuestRegistry.get(ResourceLocation.parse(id));
        return definition == null
                ? ArcQuestSuggestionUtil.idTooltip("Quest", id)
                : ArcQuestSuggestionUtil.displayTooltip(
                        "Quest", definition.getDisplayName(), id);
    }
}
