package org.arcadia.arc_quest.client.quest.collection;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.quest.collection.AtlasNativeTooltip;
import org.arcadia.arc_quest.client.hud.component.HudTextMetrics;

/** A compact two-column attribute panel, separate from the item's narrative tooltip. */
public final class AtlasStatGrid implements AtlasNativeTooltip.StyledComponent {
    private final AtlasRenderContext context;
    public record Stat(Component label, String value) { public Stat { java.util.Objects.requireNonNull(label); java.util.Objects.requireNonNull(value); } }
    private final List<Stat> stats;
    private final int width;
    private final float labelScale, valueScale;
    private final int rowHeight;
    private final boolean stacked;

    public AtlasStatGrid(List<Stat> stats, AtlasRenderContext context) {
        if (stats.isEmpty() || stats.size()>32) throw new IllegalArgumentException("Stat grid needs 1..32 rows");
        this.context=context; this.width=context.width(); this.stats=List.copyOf(stats);
        var font=context.font(); double pixels=context.pixelsPerUnit();
        labelScale = HudTextMetrics.readableScale(.9f, pixels);
        valueScale = HudTextMetrics.readableScale(1f, pixels);
        stacked = stats.stream().anyMatch(stat -> font.width(stat.label()) * labelScale + font.width(stat.value()) * valueScale + 19 > width / 2f);
        rowHeight = Math.max(18, (int) Math.ceil(stacked ? 8 * (labelScale + valueScale) + 10 : 8 * valueScale + 10));
    }
    @Override public int getHeight() { return rowHeight * ((stats.size()+1)/2) + 6; }
    @Override public int getWidth(Font font) { return width; }
    @Override public String plainText() {
        return String.join(" · ", stats.stream().map(stat -> stat.label().getString() + " " + stat.value()).toList());
    }
    @Override public void renderInAtlas(Font font, GuiGraphics g, int alpha, int theme) {
        int height = rowHeight * ((stats.size()+1)/2), middle = width / 2;
        g.fillGradient(0, 0, width, height, tint(0x15212A, alpha * 3 / 5), tint(0x101820, alpha * 2 / 5));
        // Half-unit rules match the surrounding V6 panel borders.
        g.pose().pushPose(); g.pose().scale(.5f, .5f, 1);
        g.fill(0, 0, width * 2, 1, tint(0x93A4AE, alpha / 4));
        g.fill(0, height * 2 - 1, width * 2, height * 2, tint(0x93A4AE, alpha / 6));
        g.fill(middle * 2, 8, middle * 2 + 1, height * 2 - 8, tint(0x93A4AE, alpha / 6));
        for (int row = 1; row < (stats.size()+1)/2; row++)
            g.fill(12, row * rowHeight * 2, width * 2 - 12, row * rowHeight * 2 + 1, tint(0x93A4AE, alpha / 9));
        g.pose().popPose();
        for (int i = 0; i < stats.size(); i++) {
            var stat = stats.get(i);
            int left = i % 2 * middle, right = i % 2 == 0 ? middle : width;
            float top = i / 2 * rowHeight;
            float labelY = top + (rowHeight - 8 * labelScale) / 2f;
            float valueY = top + (rowHeight - 8 * valueScale) / 2f;
            if (stacked) {
                float textHeight = 8 * (labelScale + valueScale) + 3;
                labelY = top + (rowHeight - textHeight) / 2f;
                valueY = labelY + 8 * labelScale + 3;
            }
            context.text(g, stat.label(), left + 6, labelY, labelScale, tint(0xB1C2CD, alpha));
            context.text(g, Component.literal(stat.value()), right - 7 - font.width(stat.value()) * valueScale,
                    valueY, valueScale, tint(0xE8E0CE, alpha));
        }
    }
    private static int tint(int color, int alpha) { return Math.max(0, Math.min(255, alpha)) << 24 | color; }
}
