package org.arcadia.arc_quest.quest.spec;

import net.minecraft.resources.ResourceLocation;

import org.arcadia.arc_quest.data.sync.CollectionDefinitionSpecExporter;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.spec.io.QuestTextComponentCodec;

import java.util.*;

/** One codec used by Java export, authored resources and recipient sync. */
public final class CollectionAtlasCodec {
    private CollectionAtlasCodec() {}

    public static CollectionAtlasProfile profile(CollectionAtlasProfileSpecData s) {
        if (s == null) return CollectionAtlasProfile.defaults();
        return new CollectionAtlasProfile(
                s.schemaVersion,
                id(s.id),
                new CollectionAtlasProfile.Node(s.node.size, s.node.labelMaxLines),
                new CollectionAtlasProfile.Inspector(
                        s.inspector.preferredWidthFraction,
                        s.inspector.minWidth,
                        s.inspector.maxWidth,
                        s.inspector.minCanvasInteractionWidth,
                        s.inspector.sections, s.inspector.topInset, s.inspector.bottomInset),
                new CollectionAtlasProfile.Zoom(
                        s.zoom.hardMin,
                        s.zoom.baselineMax,
                        s.zoom.hardMax,
                        s.zoom.fitMultiplier,
                        s.zoom.minOperableNode,
                        s.zoom.minOperableSpacing,
                        s.zoom.maxNodeSize,
                        s.zoom.initialView),
                new CollectionAtlasProfile.Focus(
                        s.focus.zoomMultiplier,
                        s.focus.otherGroupAlpha,
                        s.focus.otherGroupFavoriteAlpha,
                        s.focus.restoreBrowseSnapshotOnClose),
                new CollectionAtlasProfile.Motion(
                        s.motion.dragThreshold,
                        s.motion.velocitySampleMs,
                        s.motion.maxVelocityPerMs,
                        s.motion.inertiaDecayMs,
                        s.motion.maxOverscroll,
                        s.motion.settleDecayMs),
                new CollectionAtlasProfile.InputDefaults(
                        s.inputDefaults.wheelMode,
                        s.inputDefaults.wasdNavigation,
                        s.inputDefaults.reduceMotion));
    }

    public static CollectionAtlasProfileSpecData profile(CollectionAtlasProfile s) {
        var gson = new com.google.gson.Gson();
        var tree = gson.toJsonTree(s).getAsJsonObject();
        tree.addProperty("id", s.id().toString());
        return gson.fromJson(tree, CollectionAtlasProfileSpecData.class);
    }

    public static CollectionSourceSetDefinition source(CollectionSourceSetSpecData s) {
        return new CollectionSourceSetDefinition(
                s.schemaVersion,
                id(s.id),
                s.relations.stream()
                        .peek(
                                r -> {
                                    if (!"PUBLIC_WHEN_ENDPOINTS_VISIBLE".equals(r.disclosure))
                                        throw new IllegalArgumentException(
                                                "Unsupported source disclosure: " + r.disclosure);
                                    if (!"COLLECTION_ENTRY".equals(r.source.type))
                                        throw new IllegalArgumentException(
                                                "Unsupported source endpoint: " + r.source.type);
                                })
                        .map(
                                r ->
                                        new CollectionSourceSetDefinition.Relation(
                                                id(r.relationId),
                                                id(r.source.entryId),
                                                id(r.target.entryId),
                                                r.target.variantIds,
                                                r.method,
                                                text(r.description),
                                                r.sortOrder))
                        .toList());
    }

    public static CollectionSourceSetSpecData source(CollectionSourceSetDefinition s) {
        var r = new CollectionSourceSetSpecData();
        r.id = s.id().toString();
        for (var rel : s.relations()) {
            var v = new CollectionSourceSetSpecData.Relation();
            v.relationId = rel.relationId().toString();
            v.source.entryId = rel.sourceEntryId().toString();
            v.target.entryId = rel.targetEntryId().toString();
            v.target.variantIds = rel.variantIds();
            v.method = rel.method();
            v.description = CollectionDefinitionSpecExporter.text(rel.description(), null);
            v.sortOrder = rel.sortOrder();
            r.relations.add(v);
        }
        return r;
    }

