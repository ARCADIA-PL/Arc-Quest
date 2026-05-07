package org.arcadia.arc_quest.client.hud.quest.editor;

import java.util.ArrayList;
import java.util.List;

public class CollectionCategoryLayout {
    public String categoryId = "";
    public double x;
    public double y;
    public double w;
    public double h;
    public boolean collapsed;
    public List<CollectionEntryLayout> entries = new ArrayList<>();
}
