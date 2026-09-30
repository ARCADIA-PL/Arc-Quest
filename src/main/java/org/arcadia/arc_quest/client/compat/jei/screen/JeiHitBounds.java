package org.arcadia.arc_quest.client.compat.jei.screen;

/** Screen-space, half-open bounds. Independent of Minecraft for geometry tests. */
public record JeiHitBounds(double left, double top, double right, double bottom) {
    public static JeiHitBounds transformed(double x, double y, double w, double h,
                                           double m00, double m01, double m10, double m11, double tx, double ty) {
        double x1 = m00 * x + m10 * y + tx, y1 = m01 * x + m11 * y + ty;
        double x2 = m00 * (x + w) + m10 * y + tx, y2 = m01 * (x + w) + m11 * y + ty;
        double x3 = m00 * x + m10 * (y + h) + tx, y3 = m01 * x + m11 * (y + h) + ty;
        double x4 = m00 * (x + w) + m10 * (y + h) + tx, y4 = m01 * (x + w) + m11 * (y + h) + ty;
        return new JeiHitBounds(Math.min(Math.min(x1, x2), Math.min(x3, x4)),
                Math.min(Math.min(y1, y2), Math.min(y3, y4)),
                Math.max(Math.max(x1, x2), Math.max(x3, x4)), Math.max(Math.max(y1, y2), Math.max(y3, y4)));
    }
    public JeiHitBounds intersect(JeiHitBounds other) {
        return new JeiHitBounds(Math.max(left, other.left), Math.max(top, other.top),
                Math.min(right, other.right), Math.min(bottom, other.bottom));
    }
    public boolean contains(double x, double y) {
        return x >= left && x < right && y >= top && y < bottom;
    }
    public boolean empty() { return !(right > left && bottom > top); }
}
