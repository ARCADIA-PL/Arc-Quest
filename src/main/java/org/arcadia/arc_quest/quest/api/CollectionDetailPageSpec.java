package org.arcadia.arc_quest.quest.api;

import java.util.List;
import java.util.Objects;

/** Pure presentation. Browsing a page never runs an objective, transaction or reward. */
public record CollectionDetailPageSpec(String id, QuestText title, Type type,
        List<QuestText> paragraphs, int providerSection, int providerSectionCount) {
    public enum Type { TEXT, CONTENT, SOURCES, PROVIDER }

    public CollectionDetailPageSpec(String id, QuestText title, Type type,
            List<QuestText> paragraphs, int providerSection) {
        this(id, title, type, paragraphs, providerSection, 1);
    }

    public CollectionDetailPageSpec {
        if (id == null || !id.matches("[a-z0-9_.:/-]+") || id.startsWith("__"))
            throw new IllegalArgumentException("Invalid/reserved detail page id: " + id);
        Objects.requireNonNull(title, "Detail page title");
        Objects.requireNonNull(type, "Detail page type");
        paragraphs = List.copyOf(paragraphs == null ? List.of() : paragraphs);
        if (paragraphs.size() > 128 || providerSection < -1 || providerSection >= 16
                || providerSectionCount < 0 || providerSectionCount > 16)
            throw new IllegalArgumentException("Detail page bounds exceeded: " + id);
    }

    public static CollectionDetailPageSpec text(String id, QuestText title, QuestText... paragraphs) {
        return new CollectionDetailPageSpec(id, title, Type.TEXT, List.of(paragraphs), -1);
    }

    public static CollectionDetailPageSpec sources(String id, QuestText title) {
        return new CollectionDetailPageSpec(id, title, Type.SOURCES, List.of(), -1);
    }

    /** -1 includes the whole authorized document; a nonnegative index selects one section. */
    public static CollectionDetailPageSpec provider(String id, QuestText title, int section) {
        return new CollectionDetailPageSpec(id, title, Type.PROVIDER, List.of(), section);
    }

    /** All authorized sections from firstSection onwards, including every exchange plan/result. */
    public static CollectionDetailPageSpec providerTail(String id, QuestText title, int firstSection) {
        if (firstSection < 0) throw new IllegalArgumentException("First section must be nonnegative");
        return new CollectionDetailPageSpec(id, title, Type.PROVIDER, List.of(), firstSection, 0);
    }
}