    private static ResourceLocation id(String s) {
        return s == null || s.isBlank() ? null : ResourceLocation.parse(s);
    }

    public static QuestText text(QuestTextSpec s) {
        if (s == null) return QuestText.literal("");
        if ("component".equals(s.mode))
            return QuestText.component(QuestTextComponentCodec.decode(s.value));
        if ("translatable".equals(s.mode))
            return QuestText.translatable(
                    s.value,
                    s.args.stream().map(QuestText.Arg::constant).toArray(QuestText.Arg[]::new));
        return QuestText.literal(s.value);
    }

    public static CollectionSubjectSpec subject(CollectionPresentationSpecData.Subject s) {
        if (s == null) return null;
        String rawId = s.id, nbt = s.snbt, mode = s.matchMode, match = s.matchSnbt;
        int count = s.count;
        var parameters = new LinkedHashMap<>(s.parameters);
        if (s.params != null) {
            var p = s.params;
            for (var e : p.entrySet())
                if (e.getValue().isJsonPrimitive())
                    parameters.put(e.getKey(), e.getValue().getAsString());
            if (p.has("item")) rawId = p.get("item").getAsString();
            else if (p.has("gem")) rawId = p.get("gem").getAsString();
            else if (p.has("id")) rawId = p.get("id").getAsString();
            else if (p.has("entity")) rawId = p.get("entity").getAsString();
            else if (p.has("contentId")) rawId = p.get("contentId").getAsString();
            else if (p.has("tag")) rawId = p.get("tag").getAsString();
            if (p.has("count")) count = p.get("count").getAsInt();
            if (p.has("nbt")) nbt = p.get("nbt").getAsString();
            if (p.has("identity")) {
                var i = p.getAsJsonObject("identity");
                if (i.has("mode")) mode = i.get("mode").getAsString();
                if (i.has("nbt")) match = i.get("nbt").getAsString();
            }
        }
        return new CollectionSubjectSpec(
                id(s.type),
                id(rawId),
                count,
                nbt,
                new StackMatchSpec(StackMatchSpec.Mode.valueOf(mode), match),
                parameters);
    }

    public static CollectionPresentationSpecData.Subject subject(CollectionSubjectSpec s) {
        if (s == null) return null;
        var r = new CollectionPresentationSpecData.Subject();
        r.type = s.type().toString();
        r.id = s.id().toString();
        r.count = s.count();
        r.snbt = s.snbt();
        r.matchMode = s.identity().mode().name();
        r.matchSnbt = s.identity().snbt();
        r.parameters = new LinkedHashMap<>(s.parameters());
        return r;
    }

    public static CollectionEntryPresentation presentation(CollectionPresentationSpecData s) {
        if (s == null) return null;
        return new CollectionEntryPresentation(
                subject(s.subject),
                s.variants.stream()
                        .map(
                                v ->
                                        new CollectionVariantDefinition(
                                                v.variantId,
                                                text(v.label),
                                                subject(v.subject),
                                                v.recordRef == null
                                                        ? v.outcomeId
                                                        : v.recordRef.outcomeId,
                                                CollectionContentReveal.valueOf(
                                                        "INHERIT_IDENTITY".equals(v.disclosure)
                                                                ? "ALWAYS"
                                                                : v.disclosure)))
                        .toList(),
                s.defaultVariantId,
                s.sourceSetIds.stream().map(CollectionAtlasCodec::id).toList(),
                s.detailPages.stream().map(p -> new CollectionDetailPageSpec(p.id, text(p.title),
                        CollectionDetailPageSpec.Type.valueOf(p.type.toUpperCase(java.util.Locale.ROOT)),
                        p.paragraphs.stream().map(CollectionAtlasCodec::text).toList(), p.providerSection, p.providerSectionCount)).toList(), s.obtainableAnyFlags);
    }

