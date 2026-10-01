package org.arcadia.arc_quest.client.hud.shop;

import org.arcadia.arc_quest.client.compat.jei.screen.JeiHitBounds;

/** Stable input bounds; visual emphasis must never move the click target. */
final class TradeItemInteractionLayout {
    static final float MAX_HOVER_SCALE = 1.2f;
    static final int COST_ICON_SIZE = 12;
    static final float COST_OUTLINE_WIDTH = .5f;
    private static final int ICON_PADDING = 4;
    private TradeItemInteractionLayout() {}

    static JeiHitBounds product(double left, double top, double edge) {
        return new JeiHitBounds(left - ICON_PADDING, top - ICON_PADDING,
                left + edge + ICON_PADDING, top + edge + ICON_PADDING);
    }

    /** Include the name and quantity, leaving adjacent tokens and page arrows separate. */
    static JeiHitBounds cost(double left, double top, double width, double height) {
        return new JeiHitBounds(left, top - 3, left + width, top + height + 5);
    }
}
