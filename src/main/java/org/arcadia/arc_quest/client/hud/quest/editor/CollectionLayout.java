package org.arcadia.arc_quest.client.hud.quest.editor;

import java.util.ArrayList;
import java.util.List;

public class CollectionLayout {
    public List<CollectionCategoryLayout> categories = new ArrayList<>();
    public List<CollectionEntryLayout> globalEntries = new ArrayList<>();
    public GraphBounds bounds = new GraphBounds();
}
