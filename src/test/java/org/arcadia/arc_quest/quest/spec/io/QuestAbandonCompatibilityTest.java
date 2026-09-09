package org.arcadia.arc_quest.quest.spec.io;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestAbandonCompatibilityTest {
    @Test
    void acceptsBothBranchFieldNames() {
        assertFalse(QuestSpecJsonReader.read("{\"abandonable\":false}").abandonable);
        assertFalse(QuestSpecJsonReader.read("{\"allowAbandon\":false}").abandonable);
        assertTrue(QuestSpecJsonReader.read("{}").abandonable);
    }

    @Test
    void explicitCanonicalFieldWinsWithoutMutatingInput() {
        JsonObject input = JsonParser.parseString(
                "{\"allowAbandon\":false,\"abandonable\":true}").getAsJsonObject();
        assertFalse(QuestSpecJsonReader.read(input).abandonable);
        assertTrue(input.has("abandonable"));
        assertFalse(QuestSpecJsonReader.read(
                "{\"abandonable\":true,\"allowAbandon\":false}").abandonable);
    }

    @Test
    void legacyJavaFieldWritesCanonicalJsonAndRoundTrips() {
        QuestSpec spec = new QuestSpec();
        spec.abandonable = false;
        String json = QuestSpecJsonWriter.write(spec);
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        assertFalse(object.get("allowAbandon").getAsBoolean());
        assertFalse(object.has("abandonable"));
        assertFalse(QuestSpecJsonReader.read(json).abandonable);
    }
}
