package org.arcadia.arc_quest.quest.editor.service;

import org.arcadia.arc_quest.client.hud.quest.editor.CategoryDeletePolicy;
import org.arcadia.arc_quest.client.hud.quest.editor.EditorObjectRef;
import org.arcadia.arc_quest.quest.api.CountingMode;
import org.arcadia.arc_quest.quest.api.EntryRewardGrantMode;
import org.arcadia.arc_quest.quest.api.HiddenPresentationMode;
import org.arcadia.arc_quest.quest.api.VisibilityMode;
import org.arcadia.arc_quest.quest.editor.model.*;
import org.arcadia.arc_quest.quest.spec.QuestTextSpec;

import java.util.*;

public class EditableCollectionService {

    public EditableCollectionCategory addCategory(EditableQuest quest, String categoryId) {
        EditableCollectionCategory category = new EditableCollectionCategory();
        category.categoryId = categoryId == null ? "" : categoryId;
        category.displayName = QuestTextSpec.literal(category.categoryId);
        category.sortOrder = quest.collectionConfig.categories.size();
        quest.collectionConfig.categories.add(category);
        return category;
    }

    public boolean removeCategory(EditableQuest quest, String categoryId, CategoryDeletePolicy policy, String defaultCategoryId) {
        EditableCollectionCategory category = getCategory(quest, categoryId);
        if (category == null) return false;

        List<EditableCollectionEntry> owned = entriesByCategory(quest, categoryId);
        if (!owned.isEmpty()) {
            if (policy == CategoryDeletePolicy.BLOCK_IF_NOT_EMPTY) return false;
            if (policy == CategoryDeletePolicy.MOVE_ENTRIES_TO_DEFAULT) {
                EditableCollectionCategory defaultCategory = getCategory(quest, defaultCategoryId);
                if (defaultCategory == null) return false;
                for (EditableCollectionEntry entry : owned) entry.categoryId = defaultCategory.categoryId;
            } else if (policy == CategoryDeletePolicy.DELETE_WITH_ENTRIES) {
                quest.collectionEntries.removeIf(e -> categoryId.equals(e.categoryId));
            }
        }

        boolean removed = quest.collectionConfig.categories.removeIf(c -> categoryId.equals(c.categoryId));
        if (removed) normalizeCategorySort(quest);
        return removed;
    }

    public boolean updateCategoryBasics(EditableQuest quest, String categoryId, String displayName, String iconTexture) {
        EditableCollectionCategory category = getCategory(quest, categoryId);
        if (category == null) return false;
        category.displayName = QuestTextSpec.literal(displayName == null ? "" : displayName);
        category.iconTexture = iconTexture == null ? "" : iconTexture;
        return true;
    }

    public boolean moveCategory(EditableQuest quest, String categoryId, int toIndex) {
        List<EditableCollectionCategory> list = quest.collectionConfig.categories;
        int from = indexOfCategory(list, categoryId);
        if (from < 0) return false;
        int target = Math.max(0, Math.min(toIndex, list.size() - 1));
        if (from == target) return false;
        EditableCollectionCategory c = list.remove(from);
        list.add(target, c);
        normalizeCategorySort(quest);
        return true;
    }

    public EditableCollectionEntry addEntry(EditableQuest quest, String categoryId, String entryId) {
        if (getCategory(quest, categoryId) == null) return null;
        EditableCollectionEntry entry = new EditableCollectionEntry();
        entry.entryId = entryId == null ? "" : entryId;
        entry.categoryId = categoryId;
        entry.sortOrder = entriesByCategory(quest, categoryId).size();
        quest.collectionEntries.add(entry);
        return entry;
    }

    public boolean removeEntry(EditableQuest quest, String entryId) {
        EditableCollectionEntry e = getEntry(quest, entryId);
        if (e == null) return false;
        String categoryId = e.categoryId;
        boolean removed = quest.collectionEntries.removeIf(x -> entryId.equals(x.entryId));
        if (removed) normalizeEntrySort(quest, categoryId);
        return removed;
    }

