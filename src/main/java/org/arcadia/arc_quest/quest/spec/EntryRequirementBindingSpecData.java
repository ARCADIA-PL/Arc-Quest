package org.arcadia.arc_quest.quest.spec;

import java.util.ArrayList;
import java.util.List;

public class EntryRequirementBindingSpecData {
    public String bindingId = "";
    public String entryId = "";
    public List<String> objectiveIds = new ArrayList<>();
    public List<CollectionRecordRequirementSpecData> recordRequirements = new ArrayList<>();
    public String requirementMode = "ALL";
    public String recordPolicy = "EXISTING_RECORDS";
    public boolean optional = false;
    public int sortOrder = 0;
}
