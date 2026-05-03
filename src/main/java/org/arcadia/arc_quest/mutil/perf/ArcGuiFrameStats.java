package org.arcadia.arc_quest.mutil.perf;

public class ArcGuiFrameStats {
    private int elementsUpdated;
    private int elementsDrawn;
    private int layoutInvalidations;
    private int layoutRebuilds;
    private int textLayouts;
    private int scissorPushes;
    private int scissorPops;
    private int itemRenderTasks;
    private int tooltipTasks;

    public void reset() {
        elementsUpdated = 0;
        elementsDrawn = 0;
        layoutInvalidations = 0;
        layoutRebuilds = 0;
        textLayouts = 0;
        scissorPushes = 0;
        scissorPops = 0;
        itemRenderTasks = 0;
        tooltipTasks = 0;
    }

    public void recordElementUpdated() { elementsUpdated++; }
    public void recordElementDrawn() { elementsDrawn++; }
    public void recordLayoutInvalidation() { layoutInvalidations++; }
    public void recordLayoutRebuild() { layoutRebuilds++; }
    public void recordTextLayout() { textLayouts++; }
    public void recordScissorPush() { scissorPushes++; }
    public void recordScissorPop() { scissorPops++; }
    public void recordItemRenderTask() { itemRenderTasks++; }
    public void recordTooltipTask() { tooltipTasks++; }

    public int getElementsUpdated() { return elementsUpdated; }
    public int getElementsDrawn() { return elementsDrawn; }
    public int getLayoutInvalidations() { return layoutInvalidations; }
    public int getLayoutRebuilds() { return layoutRebuilds; }
    public int getTextLayouts() { return textLayouts; }
    public int getScissorPushes() { return scissorPushes; }
    public int getScissorPops() { return scissorPops; }
    public int getItemRenderTasks() { return itemRenderTasks; }
    public int getTooltipTasks() { return tooltipTasks; }
}
