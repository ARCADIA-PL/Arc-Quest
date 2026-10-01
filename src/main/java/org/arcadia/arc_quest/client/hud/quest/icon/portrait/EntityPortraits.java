package org.arcadia.arc_quest.client.hud.quest.icon.portrait;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraftforge.registries.ForgeRegistries;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconVisual;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ObjectiveType;
import org.arcadia.arc_quest.util.log.ArcQuestLog;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Client-only entity pictures. No world, living entity, spawn egg or JEI ingredient is created here.
 * Register Java adapters before freezeRegistrations; resource-pack overrides are independent snapshots.
 */
public final class EntityPortraits {
    private static final String RULE_DIRECTORY = "arc_quest/objective_icons/entities/";
    private static final Map<ResourceLocation, HeadPortraitDefinition> HEADS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, TexturePortraitDefinition> TEXTURES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, HeadPortraitAdapter> ADAPTERS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> BUILTIN_TEXTURE_FILES = Map.of(
            minecraft("cow"), ResourceLocation.fromNamespaceAndPath("arc_quest", "objective_icons/portraits/cow.json"),
            minecraft("pig"), ResourceLocation.fromNamespaceAndPath("arc_quest", "objective_icons/portraits/pig.json"));
    private static boolean frozen;
    private static long generation;
    private static volatile State current;

    static {
        // Initialized before the first resource reload, which can precede FMLClientSetupEvent.
        var cube = new HeadPortraitDefinition.Frame(-0.30f, -0.05f, 0.30f, 0.55f);
        builtinHead("skeleton", "skeleton_skull", cube);
        builtinHead("wither_skeleton", "wither_skeleton_skull", cube);
        builtinHead("zombie", "zombie_head", cube);
        builtinHead("creeper", "creeper_head", cube);
        // At animation=0 the ears reach +/- (4.5 + cos(.7) + 5*sin(.7)) = 8.486 model pixels.
        // A square 18-pixel frame keeps both tips and centers the face at model-space Y=4/16.
        builtinHead("piglin", "piglin_head", new HeadPortraitDefinition.Frame(-9f / 16, -5f / 16, 9f / 16, 13f / 16));
        builtinHead("ender_dragon", "dragon_head", new HeadPortraitDefinition.Frame(-0.56f, -0.10f, 0.56f, 1.02f));
        ADAPTERS.put(HeadPortraitDefinition.SKULL_ADAPTER, (definition, models, resources) -> {
            var item = ForgeRegistries.ITEMS.getValue(definition.headItem());
            if (!(item instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof AbstractSkullBlock skull))
                throw new IllegalArgumentException("Registered source is not an AbstractSkullBlock item: " + definition.headItem());
            if (skull.getType() == SkullBlock.Types.PLAYER)
                throw new IllegalArgumentException("Player profile portraits need an explicit static adapter or texture");
            var model = SkullBlockRenderer.createSkullRenderers(models).get(skull.getType());
            var texture = SkullBlockRenderer.SKIN_BY_TYPE.get(skull.getType());
            if (model == null || texture == null)
                throw new IllegalArgumentException("Skull source has no registered model and static skin: " + definition.headItem());
            return new HeadPortraitAdapter.HeadModel(model, texture);
        });
    }

    private EntityPortraits() { }

