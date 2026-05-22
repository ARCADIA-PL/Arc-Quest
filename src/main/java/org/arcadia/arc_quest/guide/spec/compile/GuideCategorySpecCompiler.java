package org.arcadia.arc_quest.guide.spec.compile;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.guide.api.GuideCategory;
import org.arcadia.arc_quest.guide.builder.GuideCategoryBuilder;
import org.arcadia.arc_quest.guide.spec.GuideCategorySpec;
import org.arcadia.arc_quest.guide.spec.validate.GuideCategorySpecValidator;

public final class GuideCategorySpecCompiler {

    private final GuideCategorySpecValidator validator = new GuideCategorySpecValidator();

    public GuideCategory compile(GuideCategorySpec spec) {
        var report = validator.validate(spec);
        if (report.hasErrors()) {
            throw new GuideCompileException("GuideCategorySpec validation failed for '" + (spec == null ? "null" : spec.id) + "'");
        }

        GuideCategoryBuilder builder = GuideCategoryBuilder.create(parseId(spec.id))
                .displayName(GuideSpecCompiler.compileText(spec.displayName).resolve(null, null))
                .themeColor(spec.themeColor)
                .sortOrder(spec.sortOrder);

        ResourceLocation iconTexture = parseNullableId(spec.iconTexture);
        if (iconTexture != null) {
            builder.iconTexture(iconTexture);
        }
        return builder.build();
    }

    private ResourceLocation parseId(String value) {
        ResourceLocation id = GuideCategorySpecValidator.parseCategoryId(value);
        if (id == null) {
            throw new GuideCompileException("Invalid guide category id: '" + value + "'");
        }
        return id;
    }

    private ResourceLocation parseNullableId(String value) {
        return value == null || value.isBlank() ? null : ResourceLocation.tryParse(value);
    }
}
