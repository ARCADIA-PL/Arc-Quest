package org.arcadia.arc_quest.integration.jei.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Add-on catalog providers share the same player-authorized, read-only synchronization path. */
public final class JeiCatalogProviders {
    private static final Map<ResourceLocation, JeiCatalogProvider> PROVIDERS = new LinkedHashMap<>();
    private JeiCatalogProviders() {}
    public static synchronized void register(ResourceLocation id, JeiCatalogProvider provider) {
        Objects.requireNonNull(id);
        Objects.requireNonNull(provider);
        if (PROVIDERS.putIfAbsent(id, provider) != null) throw new IllegalStateException("Duplicate JEI provider: " + id);
    }
    public static List<JeiCatalogEntry> collect(ServerPlayer player, ArcQuestPlayer data) {
        List<Map.Entry<ResourceLocation, JeiCatalogProvider>> providers;
        synchronized (JeiCatalogProviders.class) { providers = new ArrayList<>(PROVIDERS.entrySet()); }
        Map<String, JeiCatalogEntry> entries = new TreeMap<>();
        for (var provider : providers) {
            // Publish a provider atomically: a failed condition must not leave a partially authorized list.
            Map<String, JeiCatalogEntry> pending = new TreeMap<>();
            try {
                provider.getValue().collect(player, data, entry -> {
                    if (entries.containsKey(entry.id()) || pending.putIfAbsent(entry.id(), entry) != null)
                        throw new IllegalArgumentException("Duplicate catalog id: " + entry.id());
                });
                entries.putAll(pending);
            } catch (RuntimeException exception) {
                ArcQuestLog.error(ArcQuestLog.Category.DATA, "JEI provider {} failed for player {}",
                        provider.getKey(), player.getUUID(), exception);
            }
        }
        return List.copyOf(entries.values());
    }
}
