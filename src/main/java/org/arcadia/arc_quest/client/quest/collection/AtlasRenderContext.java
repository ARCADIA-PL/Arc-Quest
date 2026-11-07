package org.arcadia.arc_quest.client.quest.collection;

import java.util.List;
import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.client.hud.component.HudTextMetrics;

/** Client-only immutable geometry in logical panel units, with physical pixel density. */
public record AtlasRenderContext(Font font, int width, int height, double pixelsPerUnit,
                                 int alpha, int theme, Rect clip, List<Rect> tooltipAvoidance) {
    public record Rect(int x, int y, int width, int height) {}
    public AtlasRenderContext {
        if (width < 1 || height < 1 || !Double.isFinite(pixelsPerUnit) || pixelsPerUnit <= 0) throw new IllegalArgumentException("Invalid render geometry");
        tooltipAvoidance=List.copyOf(tooltipAvoidance);
    }
    public float readableScale(float preferred) { return HudTextMetrics.readableScale(preferred,pixelsPerUnit); }
    public AtlasRenderContext appearance(int alpha,int theme) { return new AtlasRenderContext(font,width,height,pixelsPerUnit,alpha,theme,clip,tooltipAvoidance); }
    public void text(GuiGraphics g,Component text,float x,float y,float scale,int color) {
        g.pose().pushPose(); g.pose().translate(x,y,0); g.pose().scale(scale,scale,1); g.drawString(font,text,0,0,color,false); g.pose().popPose();
    }
}
