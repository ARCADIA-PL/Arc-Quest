package org.arcadia.arc_quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import org.arcadia.arc_quest.npc.io.NpcDatapackHotReloadService;
import org.arcadia.arc_quest.npc.runtime.NpcBindingRegistry;
import org.arcadia.arc_quest.npc.spec.NpcBindingSpec;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class NpcCommands {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final NpcDatapackHotReloadService NPC_HOT_RELOAD_SERVICE = new NpcDatapackHotReloadService();

    public static LiteralArgumentBuilder<CommandSourceStack> registerSubtree(CommandDispatcher<CommandSourceStack> dispatcher) {
        return Commands.literal("npc")
                .then(Commands.literal("list")
                        .executes(NpcCommands::cmdList))
                .then(Commands.literal("debug")
                        .then(Commands.argument("entity_type", ResourceLocationArgument.id())
                                .suggests(NpcCommands::suggestEntityTypes)
                                .executes(NpcCommands::cmdDebug)))
                .then(Commands.literal("reload")
                        .executes(NpcCommands::cmdReload))
                .then(Commands.literal("status")
                        .executes(NpcCommands::cmdStatus));
    }

    private static CompletableFuture<Suggestions> suggestEntityTypes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        Set<EntityType<?>> types = NpcBindingRegistry.INSTANCE.getAllEntityTypes();
        return SharedSuggestionProvider.suggest(
                types.stream()
                        .map(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t))
                        .filter(k -> k != null)
                        .map(ResourceLocation::toString),
                builder);
    }

    private static void success(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal("§a[ArcQuest] §f" + msg), true);
    }

    private static void error(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal("§c[ArcQuest] §f" + msg));
    }

    private static int cmdList(CommandContext<CommandSourceStack> ctx) {
        Set<EntityType<?>> types = NpcBindingRegistry.INSTANCE.getAllEntityTypes();

        MutableComponent msg = Component.literal("§6═══ NPC Bindings ═══\n");
        msg.append(Component.literal("§e  Entity types with NPC bindings: §f" + types.size() + "\n"));

        if (types.isEmpty()) {
            msg.append(Component.literal("§7  (no NPC bindings registered)\n"));
        } else {
            for (EntityType<?> type : types) {
                ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
                String typeName = key != null ? key.toString() : type.toString();
                List<NpcSpec> specs = NpcBindingRegistry.INSTANCE.getSpecsFor(type);
                int bindingCount = specs.stream().mapToInt(s -> s.bindings != null ? s.bindings.size() : 0).sum();
                msg.append(Component.literal("§a  " + typeName + " §7[" + specs.size() + " specs, " + bindingCount + " bindings]\n"));
            }
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdDebug(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation typeKey = ResourceLocationArgument.getId(ctx, "entity_type");
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(typeKey);

        if (entityType == null) {
            error(ctx, "Unknown entity type: " + typeKey);
            return 0;
        }

        List<NpcSpec> specs = NpcBindingRegistry.INSTANCE.getSpecsFor(entityType);
        if (specs.isEmpty()) {
            error(ctx, "No NPC bindings for entity type: " + typeKey);
            return 0;
        }

        MutableComponent msg = Component.literal("§6═══ NPC Debug: " + typeKey + " ═══\n");
        msg.append(Component.literal("§e  Specs: §f" + specs.size() + "\n"));

        for (int i = 0; i < specs.size(); i++) {
            NpcSpec spec = specs.get(i);
            msg.append(Component.literal("§d  --- Spec #" + i + " ---\n"));
            msg.append(Component.literal("§7    entityType: §f" + spec.entityType + "\n"));
            msg.append(Component.literal("§7    dialogueDistance: §f" + spec.dialogueDistance + "\n"));
            msg.append(Component.literal("§7    cancelVanillaInteract: §f" + spec.cancelVanillaInteract + "\n"));
            msg.append(Component.literal("§7    shouldLookAtPlayer: §f" + spec.shouldLookAtPlayer + "\n"));
            msg.append(Component.literal("§7    shouldStopMoving: §f" + spec.shouldStopMoving + "\n"));
            msg.append(Component.literal("§7    interactCondition: §f" + (spec.interactCondition != null ? spec.interactCondition.condition : "none") + "\n"));

            if (spec.onDialogueStartCommands != null && !spec.onDialogueStartCommands.isEmpty()) {
                msg.append(Component.literal("§7    onDialogueStartCommands: §f" + spec.onDialogueStartCommands + "\n"));
            }
            if (spec.onDialogueEndCommands != null && !spec.onDialogueEndCommands.isEmpty()) {
                msg.append(Component.literal("§7    onDialogueEndCommands: §f" + spec.onDialogueEndCommands + "\n"));
            }

            if (spec.bindings != null && !spec.bindings.isEmpty()) {
                msg.append(Component.literal("§b    Bindings (" + spec.bindings.size() + "):\n"));
                for (int j = 0; j < spec.bindings.size(); j++) {
                    NpcBindingSpec binding = spec.bindings.get(j);
                    msg.append(Component.literal("§7      [" + j + "] bindingId=" + (binding.bindingId != null ? binding.bindingId : "-")
                            + " dialogueId=" + (binding.dialogueId != null ? binding.dialogueId : "-")
                            + " dialogueIdFromNbt=" + (binding.dialogueIdFromNbt != null ? binding.dialogueIdFromNbt : "-")
                            + " priority=" + binding.priority
                            + " condition=" + (binding.condition != null ? binding.condition.condition : "none") + "\n"));
                }
            } else {
                msg.append(Component.literal("§7    Bindings: (none)\n"));
            }
        }

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int cmdReload(CommandContext<CommandSourceStack> ctx) {
        var result = NPC_HOT_RELOAD_SERVICE.reload();
        success(ctx, "NPC datapack reloaded. scanned=" + result.scanned()
                + ", loaded=" + result.discovered()
                + ", failed=" + result.failed()
                + ", activeBindings=" + result.activeBindings());
        return 1;
    }

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        int totalSpecs = NpcBindingRegistry.INSTANCE.size();
        int entityTypeCount = NpcBindingRegistry.INSTANCE.getAllEntityTypes().size();

        MutableComponent msg = Component.literal("§6═══ NPC Status ═══\n");
        msg.append(Component.literal("§e  Entity types with bindings: §f" + entityTypeCount + "\n"));
        msg.append(Component.literal("§e  Total NPC specs: §f" + totalSpecs + "\n"));

        ctx.getSource().sendSuccess(() -> msg, false);
        return 1;
    }
}