package org.arcadia.arc_quest.client.hud;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UnreadDotBatchTest {
    @Test void batchingKeepsEveryPixelAndLayerColor() {
        var mesh = new Capture();
        int[] colors = {0x39FF1F2A, 0xB5FF3B30, 0x72FF8A80};
        HudRenderUtil.drawPixelCircle(mesh, new Matrix4f(), 0, 0, 6, colors[0]);
        HudRenderUtil.drawPixelCircle(mesh, new Matrix4f(), 0, 0, 4, colors[1]);
        HudRenderUtil.drawPixelCircle(mesh, new Matrix4f(), -1, -1, 2, colors[2]);
        assertEquals(108, mesh.vertices.size(), "All three circles must share the original 27 quads");
        for (int y = -7; y <= 7; y++) {
            for (int x = -7; x <= 7; x++) {
                var expected = new ArrayList<Integer>();
                if (x * x + y * y <= 36) expected.add(colors[0]);
                if (x * x + y * y <= 16) expected.add(colors[1]);
                if ((x + 1) * (x + 1) + (y + 1) * (y + 1) <= 4) expected.add(colors[2]);
                assertEquals(expected, mesh.layersAt(x + 0.5, y + 0.5), "Changed pixel/layer order at " + x + "," + y);
            }
        }
    }

    @Test void guiPoseTransformAndWindingSurviveTheSingleBatch() {
        var mesh = new Capture();
        var matrix = new Matrix4f().translate(42.5f, 15.5f, 30).scale(0.78f, 0.78f, 1);
        HudRenderUtil.drawPixelCircle(mesh, matrix, 0, 0, 6, 0x10FF1F2A);
        assertEquals(42.5 - 6 * 0.78, mesh.vertices.stream().mapToDouble(Vertex::x).min().orElseThrow(), 0.0001);
        assertEquals(42.5 + 7 * 0.78, mesh.vertices.stream().mapToDouble(Vertex::x).max().orElseThrow(), 0.0001);
        assertTrue(mesh.vertices.stream().allMatch(v -> v.z == 30 && v.color == 0x10FF1F2A));
        for (int index = 0; index < mesh.vertices.size(); index += 4) {
            var a = mesh.vertices.get(index);
            var b = mesh.vertices.get(index + 1);
            var c = mesh.vertices.get(index + 2);
            assertTrue((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x) < 0);
        }
    }

    private record Vertex(double x, double y, double z, int color) { }
    private static final class Capture implements VertexConsumer {
        final List<Vertex> vertices = new ArrayList<>();
        double x, y, z;
        int color;
        List<Integer> layersAt(double x, double y) {
            var result = new ArrayList<Integer>();
            for (int index = 0; index < vertices.size(); index += 4) {
                var a = vertices.get(index);
                var c = vertices.get(index + 2);
                if (x >= c.x && x < a.x && y >= c.y && y < a.y) result.add(a.color);
            }
            return result;
        }
        public VertexConsumer vertex(double x, double y, double z) { this.x = x; this.y = y; this.z = z; return this; }
        public VertexConsumer color(int r, int g, int b, int a) { color = a << 24 | r << 16 | g << 8 | b; return this; }
        public void endVertex() { vertices.add(new Vertex(x, y, z, color)); }
        public VertexConsumer uv(float u, float v) { return this; }
        public VertexConsumer overlayCoords(int u, int v) { return this; }
        public VertexConsumer uv2(int u, int v) { return this; }
        public VertexConsumer normal(float x, float y, float z) { return this; }
        public void defaultColor(int r, int g, int b, int a) { }
        public void unsetDefaultColor() { }
    }
}
