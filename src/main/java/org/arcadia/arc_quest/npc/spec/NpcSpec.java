package org.arcadia.arc_quest.npc.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;

import java.util.ArrayList;
import java.util.List;

public class NpcSpec {
    public String entityType = "";
    public List<NpcBindingSpec> bindings = new ArrayList<>();
    public boolean cancelVanillaInteract = true;
    public double dialogueDistance = 8.0;
    public boolean shouldLookAtPlayer = true;
    public boolean shouldStopMoving = true;
    public ConditionSpec interactCondition = null;
    public List<String> onDialogueStartCommands = new ArrayList<>();
    public List<String> onDialogueEndCommands = new ArrayList<>();
}