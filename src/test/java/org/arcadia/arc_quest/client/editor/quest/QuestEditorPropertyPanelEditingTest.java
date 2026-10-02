package org.arcadia.arc_quest.client.editor.quest;

import org.arcadia.arc_quest.client.hud.component.HudRect;
import org.arcadia.arc_quest.quest.spec.ObjectiveSpec;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the property panel's transient editor selection.
 *
 * A pending Map key edit must never leak into the next Map value edit. The
 * interaction is intentionally driven through the real mouse/keyboard entry
 * points so this catches regressions in dispatch state, not just parser code.
 */
class QuestEditorPropertyPanelEditingTest {
    private static final HudRect WORKSPACE = new HudRect(0, 0, 800, 1200);

    @Test
    void selectingAnotherMapValueClearsPendingMapKeyMode() {
        QuestSpec document = new QuestSpec();
        PhaseSpec phase = new PhaseSpec();
        phase.phaseId = "phase";
        phase.objectives.add(new ObjectiveSpec());
        phase.objectives.get(0).extraData.put("first", "alpha");
        phase.objectives.get(0).extraData.put("second", "beta");
        phase.flagsToSetOnEnter.add("untouched");
        document.initialPhaseId = phase.phaseId;
        document.phases.add(phase);

        QuestEditorDocumentController controller = new QuestEditorDocumentController(document);
        QuestEditorPropertyPanel panel = new QuestEditorPropertyPanel();
        panel.reset(controller, phase);

        // PHASE root -> objectives list -> first objective -> extraData map.
        click(panel, controller, phase, 4);
        click(panel, controller, phase, 0);
        click(panel, controller, phase, 21);

        // Middle click the first key to begin editing that key.
        click(panel, controller, phase, 0, 2);
        // A left click on a different scalar Map row starts a value edit.
        click(panel, controller, phase, 1);
        replaceInput(panel, "gamma");
        assertTrue(panel.keyPressed(257, controller));

        assertEquals("alpha", phase.objectives.get(0).extraData.get("first"));
        assertEquals("gamma", phase.objectives.get(0).extraData.get("second"));
        assertEquals(2, phase.objectives.get(0).extraData.size());
        assertTrue(phase.objectives.get(0).extraData.containsKey("second"));
        assertEquals(java.util.List.of("untouched"), phase.flagsToSetOnEnter);
    }

    private static void click(QuestEditorPropertyPanel panel,
                              QuestEditorDocumentController controller,
                              PhaseSpec phase,
                              int row) {
        click(panel, controller, phase, row, 0);
    }

    private static void click(QuestEditorPropertyPanel panel,
                              QuestEditorDocumentController controller,
                              PhaseSpec phase,
                              int row,
                              int button) {
        int y = 52 + row * 22 + 1;
        assertTrue(panel.mouseClicked(500, y, button, WORKSPACE, controller, phase));
    }

    private static void replaceInput(QuestEditorPropertyPanel panel, String replacement) {
        // The selected Map value is "beta".
        for (int i = 0; i < 4; i++) assertTrue(panel.keyPressed(259, null));
        for (char c : replacement.toCharArray()) assertTrue(panel.charTyped(c));
    }
}
