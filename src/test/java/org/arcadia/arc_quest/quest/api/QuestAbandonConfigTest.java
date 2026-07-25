package org.arcadia.arc_quest.quest.api;

import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestAbandonConfigTest {

    @Test
    void questsAllowAbandonByDefault() {
        QuestSpec spec = QuestSpecJsonReader.read("{}\n");

        assertTrue(spec.allowAbandon);
        assertTrue(buildQuest(false).isAbandonAllowed());
    }

    @Test
    void jsonAndBuilderCanDisableAbandon() {
        QuestSpec spec = QuestSpecJsonReader.read("""
                {
                  "id": "arc_quest:abandon_config_test",
                  "allowAbandon": false,
                  "initialPhaseId": "start",
                  "phases": [
                    {
                      "phaseId": "start",
                      "objectives": [
                        {
                          "id": "noop",
                          "type": "arc_quest:null"
                        }
                      ]
                    }
                  ]
                }
                """);

        assertFalse(spec.allowAbandon);
        assertFalse(new QuestSpecCompiler().compile(spec).isAbandonAllowed());
        assertFalse(buildQuest(true).isAbandonAllowed());
    }

    private QuestDefinition buildQuest(boolean cannotAbandon) {
        QuestBuilder builder = QuestBuilder.create("abandon_config_test")
                .phase(PhaseBuilder.create("start")
                        .objective(ObjectiveBuilder.nullObjective()));
        if (cannotAbandon) builder.cannotAbandon();
        return builder.build();
    }
}
