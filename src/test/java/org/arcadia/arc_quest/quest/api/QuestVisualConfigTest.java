package org.arcadia.arc_quest.quest.api;

import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestVisualConfigTest {

    @Test
    void questSplashPresentationIsDisabledByDefault() {
        assertFalse(QuestVisualConfig.builder().build().usesQuestSplashPresentation());
    }

    @Test
    void builderAndPhaseJsonCanEnableQuestSplashPresentation() {
        assertTrue(QuestVisualConfig.builder()
                .useQuestSplashPresentation(true)
                .build()
                .usesQuestSplashPresentation());

        QuestSpec spec = QuestSpecJsonReader.read("""
                {
                  "id": "arc_quest:test",
                  "phases": [
                    {
                      "phaseId": "phase_1",
                      "visualConfig": {
                        "useQuestSplashPresentation": true
                      }
                    }
                  ]
                }
                """);

        assertNotNull(spec.phases.get(0).visualConfig);
        assertTrue(spec.phases.get(0).visualConfig.useQuestSplashPresentation);
    }
}
