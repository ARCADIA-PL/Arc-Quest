package org.arcadia.arc_quest.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.dialogue.spec.DialogueSpec;
import org.arcadia.arc_quest.dialogue.spec.io.DialogueSpecJsonReader;
import org.arcadia.arc_quest.guide.spec.GuideSpec;
import org.arcadia.arc_quest.guide.spec.io.GuideSpecJsonReader;
import org.arcadia.arc_quest.npc.spec.NpcSpec;
import org.arcadia.arc_quest.npc.spec.io.NpcSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.api.CompareOp;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.trade.gacha.spec.GachaShopSpec;
import org.arcadia.arc_quest.trade.gacha.spec.io.GachaSpecJsonReader;
import org.arcadia.arc_quest.trade.spec.TradeShopSpec;
import org.arcadia.arc_quest.trade.spec.io.TradeSpecJsonReader;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** 编辑器与 Java reader 共用的条件契约夹具。 */
class ConditionContractJsonReaderTest {
    private static JsonObject fixture(String name) throws Exception {
        try (InputStream stream = ConditionContractJsonReaderTest.class.getResourceAsStream(
                "/editor/condition-contract.json")) {
            assertNotNull(stream);
            JsonObject root = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return root.getAsJsonObject(name);
        }
    }

    private static void assertVariable(ConditionSpec condition) {
        assertEquals("arc_quest:variable_check", condition.condition);
        assertEquals("score", condition.key);
        assertEquals(">=", condition.op);
        assertEquals(0, condition.value);
        assertEquals(CompareOp.GREATER_OR_EQUAL, CompareOp.fromSymbol(condition.op));
        assertTrue(CompareOp.fromSymbol(condition.op).evaluate(1, condition.value));
        assertFalse(CompareOp.fromSymbol(condition.op).evaluate(-1, condition.value));
    }

    @Test
    void allDomainReadersPreserveCamelCaseConditionFields() throws Exception {
        QuestSpec quest = QuestSpecJsonReader.read(fixture("quest"));
        assertEquals("arc_quest:contract", quest.id);
        assertVariable(quest.unlockConditions.get(0));
        assertEquals("arc_quest:quest_phase_completed", quest.phases.get(0).enterCondition.condition);
        assertEquals("intro", quest.phases.get(0).enterCondition.phaseId);
        assertEquals("{}", quest.phases.get(0).transitions.get(0).condition.predicate.get("nbt").getAsString());

        DialogueSpec dialogue = DialogueSpecJsonReader.read(fixture("dialogue"));
        assertEquals("arc_quest:contract_dialogue", dialogue.id);
        assertEquals("default_say", dialogue.nodes.get(0).defaultSayId);
        ConditionSpec hold = dialogue.nodes.get(0).choices.get(0).conditions.get(0);
        assertEquals("arc_quest:hold_item", hold.condition);
        assertEquals("minecraft:diamond", hold.itemId);
        assertEquals("inventory", hold.itemSource);
        assertEquals(2, hold.count);
        assertEquals(-5, dialogue.nodes.get(0).choices.get(0).priority);
        assertEquals("contract:action", dialogue.nodes.get(0).choices.get(0).actions.get(0).customTypeId);
        assertEquals("", dialogue.nodes.get(0).choices.get(0).actions.get(0).customData.get("empty"));
        var payload = dialogue.nodes.get(0).choices.get(0).actions.get(0).customData;
        assertTrue(payload.containsKey("none"));
        assertNull(payload.get("none"));
        assertEquals(false, payload.get("off"));
        assertEquals(0.0, payload.get("zero"));
        assertEquals(java.util.List.of(), payload.get("list"));
        assertEquals(java.util.Map.of(), payload.get("object"));
        assertEquals(-2, dialogue.nodes.get(0).relatedMarks.get(0).priority);
        assertEquals("intro", dialogue.nodes.get(0).conditionalTexts.get("special").conditions.get(0).fromPhaseId);
        assertEquals("end", dialogue.nodes.get(0).conditionalTexts.get("special").conditions.get(0).toPhaseId);

        NpcSpec npc = NpcSpecJsonReader.read(fixture("npc"));
        assertEquals("npc_id", npc.interactCondition.nbtKey);
        assertEquals(-7, npc.bindings.get(0).priority);
        assertVariable(npc.bindings.get(0).condition.conditions.get(0));

        TradeShopSpec trade = TradeSpecJsonReader.read(fixture("trade"));
        assertVariable(trade.entries.get("diamond").visibleCondition);
        assertEquals("unlocked", trade.entries.get("diamond").canBuyCondition.flag);

        GachaShopSpec gacha = GachaSpecJsonReader.read(fixture("gacha"));
        assertVariable(gacha.drawCondition);
        assertEquals("unlocked", gacha.resetCondition.flag);
        assertVariable(gacha.pity.resetCondition);
        assertEquals("arc_quest:quest_phase_completed", gacha.pools.get(0).items.get(0).visibleCondition.condition);

        GuideSpec guide = GuideSpecJsonReader.read(fixture("guide"));
        assertVariable(guide.unlockConditions.get(0));
    }
}
