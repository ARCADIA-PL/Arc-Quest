package org.arcadia.arc_quest.quest.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.arcadia.arc_quest.condition.ConditionBridge;
import org.arcadia.arc_quest.condition.ConditionSpec;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.editor.QuestAuthoringSnapshotRegistry;
import org.arcadia.arc_quest.quest.registry.QuestRegistry;
import org.arcadia.arc_quest.quest.registry.QuestSourceType;
import org.arcadia.arc_quest.quest.spec.CollectionEntrySpecData;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.compile.QuestSpecCompiler;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonReader;
import org.arcadia.arc_quest.quest.spec.io.QuestSpecJsonWriter;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Server-world, content-addressed investigation definitions. A runtime stores only its hash.
 * Full authoring documents include executable reward payloads and MUST NOT enter player packets.
 * Java callbacks are not reverse-engineered: code quests need an explicit authoring factory.
 */
public final class CollectionRunDefinitionStore extends SavedData {
    public static final String DATA_NAME = "arcq_collection_run_definitions";
    public static final int FORMAT_VERSION = 1;
    public static final int MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;
    private static final Map<ResourceLocation, Supplier<QuestSpec>> CODE_FACTORIES = new ConcurrentHashMap<>();
    private static final Map<LegacyFactoryKey, Supplier<QuestSpec>> LEGACY_FACTORIES = new ConcurrentHashMap<>();
    private static final Map<LegacyFactoryKey, Supplier<QuestDefinition>> CODE_DEFINITION_FACTORIES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, String> CURRENT_CODE_VERSIONS = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, String> UNVERSIONED_LEGACY_VERSIONS = new ConcurrentHashMap<>();
    private final Map<String, Snapshot> snapshots = new LinkedHashMap<>();
    private final Map<String, QuestDefinition> compiled = new HashMap<>();

    public CollectionRunDefinitionStore() { }

