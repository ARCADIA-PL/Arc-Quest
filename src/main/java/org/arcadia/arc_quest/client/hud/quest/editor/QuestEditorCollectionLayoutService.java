package org.arcadia.arc_quest.client.hud.quest.editor;

import org.arcadia.arc_quest.quest.editor.model.EditableCollectionCategory;
import org.arcadia.arc_quest.quest.editor.model.EditableCollectionEntry;
import org.arcadia.arc_quest.quest.editor.model.EditableQuest;

import java.util.*;

public class QuestEditorCollectionLayoutService {
    private static final double CAT_W = 300;
    private static final double CAT_H = 80;
    private static final double ENTRY_W = 130;
    private static final double ENTRY_H = 72;

    public CollectionLayout layout(EditableQuest quest, Set<String> collapsedCategoryIds) {
        CollectionLayout out = new CollectionLayout();
        if (quest == null || quest.collectionConfig == null) return out;

        boolean showCategories = quest.collectionConfig.showCategories;
        List<EditableCollectionCategory> categories = new ArrayList<>(quest.collectionConfig.categories);
        categories.sort(Comparator.comparingInt(c -> c.sortOrder));

        if (!showCategories) {
            List<EditableCollectionEntry> all = new ArrayList<>(quest.collectionEntries);
            all.sort(Comparator.comparingInt(e -> e.sortOrder));
            for (int i = 0; i < all.size(); i++) {
                EditableCollectionEntry e = all.get(i);
                CollectionEntryLayout l = new CollectionEntryLayout();
                l.entryId = e.entryId;
                l.categoryId = e.categoryId;
                l.x = 40 + (i % 5) * (ENTRY_W + 16);
                l.y = 40 + (i / 5) * (ENTRY_H + 12);
                l.w = ENTRY_W;
                l.h = ENTRY_H;
                out.globalEntries.add(l);
            }
            computeBoundsGlobal(out);
            return out;
        }

        double yCursor = 40;
        for (EditableCollectionCategory c : categories) {
            CollectionCategoryLayout cl = new CollectionCategoryLayout();
            cl.categoryId = c.categoryId;
            cl.x = 40;
            cl.y = yCursor;
            cl.w = CAT_W;
            cl.h = CAT_H;
            cl.collapsed = collapsedCategoryIds != null && collapsedCategoryIds.contains(c.categoryId);

            if (!cl.collapsed) {
                List<EditableCollectionEntry> entries = new ArrayList<>();
                for (EditableCollectionEntry e : quest.collectionEntries) {
                    if (c.categoryId.equals(e.categoryId)) entries.add(e);
                }
                entries.sort(Comparator.comparingInt(e -> e.sortOrder));
                for (int i = 0; i < entries.size(); i++) {
                    EditableCollectionEntry e = entries.get(i);
                    CollectionEntryLayout el = new CollectionEntryLayout();
                    el.entryId = e.entryId;
                    el.categoryId = e.categoryId;
                    el.x = cl.x + (i % 2) * (ENTRY_W + 12);
                    el.y = cl.y + CAT_H + 8 + (i / 2) * (ENTRY_H + 10);
                    el.w = ENTRY_W;
                    el.h = ENTRY_H;
                    cl.entries.add(el);
                }
                int rows = (entries.size() + 1) / 2;
                cl.h = CAT_H + (rows == 0 ? 0 : (8 + rows * ENTRY_H + Math.max(0, rows - 1) * 10)) + 12;
            }

            out.categories.add(cl);
            yCursor += cl.h + 16;
        }

        computeBoundsCategory(out);
        return out;
    }

    private void computeBoundsGlobal(CollectionLayout out) {
        if (out.globalEntries.isEmpty()) return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (CollectionEntryLayout e : out.globalEntries) {
            minX = Math.min(minX, e.x);
            minY = Math.min(minY, e.y);
            maxX = Math.max(maxX, e.x + e.w);
            maxY = Math.max(maxY, e.y + e.h);
        }
        out.bounds.minX = minX;
        out.bounds.minY = minY;
        out.bounds.maxX = maxX;
        out.bounds.maxY = maxY;
    }

    private void computeBoundsCategory(CollectionLayout out) {
        if (out.categories.isEmpty()) return;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (CollectionCategoryLayout c : out.categories) {
            minX = Math.min(minX, c.x);
            minY = Math.min(minY, c.y);
            maxX = Math.max(maxX, c.x + c.w);
            maxY = Math.max(maxY, c.y + c.h);
            for (CollectionEntryLayout e : c.entries) {
                minX = Math.min(minX, e.x);
                minY = Math.min(minY, e.y);
                maxX = Math.max(maxX, e.x + e.w);
                maxY = Math.max(maxY, e.y + e.h);
            }
        }
        out.bounds.minX = minX;
        out.bounds.minY = minY;
        out.bounds.maxX = maxX;
        out.bounds.maxY = maxY;
    }
}
