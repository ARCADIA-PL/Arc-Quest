package org.arcadia.arc_quest.client.quest.collection;

import java.util.List;
import java.util.ArrayList;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import org.arcadia.arc_quest.mixin.client.ClientTextTooltipAccessor;

/** One Forge tooltip event pass, preserving styled text, embedded images and modded components. */
public final class AtlasNativeTooltip {
    private static final List<BiFunction<ItemStack, List<Component>, List<Component>>> FILTERS = new ArrayList<>();
    private static final List<BiPredicate<ItemStack, ClientTooltipComponent>> COMPONENT_FILTERS = new ArrayList<>();
    private static final List<HeaderProvider> HEADERS = new ArrayList<>();
    @FunctionalInterface
    public interface HeaderProvider {
        List<? extends ClientTooltipComponent> create(ItemStack stack, Font font, int width);
    }
    /** Optional atlas styling for custom tooltip sections; native inventory tooltips stay untouched. */
    public interface StyledComponent extends ClientTooltipComponent {
        String plainText();
        void renderInAtlas(Font font, GuiGraphics graphics, int alpha, int theme);
    }
    private AtlasNativeTooltip() {}
    /** Client display policy only; never modifies the item or the inventory tooltip. */
    public static void registerFilter(BiFunction<ItemStack, List<Component>, List<Component>> filter) {
        FILTERS.add(java.util.Objects.requireNonNull(filter));
    }
    /** Selectively omits native display components from atlas details, including Forge-injected images. */
    public static void registerComponentFilter(BiPredicate<ItemStack, ClientTooltipComponent> filter) {
        COMPONENT_FILTERS.add(java.util.Objects.requireNonNull(filter));
    }
    public static void registerHeader(HeaderProvider provider) {
        HEADERS.add(java.util.Objects.requireNonNull(provider));
    }
    public static List<AtlasTooltipLayout.Part<ClientTooltipComponent>> gather(Minecraft minecraft, ItemStack stack, Font font, int width) {
        List<Component> lines = Screen.getTooltipFromItem(minecraft, stack);
        for (var filter : FILTERS) lines = filter.apply(stack, List.copyOf(lines));
        var body = lines.size() > 1 ? lines.subList(1, lines.size()) : List.<net.minecraft.network.chat.Component>of();
        int contentWidth = Math.max(1, width - 12);
        var components = new ArrayList<ClientTooltipComponent>();
        for (var header : HEADERS) components.addAll(header.create(stack, font, contentWidth));
        components.addAll(ForgeHooksClient.gatherTooltipComponents(stack, body, stack.getTooltipImage(), 0,
                contentWidth + 16, 10000, font));
        return components.stream()
                .filter(component -> COMPONENT_FILTERS.stream().allMatch(filter -> filter.test(stack, component)))
                .map(component -> {
                    StringBuilder text = new StringBuilder();
                    boolean nativeText = component instanceof ClientTextTooltipAccessor;
                    if (component instanceof ClientTextTooltipAccessor accessor)
                        accessor.arcq$text().accept((index, style, codepoint) -> { text.appendCodePoint(codepoint); return true; });
                    else if (component instanceof StyledComponent styled) text.append(styled.plainText());
                    return new AtlasTooltipLayout.Part<>(component.getHeight(), text.toString(), nativeText, component);
                }).toList();
    }
}
