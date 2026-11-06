package org.arcadia.arc_quest.quest.spec.io;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;

import org.arcadia.arc_quest.quest.api.StackMatchSpec;
import org.arcadia.arc_quest.quest.spec.*;
import org.junit.jupiter.api.Test;

class CollectionAtlasCodecTest {
    @Test
    void undiscoveredDimmingRoundTripsWithoutHidingOrAwardingDiscovery() {
        var source = new CollectionPresentationSpecData();
        source.dimUntilDiscovered = true;
        var view = CollectionAtlasCodec.presentation(CollectionAtlasCodec.presentation(CollectionAtlasCodec.presentation(source)));
        assertTrue(view.dimUntilDiscovered());
        assertFalse(view.obtainable(flag -> true, false));
        assertTrue(view.obtainable(flag -> false, true));
        assertFalse(CollectionAtlasCodec.presentation(new CollectionPresentationSpecData()).dimUntilDiscovered());
    }
    @Test
    void obtainablePresentationFlagsRoundTripAndNeverOverrideAcquisition() {
        var view = new org.arcadia.arc_quest.quest.api.CollectionEntryPresentation(null, java.util.List.of(), "",
                java.util.List.of(), java.util.List.of(), java.util.List.of("test:research"));
        var restored = CollectionAtlasCodec.presentation(CollectionAtlasCodec.presentation(view));
        assertFalse(restored.obtainable(flag -> false, false));
        assertTrue(restored.obtainable("test:research"::equals, false));
        assertTrue(restored.obtainable(flag -> false, true));
        assertTrue(org.arcadia.arc_quest.quest.api.CollectionEntryPresentation.empty().obtainable(flag -> false, false));
    }
    @Test
    void customPagesSurviveExportAndRejectInvalidAuthoring() {
        var source = new CollectionPresentationSpecData();
        var page = new CollectionPresentationSpecData.DetailPage();
        page.id = "research"; page.type = "provider"; page.providerSection = 2;
        page.title = QuestTextSpec.translatable("example.research");
        source.detailPages.add(page);
        var compiled = CollectionAtlasCodec.presentation(source);
        var reloaded = CollectionAtlasCodec.presentation(CollectionAtlasCodec.presentation(compiled));
        assertEquals("research", reloaded.detailPages().get(0).id());
        assertEquals(2, reloaded.detailPages().get(0).providerSection());
        assertEquals(org.arcadia.arc_quest.quest.api.CollectionDetailPageSpec.Type.PROVIDER, reloaded.detailPages().get(0).type());
        source.detailPages.add(page);
        assertThrows(IllegalArgumentException.class, () -> CollectionAtlasCodec.presentation(source));
        source.detailPages.remove(1); page.type = "execute_command";
        assertThrows(IllegalArgumentException.class, () -> CollectionAtlasCodec.presentation(source));
    }

    @Test
    void providerTailKeepsAllExchangeSectionsAndLegacySingleSectionDefault() {
        var page = org.arcadia.arc_quest.quest.api.CollectionDetailPageSpec.providerTail("exchange",
                org.arcadia.arc_quest.quest.api.QuestText.literal("Exchange"), 1);
        var view = new org.arcadia.arc_quest.quest.api.CollectionEntryPresentation(null, java.util.List.of(), "",
                java.util.List.of(), java.util.List.of(page));
        var restored = CollectionAtlasCodec.presentation(CollectionAtlasCodec.presentation(view));
        assertEquals(1, restored.detailPages().get(0).providerSection());
        assertEquals(0, restored.detailPages().get(0).providerSectionCount());
        assertEquals(1, new CollectionPresentationSpecData.DetailPage().providerSectionCount);
        assertThrows(IllegalArgumentException.class, () ->
                org.arcadia.arc_quest.quest.api.CollectionDetailPageSpec.providerTail("bad",
                        org.arcadia.arc_quest.quest.api.QuestText.literal("Bad"), -1));
    }

