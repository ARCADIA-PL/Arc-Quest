package org.arcadia.arc_quest.client.hud.ponder;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

public final class EmbeddedPonderSceneHandle {

    @Nullable
    private final ResourceLocation sceneId;
    @Nullable
    private final List<PonderScene> scenes;
    private int sceneIndex;
    private boolean paused;
    private boolean valid;
    private final int themeColor;

    public EmbeddedPonderSceneHandle(@Nullable ResourceLocation sceneId,
                                     @Nullable List<PonderScene> scenes,
                                     boolean paused,
                                     int themeColor) {
        this.sceneId = sceneId;
        this.scenes = scenes == null ? null : List.copyOf(scenes);
        this.sceneIndex = 0;
        this.paused = paused;
        this.valid = this.scenes != null && !this.scenes.isEmpty();
        this.themeColor = themeColor;
        if (valid) {
            currentScene().begin();
        }
    }

    public boolean isValid() {
        return valid;
    }

    @Nullable
    public ResourceLocation getSceneId() {
        return sceneId;
    }

    public int getSceneIndex() {
        return sceneIndex;
    }

    public int getSceneCount() {
        return scenes == null ? 0 : scenes.size();
    }

    public boolean isPaused() {
        return paused;
    }

    public int getThemeColor() {
        return themeColor;
    }

    public void tick() {
        if (!valid || paused) {
            return;
        }
        PonderUI.ponderTicks++;
        currentScene().tick();
    }

    public void replay() {
        if (!valid) {
            return;
        }
        currentScene().begin();
        paused = false;
    }

    public void scrollForward() {
        if (!valid || scenes == null || sceneIndex >= scenes.size() - 1) {
            return;
        }
        currentScene().fadeOut();
        sceneIndex++;
        currentScene().begin();
        paused = false;
    }

    public void scrollBack() {
        if (!valid || scenes == null || sceneIndex <= 0) {
            return;
        }
        currentScene().fadeOut();
        sceneIndex--;
        currentScene().begin();
        paused = false;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public void togglePause() {
        this.paused = !this.paused;
    }

    public void seekToTime(int time) {
        if (!valid || scenes == null || scenes.isEmpty()) return;
        PonderScene scene = currentScene();
        if (scene.getTotalTime() > 0 && time < scene.getCurrentTime()) scene.begin();
        scene.seekToTime(time);
    }

    public void release() {
        if (!valid) {
            return;
        }
        currentScene().fadeOut();
        valid = false;
        paused = true;
    }

    public PonderScene currentScene() {
        if (!valid || scenes == null || scenes.isEmpty()) {
            throw new IllegalStateException("Embedded ponder handle has no active scene");
        }
        return scenes.get(sceneIndex);
    }
}
