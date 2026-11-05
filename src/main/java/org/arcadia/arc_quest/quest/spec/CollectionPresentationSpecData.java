package org.arcadia.arc_quest.quest.spec;

import java.util.*;

public class CollectionPresentationSpecData {
    public Subject subject;
    public List<String> obtainableAnyFlags = new ArrayList<>();
    public List<Variant> variants = new ArrayList<>();
    public String defaultVariantId = "";
    public List<String> sourceSetIds = new ArrayList<>();
    public List<DetailPage> detailPages = new ArrayList<>();

    public static class DetailPage {
        public String id = "", type = "TEXT";
        public QuestTextSpec title = QuestTextSpec.literal("");
        public List<QuestTextSpec> paragraphs = new ArrayList<>();
        public int providerSection = -1;
        public int providerSectionCount = 1;
    }

    public static class Subject {
        public String type = "arc_quest:item_stack", id = "", snbt = "";
        public com.google.gson.JsonObject params;
        public int count = 1;
        public String matchMode = "ITEM", matchSnbt = "";
        public Map<String, String> parameters = new LinkedHashMap<>();
    }

    public static class Variant {
        public String variantId = "", outcomeId = "", disclosure = "ALWAYS";
        public QuestTextSpec label = QuestTextSpec.literal("");
        public Subject subject;
    public List<String> obtainableAnyFlags = new ArrayList<>();
        public RecordRef recordRef;
    }

    public static class RecordRef {
        public String type = "OUTCOME", outcomeId = "";
    }
}