    @Test
    void legacyPresentationAndInsetProfileRemainCompatible() {
        var old = new org.arcadia.arc_quest.quest.api.CollectionEntryPresentation(null, java.util.List.of(), "", java.util.List.of());
        assertTrue(CollectionAtlasCodec.presentation(CollectionAtlasCodec.presentation(old)).detailPages().isEmpty());
        var profile = new CollectionAtlasProfileSpecData();
        profile.inspector.topInset = 12; profile.inspector.bottomInset = 20;
        var reloaded = CollectionAtlasCodec.profile(CollectionAtlasCodec.profile(CollectionAtlasCodec.profile(profile)));
        assertEquals(12, reloaded.inspector().topInset());
        assertEquals(20, reloaded.inspector().bottomInset());
    }
    @Test
    void approvedParamsAndRecordReferenceAreCompiledWithoutLosingNbtTypeSyntax() {
        var source = new CollectionPresentationSpecData();
        var variant = new CollectionPresentationSpecData.Variant();
        variant.variantId = "rare";
        variant.disclosure = "INHERIT_IDENTITY";
        variant.recordRef = new CollectionPresentationSpecData.RecordRef();
        variant.recordRef.outcomeId = "quality/rare";
        variant.subject = new CollectionPresentationSpecData.Subject();
        variant.subject.params =
                com.google.gson.JsonParser.parseString(
                                """
{"item":"minecraft:diamond","count":1,"nbt":"{quality:2b}","identity":{"mode":"NBT_SUBSET","nbt":"{quality:2b}"}}
""")
                        .getAsJsonObject();
        source.variants.add(variant);
        source.defaultVariantId = "rare";
        var compiled = CollectionAtlasCodec.presentation(source);
        var resolved = compiled.variants().get(0);
        assertEquals("quality/rare", resolved.outcomeId());
        assertEquals(StackMatchSpec.Mode.NBT_SUBSET, resolved.subject().identity().mode());
        assertEquals("{quality:2b}", resolved.subject().snbt());
        var roundTrip =
                CollectionAtlasCodec.presentation(CollectionAtlasCodec.presentation(compiled));
        assertEquals(resolved.subject(), roundTrip.variants().get(0).subject());
    }

    @Test
    void categoryAndGroupLayoutParametersSurviveExportAndReload() {
        var atlas = new CollectionAtlasSpecData();
        atlas.id = "example:gems";
        var category = new CollectionAtlasSpecData.Category();
        category.categoryId = "defense";
        category.layout = new CollectionAtlasSpecData.Layout();
        category.layout.type = "arc_quest:group_grid";
        category.layout.params.put("gapX", 24d);
        var group = new CollectionAtlasSpecData.Group();
        group.groupId = "armor";
        group.entries.add("example:royalty");
        group.layout = new CollectionAtlasSpecData.Layout();
        group.layout.params.put("columns", 4d);
        group.layout.params.put("gapY", 12d);
        category.groups.add(group);
        atlas.categories.add(category);
        var compiled = CollectionAtlasCodec.atlas(atlas);
        assertEquals(4, compiled.categories().get(0).groups().get(0).columns());
        var reloaded = CollectionAtlasCodec.atlas(CollectionAtlasCodec.atlas(compiled));
        assertEquals(24d, reloaded.categories().get(0).layoutParameters().get("gapX"));
        assertEquals(
                12d, reloaded.categories().get(0).groups().get(0).layoutParameters().get("gapY"));
        assertEquals(
                ResourceLocation.parse("example:royalty"),
                reloaded.categories().get(0).groups().get(0).nodes().get(0).entryId());
    }

    @Test
    void profileNumbersRejectNonFiniteAndImpossibleZoomBounds() {
        var profile = new CollectionAtlasProfileSpecData();
        profile.zoom.hardMin = 2;
        profile.zoom.hardMax = 1;
        assertThrows(IllegalArgumentException.class, () -> CollectionAtlasCodec.profile(profile));
    }
}
