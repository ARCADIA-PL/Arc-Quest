package org.arcadia.arc_quest.mutil.demo;

import org.arcadia.arc_quest.mutil.core.ArcGuiTickContext;
import org.arcadia.arc_quest.mutil.presenter.ArcHudPresenter;
import org.arcadia.arc_quest.mutil.viewmodel.ArcViewModelVersion;

public class ArcDemoOverlayPresenter implements ArcHudPresenter {
    private final Model model = new Model();
    private float progress;
    private boolean forward = true;

    @Override
    public void tick(ArcGuiTickContext context) {
        int targetX = context.screenWidth() - 230;
        int targetY = 18;
        float delta = context.deltaTime() * 0.45f;
        progress += forward ? delta : -delta;
        if (progress >= 1f) {
            progress = 1f;
            forward = false;
        } else if (progress <= 0f) {
            progress = 0f;
            forward = true;
        }
        model.set(targetX, targetY, progress);
    }

    public Model model() {
        return model;
    }

    public static class Model {
        private final ArcViewModelVersion version = new ArcViewModelVersion();
        private int targetX;
        private int targetY;
        private float progress;

        public int targetX() {
            return targetX;
        }

        public int targetY() {
            return targetY;
        }

        public float progress() {
            return progress;
        }

        public boolean isDirty() {
            return version.isDirty();
        }

        public void markClean() {
            version.markClean();
        }

        private void set(int targetX, int targetY, float progress) {
            if (this.targetX == targetX && this.targetY == targetY && Math.abs(this.progress - progress) < 0.001f) return;
            this.targetX = targetX;
            this.targetY = targetY;
            this.progress = progress;
            version.markDirty();
        }
    }
}
