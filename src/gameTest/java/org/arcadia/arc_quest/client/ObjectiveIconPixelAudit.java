package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;

/** Screenshot sampling uses the actual framebuffer/GUI ratio, including nonintegral UI scales. */
final class ObjectiveIconPixelAudit {
    private ObjectiveIconPixelAudit() {}
    record Rect(double x, double y, double width, double height) {}
    record Sample(int width, int height, int[] pixels) {
        double energy(int background) {
            double sum = 0;
            for (int pixel : pixels) sum += difference(pixel, background);
            return sum / pixels.length;
        }
        double linearError(Sample opaque, int background, double alpha) {
            ObjectiveIconClientAudit.check(width == opaque.width && height == opaque.height, "Pixel sample dimensions differ");
            double error = 0;
            for (int i = 0; i < pixels.length; i++) for (int shift : new int[]{0, 8, 16}) {
                int bg = background >>> shift & 255;
                double expected = bg + ((opaque.pixels[i] >>> shift & 255) - bg) * alpha;
                error += Math.abs((pixels[i] >>> shift & 255) - expected);
            }
            return error / (pixels.length * 3.0);
        }
    }
    static double difference(int a, int b) {
        return (Math.abs((a & 255) - (b & 255)) + Math.abs((a >>> 8 & 255) - (b >>> 8 & 255))
                + Math.abs((a >>> 16 & 255) - (b >>> 16 & 255))) / 3.0;
    }
    static Sample sample(NativeImage image, int guiWidth, int guiHeight, Rect rect) {
        double sx = image.getWidth() / (double) guiWidth, sy = image.getHeight() / (double) guiHeight;
        int x = (int) Math.round(rect.x * sx), y = (int) Math.round(rect.y * sy);
        int w = Math.max(1, (int) Math.round(rect.width * sx)), h = Math.max(1, (int) Math.round(rect.height * sy));
        ObjectiveIconClientAudit.check(x >= 0 && y >= 0 && x + w <= image.getWidth() && y + h <= image.getHeight(),
                "Screenshot sample is outside framebuffer: " + rect);
        int[] pixels = new int[w * h];
        for (int row = 0; row < h; row++) for (int col = 0; col < w; col++)
            pixels[row * w + col] = image.getPixelRGBA(x + col, y + row);
        return new Sample(w, h, pixels);
    }
}
