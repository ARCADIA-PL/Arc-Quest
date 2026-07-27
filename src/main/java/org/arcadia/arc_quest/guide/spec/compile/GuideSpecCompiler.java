package org.arcadia.arc_quest.guide.spec.compile;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import org.arcadia.arc_quest.condition.ConditionBridge;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.api.GuideDefinition;
import org.arcadia.arc_quest.guide.api.GuideText;
import org.arcadia.arc_quest.guide.builder.GuideBuilder;
import org.arcadia.arc_quest.guide.builder.GuideMediaBuilder;
import org.arcadia.arc_quest.guide.builder.GuidePageBuilder;
import org.arcadia.arc_quest.guide.spec.GuideMediaSpec;
import org.arcadia.arc_quest.guide.spec.GuidePageSpec;
import org.arcadia.arc_quest.guide.spec.GuideSpec;
import org.arcadia.arc_quest.guide.spec.GuideTextSpec;
import org.arcadia.arc_quest.guide.spec.validate.GuideCategorySpecValidator;
import org.arcadia.arc_quest.guide.spec.validate.GuideSpecValidator;
import org.arcadia.arc_quest.quest.util.IntelSceneIdHelper;

import java.util.Map;

public final class GuideSpecCompiler {

    private final GuideSpecValidator validator = new GuideSpecValidator();
    private final Map<ResourceLocation, GuideCategory> categories;

    public GuideSpecCompiler() {
        this(Map.ofEntries(
                Map.entry(GuideCategory.BASICS.getId(), GuideCategory.BASICS),
                Map.entry(GuideCategory.QUEST.getId(), GuideCategory.QUEST),
                Map.entry(GuideCategory.DIALOGUE.getId(), GuideCategory.DIALOGUE),
                Map.entry(GuideCategory.TRADE.getId(), GuideCategory.TRADE),
                Map.entry(GuideCategory.PONDER.getId(), GuideCategory.PONDER),
                Map.entry(GuideCategory.ADVANCED.getId(), GuideCategory.ADVANCED)
        ));
    }

    public GuideSpecCompiler(Map<ResourceLocation, GuideCategory> categories) {
        this.categories = categories;
    }

    static GuideText compileText(GuideTextSpec spec) {
        if (spec == null) {
            return GuideText.literal("");
        }
        return switch (spec.mode) {
            case "translatable" ->
                    GuideText.translatable(spec.value, spec.args == null ? new GuideText.Arg[0] : spec.args.stream().map(value -> GuideText.Arg.of((player, context) -> value)).toArray(GuideText.Arg[]::new));
            case "literal" -> GuideText.literal(spec.value);
            default -> throw new GuideCompileException("Unsupported GuideTextSpec mode: '" + spec.mode + "'");
        };
    }

    public GuideDefinition compile(GuideSpec spec) {
        var report = validator.validate(spec);
        if (report.hasErrors()) {
            throw new GuideCompileException("GuideSpec validation failed for '" + (spec == null ? "null" : spec.id) + "'");
        }

        GuideBuilder builder = GuideBuilder.create(parseId(spec.id))
                .category(resolveCategory(spec.category))
                .title(compileText(spec.title))
                .summary(compileText(spec.summary))
                .sortOrder(spec.sortOrder);

        ResourceLocation iconId = parseNullableId(spec.icon);
        if (iconId != null) builder.icon(BuiltInRegistries.ITEM.get(iconId));
        builder.renderLargeIconOnIntro(spec.renderLargeIconOnIntro)
                .unlockPopup(spec.showUnlockPopup)
                .renderPopupBackground(spec.renderPopupBackground);
        ResourceLocation popupBackground = parseNullableId(spec.popupBackground);
        if (popupBackground != null) builder.popupBackground(popupBackground)
                .renderPopupBackground(spec.renderPopupBackground);

        if (spec.hidden) {
            builder.hidden();
        }
        if (spec.repeatablePopup) {
            builder.repeatablePopup();
        }
        for (var condition : ConditionBridge.toQuestConditions(spec.unlockConditions)) {
            builder.unlockCondition(condition);
        }
        for (GuidePageSpec pageSpec : spec.pages) {
            builder.page(compilePage(pageSpec));
        }
        return builder.build();
    }

    private GuidePageBuilder compilePage(GuidePageSpec spec) {
        return GuidePageBuilder.create()
                .media(compileMedia(spec.media))
                .description(compileText(spec.description));
    }

    private GuideMediaBuilder compileMedia(GuideMediaSpec spec) {
        if (spec == null) {
            return GuideMediaBuilder.none();
        }
        String type = normalizeMediaType(spec.type);
        return switch (type) {
            case "none" -> GuideMediaBuilder.none();
            case "image" -> GuideMediaBuilder.image(parseId(spec.texture)).size(spec.width, spec.height);
            case "ponder" ->
                    GuideMediaBuilder.ponder(normalizeGuideSceneId(spec.sceneId)).size(spec.width, spec.height).autoplay(spec.autoplay).loop(spec.loop);
            default -> throw new GuideCompileException("Invalid guide media type: '" + spec.type + "'");
        };
    }

    private ResourceLocation normalizeGuideSceneId(String rawSceneId) {
        String value = blankToNull(rawSceneId);
        if (value == null) {
            throw new GuideCompileException("Ponder media requires sceneId");
        }
        ResourceLocation parsed = parseNullableId(value);
        if (parsed == null) {
            throw new GuideCompileException("Invalid ponder sceneId: '" + rawSceneId + "'");
        }

        String path = parsed.getPath();

        if (path.startsWith("quest_phase/") || path.startsWith("dialogue/")
                || path.startsWith("trade/") || path.startsWith("tutorial/")) {
            return parsed;
        }

        if (!path.contains("/")) {
            return parsed;
        }

        String[] segments = path.split("/", 2);
        if (segments.length == 2 && !segments[0].isBlank() && !segments[1].isBlank()) {
            String questId = parsed.getNamespace() + ":" + segments[0];
            String phaseId = parsed.getNamespace() + ":" + segments[1];
            return IntelSceneIdHelper.questPhaseId(questId, phaseId);
        }

        throw new GuideCompileException("Invalid ponder sceneId shorthand: '" + rawSceneId + "'");
    }

    private GuideCategory resolveCategory(String rawCategory) {
        ResourceLocation categoryId = GuideCategorySpecValidator.parseCategoryId(rawCategory);
        if (categoryId == null) {
            throw new GuideCompileException("Invalid guide category: '" + rawCategory + "'");
        }
        GuideCategory category = categories.get(categoryId);
        if (category == null) {
            throw new GuideCompileException("Unknown guide category: '" + categoryId + "'");
        }
        return category;
    }

    private String normalizeMediaType(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase();
    }

    private ResourceLocation parseId(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new GuideCompileException("Invalid resource id: '" + value + "'");
        }
        return id;
    }

    private ResourceLocation parseNullableId(String value) {
        return blankToNull(value) == null ? null : ResourceLocation.tryParse(value);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
