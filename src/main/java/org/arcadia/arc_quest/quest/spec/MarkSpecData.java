package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.questmarker.api.MarkTrigger;
import org.arcadia.arc_quest.questmarker.api.MarkTriggers;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerType;

import java.util.LinkedHashMap;
import java.util.Map;

public class MarkSpecData {
    public String id = "";
    public MarkTargetSpec target = new MarkTargetSpec();
    public MarkActivationSpec activateWhen = null;
    public MarkActivationSpec deactivateWhen = null;
    public QuestMarkerType markerType = QuestMarkerType.QUEST_OBJECTIVE;
    public int priority = 0;
    public int maxDistance = 256;
    public int refreshTicks = 20;
    public boolean trackMovingEntity = true;
    public boolean oneShot = false;
    public MarkTrigger trigger = MarkTrigger.CONTINUOUS;
    public int durationTicks = MarkTriggers.DEFAULT_TRIGGER_DURATION_TICKS;
    public Map<String, String> styleHints = new LinkedHashMap<>();
}
