package org.arcadia.arc_quest.mutil.render;

import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.mutil.perf.ArcGuiProfiler;

import java.util.ArrayList;
import java.util.List;

public class ArcRenderQueue {
    private final List<RenderTask> backgroundTasks = new ArrayList<>();
    private final List<RenderTask> itemTasks = new ArrayList<>();
    private final List<RenderTask> tooltipTasks = new ArrayList<>();

    public void background(RenderTask task) {
        if (task != null) backgroundTasks.add(task);
    }

    public void item(RenderTask task) {
        if (task != null) {
            itemTasks.add(task);
            ArcGuiProfiler.itemRenderTask();
        }
    }

    public void tooltip(RenderTask task) {
        if (task != null) {
            tooltipTasks.add(task);
            ArcGuiProfiler.tooltipTask();
        }
    }

    public void flush(GuiGraphics graphics) {
        flush(backgroundTasks, graphics);
        flush(itemTasks, graphics);
        flush(tooltipTasks, graphics);
    }

    public void clear() {
        backgroundTasks.clear();
        itemTasks.clear();
        tooltipTasks.clear();
    }

    private void flush(List<RenderTask> tasks, GuiGraphics graphics) {
        for (RenderTask task : tasks) task.render(graphics);
        tasks.clear();
    }

    @FunctionalInterface
    public interface RenderTask {
        void render(GuiGraphics graphics);
    }
}