    public static CollectionPresentationSpecData presentation(CollectionEntryPresentation s) {
        if (s == null) return null;
        var r = new CollectionPresentationSpecData();
        r.subject = subject(s.subject());
        r.obtainableAnyFlags = new ArrayList<>(s.obtainableAnyFlags());
        r.defaultVariantId = s.defaultVariantId();
        r.sourceSetIds = s.sourceSetIds().stream().map(Object::toString).toList();
        for (var page : s.detailPages()) {
            var p = new CollectionPresentationSpecData.DetailPage();
            p.id = page.id();
            p.title = CollectionDefinitionSpecExporter.text(page.title(), null);
            p.type = page.type().name();
            p.paragraphs = page.paragraphs().stream()
                    .map(t -> CollectionDefinitionSpecExporter.text(t, null)).toList();
            p.providerSection = page.providerSection();
            p.providerSectionCount = page.providerSectionCount();
            r.detailPages.add(p);
        }
        for (var v : s.variants()) {
            var w = new CollectionPresentationSpecData.Variant();
            w.variantId = v.variantId();
            w.label = CollectionDefinitionSpecExporter.text(v.label(), null);
            w.subject = subject(v.subject());
            w.outcomeId = v.outcomeId();
            w.disclosure = v.disclosure().name();
            r.variants.add(w);
        }
        return r;
    }

    public static CollectionAtlasDefinition atlas(CollectionAtlasSpecData s) {
        if (s == null) return null;
        for (var c : s.categories)
            for (var g : c.groups) {
                if (g.layout != null && g.layout.params.containsKey("columns"))
                    g.columns = g.layout.params.get("columns").intValue();
                if (g.nodes.isEmpty())
                    for (String entry : g.entries) {
                        var n = new CollectionAtlasSpecData.Node();
                        n.entryId = entry;
                        g.nodes.add(n);
                    }
            }
        return new CollectionAtlasDefinition(
                s.schemaVersion,
                id(s.id),
                id(s.profileId),
                s.categories.stream()
                        .map(
                                c ->
                                        new CollectionAtlasDefinition.Category(
                                                c.categoryId,
                                                c.groups.stream()
                                                        .map(
                                                                g ->
                                                                        new CollectionAtlasDefinition
                                                                                .Group(
                                                                                g.groupId,
                                                                                text(g.title),
                                                                                g.columns,
                                                                                g.x,
                                                                                g.y,
                                                                                g.nodes.stream()
                                                                                        .map(
                                                                                                n ->
                                                                                                        new CollectionAtlasDefinition
                                                                                                                .Node(
                                                                                                                id(
                                                                                                                        n.entryId),
                                                                                                                n.x,
                                                                                                                n.y))
                                                                                        .toList(),
                                                                                g.layout == null
                                                                                        ? null
                                                                                        : id(
                                                                                                g.layout
                                                                                                        .type),
                                                                                g.layout == null
                                                                                        ? Map.of()
                                                                                        : g.layout
                                                                                                .params))
                                                        .toList(),
                                                c.layout == null ? null : id(c.layout.type),
                                                c.layout == null ? Map.of() : c.layout.params))
                        .toList());
    }

    public static CollectionAtlasSpecData atlas(CollectionAtlasDefinition s) {
        if (s == null) return null;
        var r = new CollectionAtlasSpecData();
        r.schemaVersion = s.schemaVersion();
        r.id = s.id().toString();
        r.profileId = s.profileId() == null ? "" : s.profileId().toString();
        for (var c : s.categories()) {
            var d = new CollectionAtlasSpecData.Category();
            d.categoryId = c.categoryId();
            d.layout = new CollectionAtlasSpecData.Layout();
            d.layout.type = c.layoutType().toString();
            d.layout.params = c.layoutParameters();
            for (var g : c.groups()) {
                var h = new CollectionAtlasSpecData.Group();
                h.groupId = g.groupId();
                h.layout = new CollectionAtlasSpecData.Layout();
                h.layout.type = g.layoutType().toString();
                h.layout.params = g.layoutParameters();
                h.title = CollectionDefinitionSpecExporter.text(g.title(), null);
                h.columns = g.columns();
                h.x = g.x();
                h.y = g.y();
                for (var n : g.nodes()) {
                    var m = new CollectionAtlasSpecData.Node();
                    m.entryId = n.entryId().toString();
                    m.x = n.x();
                    m.y = n.y();
                    h.nodes.add(m);
                }
                d.groups.add(h);
            }
            r.categories.add(d);
        }
        return r;
    }
}
