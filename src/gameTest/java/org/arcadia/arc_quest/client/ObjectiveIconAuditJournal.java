package org.arcadia.arc_quest.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.component.JournalScreenLayout;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailPanel;
import org.arcadia.arc_quest.client.hud.quest.journal.detail.JournalDetailSinglePhase;

import java.lang.reflect.Field;
import java.util.List;

/** Holds only animation clocks for reproducible screenshots; all journal rows remain production code. */
final class ObjectiveIconAuditJournal extends QuestJournalScreen {
    private static final Field TRANSITION = field(QuestJournalScreen.class, "transitionAlpha");
    private static final Field SUSPEND = field(QuestJournalScreen.class, "suspendAlpha");
    private static final Field LAST_RENDER = field(QuestJournalScreen.class, "lastRenderTime");
    private static final Field CLOSING = field(QuestJournalScreen.class, "isClosing");
    private static final Field TOOLTIP_ALPHA = field(QuestJournalScreen.class, "tooltipTipAlpha");
    private static final Field DETAIL = field(JournalDetailPanel.class, "detailReveal");
    private static final Field PHASE = field(JournalDetailPanel.class, "phaseTransitionAnim");
    private static final Field SCROLL = field(JournalDetailPanel.class, "detailScrollOffset");
    private static final Field TARGET_SCROLL = field(JournalDetailPanel.class, "detailTargetScroll");
    private static final Field ROWS = field(JournalDetailSinglePhase.class, "detailObjReveal");
    private static final Field PROGRESS = field(JournalDetailSinglePhase.class, "objProgressAnims");
    private float alpha = 1;
    private boolean highZ;

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            TRANSITION.setFloat(this, alpha);
            SUSPEND.setFloat(this, 1);
            LAST_RENDER.setLong(this, 0);
            var detail = getDetailPanel();
            DETAIL.setFloat(detail, 1);
            PHASE.setFloat(detail, 1);
            SCROLL.setDouble(detail, 0);
            TARGET_SCROLL.setDouble(detail, 0);
            ROWS.set(detail.singlePhaseRenderer, new float[]{1});
            PROGRESS.set(detail.singlePhaseRenderer, new float[]{0});
            graphics.fill(0, 0, width, height, 0xFF101820);
            super.render(graphics, -10_000, -10_000, partialTick);
            ObjectiveIconClientAudit.check(Math.abs(getEffectiveAlpha() - alpha) < .001, "Native journal animation hold drifted");
            if (highZ) renderHighZ(graphics);
        } catch (Throwable error) { ObjectiveIconClientAudit.fail(error); }
    }
    void closeAtHalfOpacity() {
        try { TOOLTIP_ALPHA.setFloat(this, .6f); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
        onClose();
        alpha = .5f;
        ObjectiveIconClientAudit.check(closing(), "Production onClose did not enter the closing state");
    }
    boolean closing() {
        try { return CLOSING.getBoolean(this); }
        catch (IllegalAccessException error) { throw new IllegalStateException(error); }
    }
    void highZ(boolean enabled) { highZ = enabled; }
    ObjectiveIconPixelAudit.Rect iconRect() {
        float ease = closing() ? alpha * alpha * alpha : 1 - (1 - alpha) * (1 - alpha) * (1 - alpha);
        var panel = JournalScreenLayout.calculate(getScaledWidth(), getScaledHeight(), ease).detailPanel();
        double scale = getUiScale();
        return new ObjectiveIconPixelAudit.Rect((panel.x() + 12) * scale, (panel.y() + 54) * scale, 24 * scale, 24 * scale);
    }
    List<ObjectiveIconPixelAudit.Rect> modalProbeRects() {
        int panelWidth = Math.min(380, Math.max(240, width - 36));
        int panelX = (width - panelWidth) / 2, panelY = Math.max(4, (height - 164) / 2);
        return List.of(new ObjectiveIconPixelAudit.Rect(panelX + 30, panelY + 94, panelWidth - 60, 20),
                new ObjectiveIconPixelAudit.Rect(panelX + 30, panelY + 64, panelWidth - 60, 16),
                new ObjectiveIconPixelAudit.Rect(panelX + 30, panelY + 132, panelWidth - 60, 13));
    }
    private void renderHighZ(GuiGraphics graphics) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, 600);
            for (var rect : modalProbeRects()) {
                int x = (int) rect.x(), y = (int) rect.y();
                graphics.drawString(font, "PARENT WWWWWWWWWWWWWWWWWWWWW", x, y + 1, 0xFFFFFFFF, false);
                for (int offset = 12; offset + 16 < rect.width(); offset += 48)
                    graphics.renderFakeItem(new ItemStack(Items.DIAMOND), x + offset, y);
            }
        } finally { graphics.pose().popPose(); }
        // Keep ordinary deferred batches: the actual settings modal must flush and reset depth.
    }
    private static Field field(Class<?> owner, String name) {
        try { Field result = owner.getDeclaredField(name); result.setAccessible(true); return result; }
        catch (ReflectiveOperationException error) { throw new ExceptionInInitializerError(error); }
    }
}
