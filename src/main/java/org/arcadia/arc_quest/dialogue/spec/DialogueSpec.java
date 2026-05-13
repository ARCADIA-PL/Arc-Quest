package org.arcadia.arc_quest.dialogue.spec;

import org.arcadia.arc_quest.quest.spec.QuestVisualSpec;

import java.util.ArrayList;
import java.util.List;

public class DialogueSpec {
    public String id = "";
    public DialogueTextSpec defaultNpc = new DialogueTextSpec();
    public String startNodeId = "";
    public List<DialogueNodeSpec> nodes = new ArrayList<>();
    public QuestVisualSpec visualConfig = null;
    public boolean repeatable = true;
    public long cooldownSeconds = 0;
    public String cooldownType = "NONE";
    public int resetTimeTicks = 0;
    public List<NpcBindingSpec> npcBindings = new ArrayList<>();
    public List<EntityBindingSpec> entityBindings = new ArrayList<>();
}