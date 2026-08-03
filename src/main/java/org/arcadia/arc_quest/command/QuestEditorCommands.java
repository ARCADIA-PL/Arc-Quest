package org.arcadia.arc_quest.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.editor.QuestAuthoringEntryLoader;
import org.arcadia.arc_quest.quest.editor.QuestAuthoringSnapshotRegistry;
import org.arcadia.arc_quest.quest.editor.QuestEditorSessionService;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;

import java.util.LinkedHashSet;
import java.util.Set;

public final class QuestEditorCommands {
    private QuestEditorCommands() { }

    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree() {
        return Commands.literal("editor").then(Commands.literal("open")
                .then(Commands.argument("quest_id", ResourceLocationArgument.id())
                        .suggests((context, builder) -> {
                            Set<ResourceLocation> questIds = new LinkedHashSet<>(
                                    QuestAuthoringSnapshotRegistry.getDatapackSnapshot().keySet());
                            questIds.addAll(QuestRegistry.getDatapackSnapshot().keySet());
                            if (!questIds.isEmpty()) {
                                return SharedSuggestionProvider.suggestResource(questIds, builder);
                            }
                            return QuestAuthoringEntryLoader.discoverQuestIdsAsync()
                                    .thenCompose(ids -> SharedSuggestionProvider.suggestResource(ids, builder));
                        })
                        .executes(context -> {
                            QuestEditorSessionService.INSTANCE.open(context.getSource().getPlayerOrException(),
                                    ResourceLocationArgument.getId(context, "quest_id"));
                            return 1;
                        })));
    }
}