    public static synchronized void registerHeadPortrait(ResourceLocation entityId, HeadPortraitDefinition definition) {
        register(HEADS, entityId, definition, "head portrait");
    }
    public static synchronized void registerTexturePortrait(ResourceLocation entityId, TexturePortraitDefinition definition) {
        if (BUILTIN_TEXTURE_FILES.containsKey(entityId))
            throw new IllegalArgumentException("Duplicate built-in texture portrait: " + entityId + "; use a resource-pack entity override");
        register(TEXTURES, entityId, definition, "texture portrait");
    }
    public static synchronized void registerHeadAdapter(ResourceLocation adapterId, HeadPortraitAdapter adapter) {
        register(ADAPTERS, adapterId, adapter, "head adapter");
    }
    public static synchronized void freezeRegistrations() {
        for (var entry : HEADS.entrySet()) {
            if (!ADAPTERS.containsKey(entry.getValue().adapter()))
                throw new IllegalStateException("Unknown head adapter " + entry.getValue().adapter() + " for " + entry.getKey());
        }
        frozen = true;
    }
    private static <T> void register(Map<ResourceLocation, T> registry, ResourceLocation id, T value, String kind) {
        if (frozen) throw new IllegalStateException("Objective portrait registrations are frozen");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(value, kind);
        if (registry.putIfAbsent(id, value) != null) throw new IllegalArgumentException("Duplicate " + kind + ": " + id);
    }

