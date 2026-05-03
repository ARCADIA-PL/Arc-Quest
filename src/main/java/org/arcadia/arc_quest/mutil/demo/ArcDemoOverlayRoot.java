package org.arcadia.arc_quest.mutil.demo;

import net.minecraft.client.Minecraft;
import org.arcadia.arc_quest.mutil.animation.ArcAnimClock;
import org.arcadia.arc_quest.mutil.animation.ArcVisibilityFilter;
import org.arcadia.arc_quest.mutil.core.ArcGuiContext;
import org.arcadia.arc_quest.mutil.core.ArcGuiTickContext;
import org.arcadia.arc_quest.mutil.overlay.ArcOverlayRoot;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiRect;
import org.arcadia.arc_quest.mutil.primitive.ArcGuiText;
import org.arcadia.arc_quest.mutil.theme.ArcColorPalette;
import org.arcadia.arc_quest.mutil.theme.ArcTheme;

public class ArcDemoOverlayRoot extends ArcOverlayRoot {
    private final ArcTheme theme = ArcTheme.cyber();
    private final ArcVisibilityFilter visibilityFilter = new ArcVisibilityFilter(0.18f, 0.14f);
    private final ArcGuiRect panel;
    private final ArcGuiRect progressBg;
    private final ArcGuiRect progressFill;
    private final ArcGuiText title;
    private final ArcGuiText subtitle;
    private final ArcDemoOverlayPresenter presenter = new ArcDemoOverlayPresenter();
    private float reveal;

    public ArcDemoOverlayRoot(Minecraft minecraft) {
        super(minecraft, "arc_demo_overlay");
        panel = new ArcGuiRect(0, 0, 210, 54, theme.panelBackground());
        progressBg = new ArcGuiRect(12, 35, 186, 8, ArcColorPalette.PANEL_BG_SOFT);
        progressFill = new ArcGuiRect(12, 35, 0, 8, theme.accentColor());
        title = new ArcGuiText(12, 10, "ArcMutil Demo Overlay").setColor(theme.textPrimary());
        subtitle = new ArcGuiText(12, 21, "foundation / overlay / animation").setColor(theme.textSecondary());
        addChild(panel);
        panel.addChild(title);
        panel.addChild(subtitle);
        panel.addChild(progressBg);
        panel.addChild(progressFill);
    }

    @Override
    protected void tick(ArcGuiTickContext context, int refX, int refY) {
        presenter.tick(context);
        ArcDemoOverlayPresenter.Model model = presenter.model();
        reveal = visibilityFilter.update(isActive(), context.deltaTime());
        setX(Math.round(ArcAnimClock.lerp(getX(), model.targetX(), 0.16f, context.deltaTime())));
        setY(Math.round(ArcAnimClock.lerp(getY(), model.targetY(), 0.16f, context.deltaTime())));
        setOpacity(reveal);
        if (model.isDirty()) {
            progressFill.setWidth(Math.round(186 * model.progress()));
            model.markClean();
        }
    }

    @Override
    protected void update(ArcGuiContext context, int refX, int refY) {
        super.update(context, refX, refY);
    }
}
