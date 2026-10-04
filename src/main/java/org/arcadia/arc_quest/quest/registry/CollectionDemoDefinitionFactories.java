package org.arcadia.arc_quest.quest.registry;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.api.QuestDefinition;
import org.arcadia.arc_quest.quest.data.CollectionRunDefinitionStore;

/** Immutable executable demo versions. Registration never publishes legacy definitions to the live registry. */
public final class CollectionDemoDefinitionFactories {
    public static final String LEGACY_VERSION = "collection-v1";
    public static final String PREVIOUS_VERSION = "collection-v2";
    public static final String PRE_LOCALIZATION_VERSION = "collection-v3";
    public static final String CURRENT_VERSION = "collection-v4";
    private static boolean registered;
    private CollectionDemoDefinitionFactories() { }

    public static synchronized void ensureRegistered() {
        if (registered) return;
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.FIELD, LEGACY_VERSION,
                () -> LegacyCollectionFieldDemos.field(LegacyCollectionFieldDemos.entries()), false);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.RENEWABLE, LEGACY_VERSION,
                () -> LegacyCollectionFieldDemos.renewable(LegacyCollectionFieldDemos.entries()), false);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.PARALLEL, LEGACY_VERSION,
                () -> LegacyCollectionFieldDemos.parallel(LegacyCollectionFieldDemos.entries()), false);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.FIELD, PREVIOUS_VERSION,
                () -> FrozenCollectionFieldDemosV2.field(FrozenCollectionFieldDemosV2.entries()), false);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.RENEWABLE, PREVIOUS_VERSION,
                () -> FrozenCollectionFieldDemosV2.renewable(FrozenCollectionFieldDemosV2.entries()), false);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.PARALLEL, PREVIOUS_VERSION,
                () -> FrozenCollectionFieldDemosV2.parallel(FrozenCollectionFieldDemosV2.entries()), false);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.FIELD, PRE_LOCALIZATION_VERSION,
                () -> FrozenCollectionFieldDemosV3.field(FrozenCollectionFieldDemosV3.entries()), false);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.RENEWABLE, PRE_LOCALIZATION_VERSION,
                () -> FrozenCollectionFieldDemosV3.renewable(FrozenCollectionFieldDemosV3.entries()), false);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.PARALLEL, PRE_LOCALIZATION_VERSION,
                () -> FrozenCollectionFieldDemosV3.parallel(FrozenCollectionFieldDemosV3.entries()), false);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.FIELD, CURRENT_VERSION,
                () -> CollectionFieldDemos.field(CollectionFieldDemos.entries()), true);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.RENEWABLE, CURRENT_VERSION,
                () -> CollectionFieldDemos.renewable(CollectionFieldDemos.entries()), true);
        CollectionRunDefinitionStore.registerCodeDefinitionFactory(CollectionFieldDemos.PARALLEL, CURRENT_VERSION,
                () -> CollectionFieldDemos.parallel(CollectionFieldDemos.entries()), true);
        CollectionRunDefinitionStore.registerUnversionedLegacyVersion(CollectionFieldDemos.FIELD, LEGACY_VERSION);
        CollectionRunDefinitionStore.registerUnversionedLegacyVersion(CollectionFieldDemos.RENEWABLE, LEGACY_VERSION);
        CollectionRunDefinitionStore.registerUnversionedLegacyVersion(CollectionFieldDemos.PARALLEL, LEGACY_VERSION);
        registered = true;
    }

    public static boolean isHistoricalDemo(ResourceLocation questId) {
        return CollectionFieldDemos.FIELD.equals(questId) || CollectionFieldDemos.RENEWABLE.equals(questId)
                || CollectionFieldDemos.PARALLEL.equals(questId);
    }

    /** For pure reducers without a world, never pretend a v2 definition represents an old run. */
    public static QuestDefinition legacyDefinition(ResourceLocation questId) {
        if (CollectionFieldDemos.FIELD.equals(questId)) return LegacyCollectionFieldDemos.field(LegacyCollectionFieldDemos.entries());
        if (CollectionFieldDemos.RENEWABLE.equals(questId)) return LegacyCollectionFieldDemos.renewable(LegacyCollectionFieldDemos.entries());
        if (CollectionFieldDemos.PARALLEL.equals(questId)) return LegacyCollectionFieldDemos.parallel(LegacyCollectionFieldDemos.entries());
        throw new CollectionRunDefinitionStore.UnsupportedSnapshotException("No exact legacy demo factory: " + questId);
    }
}
