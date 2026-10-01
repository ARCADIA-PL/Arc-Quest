package org.arcadia.arc_quest.client.editor.quest;

import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;
import org.arcadia.arc_quest.quest.spec.ObjectiveSpec;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class QuestEditorIconHistoryTest {
    @Test
    void undoRedoKeepImmutableTextureAndUnknownProviderIds() {
        QuestSpec document = new QuestSpec();
        PhaseSpec phase = new PhaseSpec();
        phase.phaseId = "phase";
        phase.objectives.add(new ObjectiveSpec());
        document.phases.add(phase);
        var controller = new QuestEditorDocumentController(document);
        var texture = ObjectiveIcons.texture("example:textures/gui/atlas.png").region(4, 8, 16, 16);
        var provider = ObjectiveIcons.provider("uninstalled:provider");
        controller.mutate(spec -> spec.phases.get(0).objectives.get(0).icon = texture);
        controller.mutate(spec -> spec.phases.get(0).objectives.get(0).icon = provider);
        controller.undo();
        assertEquals(texture, controller.document().phases.get(0).objectives.get(0).icon);
        controller.undo();
        assertSame(ObjectiveIcons.auto(), controller.document().phases.get(0).objectives.get(0).icon);
        controller.redo();
        controller.redo();
        assertEquals(provider, controller.document().phases.get(0).objectives.get(0).icon);
    }
}