    public boolean moveEntryToCategory(EditableQuest quest, String entryId, String targetCategoryId) {
        EditableCollectionEntry e = getEntry(quest, entryId);
        if (e == null || getCategory(quest, targetCategoryId) == null) return false;
        String old = e.categoryId;
        e.categoryId = targetCategoryId;
        e.sortOrder = entriesByCategory(quest, targetCategoryId).size() - 1;
        normalizeEntrySort(quest, old);
        normalizeEntrySort(quest, targetCategoryId);
        return true;
    }

    public boolean moveEntry(EditableQuest quest, String entryId, int toIndex) {
        EditableCollectionEntry e = getEntry(quest, entryId);
        if (e == null) return false;
        String categoryId = e.categoryId;
        List<EditableCollectionEntry> list = entriesByCategory(quest, categoryId);
        int from = indexOfEntry(list, entryId);
        if (from < 0) return false;
        int target = Math.max(0, Math.min(toIndex, list.size() - 1));
        if (from == target) return false;
        EditableCollectionEntry item = list.remove(from);
        list.add(target, item);
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < list.size(); i++) order.put(list.get(i).entryId, i);
        for (EditableCollectionEntry raw : quest.collectionEntries) {
            if (categoryId.equals(raw.categoryId) && order.containsKey(raw.entryId))
                raw.sortOrder = order.get(raw.entryId);
        }
        return true;
    }

    public boolean updateEntryVisibility(EditableQuest quest, String entryId, VisibilityMode mode, HiddenPresentationMode hiddenMode, boolean showInTracker) {
        EditableCollectionEntry e = getEntry(quest, entryId);
        if (e == null) return false;
        e.visibilityMode = mode == null ? VisibilityMode.VISIBLE_BY_DEFAULT : mode;
        e.hiddenPresentationMode = hiddenMode == null ? HiddenPresentationMode.FULLY_HIDDEN : hiddenMode;
        e.showInTrackerByDefault = showInTracker;
        return true;
    }

    public boolean updateEntryCounting(EditableQuest quest, String entryId, CountingMode countingMode, int completionTarget, int maxCount, boolean repeatableProgress, boolean repeatableCompletion) {
        EditableCollectionEntry e = getEntry(quest, entryId);
        if (e == null) return false;
        e.countingMode = countingMode == null ? CountingMode.BINARY : countingMode;
        e.completionTarget = Math.max(0, completionTarget);
        e.maxCount = Math.max(0, maxCount);
        e.repeatableProgress = repeatableProgress;
        e.repeatableCompletion = repeatableCompletion;
        return true;
    }

    public boolean updateEntryRewardGrantMode(EditableQuest quest, String entryId, EntryRewardGrantMode grantMode) {
        EditableCollectionEntry e = getEntry(quest, entryId);
        if (e == null) return false;
        e.rewardGrantMode = grantMode == null ? EntryRewardGrantMode.AUTO : grantMode;
        return true;
    }

    public EditableCollectionRewardNode addEntryRewardNode(EditableQuest quest, String entryId, String rewardNodeId) {
        EditableCollectionEntry entry = getEntry(quest, entryId);
        if (entry == null) return null;
        EditableCollectionRewardNode node = new EditableCollectionRewardNode();
        node.rewardNodeId = rewardNodeId == null ? "" : rewardNodeId;
        node.ownerId = entryId;
        entry.rewardNodes.add(node);
        return node;
    }

    public boolean updateEntryRewardNode(EditableQuest quest, String entryId, String rewardNodeId, EntryRewardGrantMode grantMode) {
        EditableCollectionRewardNode node = getEntryRewardNode(quest, entryId, rewardNodeId);
        if (node == null) return false;
        node.grantMode = grantMode == null ? EntryRewardGrantMode.AUTO : grantMode;
        return true;
    }

    public boolean removeEntryRewardNode(EditableQuest quest, String entryId, String rewardNodeId) {
        EditableCollectionEntry entry = getEntry(quest, entryId);
        if (entry == null) return false;
        return entry.rewardNodes.removeIf(n -> rewardNodeId.equals(n.rewardNodeId));
    }

    public EditableCollectionCompletionRule addCategoryCompletionRule(EditableQuest quest, String categoryId, String type, String expression) {
        EditableCollectionCategory c = getCategory(quest, categoryId);
        if (c == null) return null;
        EditableCollectionCompletionRule r = new EditableCollectionCompletionRule();
        r.type = type == null ? "" : type;
        r.expression = expression == null ? "" : expression;
        c.completionRules.add(r);
        return r;
    }

    public boolean updateCategoryCompletionRule(EditableQuest quest, String categoryId, int ruleIndex, String type, String expression) {
        EditableCollectionCategory c = getCategory(quest, categoryId);
        if (c == null || ruleIndex < 0 || ruleIndex >= c.completionRules.size()) return false;
        EditableCollectionCompletionRule r = c.completionRules.get(ruleIndex);
        r.type = type == null ? "" : type;
        r.expression = expression == null ? "" : expression;
        return true;
    }

    public boolean removeCategoryCompletionRule(EditableQuest quest, String categoryId, int ruleIndex) {
        EditableCollectionCategory c = getCategory(quest, categoryId);
        if (c == null || ruleIndex < 0 || ruleIndex >= c.completionRules.size()) return false;
        c.completionRules.remove(ruleIndex);
        return true;
    }

    public boolean deleteObject(EditableQuest quest, EditorObjectRef ref, CategoryDeletePolicy categoryPolicy, String defaultCategoryId) {
        if (ref == null || ref.type() == null) return false;
        return switch (ref.type()) {
            case CATEGORY -> removeCategory(quest, ref.id(), categoryPolicy, defaultCategoryId);
            case ENTRY -> removeEntry(quest, ref.id());
            case REWARD_NODE -> removeEntryRewardNode(quest, ref.ownerId(), ref.id());
            case COMPLETION_RULE -> removeCategoryCompletionRule(quest, ref.ownerId(), parseRuleIndex(ref.id()));
        };
    }

    private int parseRuleIndex(String id) {
        try {
            return Integer.parseInt(id);
        } catch (Exception ex) {
            return -1;
        }
    }

    private EditableCollectionCategory getCategory(EditableQuest quest, String categoryId) {
        return quest.collectionConfig.categories.stream().filter(c -> categoryId.equals(c.categoryId)).findFirst().orElse(null);
    }

    private EditableCollectionEntry getEntry(EditableQuest quest, String entryId) {
        return quest.collectionEntries.stream().filter(e -> entryId.equals(e.entryId)).findFirst().orElse(null);
    }

    private EditableCollectionRewardNode getEntryRewardNode(EditableQuest quest, String entryId, String rewardNodeId) {
        EditableCollectionEntry e = getEntry(quest, entryId);
        if (e == null) return null;
        return e.rewardNodes.stream().filter(n -> rewardNodeId.equals(n.rewardNodeId)).findFirst().orElse(null);
    }

    private List<EditableCollectionEntry> entriesByCategory(EditableQuest quest, String categoryId) {
        List<EditableCollectionEntry> list = new ArrayList<>();
        for (EditableCollectionEntry e : quest.collectionEntries) if (categoryId.equals(e.categoryId)) list.add(e);
        list.sort(Comparator.comparingInt(x -> x.sortOrder));
        return list;
    }

    private int indexOfCategory(List<EditableCollectionCategory> list, String categoryId) {
        for (int i = 0; i < list.size(); i++) if (categoryId.equals(list.get(i).categoryId)) return i;
        return -1;
    }

    private int indexOfEntry(List<EditableCollectionEntry> list, String entryId) {
        for (int i = 0; i < list.size(); i++) if (entryId.equals(list.get(i).entryId)) return i;
        return -1;
    }

    private void normalizeCategorySort(EditableQuest quest) {
        for (int i = 0; i < quest.collectionConfig.categories.size(); i++)
            quest.collectionConfig.categories.get(i).sortOrder = i;
    }

    private void normalizeEntrySort(EditableQuest quest, String categoryId) {
        List<EditableCollectionEntry> list = entriesByCategory(quest, categoryId);
        Map<String, Integer> order = new HashMap<>();
        for (int i = 0; i < list.size(); i++) order.put(list.get(i).entryId, i);
        for (EditableCollectionEntry raw : quest.collectionEntries) {
            if (categoryId.equals(raw.categoryId) && order.containsKey(raw.entryId))
                raw.sortOrder = order.get(raw.entryId);
        }
    }
}
