package org.arcadia.arc_quest.client.hud.quest.history;

import org.arcadia.arc_quest.quest.api.VisualAsset;

/** Conservative graph-space bounds include labels, shadows and the maximum hover/pulse scale. */
final class QuestHistoryCardVisibility {
    private static final float HALF_WIDTH = (QuestHistoryNodeRenderer.CARD_WIDTH / 2f + 8f) * 1.05f;
    private static final float HALF_HEIGHT = (QuestHistoryNodeRenderer.CARD_HEIGHT / 2f + 10f) * 1.05f;

    private QuestHistoryCardVisibility() {}

    static boolean hasBoundedCover(VisualAsset image) {
        // Item covers may intentionally extend beyond their card via VisualAsset transforms.
        return image == null || image.item() == null || image.item().isEmpty()
                || image.scale() == 1f && image.offsetX() == 0f && image.offsetY() == 0f;
    }

    static boolean intersects(float nodeX, float nodeY, float panX, float panY, float zoom,
                              float viewportWidth, float viewportHeight) {
        if (!Float.isFinite(nodeX) || !Float.isFinite(nodeY) || !Float.isFinite(panX)
                || !Float.isFinite(panY) || !Float.isFinite(zoom) || zoom <= 0f) return true;
        // One logical pixel also covers integer rounding of the outer screen scissor.
        return panX + (nodeX + HALF_WIDTH) * zoom >= -1f
                && panX + (nodeX - HALF_WIDTH) * zoom <= viewportWidth + 1f
                && panY + (nodeY + HALF_HEIGHT) * zoom >= -1f
                && panY + (nodeY - HALF_HEIGHT) * zoom <= viewportHeight + 1f;
    }
}
