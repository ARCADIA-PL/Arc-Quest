package org.arcadia.arc_quest.quest.builder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.quest.api.ItemTagNames;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class ObjectiveBuilderTranslationTest {
    @BeforeAll static void setup() { MinecraftRegistryTestBootstrap.initialize(); }
    @Test void everyOfficialObjectiveLabelHasBothLocalesAndAcceptsItsDisplayArguments() throws Exception {
        Path project = Path.of(System.getProperty("arcq.test.projectDir", "."));
        String builder = Files.readString(project.resolve("src/main/java/org/arcadia/arc_quest/quest/builder/ObjectiveBuilder.java"));
        var matcher = Pattern.compile("\"(arc_quest\\.obj\\.[a-z_]+)\"").matcher(builder);
        Set<String> keys = new LinkedHashSet<>();
        while (matcher.find()) keys.add(matcher.group(1));
        assertEquals(Set.of("arc_quest.obj.kill", "arc_quest.obj.collect", "arc_quest.obj.craft", "arc_quest.obj.possess",
                "arc_quest.obj.talk", "arc_quest.obj.deliver", "arc_quest.obj.reach", "arc_quest.obj.interact"), keys);
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            JsonObject translations = JsonParser.parseString(Files.readString(project.resolve(
                    "src/generated/resources/assets/arc_quest/lang/" + locale + ".json"))).getAsJsonObject();
            for (String key : keys) {
                assertTrue(translations.has(key), locale + " is missing " + key);
                String template = translations.get(key).getAsString();
                assertFalse(template.isBlank());
                String rendered = String.format(Locale.ROOT, template, "Zombie", 4);
                assertTrue(rendered.contains("Zombie"), locale + " must show the objective target for " + key);
                if (Set.of("kill", "collect", "craft", "deliver", "possess").contains(key.substring("arc_quest.obj.".length()))) {
                    assertTrue(rendered.contains("4"), locale + " must show the objective count for " + key);
                }
            }
        }
    }

    @Test void possessionFactoriesSupplyTheirTargetAndCountToTheDedicatedHoldingLabel() {
        var tag = ResourceLocation.parse("minecraft:logs");
        assertHoldingArguments(ObjectiveBuilder.possess(Items.APPLE, 4).build().getDisplayText(), Items.APPLE.getDescription());
        assertHoldingArguments(ObjectiveBuilder.possessTag(tag, 4).build().getDisplayText(), ItemTagNames.name(tag));
    }

    private static void assertHoldingArguments(Component component, Component target) {
        assertInstanceOf(TranslatableContents.class, component.getContents());
        var translated = (TranslatableContents) component.getContents();
        assertEquals("arc_quest.obj.possess", translated.getKey());
        assertEquals(2, translated.getArgs().length);
        assertEquals(target, translated.getArgs()[0]);
        assertEquals(4, translated.getArgs()[1]);
    }
}
