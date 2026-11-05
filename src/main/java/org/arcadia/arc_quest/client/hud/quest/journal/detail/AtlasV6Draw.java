package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;
import org.arcadia.arc_quest.client.hud.component.HudRect;

/** Small drawing primitives shared by the native V6 atlas; no data or input dependencies. */
final class AtlasV6Draw {
  private static final int[] CIRCLE_SEGMENTS = {32, 48, 64, 96, 128, 192, 256, 384, 512};
  private static final float[][] CIRCLE_POINTS = new float[CIRCLE_SEGMENTS.length][];
  static {
    for (int bucket = 0; bucket < CIRCLE_SEGMENTS.length; bucket++) {
      int segments = CIRCLE_SEGMENTS[bucket];
      float[] points = CIRCLE_POINTS[bucket] = new float[(segments + 1) * 2];
      for (int i = 0; i < segments; i++) {
        double angle = i * (Math.PI * 2 / segments);
        points[i * 2] = (float) Math.cos(angle);
        points[i * 2 + 1] = (float) Math.sin(angle);
      }
      // Reuse the exact first vertex at the seam, rather than a rounded sin(2π).
      points[segments * 2] = points[0];
      points[segments * 2 + 1] = points[1];
    }
  }

  private AtlasV6Draw() {}

  static int tint(int rgb, int alpha) {
    return Math.max(0, Math.min(255, alpha)) << 24 | rgb & 0xFFFFFF;
  }

  static void frame(GuiGraphics g, HudRect r, int rgb, int alpha) {
    int c = tint(rgb, alpha);
    // A V6 border is half a journal unit, rather than the heavy full-pixel card outline.
    g.pose().pushPose(); g.pose().scale(.5f, .5f, 1);
    int x = r.x() * 2, y = r.y() * 2, right = r.right() * 2, bottom = r.bottom() * 2;
    g.fill(x, y, right, y + 1, c);
    g.fill(x, bottom - 1, right, bottom, c);
    g.fill(x, y, x + 1, bottom, c);
    g.fill(right - 1, y, right, bottom, c);
    g.pose().popPose();
  }

  /** Continuous silhouettes with a one-framebuffer-pixel coverage edge. */
  static void circle(GuiGraphics g, int x, int y, int size, int rgb, int alpha) {
    ring(g, x, y, size, size / 2f, rgb, alpha);
  }

  static void ring(GuiGraphics g, int x, int y, int size, float thickness, int rgb, int alpha) {
    if (size <= 0 || thickness <= 0 || alpha <= 0) return;
    Matrix4f matrix = g.pose().last().pose();
    var window = Minecraft.getInstance().getWindow();
    double pixelX = window.getWidth() / (double) Math.max(1, window.getGuiScaledWidth());
    double pixelY = window.getHeight() / (double) Math.max(1, window.getGuiScaledHeight());
    double pixelsPerUnit = Math.max(Math.hypot(matrix.m00() * pixelX, matrix.m01() * pixelY),
        Math.hypot(matrix.m10() * pixelX, matrix.m11() * pixelY));
    if (!Double.isFinite(pixelsPerUnit) || pixelsPerUnit < 1e-5) return;

    float radius = size / 2f, inner = Math.max(0, radius - thickness);
    float halfPixel = (float) (.5 / pixelsPerUnit);
    float outerCore = radius - halfPixel, innerCore = inner + halfPixel;
    int coreAlpha = alpha;
    if (inner > 0 && thickness < halfPixel * 2) {
      // Subpixel strokes retain their coverage instead of disappearing or growing blurry halos.
      outerCore = inner + halfPixel;
      innerCore = Math.max(0, radius - halfPixel);
      coreAlpha = Math.round(alpha * thickness / (halfPixel * 2));
    }
    int clear = tint(rgb, 0), solid = tint(rgb, coreAlpha);
    float centerX = x + radius, centerY = y + radius;
    int bucket = 0;
    double projectedRadius = (radius + halfPixel) * pixelsPerUnit;
    // The polygon stays within 0.1 physical pixels of the mathematical circle at every GUI scale.
    while (bucket < CIRCLE_SEGMENTS.length - 1
        && projectedRadius * (1 - Math.cos(Math.PI / CIRCLE_SEGMENTS[bucket])) > .1) bucket++;
    float[] points = CIRCLE_POINTS[bucket];
    VertexConsumer consumer = g.bufferSource().getBuffer(RenderType.gui());
    if (inner > 0) {
      circleBand(consumer, matrix, centerX, centerY, Math.max(0, inner - halfPixel), innerCore, clear, solid, points);
      circleBand(consumer, matrix, centerX, centerY, innerCore, outerCore, solid, solid, points);
    } else {
      circleBand(consumer, matrix, centerX, centerY, 0, Math.max(0, outerCore), solid, solid, points);
    }
    circleBand(consumer, matrix, centerX, centerY, Math.max(0, outerCore), radius + halfPixel, solid, clear, points);
    // Keep native GUI ordering, clipping and renderer compatibility; no global GL state changes.
    g.flush();
  }

