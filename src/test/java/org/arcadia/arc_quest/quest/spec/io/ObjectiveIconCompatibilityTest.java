package org.arcadia.arc_quest.quest.spec.io;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.quest.api.QuestCategory;
import org.arcadia.arc_quest.quest.api.QuestText;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIconSpec;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.parity.QuestParityComparator;
import org.arcadia.arc_quest.quest.spec.validate.QuestSpecValidator;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ObjectiveIconCompatibilityTest {
    @BeforeAll
    static void initialize() {
        MinecraftRegistryTestBootstrap.initialize();
        assertNotNull(ObjectiveType.CUSTOM);
        assertNotNull(QuestCategory.ADVENTURE);
    }

    @Test
    void legacyConstructorsAndMissingNullOrExplicitAutoJsonRemainAuto() {
        var type = ObjectiveType.CUSTOM;
        var target = ResourceLocation.parse("example:target");
        assertSame(ObjectiveIcons.auto(), new ObjectiveEntry(type, target, 1, Component.literal("test"), false, false, Map.of()).getIcon());
        assertSame(ObjectiveIcons.auto(), new ObjectiveEntry(type, target, 1, QuestText.literal("test"), false, false, Map.of(), List.of(), null).getIcon());
        assertSame(ObjectiveIcons.auto(), new ObjectiveEntry("one", type, target, 1, QuestText.literal("test"), false, false, Map.of(), List.of(), null).getIcon());
        for (String icon : new String[]{"", ",\"icon\":null", ",\"icon\":{\"type\":\"arc_quest:auto\"}"}) {
            QuestSpec spec = quest(icon);
            assertSame(ObjectiveIcons.auto(), spec.phases.get(0).objectives.get(0).icon);
            assertFalse(objectiveJson(QuestSpecJsonWriter.write(spec)).has("icon"));
        }
    }

    @Test
    void builderLastWriteWinsAndPhaseIdNormalizationPreservesIcon() {
        var icon = ObjectiveIcons.texture("example:textures/gui/cow.png").region(0, 4, 16, 20);
        var builder = ObjectiveBuilder.nullObjective().icon(icon);
        var phase = PhaseBuilder.create("phase").objective(builder).build();
        assertEquals("objective_1", phase.getObjectives().get(0).getObjectiveId());
        assertEquals(icon, phase.getObjectives().get(0).getIcon());
        assertEquals(icon, builder.build().withObjectiveId("new_id").getIcon());
        assertSame(ObjectiveIcons.none(), builder.noIcon().build().getIcon());
        assertSame(ObjectiveIcons.auto(), builder.autoIcon().build().getIcon());
        assertEquals(icon.texture(), builder.iconTexture(icon.texture()).build().getIcon().texture());
        assertEquals(icon.texture(), builder.iconTexture(icon.texture().toString()).build().getIcon().texture());
        assertThrows(NullPointerException.class, () -> builder.icon(null));
    }

    @Test
    void allModesRoundTripAndCompileOnBothDefinitionPaths() {
        for (var icon : List.of(ObjectiveIcons.auto(), ObjectiveIcons.none(),
                ObjectiveIcons.texture("example:textures/gui/cow.png"),
                ObjectiveIcons.texture("example:textures/gui/atlas.png").region(5, 8, 16, 24),
                ObjectiveIcons.provider("missing_addon:custom_icon"))) {
            QuestSpec source = quest("");
            source.phases.get(0).objectives.get(0).icon = icon;
            String encoded = QuestSpecJsonWriter.write(source);
            QuestSpec decoded = QuestSpecJsonReader.read(encoded);
            assertEquals(icon, decoded.phases.get(0).objectives.get(0).icon);
            assertEquals(encoded, QuestSpecJsonWriter.write(decoded));
            assertFalse(new QuestSpecValidator().validate(decoded).hasErrors());
            assertEquals(icon, new QuestSpecCompiler().compile(decoded).getPhase("phase").getObjectives().get(0).getIcon());
            assertEquals(icon, QuestSpecCompiler.compileClientPresentation(decoded, Map.of())
                    .getPhase("phase").getObjectives().get(0).getIcon());
            assertTrue(new QuestParityComparator().compare(source, decoded).isEmpty());
        }
    }

    @Test
    void parityDetectsIconDifferencesButNormalizesLegacyNull() {
        QuestSpec expected = quest(""), actual = quest("");
        actual.phases.get(0).objectives.get(0).icon = null;
        assertTrue(new QuestParityComparator().compare(expected, actual).isEmpty());
        actual.phases.get(0).objectives.get(0).icon = ObjectiveIcons.none();
        assertTrue(new QuestParityComparator().compare(expected, actual).stream()
                .anyMatch(diff -> diff.path().equals("phases[0].objectives[0].icon")));
    }

    @Test
    void malformedIconDataIsRejectedBeforeEitherCompilerCanAcceptIt() {
        for (String icon : new String[]{
                "{}", "[]", "\"not an object\"", "{\"type\":\"addon:unknown\"}",
                "{\"type\":\"arc_quest:texture\"}",
                "{\"type\":\"arc_quest:texture\",\"texture\":\"bad ID\"}",
                "{\"type\":\"arc_quest:none\",\"texture\":\"example:a.png\"}",
                "{\"type\":\"arc_quest:texture\",\"texture\":\"example:a.png\",\"provider\":\"example:p\"}",
                "{\"type\":\"arc_quest:texture\",\"texture\":\"example:a.png\",\"region\":{\"x\":0,\"y\":0,\"width\":1.5,\"height\":16}}",
                "{\"type\":\"arc_quest:texture\",\"texture\":\"example:a.png\",\"region\":{\"x\":2147483647,\"y\":0,\"width\":1,\"height\":16}}",
                "{\"type\":\"arc_quest:provider\",\"provider\":\"https://invalid.test/p\"}"}) {
            var error = assertThrows(JsonParseException.class, () -> quest(",\"icon\":" + icon), icon);
            assertTrue(error.getMessage().contains("icon"), error.getMessage());
        }
    }

    private static QuestSpec quest(String iconField) {
        return QuestSpecJsonReader.read("{\"id\":\"example:icons\",\"initialPhaseId\":\"phase\",\"phases\":[{\"phaseId\":\"phase\",\"objectives\":[{\"id\":\"one\",\"type\":\"arc_quest:custom\",\"targetId\":\"example:target\""
                + iconField + "}]}]}");
    }

    private static JsonObject objectiveJson(String json) {
        return JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("phases").get(0)
                .getAsJsonObject().getAsJsonArray("objectives").get(0).getAsJsonObject();
    }
}
