package org.arcadia.arc_quest.quest.api;

public record CollectionVariantDefinition(
        String variantId,
        QuestText label,
        CollectionSubjectSpec subject,
        String outcomeId,
        CollectionContentReveal disclosure) {
    public CollectionVariantDefinition {
        if (variantId == null || variantId.isBlank() || variantId.length() > 128)
            throw new IllegalArgumentException("Invalid variantId");
        if (subject == null) throw new IllegalArgumentException("Variant subject required");
        label = label == null ? QuestText.literal(variantId) : label;
        outcomeId = outcomeId == null ? "" : outcomeId;
        disclosure = disclosure == null ? CollectionContentReveal.ALWAYS : disclosure;
    }
}
