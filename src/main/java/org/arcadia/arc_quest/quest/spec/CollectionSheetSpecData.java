package org.arcadia.arc_quest.quest.spec;

import java.util.ArrayList;
import java.util.List;

public class CollectionSheetSpecData {
    public List<EntryRequirementBindingSpecData> bindings = new ArrayList<>();
    public String completionPolicy = "ALL";
    /** Explicit quota only; ALL derives the threshold from required bindings. */
    public int requiredCount = 0;
    public boolean countDistinctEntries = false;
}
