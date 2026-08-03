package org.arcadia.arc_quest.client.hud.quest.graph;

import org.arcadia.arc_quest.client.hud.HudAnimUtil;

public final class GraphViewportController {
    private final float minimumZoom;
    private final float maximumZoom;
    private final float defaultZoom;
    private float zoom;
    private float panX;
    private float panY;
    private float targetZoom;
    private float targetPanX;
    private float targetPanY;

    public GraphViewportController(float minimumZoom, float maximumZoom, float defaultZoom) {
        this.minimumZoom = minimumZoom;
        this.maximumZoom = maximumZoom;
        this.defaultZoom = clampZoom(defaultZoom);
        reset();
    }

    public void reset() {
        zoom = defaultZoom;
        targetZoom = defaultZoom;
        panX = 0f;
        panY = 0f;
        targetPanX = 0f;
        targetPanY = 0f;
    }

    public void update(float deltaTime) {
        zoom = HudAnimUtil.smoothExp(zoom, targetZoom, 15f, deltaTime);
        panX = HudAnimUtil.smoothExp(panX, targetPanX, 15f, deltaTime);
        panY = HudAnimUtil.smoothExp(panY, targetPanY, 15f, deltaTime);
    }

    public void fit(GraphBounds bounds, int viewWidth, int viewHeight, boolean immediate) {
        if (bounds == null) return;
        float fitZoom = Math.min(viewWidth / bounds.width(), viewHeight / bounds.height());
        targetZoom = clampZoom(Math.min(defaultZoom, fitZoom));
        targetPanX = (viewWidth - bounds.width() * targetZoom) * 0.5f - bounds.minX() * targetZoom;
        targetPanY = (viewHeight - bounds.height() * targetZoom) * 0.5f - bounds.minY() * targetZoom;
        if (immediate) snapToTarget();
    }

    public void focus(float worldX, float worldY, int viewWidth, int viewHeight, float minimumFocusZoom) {
        targetZoom = Math.max(targetZoom, clampZoom(minimumFocusZoom));
        targetPanX = viewWidth * 0.5f - worldX * targetZoom;
        targetPanY = viewHeight * 0.5f - worldY * targetZoom;
    }

    public void panBy(float deltaX, float deltaY) {
        targetPanX += deltaX;
        targetPanY += deltaY;
    }

    public void zoomAt(float localX, float localY, float delta) {
        float oldZoom = targetZoom;
        targetZoom = clampZoom(targetZoom + delta);
        if (oldZoom <= 0f || oldZoom == targetZoom) return;
        targetPanX = localX - ((localX - targetPanX) / oldZoom) * targetZoom;
        targetPanY = localY - ((localY - targetPanY) / oldZoom) * targetZoom;
    }

    public float zoom() {
        return zoom;
    }

    public float panX() {
        return panX;
    }

    public float panY() {
        return panY;
    }

    public float targetZoom() {
        return targetZoom;
    }

    private void snapToTarget() {
        zoom = targetZoom;
        panX = targetPanX;
        panY = targetPanY;
    }

    private float clampZoom(float value) {
        return Math.max(minimumZoom, Math.min(maximumZoom, value));
    }
}
