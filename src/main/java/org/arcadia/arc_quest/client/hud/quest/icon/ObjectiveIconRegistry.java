package org.arcadia.arc_quest.client.hud.quest.icon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModLoader;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.EntityPortraits;
import org.arcadia.arc_quest.quest.api.ObjectiveItemResolver;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.util.log.ArcQuestLog;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ObjectiveIconRegistry {
    private static final Map<ResourceLocation, ObjectiveIconProvider> PROVIDERS = new HashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> DEFAULTS = new HashMap<>();
    private static final Set<String> FAILURES = new LinkedHashSet<>();
    private static boolean initialized, frozen;
    private ObjectiveIconRegistry() {}
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        var item = ResourceLocation.fromNamespaceAndPath("arc_quest", "objective_item");
        var entity = ResourceLocation.fromNamespaceAndPath("arc_quest", "entity_portrait");
        registerProvider(item, context -> ResolvedObjectiveIcon.items(ObjectiveItemResolver.candidates(context.objective())));
        registerProvider(entity, context -> EntityPortraits.resolve(context.objective())
                .map(ResolvedObjectiveIcon::visual).orElse(ResolvedObjectiveIcon.none()));
        bindDefault(ObjectiveType.COLLECT.getId(), item);
        bindDefault(ObjectiveType.CRAFT.getId(), item);
        bindDefault(ObjectiveType.OFFER.getId(), item);
        bindDefault(ObjectiveType.DELIVER.getId(), item);
        bindDefault(ObjectiveType.KILL.getId(), entity);
        ModLoader.postEvent(new RegisterObjectiveIconsEvent());
        EntityPortraits.freezeRegistrations();
        frozen = true;
    }
    public static void registerProvider(ResourceLocation id, ObjectiveIconProvider provider) {
        if (frozen) throw new IllegalStateException("Objective icon registration has closed");
        if (PROVIDERS.putIfAbsent(Objects.requireNonNull(id), Objects.requireNonNull(provider)) != null)
            throw new IllegalArgumentException("Duplicate objective icon provider " + id);
    }
    public static void bindDefault(ResourceLocation type, ResourceLocation provider) {
        if (frozen) throw new IllegalStateException("Objective icon registration has closed");
        if (DEFAULTS.putIfAbsent(Objects.requireNonNull(type), Objects.requireNonNull(provider)) != null)
            throw new IllegalArgumentException("Duplicate objective icon default " + type);
    }
    public static ResolvedObjectiveIcon resolve(ObjectiveIconContext context) {
        if (context.objective().isHidden()) return ResolvedObjectiveIcon.none();
        var spec = context.objective().getIcon();
        try {
            return switch (spec.mode()) {
                case NONE -> ResolvedObjectiveIcon.none();
                case TEXTURE -> ResolvedObjectiveIcon.visual(TextureObjectiveIcon.load(spec));
                case ITEM -> ResolvedObjectiveIcon.items(List.of(BuiltInRegistries.ITEM.getOptional(spec.item())
                        .orElseThrow(() -> new IllegalArgumentException("unregistered icon item " + spec.item()))
                        .getDefaultInstance()));
                case PROVIDER -> fromProvider(spec.provider(), context);
                case AUTO -> fromProvider(DEFAULTS.get(context.objective().getType().getId()), context);
            };
        } catch (Exception exception) {
            warn(context.key() + "/" + spec, exception.getMessage());
            return ResolvedObjectiveIcon.none();
        }
    }
    private static ResolvedObjectiveIcon fromProvider(ResourceLocation id, ObjectiveIconContext context) {
        if (id == null) return ResolvedObjectiveIcon.none();
        var provider = PROVIDERS.get(id);
        if (provider == null) {
            warn(context.key() + "/" + id, "unregistered provider");
            return ResolvedObjectiveIcon.none();
        }
        return Objects.requireNonNullElse(provider.resolve(context), ResolvedObjectiveIcon.none());
    }
    private static void warn(String key, String reason) {
        if (FAILURES.contains(key)) return;
        if (FAILURES.size() >= 512) return;
        FAILURES.add(key);
        ArcQuestLog.warn(ArcQuestLog.Category.DATA, "Objective icon unavailable {}: {}", key, reason);
    }
    static void invalidate() { FAILURES.clear(); }
}
