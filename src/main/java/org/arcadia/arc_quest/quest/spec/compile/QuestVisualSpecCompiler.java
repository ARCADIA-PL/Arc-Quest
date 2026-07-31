package org.arcadia.arc_quest.quest.spec.compile;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.quest.api.IconPosition;
import org.arcadia.arc_quest.quest.api.QuestVisualConfig;
import org.arcadia.arc_quest.quest.api.SplashType;
import org.arcadia.arc_quest.quest.api.VisualAsset;
import org.arcadia.arc_quest.quest.spec.QuestVisualSpec;

import java.util.Map;

public final class QuestVisualSpecCompiler {

    private QuestVisualSpecCompiler() {
    }

    public static QuestVisualConfig compile(QuestVisualSpec spec) {
        QuestVisualConfig.Builder builder = QuestVisualConfig.builder()
                .themeColor(spec == null ? 0xFFFFFF : spec.themeColor)
                .useQuestSplashPresentation(spec != null && Boolean.TRUE.equals(spec.useQuestSplashPresentation));
        if (spec == null) return builder.build();

        for (Map.Entry<String, QuestVisualSpec.AssetSpec> entry : spec.splashes.entrySet()) {
            VisualAsset asset = compileAsset(entry.getValue(), "splashes." + entry.getKey());
            if (asset.enabled()) builder.splash(parseEnum(SplashType.class, entry.getKey()), asset);
        }
        for (Map.Entry<String, QuestVisualSpec.AssetSpec> entry : spec.icons.entrySet()) {
            VisualAsset asset = compileAsset(entry.getValue(), "icons." + entry.getKey());
            if (asset.enabled()) builder.icon(parseEnum(IconPosition.class, entry.getKey()), asset);
        }
        return builder.build();
    }

    private static VisualAsset compileAsset(QuestVisualSpec.AssetSpec spec, String path) {
        if (spec == null || !spec.enabled) return VisualAsset.DISABLED;

        VisualAsset.Builder builder = VisualAsset.builder()
                .scale(spec.scale)
                .offset(spec.offsetX, spec.offsetY)
                .tintColor(spec.tintColor)
                .enabled(true);

        if (spec.texture != null && !spec.texture.isBlank()) {
            builder.texture(parseId(spec.texture, path + ".texture"));
        }
        if (spec.itemId != null && !spec.itemId.isBlank()) {
            ResourceLocation itemId = parseId(spec.itemId, path + ".itemId");
            Item item = BuiltInRegistries.ITEM.getOptional(itemId)
                    .orElseThrow(() -> new QuestCompileException("Unknown item id at " + path + ": " + itemId));
            builder.item(new ItemStack(item, Math.max(1, spec.itemCount)));
        }
        if ((spec.texture == null || spec.texture.isBlank()) && (spec.itemId == null || spec.itemId.isBlank())) {
            throw new QuestCompileException("Visual asset requires texture or itemId at " + path);
        }
        return builder.build();
    }

    private static ResourceLocation parseId(String value, String path) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new QuestCompileException("Invalid resource id at " + path + ": " + value);
        return id;
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new QuestCompileException("Unknown " + type.getSimpleName() + ": " + value);
        }
    }
}
