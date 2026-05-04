package org.arcadia.arc_quest.mutil.animation;

public class ArcPanelTransition {
    private final int panelWidth;
    private final int panelHeight;
    private final float enterTime;
    private final float exitTime;
    private float enterTimer;
    private float exitTimer;
    private boolean closing;
    private boolean finished;

    public ArcPanelTransition(int panelWidth, int panelHeight, float enterTime, float exitTime) {
        this.panelWidth = panelWidth;
        this.panelHeight = panelHeight;
        this.enterTime = Math.max(0.001f, enterTime);
        this.exitTime = Math.max(0.001f, exitTime);
    }

    public void reset() {
        enterTimer = 0f;
        exitTimer = 0f;
        closing = false;
        finished = false;
    }

    public void close() {
        if (closing || finished) return;
        closing = true;
        exitTimer = 0f;
    }

    public void setClosing(boolean closing) {
        if (closing) close();
        else this.closing = false;
    }

    public boolean isClosing() {
        return closing;
    }

    public boolean isFinished() {
        return finished;
    }

    public Frame update(float dt, int screenWidth, int screenHeight, float finalScale) {
        return update(dt, screenWidth, screenHeight, finalScale, 4.0f);
    }

    public Frame update(float dt, int screenWidth, int screenHeight, float finalScale, float flyDistance) {
        float baseX = (screenWidth / 2f) - ((panelWidth * finalScale) / 2f);
        float baseY = (screenHeight / 2f) - ((panelHeight * finalScale) / 2f);
        float scaleAnim = finalScale;
        float currentX = baseX;
        float currentY = baseY;
        float alpha = 1f;
        float reveal = 1f;
        float wipe = 0f;
        float actualFlyDist = flyDistance * finalScale;

        if (closing) {
            exitTimer += Math.max(0f, dt);
            if (exitTimer >= exitTime) {
                finished = true;
                return Frame.hidden();
            }
            float t = Math.min(1f, exitTimer / exitTime);
            wipe = (float) Math.pow(t, 4.0);
            currentX = baseX - (wipe * actualFlyDist * 1.5f);
            alpha = 1.0f - (float) Math.pow(t, 8.0);
        } else {
            enterTimer = Math.min(enterTime, enterTimer + Math.max(0f, dt));
            float t = Math.min(1f, enterTimer / enterTime);
            reveal = (float) (1.0 - Math.pow(1.0 - t, 5));
            alpha = reveal;
            scaleAnim = finalScale * (1.10f - 0.10f * reveal);
            currentX = baseX - (1.0f - reveal) * actualFlyDist * 2f;
        }

        if (reveal <= 0.001f || wipe >= 0.999f) return Frame.hidden();

        float drawWidth = panelWidth * scaleAnim;
        float drawHeight = panelHeight * scaleAnim;
        float drawX = currentX - (drawWidth - panelWidth * finalScale) / 2f;
        float drawY = currentY - (drawHeight - panelHeight * finalScale) / 2f;
        int scX1 = (int) (drawX - 10);
        int scX2 = (int) (drawX + drawWidth + 10);
        if (closing) scX2 = (int) (drawX + drawWidth * (1.0f - wipe));
        else if (enterTimer < enterTime) scX2 = (int) (drawX + drawWidth * reveal);

        return new Frame(true, drawX, drawY, scaleAnim, drawWidth, drawHeight, alpha, reveal, wipe, scX1, (int) (drawY - 10), scX2, (int) (drawY + drawHeight + 10));
    }

    public record Frame(boolean visible, float drawX, float drawY, float scale, float drawWidth, float drawHeight,
                        float alpha, float revealProgress, float wipeProgress, int scissorX1, int scissorY1,
                        int scissorX2, int scissorY2) {
        static Frame hidden() {
            return new Frame(false, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0, 0, 0, 0);
        }
    }
}
