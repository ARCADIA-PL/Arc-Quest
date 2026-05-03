package org.arcadia.arc_quest.mutil.perf;

public final class ArcGuiProfiler {
    private static final ArcGuiFrameStats CURRENT = new ArcGuiFrameStats();
    private static final ArcGuiFrameStats LAST = new ArcGuiFrameStats();
    private static boolean enabled = false;

    private ArcGuiProfiler() {
    }

    public static void setEnabled(boolean enabled) {
        ArcGuiProfiler.enabled = enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void beginFrame() {
        if (enabled) CURRENT.reset();
    }

    public static void endFrame() {
        if (!enabled) return;
        copy(CURRENT, LAST);
    }

    public static ArcGuiFrameStats current() {
        return CURRENT;
    }

    public static ArcGuiFrameStats last() {
        return LAST;
    }

    public static String dumpLastFrame() {
        return "ArcGuiFrameStats{" +
                "updated=" + LAST.getElementsUpdated() +
                ", drawn=" + LAST.getElementsDrawn() +
                ", layoutInvalidations=" + LAST.getLayoutInvalidations() +
                ", layoutRebuilds=" + LAST.getLayoutRebuilds() +
                ", textLayouts=" + LAST.getTextLayouts() +
                ", scissor=" + LAST.getScissorPushes() + '/' + LAST.getScissorPops() +
                ", itemTasks=" + LAST.getItemRenderTasks() +
                ", tooltipTasks=" + LAST.getTooltipTasks() +
                '}';
    }

    public static void elementUpdated() { if (enabled) CURRENT.recordElementUpdated(); }
    public static void elementDrawn() { if (enabled) CURRENT.recordElementDrawn(); }
    public static void layoutInvalidated() { if (enabled) CURRENT.recordLayoutInvalidation(); }
    public static void layoutRebuilt() { if (enabled) CURRENT.recordLayoutRebuild(); }
    public static void textLaidOut() { if (enabled) CURRENT.recordTextLayout(); }
    public static void scissorPushed() { if (enabled) CURRENT.recordScissorPush(); }
    public static void scissorPopped() { if (enabled) CURRENT.recordScissorPop(); }
    public static void itemRenderTask() { if (enabled) CURRENT.recordItemRenderTask(); }
    public static void tooltipTask() { if (enabled) CURRENT.recordTooltipTask(); }

    private static void copy(ArcGuiFrameStats from, ArcGuiFrameStats to) {
        to.reset();
        for (int i = 0; i < from.getElementsUpdated(); i++) to.recordElementUpdated();
        for (int i = 0; i < from.getElementsDrawn(); i++) to.recordElementDrawn();
        for (int i = 0; i < from.getLayoutInvalidations(); i++) to.recordLayoutInvalidation();
        for (int i = 0; i < from.getLayoutRebuilds(); i++) to.recordLayoutRebuild();
        for (int i = 0; i < from.getTextLayouts(); i++) to.recordTextLayout();
        for (int i = 0; i < from.getScissorPushes(); i++) to.recordScissorPush();
        for (int i = 0; i < from.getScissorPops(); i++) to.recordScissorPop();
        for (int i = 0; i < from.getItemRenderTasks(); i++) to.recordItemRenderTask();
        for (int i = 0; i < from.getTooltipTasks(); i++) to.recordTooltipTask();
    }
}
