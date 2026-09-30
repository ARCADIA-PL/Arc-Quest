package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import org.arcadia.arc_quest.client.hud.quest.icon.IconFrameSelection;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconAlpha;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconContext;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconSession;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconsClient;
import org.arcadia.arc_quest.client.hud.quest.icon.TextureObjectiveIcon;
import org.arcadia.arc_quest.client.hud.quest.icon.portrait.EntityPortraits;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;

import java.util.List;

/** Pixel acceptance of native items and production visual providers at group opacity. */
final class ObjectiveIconAlphaAuditGallery extends Screen {
    private static final float[] ALPHAS = {1, .5f, .05f, 0, 1};
    private static final String[] LABELS = {"Cutout diamond", "3D table", "Enchanted sword",
            "Explicit texture", "Cow UV layers", "Baked zombie", "Soft RGBA bands", "Nested item .5"};
    private final ObjectiveIconSession session = new ObjectiveIconSession();
    private final List<ObjectiveEntry> visuals = List.of(
            ObjectiveBuilder.collect(Items.DIAMOND, 9999).id("texture")
                    .iconTexture("minecraft:textures/item/diamond.png").build(),
            ObjectiveBuilder.kill(EntityType.COW, 9999).id("cow").build(),
            ObjectiveBuilder.kill(EntityType.ZOMBIE, 9999).id("zombie").build());
    private final IconFrameSelection[] frames = new IconFrameSelection[8];
    private int size, rowHeight, firstColumn, columnWidth;
    private boolean complete;

