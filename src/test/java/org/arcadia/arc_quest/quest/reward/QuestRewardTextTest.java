package org.arcadia.arc_quest.quest.reward;

import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.integration.jei.api.JeiDisplayAdapters;
import org.arcadia.arc_quest.quest.api.IReward;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QuestRewardTextTest {
    private final Language original = Language.getInstance();
    @BeforeAll static void initialize() { MinecraftRegistryTestBootstrap.initialize(); }
    @AfterEach void restoreLanguage() { Language.inject(original); }

    @Test void itemNamesStayNestedAndTranslateWhenTheClientLanguageChanges() {
        IReward reward = new ItemReward(Items.COAL, 3);
        Component label = reward.describeComponent();
        Object name = assertInstanceOf(TranslatableContents.class, label.getContents()).getArgs()[0];
        assertInstanceOf(TranslatableContents.class, assertInstanceOf(Component.class, name).getContents());
        language(Map.of("arc_quest.reward.item", "%1$s × %2$s", "item.minecraft.coal", "Coal"));
        assertEquals("Coal × 3", label.getString());
        language(Map.of("arc_quest.reward.item", "%1$s × %2$s", "item.minecraft.coal", "煤炭"));
        assertEquals("煤炭 × 3", label.getString());
        assertEquals("Item(minecraft:coal x3)", reward.describe());
    }

    @Test void standardNonItemRewardsUseTranslationKeysAndKeepDiagnosticDescriptions() {
        var reward = VariableReward.add("survey_credit", 5);
        Component label = reward.describeComponent();
        language(Map.of("arc_quest.reward.variable_add", "Add %2$s to %1$s"));
        assertEquals("Add 5 to survey_credit", label.getString());
        language(Map.of("arc_quest.reward.variable_add", "%1$s 增加 %2$s"));
        assertEquals("survey_credit 增加 5", label.getString());
        assertEquals("Var(survey_credit ADD 5)", reward.describe());
        assertEquals("arc_quest.reward.flag_set", key(FlagReward.set("field_done").describeComponent()));
        assertEquals("arc_quest.reward.flag_clear", key(FlagReward.clear("field_done").describeComponent()));
        for (var operation : VariableReward.Op.values())
            assertEquals("arc_quest.reward.variable_" + operation.name().toLowerCase(java.util.Locale.ROOT),
                    key(new VariableReward("survey_credit", operation, 3).describeComponent()));
    }

    @Test void commandPreviewsDoNotRevealExecutablePayloadsAndJeiUsesTheSameComponent() {
        var reward = new CommandReward("give {player} minecraft:diamond 64");
        language(Map.of("arc_quest.reward.command", "Special reward"));
        Component label = reward.describeComponent();
        assertEquals("Special reward", label.getString());
        assertFalse(label.getString().contains("diamond"));
        assertEquals(label, JeiDisplayAdapters.reward(reward, null).notes().get(0));
        assertEquals("Command(give {player} minecraft:diamond 64)", reward.describe());
    }

    @Test void customComponentsRetainTranslationArgumentsSiblingsAndStylesThroughRewardAdapters() {
        Component custom = Component.translatable("example.reward", Component.translatable("example.rank"))
                .withStyle(ChatFormatting.GOLD).append(Component.translatable("example.suffix"));
        IReward reward = new IReward() {
            @Override public void grant(ServerPlayer player) { }
            @Override public String describe() { return "diagnostic reward"; }
            @Override public Component describeComponent() { return custom; }
        };
        Component preview = JeiDisplayAdapters.reward(reward, null).notes().get(0);
        assertEquals(custom, preview);
        language(Map.of("example.reward", "Rank: %s", "example.rank", "Scholar", "example.suffix", "!"));
        assertEquals("Rank: Scholar!", preview.getString());
        language(Map.of("example.reward", "称号：%s", "example.rank", "学者", "example.suffix", "！"));
        assertEquals("称号：学者！", preview.getString());
        assertEquals(ChatFormatting.GOLD.getColor().intValue(), preview.getStyle().getColor().getValue());

        IReward legacyCustom = new IReward() {
            @Override public void grant(ServerPlayer player) { }
            @Override public String describe() { return "Existing custom reward"; }
        };
        assertEquals("Existing custom reward", legacyCustom.describeComponent().getString());
    }

    private static String key(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents()).getKey();
    }

    private void language(Map<String, String> values) {
        Language.inject(new Language() {
            @Override public String getOrDefault(String key, String fallback) { return values.getOrDefault(key, fallback); }
            @Override public boolean has(String key) { return values.containsKey(key); }
            @Override public boolean isDefaultRightToLeft() { return false; }
            @Override public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.forward(text.getString(), Style.EMPTY);
            }
        });
    }
}
