package org.arcadia.arc_quest.quest.spec;

import java.util.*;

public class CollectionAtlasSpecData {
    public int schemaVersion = 1;
    public String id = "", profileId = "";
    public List<Category> categories = new ArrayList<>();

    public static class Category {
        public String categoryId = "";
        public Layout layout;
        public String unassignedEntries = "AUTO_GROUP";
        public List<Group> groups = new ArrayList<>();
    }

    public static class Group {
        public String groupId = "";
        public QuestTextSpec title = QuestTextSpec.literal("");
        public Layout layout;
        public List<String> entries = new ArrayList<>();
        public int columns = 5;
        public double x, y;
        public List<Node> nodes = new ArrayList<>();
    }

    public static class Layout {
        public String type = "arc_quest:node_grid";
        public Map<String, Double> params = new LinkedHashMap<>();
    }

    public static class Node {
        public String entryId = "";
        public double x, y;
    }
}
