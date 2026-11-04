package org.arcadia.arc_quest.quest.spec.io;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;

import org.arcadia.arc_quest.quest.api.StackMatchSpec;
import org.arcadia.arc_quest.quest.spec.*;
import org.junit.jupiter.api.Test;

class CollectionAtlasCodecTest {
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