    ObjectiveIconAlphaAuditGallery() {
        super(Component.literal("Objective icon group opacity / native framebuffer audit"));
        frames[0] = item("diamond", new ItemStack(Items.DIAMOND));
        frames[1] = item("table", new ItemStack(Items.CRAFTING_TABLE));
        ItemStack enchanted = new ItemStack(Items.DIAMOND_SWORD);
        enchanted.enchant(Enchantments.SHARPNESS, 1);
        frames[2] = item("enchanted", enchanted);
        NativeImage soft = new NativeImage(16, 16, false);
        int[] opacity = {32, 64, 128, 255};
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
            soft.setPixelRGBA(x, y, opacity[x / 4] << 24 | 160 << 16 | 224 << 8 | 64);
        var textures = Minecraft.getInstance().getTextureManager();
        var textureId = textures.register("objective_alpha_audit", new DynamicTexture(soft));
        ObjectiveIconClientAudit.restoreOnExit(() -> textures.release(textureId));
        frames[6] = new IconFrameSelection("soft", "soft", ItemStack.EMPTY,
                new TextureObjectiveIcon(textureId, 0, 0, 16, 16, 16, 16), 0, 0, ObjectiveIconsClient.generation());
        ItemStack innerItem = new ItemStack(Items.DIAMOND);
        frames[7] = new IconFrameSelection("nested", "nested", ItemStack.EMPTY,
                (graphics, x, y, edge) -> ObjectiveIconAlpha.renderItem(graphics, innerItem, x, y, edge, .5f),
                0, 0, ObjectiveIconsClient.generation());
    }
    private static IconFrameSelection item(String key, ItemStack stack) {
        return new IconFrameSelection(key, key, stack, null, 0, 1, ObjectiveIconsClient.generation());
    }
    private ObjectiveIconContext context(int index) {
        var objective = visuals.get(index);
        return new ObjectiveIconContext("alpha_audit", "visuals", index, objective, 0,
                objective.getRequiredCount(), ObjectiveIconsClient.generation());
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            session.beginFrame();
            for (int i = 0; i < visuals.size(); i++) session.resolve(context(i));
            EntityPortraits.prepare();
            EntityPortraits.prepare();
            for (int i = 0; i < visuals.size(); i++) frames[i + 3] = session.select(context(i), true, true);
            complete = java.util.Arrays.stream(frames).allMatch(IconFrameSelection::available);
            graphics.fill(0, 0, width, height, 0xFF101820);
            graphics.drawCenteredString(font, title, width / 2, 9, 0xFFFFFFFF);
            firstColumn = Math.min(140, width / 4);
            columnWidth = Math.max(20, (width - firstColumn - 12) / ALPHAS.length);
            rowHeight = Math.max(18, (height - 46) / LABELS.length);
            size = Math.max(8, Math.min(32, Math.min(rowHeight - 8, columnWidth - 8)));
            for (int col = 0; col < ALPHAS.length; col++) {
                String label = col == 4 ? "1 restored" : Float.toString(ALPHAS[col]);
                graphics.drawCenteredString(font, label, firstColumn + col * columnWidth + size / 2, 25, 0xFF9FC5D8);
            }
            for (int row = 0; row < LABELS.length; row++) {
                graphics.drawString(font, LABELS[row], 8, 42 + row * rowHeight + size / 2 - 4, 0xFFFFFFFF, false);
                for (int col = 0; col < ALPHAS.length; col++)
                    frames[row].render(graphics, firstColumn + col * columnWidth, 42 + row * rowHeight, size, ALPHAS[col]);
            }
            session.endFrame();
        } catch (Throwable error) { ObjectiveIconClientAudit.fail(error); }
    }
    boolean complete() { return complete; }
    void verifyPixels(NativeImage image) {
        int background = image.getPixelRGBA(0, image.getHeight() - 1);
        ObjectiveIconPixelAudit.Sample opaqueDiamond = null;
        for (int row = 0; row < LABELS.length; row++) {
            var samples = new ObjectiveIconPixelAudit.Sample[ALPHAS.length];
            double[] energy = new double[ALPHAS.length];
            for (int col = 0; col < ALPHAS.length; col++) {
                samples[col] = ObjectiveIconPixelAudit.sample(image, width, height,
                        new ObjectiveIconPixelAudit.Rect(firstColumn + col * columnWidth, 42 + row * rowHeight, size, size));
                energy[col] = samples[col].energy(background);
            }
            ObjectiveIconClientAudit.LOG.info("{} ALPHA_PIXELS type={} opaque={} half={} low={} zero={} restored={}",
                    ObjectiveIconClientAudit.MARKER, LABELS[row], energy[0], energy[1], energy[2], energy[3], energy[4]);
            if (row == 0) opaqueDiamond = samples[0];
            ObjectiveIconClientAudit.check(energy[0] > 2, LABELS[row] + " opaque baseline has no icon pixels");
            ObjectiveIconClientAudit.check(energy[1] / energy[0] > .40 && energy[1] / energy[0] < .60,
                    LABELS[row] + " half-opacity pixels did not fade");
            ObjectiveIconClientAudit.check(energy[2] > .025 && energy[2] / energy[0] > .008 && energy[2] / energy[0] < .14,
                    LABELS[row] + " low opacity disappeared or stayed opaque (cutout/glint regression)");
            ObjectiveIconClientAudit.check(energy[3] < .01, LABELS[row] + " zero opacity altered the background");
            ObjectiveIconClientAudit.check(energy[4] / energy[0] > .85 && energy[4] / energy[0] < 1.15,
                    LABELS[row] + " opacity drawing leaked render state into the following opaque icon");
            if (row == 4 || row == 6 || row == 7) {
                double error = samples[1].linearError(samples[0], background, .5);
                ObjectiveIconClientAudit.LOG.info("{} GROUP_LINEAR_ERROR type={} error={}", ObjectiveIconClientAudit.MARKER, LABELS[row], error);
                ObjectiveIconClientAudit.check(error < Math.max(2.5, energy[0] * .12),
                        LABELS[row] + " did not fade as one completed image (source alpha must not multiply RGB twice)");
            }
            if (row == 7) {
                ObjectiveIconClientAudit.check(opaqueDiamond != null, "Nested provider lacks its plain-item baseline");
                double innerError = samples[0].linearError(opaqueDiamond, background, .5);
                double combinedError = samples[1].linearError(opaqueDiamond, background, .25);
                double lowError = samples[2].linearError(opaqueDiamond, background, .025);
                ObjectiveIconClientAudit.LOG.info("{} NESTED_PROVIDER_PIXELS innerError={} combinedError={} lowError={}",
                        ObjectiveIconClientAudit.MARKER, innerError, combinedError, lowError);
                double tolerance = Math.max(2.5, opaqueDiamond.energy(background) * .12);
                ObjectiveIconClientAudit.check(innerError < tolerance && combinedError < tolerance && lowError < tolerance,
                        "A public visual provider lost inner item opacity while applying its outer group opacity");
            }
            if (row == 6) {
                int[] opacity = {32, 64, 128, 255};
                for (int band = 0; band < opacity.length; band++) {
                    int px = Math.min(samples[0].width() - 1, (int) ((band + .5) * samples[0].width() / 4));
                    int actual = samples[0].pixels()[(samples[0].height() / 2) * samples[0].width() + px];
                    double error = 0;
                    int source = 160 << 16 | 224 << 8 | 64;
                    for (int shift : new int[]{0, 8, 16}) {
                        int bg = background >>> shift & 255;
                        double expected = bg + ((source >>> shift & 255) - bg) * opacity[band] / 255.0;
                        error += Math.abs((actual >>> shift & 255) - expected);
                    }
                    ObjectiveIconClientAudit.check(error / 3 < 3,
                            "TextureObjectiveIcon source alpha was ignored or corrupted in band " + band);
                }
            }
        }
    }
}
