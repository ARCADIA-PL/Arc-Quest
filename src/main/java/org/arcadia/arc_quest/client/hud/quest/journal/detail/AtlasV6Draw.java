package org.arcadia.arc_quest.client.hud.quest.journal.detail;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.client.hud.component.HudRect;

/** Small drawing primitives shared by the native V6 atlas; no data or input dependencies. */
final class AtlasV6Draw {
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

  /** Scan-convert the sixteen sided plate used by the approved HTML, without a square card. */
  static void plate(GuiGraphics g, int x, int y, int size, int rgb, int alpha) {
    double[] ys = {0, .07, .22, .33, .67, .78, .93, 1};
    double[] inset = {.30, .15, .06, 0, 0, .06, .15, .30};
    int c = tint(rgb, alpha);
    for (int row = 0; row < size; row++) {
      double p = (row + .5) / size;
      int segment = 0;
      while (segment < ys.length - 2 && p > ys[segment + 1]) segment++;
      double t = (p - ys[segment]) / (ys[segment + 1] - ys[segment]);
      int cut = (int) Math.round(size * (inset[segment] + t * (inset[segment + 1] - inset[segment])));
      g.fill(x + cut, y + row, x + size - cut, y + row + 1, c);
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

  static void text(GuiGraphics g, Font font, String text, int x, int y, float scale, int color) {
    if ((color >>> 24) < 4) return;
    g.pose().pushPose();
    g.pose().translate(x, y, 0);
    g.pose().scale(scale, scale, 1);
    g.drawString(font, text, 0, 0, color, false);
    g.pose().popPose();
  }
}
