package org.arcadia.arc_quest.trade.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.quest.capability.IQuestCapability;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class TradeShopDefinition {

    private final String shopId;
    private final TradeText displayName;
    @Nullable
    private final TradeText description;
    private final List<TradeCategory> categories;
    private final LinkedHashMap<String, TradeEntry> entries;
    @Nullable
    private final ICondition openCondition;
    private final boolean simpleMode;
    private final int themeColor;
    @Nullable
    private final SoundEvent openSound;
    @Nullable
    private final SoundEvent closeSound;

    public TradeShopDefinition(String shopId,
                               TradeText displayName,
                               @Nullable TradeText description,
                               List<TradeCategory> categories,
                               LinkedHashMap<String, TradeEntry> entries,
                               @Nullable ICondition openCondition,
                               boolean simpleMode,
                               int themeColor,
                               @Nullable SoundEvent openSound,
                               @Nullable SoundEvent closeSound) {
        this.shopId = Objects.requireNonNull(shopId);
        this.displayName = Objects.requireNonNull(displayName);
        this.description = description;
        this.categories = Collections.unmodifiableList(categories);
        this.entries = new LinkedHashMap<>(entries);
        this.openCondition = openCondition;
        this.simpleMode = simpleMode;
        this.themeColor = themeColor;
        this.openSound = openSound;
        this.closeSound = closeSound;
    }

    public String getShopId() { return shopId; }
    public Component getDisplayName() { return displayName.resolveFallback(); }
    public Component getDisplayName(ServerPlayer player, @Nullable IQuestCapability cap) {
        return displayName.resolve(TradeTextContext.of(player, shopId, cap));
    }
    @Nullable public Component getDescription() { return description != null ? description.resolveFallback() : null; }
    @Nullable public Component getDescription(ServerPlayer player, @Nullable IQuestCapability cap) {
        return description != null ? description.resolve(TradeTextContext.of(player, shopId, cap)) : null;
    }
    public List<TradeCategory> getCategories() { return categories; }

    public Collection<TradeEntry> getAllEntries() { return Collections.unmodifiableCollection(entries.values()); }
    @Nullable public TradeEntry getEntry(String entryId) { return entries.get(entryId); }
    public Set<String> getEntryIds() { return Collections.unmodifiableSet(entries.keySet()); }

    public List<TradeEntry> getEntriesByCategory(TradeCategory category) {
        if (category == null || category.equals(TradeCategory.ALL)) return List.copyOf(entries.values());
        List<TradeEntry> result = new ArrayList<>();
        for (TradeEntry entry : entries.values()) if (category.equals(entry.getCategory())) result.add(entry);
        return result;
    }

    @Nullable public ICondition getOpenCondition() { return openCondition; }
    public boolean isSimpleMode() { return simpleMode; }
    public int getThemeColor() { return themeColor; }
    @Nullable public SoundEvent getOpenSound() { return openSound; }
    @Nullable public SoundEvent getCloseSound() { return closeSound; }

    public boolean canOpen(ServerPlayer player,
                           Set<ResourceLocation> completedQuests,
                           Set<String> flags,
                           Map<String, Integer> variables) {
        return openCondition == null || openCondition.test(player, completedQuests, flags, variables);
    }

    @Override
    public String toString() {
        return "TradeShop[" + shopId + ", " + entries.size() + " entries]";
    }
}
