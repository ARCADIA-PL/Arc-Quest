package org.arcadia.arc_quest.guide.spec;

import org.arcadia.arc_quest.condition.ConditionSpec;

import java.util.ArrayList;
import java.util.List;

public class GuideSpec {
    public String id = "";
    public String category = "arc_quest:basics";
    public GuideTextSpec title = GuideTextSpec.literal("");
    public int sortOrder = 0;
    public boolean hidden = false;
    public boolean repeatablePopup = false;
    public List<ConditionSpec> unlockConditions = new ArrayList<>();
    public List<GuidePageSpec> pages = new ArrayList<>();
}
