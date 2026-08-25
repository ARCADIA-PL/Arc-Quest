package org.arcadia.arc_quest.client.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GroupExpansionStatePersistence {
    public static final String JOURNAL_SCOPE = "journal";
    public static final String GUIDE_SCOPE = "guide";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "arc_quest_group_expansion.json";
    private static GroupExpansionStatePersistence instance;

    private final Path file;
    private final Map<String, Boolean> journalGroups = new LinkedHashMap<>();
    private final Map<String, Boolean> guideGroups = new LinkedHashMap<>();
    private boolean loaded;

    private GroupExpansionStatePersistence(Path file) {
        this.file = file;
    }

    public static GroupExpansionStatePersistence getInstance() {
        if (instance == null) {
            instance = new GroupExpansionStatePersistence(defaultFile());
        }
        return instance;
    }

    static GroupExpansionStatePersistence forFile(Path file) {
        return new GroupExpansionStatePersistence(file);
    }

    public synchronized boolean isExpanded(String scope, ResourceLocation groupId) {
        if (groupId == null) return true;
        ensureLoaded();
        return groups(scope).getOrDefault(groupId.toString(), true);
    }

    public synchronized void setExpanded(String scope, ResourceLocation groupId, boolean expanded) {
        if (groupId == null) return;
        ensureLoaded();
        Map<String, Boolean> groups = groups(scope);
        String key = groupId.toString();
        if (groups.getOrDefault(key, true) == expanded) return;
        groups.put(key, expanded);
        save();
    }

    private Map<String, Boolean> groups(String scope) {
        return GUIDE_SCOPE.equals(scope) ? guideGroups : journalGroups;
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (file == null || !Files.exists(file)) return;
        try {
            StoredState state = GSON.fromJson(Files.readString(file), StoredState.class);
            if (state == null) return;
            copyInto(journalGroups, state.journalGroups);
            copyInto(guideGroups, state.guideGroups);
        } catch (Exception e) {
            LOGGER.warn("[ArcQuest] Failed to load group expansion state, using defaults.", e);
        }
    }

    private void save() {
        if (file == null) return;
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            StoredState state = new StoredState();
            state.journalGroups.putAll(journalGroups);
            state.guideGroups.putAll(guideGroups);
            Files.writeString(temporary, GSON.toJson(state));
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.warn("[ArcQuest] Failed to save group expansion state.", e);
        }
    }

    private static void copyInto(Map<String, Boolean> target, Map<String, Boolean> source) {
        if (source == null) return;
        source.forEach((key, value) -> {
            if (key != null && value != null) target.put(key, value);
        });
    }

    private static Path defaultFile() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.gameDirectory == null) return null;
        return minecraft.gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    static final class StoredState {
        Map<String, Boolean> journalGroups = new LinkedHashMap<>();
        Map<String, Boolean> guideGroups = new LinkedHashMap<>();
    }
}
