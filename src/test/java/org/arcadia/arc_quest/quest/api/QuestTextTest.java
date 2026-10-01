package org.arcadia.arc_quest.quest.api;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class QuestTextTest {

    @Test
    void preservesExplicitArgumentsInClientFallback() {
        Component key = Component.keybind("test.open_map");
        QuestText text = QuestText.translatable("arc_quest.test.instructions",
                QuestText.Arg.of((player, context) -> Component.literal("server"), key));

        TranslatableContents contents = assertInstanceOf(
                TranslatableContents.class, text.resolve(null, QuestTextContext.empty()).getContents());

        assertEquals(1, contents.getArgs().length);
        assertEquals(key, contents.getArgs()[0]);
    }

    @Test
    void preservesConstantArgumentsInClientFallback() {
        Component item = Component.literal("Diamond");
        QuestText text = QuestText.translatable("arc_quest.test.objective",
                QuestText.Arg.constant(item), QuestText.Arg.constant(3));

        TranslatableContents contents = contents(text);

        assertEquals(2, contents.getArgs().length);
        assertSame(item, contents.getArgs()[0]);
        assertEquals(3, contents.getArgs()[1]);
    }

    @Test
    void keepsLegacyResolverWithoutFallbackBehavior() {
        QuestText text = QuestText.translatable("arc_quest.test.player",
                QuestText.Arg.of((player, context) -> player.getName()));

        assertEquals(0, contents(text).getArgs().length);
    }

    @Test
    void keepsArgumentPositionsWhenOnlySomeFallbacksAreAvailable() {
        Component key = Component.keybind("test.open_map");
        QuestText text = QuestText.translatable("arc_quest.test.mixed",
                QuestText.Arg.of((player, context) -> player.getName()),
                QuestText.Arg.of((player, context) -> Component.literal("server"), key));

        Object[] args = contents(text).getArgs();
        assertEquals(2, args.length);
        assertEquals("<?>", ((Component) args[0]).getString());
        assertSame(key, args[1]);
    }

    private static TranslatableContents contents(QuestText text) {
        return assertInstanceOf(TranslatableContents.class,
                text.resolve(null, QuestTextContext.empty()).getContents());
    }
}