    /** Only call after the parent resolver has selected AUTO. Hidden objectives never allocate pictures. */
    public static Optional<ObjectiveIconVisual> resolve(ObjectiveEntry objective) {
        if (objective == null || objective.isHidden() || !ObjectiveType.KILL.equals(objective.getType())) return Optional.empty();
        ResourceLocation entity = objective.getTargetId();
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(entity)) return Optional.empty();
        State state = state();
        return state.resolved.computeIfAbsent(entity, key -> state.resolve(key));
    }

    /**
     * Call once at the outer screen render boundary, before GUI batches or scissors are opened.
     * A preparing head does not select the texture fallback. The returned visual reports availability
     * dynamically, so callers may keep it and recheck available() on the next frame.
     */
    public static void prepare() {
        RenderSystem.assertOnRenderThread();
        State state = current;
        if (state == null || state.pending.isEmpty()) return;
        for (int budget = 0; budget < 4 && !state.pending.isEmpty(); budget++) {
            var iterator = state.pending.iterator();
            HeadEntry entry = iterator.next();
            iterator.remove();
            if (entry.status != Status.PENDING || current != state) continue;
            try {
                HeadPortraitAdapter adapter = ADAPTERS.get(entry.definition.adapter());
                if (adapter == null) throw new IllegalArgumentException("Head adapter is not registered: " + entry.definition.adapter());
                var source = Objects.requireNonNull(adapter.create(entry.definition, Minecraft.getInstance().getEntityModels(),
                        state.resources), "head model");
                if (state.imageSize(source.texture()).isEmpty())
                    throw new IllegalArgumentException("Head texture is unavailable: " + source.texture());
                var baked = HeadPortraitBaker.bake(entry.definition, source);
                if (current != state || entry.status != Status.PENDING) { baked.close(); continue; }
                entry.baked = baked;
                entry.status = Status.READY;
                var size = new TexturePortraitDefinition.Size(baked.size, baked.size);
                entry.visual = new PortraitTextureVisual(baked.texture, size,
                        new TexturePortraitDefinition.Rect(0, 0, baked.size, baked.size),
                        () -> current == state && entry.status == Status.READY && baked.available());
            } catch (Exception | LinkageError failure) {
                entry.status = Status.FAILED;
                state.warn(entry.definition.headItem(), "Head portrait unavailable: " + failure.getMessage());
            }
        }
    }

    /** Rebuilds resource rules atomically; no head geometry is baked inside the resource listener. */
    public static synchronized void reload(ResourceManager resources) {
        Objects.requireNonNull(resources, "resources");
        State replacement = new State(++generation, resources);
        for (var entry : BUILTIN_TEXTURE_FILES.entrySet()) {
            try {
                var resource = resources.getResource(entry.getValue()).orElseThrow(() -> new IOException("Missing built-in UV definition"));
                EntityPortraitRule rule = readRule(resource);
                if (rule.kind() != EntityPortraitRule.Kind.ENTITY_TEXTURE_PORTRAIT)
                    throw new IllegalArgumentException("Default UV definition must use arc_quest:entity_texture_portrait");
                replacement.defaults.put(entry.getKey(), rule.portrait());
            } catch (Exception failure) { replacement.warn(entry.getValue(), failure.getMessage()); }
        }
        resources.listResources(RULE_DIRECTORY.substring(0, RULE_DIRECTORY.length() - 1),
                id -> id.getPath().startsWith(RULE_DIRECTORY) && id.getPath().endsWith(".json")).forEach((path, resource) -> {
            String name = path.getPath().substring(RULE_DIRECTORY.length(), path.getPath().length() - 5);
            ResourceLocation entity = ResourceLocation.tryParse(path.getNamespace() + ":" + name);
            if (entity == null) { replacement.warn(path, "Invalid entity rule path"); return; }
            try { replacement.overrides.put(entity, readRule(resource)); }
            catch (Exception failure) {
                replacement.overrides.put(entity, EntityPortraitRule.NONE);
                replacement.warn(path, "Invalid explicit entity rule; using NONE: " + failure.getMessage());
            }
        });
        replace(replacement);
    }

    /** Keeps resource definitions and registrations; removes session handles, failures and owned GPU data. */
    public static synchronized void clearSession() {
        State old = current;
        if (old == null) return;
        State replacement = new State(++generation, old.resources);
        replacement.defaults.putAll(old.defaults);
        replacement.overrides.putAll(old.overrides);
        replace(replacement);
    }

    private static void replace(State replacement) {
        State old = current;
        current = replacement; // Existing handles fail availability before deferred GPU destruction.
        if (old != null) {
            if (RenderSystem.isOnRenderThread()) old.close();
            else RenderSystem.recordRenderCall(old::close);
        }
    }
    private static State state() {
        State state = current;
        if (state == null) { reload(Minecraft.getInstance().getResourceManager()); state = current; }
        return state;
    }
    private static EntityPortraitRule readRule(Resource resource) throws IOException {
        try (var reader = resource.openAsReader()) { return EntityPortraitRule.parse(JsonParser.parseReader(reader)); }
    }
    private static ResourceLocation minecraft(String path) { return ResourceLocation.fromNamespaceAndPath("minecraft", path); }
    private static void builtinHead(String entity, String item, HeadPortraitDefinition.Frame frame) {
        HEADS.put(minecraft(entity), HeadPortraitDefinition.skull(minecraft(item), frame));
    }

    private enum Status { PENDING, READY, FAILED, CLOSED }
    private static final class HeadEntry implements AutoCloseable {
        final HeadPortraitDefinition definition;
        Status status = Status.PENDING;
        HeadPortraitBaker.BakedPortrait baked;
        ObjectiveIconVisual visual;
        HeadEntry(HeadPortraitDefinition definition) { this.definition = definition; }
        @Override public void close() {
            status = Status.CLOSED;
            if (baked != null) { baked.close(); baked = null; }
            visual = null;
        }
    }

    private static final class State {
        final long generation;
        final ResourceManager resources;
        final Map<ResourceLocation, EntityPortraitRule> overrides = new LinkedHashMap<>();
        final Map<ResourceLocation, TexturePortraitDefinition> defaults = new LinkedHashMap<>();
        final Set<HeadEntry> pending = new LinkedHashSet<>();
        final BoundedPortraitCache<HeadPortraitDefinition, HeadEntry> heads = new BoundedPortraitCache<>(64, entry -> {
            pending.remove(entry); entry.close();
        });
        final BoundedPortraitCache<ResourceLocation, Optional<ObjectiveIconVisual>> resolved =
                new BoundedPortraitCache<>(256, ignored -> { });
        final BoundedPortraitCache<ResourceLocation, Optional<TexturePortraitDefinition.Size>> images =
                new BoundedPortraitCache<>(128, ignored -> { });
        final Set<ResourceLocation> warnings = new LinkedHashSet<>();
        State(long generation, ResourceManager resources) { this.generation = generation; this.resources = resources; }

        Optional<ObjectiveIconVisual> resolve(ResourceLocation entity) {
            EntityPortraitRule override = overrides.get(entity);
            if (override != null) return ruleVisual(override, entity);
            HeadPortraitDefinition head = HEADS.get(entity);
            TexturePortraitDefinition texture = TEXTURES.getOrDefault(entity, defaults.get(entity));
            if (head == null && texture == null) return Optional.empty();
            return Optional.of(new ObjectiveIconVisual() {
                private Optional<ObjectiveIconVisual> fallback;
                private ObjectiveIconVisual selected() {
                    if (current != State.this) return null;
                    if (head != null) {
                        HeadEntry entry = heads.computeIfAbsent(head, definition -> {
                            HeadEntry queued = new HeadEntry(definition); pending.add(queued); return queued;
                        });
                        if (entry.status == Status.PENDING) return null;
                        if (entry.status == Status.READY) return entry.visual;
                    }
                    if (fallback == null) fallback = texture == null ? Optional.empty() : textureVisual(texture, entity);
                    return fallback.orElse(null);
                }
                @Override public boolean available() {
                    ObjectiveIconVisual visual = selected();
                    return visual != null && visual.available();
                }
                @Override public void render(GuiGraphics graphics, int x, int y, int size) {
                    ObjectiveIconVisual visual = selected();
                    if (visual != null) visual.render(graphics, x, y, size);
                }
            });
        }

        Optional<ObjectiveIconVisual> ruleVisual(EntityPortraitRule rule, ResourceLocation entity) {
            if (rule.kind() == EntityPortraitRule.Kind.NONE) return Optional.empty();
            if (rule.kind() == EntityPortraitRule.Kind.ENTITY_TEXTURE_PORTRAIT) return textureVisual(rule.portrait(), entity);
            return imageSize(rule.texture()).flatMap(size -> {
                var crop = rule.region() == null ? new TexturePortraitDefinition.Rect(0, 0, size.width(), size.height()) : rule.region();
                if (!crop.fits(size)) { warn(entity, "Explicit picture region exceeds the resource texture"); return Optional.empty(); }
                return Optional.of(new PortraitTextureVisual(rule.texture(), size, crop, () -> current == this));
            });
        }
        Optional<ObjectiveIconVisual> textureVisual(TexturePortraitDefinition definition, ResourceLocation entity) {
            var size = imageSize(definition.texture());
            if (size.isEmpty()) return Optional.empty();
            if (!definition.acceptsTextureSize(size.get().width(), size.get().height())) {
                warn(entity, "Texture aspect ratio differs from referenceSize; provide an updated UV rule");
                return Optional.empty();
            }
            return Optional.of(new PortraitTextureVisual(definition, () -> current == this));
        }
        Optional<TexturePortraitDefinition.Size> imageSize(ResourceLocation texture) {
            return images.computeIfAbsent(texture, id -> {
                try {
                    Resource resource = resources.getResource(id).orElseThrow(() -> new IOException("Resource does not exist"));
                    TexturePortraitDefinition.Size size;
                    try (var input = resource.open()) { size = PortraitPngHeader.read(input); }
                    // Validate the entire image once, so a corrupt source cannot render half a portrait.
                    try (var input = resource.open(); NativeImage decoded = NativeImage.read(input)) {
                        if (decoded.getWidth() != size.width() || decoded.getHeight() != size.height())
                            throw new IOException("PNG dimensions changed during decode");
                    }
                    return Optional.of(size);
                } catch (Exception failure) { warn(id, "Texture unavailable: " + failure.getMessage()); return Optional.empty(); }
            });
        }
        void warn(ResourceLocation source, String reason) {
            if (warnings.contains(source) || warnings.size() >= 512) return;
            warnings.add(source);
            ArcQuestLog.rawLogger().warn("Objective portrait {} (generation {}): {}", source, generation, reason);
        }
        void close() {
            heads.clear(); pending.clear(); resolved.clear(); images.clear(); warnings.clear();
        }
    }
}
