package org.arcadia.arc_quest.quest.spec;

import java.util.*;

public class CollectionSourceSetSpecData {
    public int schemaVersion = 1;
    public String id = "";
    public List<Relation> relations = new ArrayList<>();

    public static class Endpoint {
        public String type = "COLLECTION_ENTRY", entryId = "";
        public List<String> variantIds = new ArrayList<>();
    }

    public static class Relation {
        public String relationId = "", method = "", disclosure = "PUBLIC_WHEN_ENDPOINTS_VISIBLE";
        public Endpoint source = new Endpoint(), target = new Endpoint();
        public QuestTextSpec description = QuestTextSpec.literal("");
        public int sortOrder;
    }
}