  private static void circleBand(VertexConsumer consumer, Matrix4f matrix, float x, float y,
                                 float inner, float outer, int innerColor, int outerColor, float[] points) {
    if (outer <= inner) return;
    for (int i = 0; i < points.length - 2; i += 2) {
      float ux = points[i], uy = points[i + 1], vx = points[i + 2], vy = points[i + 3];
      consumer.vertex(matrix, x + ux * outer, y + uy * outer, 0).color(outerColor).endVertex();
      consumer.vertex(matrix, x + ux * inner, y + uy * inner, 0).color(innerColor).endVertex();
      consumer.vertex(matrix, x + vx * inner, y + vy * inner, 0).color(innerColor).endVertex();
      consumer.vertex(matrix, x + vx * outer, y + vy * outer, 0).color(outerColor).endVertex();
    }
  }

  static void shadow(GuiGraphics g, HudRect box, int alpha) {
    // Soft downward offset, matching the V6 HTML overlay; the plate covers the center.
    for (int spread = 12; spread >= 1; spread--) {
      int opacity = (int) Math.round(alpha * .17 * Math.exp(-spread / 3.5));
      if (opacity > 0) frame(g, new HudRect(box.x() - spread, box.y() + 3 - spread,
          box.width() + spread * 2, box.height() + spread * 2), 0, opacity);
    }
  }

  static void star(GuiGraphics g, int x, int y, int size, int rgb, int alpha) {
    // Half-unit samples keep the small five-point outline legible at native GUI scales.
    g.pose().pushPose();
    g.pose().translate(x, y, 0);
    g.pose().scale(.5f, .5f, 1);
    x = y = 0;
    size *= 2;
    double[] px = new double[10], py = new double[10];
    for (int i = 0; i < 10; i++) {
      double a = -Math.PI / 2 + i * Math.PI / 5, radius = size * (i % 2 == 0 ? .5 : .22);
      px[i] = size / 2. + Math.cos(a) * radius;
      py[i] = size / 2. + Math.sin(a) * radius;
    }
    for (int row = 0; row < size; row++) {
      java.util.List<Double> cuts = new java.util.ArrayList<>();
      double scan = row + .5;
      for (int i = 0, j = 9; i < 10; j = i++)
        if ((py[i] <= scan && py[j] > scan) || (py[j] <= scan && py[i] > scan))
          cuts.add(px[i] + (scan - py[i]) * (px[j] - px[i]) / (py[j] - py[i]));
      cuts.sort(Double::compare);
      for (int i = 0; i + 1 < cuts.size(); i += 2)
        g.fill(x + (int) Math.floor(cuts.get(i)), y + row,
            x + (int) Math.ceil(cuts.get(i + 1)), y + row + 1, tint(rgb, alpha));
    }
    g.pose().popPose();
  }

  static String ellipsis(Font font, String text, int width) {
    if (font.width(text) <= width) return text;
    return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
  }

  static void gear(GuiGraphics g, int x, int y, int size, int rgb, int alpha) {
    // Analytic half-unit outline, independent of Minecraft's missing Unicode glyphs.
    g.pose().pushPose();
    g.pose().translate(x, y, 0); g.pose().scale(.5f, .5f, 1);
    int samples = size * 2;
    for (int iy = 0; iy < samples; iy++) for (int ix = 0; ix < samples; ix++) {
      double dx = (ix + .5 - samples / 2.) / samples, dy = (iy + .5 - samples / 2.) / samples;
      double radius = Math.hypot(dx, dy), angle = Math.atan2(dy, dx);
      double outer = Math.cos(angle * 8) > 0 ? .49 : .39;
      if ((radius > .27 && radius < outer) || radius > .12 && radius < .19)
        g.fill(ix, iy, ix + 1, iy + 1, tint(rgb, alpha));
    }
    g.pose().popPose();
  }

  static void text(GuiGraphics g, Font font, String text, int x, int y, float scale, int color) {
    if ((color >>> 24) < 4) return;
    g.pose().pushPose();
    g.pose().translate(x, y, 0);
    g.pose().scale(scale, scale, 1);
    g.drawString(font, text, 0, 0, color, false);
    g.pose().popPose();
  }
}