    public static CollectionRunDefinitionStore get(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(CollectionRunDefinitionStore::new,
                (tag, registries) -> CollectionRunDefinitionStore.load(tag)), DATA_NAME);
    }

    /** The provider declares the complete executable rules; presentation exporters are insufficient. */
    public static void registerCodeAuthoringFactory(ResourceLocation questId, Supplier<QuestSpec> factory) {
        if (CODE_FACTORIES.putIfAbsent(Objects.requireNonNull(questId), Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Duplicate code authoring factory: " + questId);
    }

    /** A legacy runtime must name an explicit old version instead of capturing today's replacement quest. */
    public static void registerLegacyCodeAuthoringFactory(ResourceLocation questId, String oldVersion, Supplier<QuestSpec> factory) {
        LegacyFactoryKey key = new LegacyFactoryKey(questId, oldVersion);
        if (LEGACY_FACTORIES.putIfAbsent(key, Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Duplicate legacy authoring factory: " + key);
    }

    /**
     * Immutable versioned providers preserve Java callbacks verbatim. Keep old versions available
     * after updates; reusing a version key for different rules violates this explicit contract.
     */
    public static void registerCodeDefinitionFactory(ResourceLocation questId, String version,
                                                     Supplier<QuestDefinition> factory, boolean makeCurrent) {
        LegacyFactoryKey key = new LegacyFactoryKey(questId, version);
        if (CODE_DEFINITION_FACTORIES.putIfAbsent(key, Objects.requireNonNull(factory)) != null)
            throw new IllegalArgumentException("Duplicate code definition version: " + key);
        if (makeCurrent) CURRENT_CODE_VERSIONS.put(questId, version);
    }

    /** Explicit migration evidence for saves made before runtime version stamps existed. */
    public static void registerUnversionedLegacyVersion(ResourceLocation questId, String originalVersion) {
        LegacyFactoryKey key = new LegacyFactoryKey(questId, originalVersion);
        if (!CODE_DEFINITION_FACTORIES.containsKey(key) && !LEGACY_FACTORIES.containsKey(key))
            throw new IllegalArgumentException("Register the exact legacy factory before its unversioned migration: " + key);
        if (UNVERSIONED_LEGACY_VERSIONS.putIfAbsent(questId, originalVersion) != null)
            throw new IllegalArgumentException("Duplicate unversioned legacy migration: " + questId);
    }

    @Nullable public static String getUnversionedLegacyVersion(ResourceLocation questId) { return UNVERSIONED_LEGACY_VERSIONS.get(questId); }
    public static boolean hasCodeDefinitionFactory(ResourceLocation questId, String version) { return CODE_DEFINITION_FACTORIES.containsKey(new LegacyFactoryKey(questId, version)); }

    public static CaptureCapability capability(QuestDefinition quest) {
        var source = QuestRegistry.getSourceInfo(quest.getId());
        if (source != null && source.sourceType() == QuestSourceType.DATAPACK && QuestAuthoringSnapshotRegistry.get(quest.getId()) != null)
            return new CaptureCapability(true, "AUTHORING_JSON", "", "");
        String version = CURRENT_CODE_VERSIONS.get(quest.getId());
        if (version != null) return new CaptureCapability(true, "CODE_FACTORY", version, "");
        if (CODE_FACTORIES.containsKey(quest.getId())) return new CaptureCapability(true, "AUTHORING_JSON", "", "");
        return new CaptureCapability(false, "", "", "Code quest has no complete authoring document or immutable versioned factory");
    }

    public String freezeRegistered(QuestDefinition current) {
        Objects.requireNonNull(current, "current quest");
        var source = QuestRegistry.getSourceInfo(current.getId());
        QuestSpec authoring;
        if (source != null && source.sourceType() == QuestSourceType.DATAPACK && QuestAuthoringSnapshotRegistry.get(current.getId()) != null) {
            var entry = QuestAuthoringSnapshotRegistry.get(current.getId());
            if (entry == null) throw unsupported(current.getId(), "Original datapack authoring document is unavailable");
            authoring = entry.spec();
        } else {
            String version = CURRENT_CODE_VERSIONS.get(current.getId());
            if (version != null) return freezeCodeFactory(current.getId(), version);
            Supplier<QuestSpec> provider = CODE_FACTORIES.get(current.getId());
            if (provider == null) throw unsupported(current.getId(), "Code callbacks require a complete authoring factory");
            authoring = provider.get();
        }
        if (authoring == null || !current.getId().toString().equals(authoring.id))
            throw unsupported(current.getId(), "Authoring factory returned a different quest identity");
        return freezeAuthoring(authoring, CollectionRunDefinitionStore::originalSharedEntry);
    }

    public String freezeLegacyCode(ResourceLocation questId, String oldVersion) {
        if (CODE_DEFINITION_FACTORIES.containsKey(new LegacyFactoryKey(questId, oldVersion))) return freezeCodeFactory(questId, oldVersion);
        Supplier<QuestSpec> provider = LEGACY_FACTORIES.get(new LegacyFactoryKey(questId, oldVersion));
        if (provider == null) throw unsupported(questId, "No explicit factory for old version '" + oldVersion + "'; reopen the run while preserving lawful owed rewards");
        QuestSpec spec = provider.get();
        if (spec == null || !questId.toString().equals(spec.id)) throw unsupported(questId, "Legacy factory returned a different quest identity");
        return freezeAuthoring(spec, CollectionRunDefinitionStore::originalSharedEntry);
    }

    public String freezeAuthoring(QuestSpec authoring) { return freezeAuthoring(authoring, ignored -> null); }

    public synchronized String freezeCodeFactory(ResourceLocation questId, String version) {
        String hash = factoryHash(questId, version);
        if (snapshots.containsKey(hash)) { resolve(hash, questId); return hash; }
        QuestDefinition definition = codeDefinition(questId, version);
        if (!snapshots.containsKey(hash)) {
            snapshots.put(hash, new Snapshot(questId, "", version));
            setDirty();
        }
        compiled.putIfAbsent(hash, definition);
        return hash;
    }

    /** Inline every shared entry from its ORIGINAL authoring spec, freezing conditions and rewards too. */
    public synchronized String freezeAuthoring(QuestSpec authoring, Function<String, CollectionEntrySpecData> sharedEntries) {
        Objects.requireNonNull(authoring, "authoring");
        QuestSpec frozen = QuestSpecJsonReader.read(QuestSpecJsonWriter.write(authoring));
        ResourceLocation questId = ResourceLocation.tryParse(frozen.id);
        if (questId == null) throw new IllegalArgumentException("Frozen definition has invalid questId");
        if (frozen.collectionConfig == null) throw new IllegalArgumentException("Frozen collection definition requires collectionConfig");
        Map<String, CollectionEntrySpecData> inline = new LinkedHashMap<>();
        for (var entry : list(frozen.collectionConfig.entries)) inline.put(entry.entryId, entry);
        for (String entryId : list(frozen.collectionConfig.entryIds)) {
            if (inline.containsKey(entryId)) continue;
            CollectionEntrySpecData original = sharedEntries.apply(entryId);
            if (original == null || !entryId.equals(original.entryId))
                throw unsupported(questId, "Shared entry has no complete authoring document: " + entryId);
            // Reuse the registered JSON adapter through a temporary whole-spec container.
            QuestSpec copyContainer = new QuestSpec();
            copyContainer.collectionConfig = new org.arcadia.arc_quest.quest.spec.CollectionQuestSpecData();
            copyContainer.collectionConfig.entries.add(original);
            inline.put(entryId, QuestSpecJsonReader.read(QuestSpecJsonWriter.write(copyContainer)).collectionConfig.entries.get(0));
        }
        frozen.collectionConfig.entries = new java.util.ArrayList<>(inline.values());
        frozen.collectionConfig.entryIds.clear();
        String json = QuestSpecJsonWriter.write(frozen);
        checkDocumentSize(json);
        String hash = hash(json);
        if (snapshots.containsKey(hash)) { resolve(hash, questId); return hash; }
        validateConditionAvailability(frozen);
        QuestDefinition definition = new QuestSpecCompiler().compile(frozen);
        if (!snapshots.containsKey(hash)) {
            snapshots.put(hash, new Snapshot(questId, json, ""));
            setDirty();
        }
        compiled.putIfAbsent(hash, definition);
        return hash;
    }

    /** Never fall back to the live registry if a snapshot cannot be restored. */
    public synchronized QuestDefinition resolve(String hash, ResourceLocation expectedQuestId) {
        Snapshot snapshot = snapshots.get(hash);
        if (snapshot == null) throw unsupported(expectedQuestId, "Frozen definition is missing: " + hash);
        if (!snapshot.questId.equals(expectedQuestId)) throw new IllegalArgumentException("Frozen definition belongs to another quest");
        QuestDefinition cached = compiled.get(hash);
        if (cached != null) return cached;
        if (!snapshot.factoryVersion.isEmpty()) {
            QuestDefinition restored = codeDefinition(snapshot.questId, snapshot.factoryVersion);
            compiled.put(hash, restored);
            return restored;
        }
        QuestSpec spec = QuestSpecJsonReader.read(snapshot.json);
        validateConditionAvailability(spec);
        QuestDefinition restored = new QuestSpecCompiler().compile(spec);
        compiled.put(hash, restored);
        return restored;
    }

    public synchronized boolean contains(String hash) { return snapshots.containsKey(hash); }
    public synchronized int snapshotCount() { return snapshots.size(); }

    @Override public synchronized CompoundTag save(CompoundTag target, HolderLookup.Provider registries) { return save(target); }

    public synchronized CompoundTag save(CompoundTag target) {
        target.putInt("FormatVersion", FORMAT_VERSION);
        CompoundTag documents = new CompoundTag();
        for (var item : snapshots.entrySet()) {
            CompoundTag snapshot = new CompoundTag();
            snapshot.putString("QuestId", item.getValue().questId.toString());
            if (item.getValue().factoryVersion.isEmpty()) {
                snapshot.putString("Kind", "AUTHORING_JSON");
                snapshot.putString("AuthoringJson", item.getValue().json);
            } else {
                snapshot.putString("Kind", "CODE_FACTORY");
                snapshot.putString("FactoryVersion", item.getValue().factoryVersion);
            }
            documents.put(item.getKey(), snapshot);
        }
        target.put("Snapshots", documents);
        return target;
    }

    public static CollectionRunDefinitionStore load(CompoundTag saved) {
        if (saved.getInt("FormatVersion") != FORMAT_VERSION)
            throw new IllegalArgumentException("Unsupported frozen investigation store format: " + saved.getInt("FormatVersion"));
        CollectionRunDefinitionStore store = new CollectionRunDefinitionStore();
        CompoundTag documents = saved.getCompound("Snapshots");
        for (String hash : documents.getAllKeys()) {
            if (!documents.contains(hash, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid frozen snapshot container");
            CompoundTag snapshot = documents.getCompound(hash);
            ResourceLocation id = ResourceLocation.tryParse(snapshot.getString("QuestId"));
            if (id == null) throw new IllegalArgumentException("Frozen snapshot has invalid questId");
            String kind = snapshot.getString("Kind");
            if ("CODE_FACTORY".equals(kind)) {
                String version = snapshot.getString("FactoryVersion");
                if (version.isBlank() || !factoryHash(id, version).equals(hash)) throw new IllegalArgumentException("Frozen factory identity/hash mismatch");
                store.snapshots.put(hash, new Snapshot(id, "", version));
                continue;
            }
            if (!"AUTHORING_JSON".equals(kind)) throw new IllegalArgumentException("Unknown frozen snapshot kind: " + kind);
            String json = snapshot.getString("AuthoringJson");
            checkDocumentSize(json);
            if (id == null || !hash(json).equals(hash)) throw new IllegalArgumentException("Frozen snapshot identity/hash mismatch");
            QuestSpec spec = QuestSpecJsonReader.read(json);
            if (!id.toString().equals(spec.id)) throw new IllegalArgumentException("Frozen snapshot questId mismatch");
            store.snapshots.put(hash, new Snapshot(id, json, ""));
        }
        return store;
    }

    private static void validateConditionAvailability(QuestSpec spec) {
        for (ConditionSpec condition : list(spec.unlockConditions)) conditionAvailable(condition);
        if (spec.collectionConfig != null) for (var entry : list(spec.collectionConfig.entries))
            for (ConditionSpec condition : list(entry.recordConditions)) conditionAvailable(condition);
        for (var phase : list(spec.phases)) {
            conditionAvailable(phase.enterCondition);
            for (var transition : list(phase.transitions)) conditionAvailable(transition.condition);
            for (var choice : list(phase.choices)) conditionAvailable(choice.visibleCondition);
            if (phase.collectionEntryConfig != null) for (var condition : list(phase.collectionEntryConfig.visibilityConditions)) conditionAvailable(condition);
        }
    }

    private static void conditionAvailable(@Nullable ConditionSpec condition) {
        if (condition == null || condition.isAlways()) return;
        for (var child : list(condition.conditions)) conditionAvailable(child);
        conditionAvailable(condition.inner);
        if (ConditionBridge.toQuestCondition(condition) == null)
            throw new UnsupportedSnapshotException("Frozen condition cannot be restored; refusing to replace it with always: " + condition.condition);
    }

    @Nullable private static CollectionEntrySpecData originalSharedEntry(String entryId) {
        CollectionEntrySpecData found = null;
        for (var authoring : QuestAuthoringSnapshotRegistry.getDatapackSnapshot().values())
            if (authoring.spec().collectionConfig != null) for (var entry : list(authoring.spec().collectionConfig.entries))
                if (entryId.equals(entry.entryId)) {
                    if (found != null && !new com.google.gson.Gson().toJsonTree(found).equals(new com.google.gson.Gson().toJsonTree(entry)))
                        throw new UnsupportedSnapshotException("Shared authoring entries disagree: " + entryId);
                    found = entry;
                }
        // A code factory can inline its own shared entries. Do not guess callback definitions from a presentation.
        return found;
    }

    private static void checkDocumentSize(String json) {
        if (json == null || json.isBlank() || json.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES)
            throw new IllegalArgumentException("Frozen definition document is empty or exceeds " + MAX_DOCUMENT_BYTES + " bytes");
    }
    private static String hash(String json) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String factoryHash(ResourceLocation questId, String version) { return hash("CODE_FACTORY\n" + questId + "\n" + version); }
    private static QuestDefinition codeDefinition(ResourceLocation questId, String version) {
        Supplier<QuestDefinition> provider = CODE_DEFINITION_FACTORIES.get(new LegacyFactoryKey(questId, version));
        if (provider == null) throw unsupported(questId, "Frozen code factory version is unavailable: " + version);
        QuestDefinition definition = provider.get();
        if (definition == null || !definition.getId().equals(questId)) throw unsupported(questId, "Code factory returned a different quest identity");
        if (definition.getCollectionConfig() == null) throw unsupported(questId, "Code factory is not a collection quest");
        return definition;
    }
    private static UnsupportedSnapshotException unsupported(ResourceLocation questId, String detail) {
        return new UnsupportedSnapshotException("Quest '" + questId + "': " + detail);
    }
    private static <T> List<T> list(List<T> values) { return values == null ? List.of() : values; }
    private record Snapshot(ResourceLocation questId, String json, String factoryVersion) { }
    public record CaptureCapability(boolean supported, String kind, String version, String reason) { }
    private record LegacyFactoryKey(ResourceLocation questId, String oldVersion) {
        private LegacyFactoryKey {
            Objects.requireNonNull(questId, "legacy questId");
            if (oldVersion == null || oldVersion.isBlank() || oldVersion.length() > 128 || !oldVersion.equals(oldVersion.trim()))
                throw new IllegalArgumentException("Factory version must be explicit and stable (1..128 characters)");
        }
    }
    public static final class UnsupportedSnapshotException extends IllegalStateException {
        public UnsupportedSnapshotException(String message) { super(message); }
    }
}
