package org.arcadia.arc_quest.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.gui.GuiLayerManager;

import java.lang.reflect.Field;
import java.util.List;

/** Development-only inspection of the actual registered NeoForge layers. */
public final class NeoForgeGuiLayerProbe {
    private NeoForgeGuiLayerProbe() {}

    public record RegisteredLayer(ResourceLocation id, LayeredDraw.Layer overlay) {}

    public static RegisteredLayer findOverlay(ResourceLocation id) {
        return getOverlays().stream().filter(layer -> layer.id().equals(id)).findFirst().orElse(null);
    }

    public static List<RegisteredLayer> getOverlays() {
        try {
            Field managerField = Gui.class.getDeclaredField("layerManager");
            managerField.setAccessible(true);
            GuiLayerManager manager = (GuiLayerManager) managerField.get(Minecraft.getInstance().gui);
            Field layersField = GuiLayerManager.class.getDeclaredField("layers");
            layersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<GuiLayerManager.NamedLayer> layers = (List<GuiLayerManager.NamedLayer>) layersField.get(manager);
            return layers.stream().map(layer -> new RegisteredLayer(layer.name(), layer.layer())).toList();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot inspect the NeoForge GUI layer registry", exception);
        }
    }
}
