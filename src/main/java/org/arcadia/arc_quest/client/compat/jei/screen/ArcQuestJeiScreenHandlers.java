package org.arcadia.arc_quest.client.compat.jei.screen;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import org.arcadia.arc_quest.client.hud.gacha.GachaScreen;
import org.arcadia.arc_quest.client.hud.guide.GuideListScreen;
import org.arcadia.arc_quest.client.hud.guide.GuideScreen;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;
import org.arcadia.arc_quest.client.hud.shop.AbstractTradeScreen;
import org.arcadia.arc_quest.integration.jei.api.JeiCatalogEntry;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Loaded exclusively by the optional JEI plugin. */
public final class ArcQuestJeiScreenHandlers {
    private static IJeiRuntime runtime;
    private static boolean listening;
    private static WeakReference<Screen> returnScreen = new WeakReference<>(null);
    private ArcQuestJeiScreenHandlers() {}

    public static void register(IGuiHandlerRegistration registration) {
        registerScreen(registration, QuestJournalScreen.class);
        registerScreen(registration, AbstractTradeScreen.class);
        registerScreen(registration, GachaScreen.class);
        registerScreen(registration, GuideScreen.class);
        registerScreen(registration, GuideListScreen.class);
    }

    private static <T extends Screen> void registerScreen(IGuiHandlerRegistration registration, Class<T> type) {
        // ArcQ draws over the entire screen. Advertising the real bounds prevents JEI
        // from placing its sidebar on top of ArcQ controls. Ingredient lookup below is
        // deliberately handled before screen input: JEI clickable slots would also
        // consume the primary mouse button used to buy, submit, drag, and select.
        // JEI also asks during screen opening, before Minecraft assigns its viewport.
        registration.addGuiScreenHandler(type, (IScreenHandler<T>) screen ->
                screen.width > 1 && screen.height > 1 ? new FullScreenProperties(screen) : null);
    }

    public static void runtimeAvailable(IJeiRuntime value) {
        runtime = value;
        JeiScreenIngredients.setRuntimeAvailable(true);
        JeiScreenIngredients.setQueryHints(() -> {
            if (runtime == null) return List.of();
            List<net.minecraft.network.chat.Component> hints = new ArrayList<>();
            var recipes = runtime.getKeyMappings().getShowRecipe();
            var uses = runtime.getKeyMappings().getShowUses();
            if (!recipes.isUnbound()) hints.add(net.minecraft.network.chat.Component.translatable(
                    "arc_quest.gui.objective.icon.recipes", recipes.getTranslatedKeyMessage()));
            if (!uses.isUnbound()) hints.add(net.minecraft.network.chat.Component.translatable(
                    "arc_quest.gui.objective.icon.uses", uses.getTranslatedKeyMessage()));
            return hints;
        });
        if (!listening) {
            listening = true;
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ArcQuestJeiScreenHandlers::keyPressed);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ArcQuestJeiScreenHandlers::mousePressed);
            MinecraftForge.EVENT_BUS.addListener(ArcQuestJeiScreenHandlers::screenOpening);
        }
    }

    public static void runtimeUnavailable() {
        runtime = null;
        JeiScreenIngredients.setRuntimeAvailable(false);
        abandonReturn();
    }

    private static void keyPressed(ScreenEvent.KeyPressed.Pre event) {
        double x = mouseX(), y = mouseY();
        if (event.getScreen() instanceof QuestJournalScreen journal && journal.canInteractWithObjectiveIcons()) {
            var focused = journal.getObjectiveIcons().focusedTarget();
            if (focused != null) { x = focused.x(); y = focused.y(); }
        }
        if (query(event.getScreen(), InputConstants.getKey(event.getKeyCode(), event.getScanCode()),
                x, y)) event.setCanceled(true);
    }

    private static void mousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        // Primary click retains the existing transaction/selection behavior even
        // though JEI normally also maps it to recipe lookup. Other bound mouse
        // buttons and every configured keyboard shortcut remain available.
        if (event.getButton() == 0) return;
        if (query(event.getScreen(), InputConstants.Type.MOUSE.getOrCreate(event.getButton()),
                event.getMouseX(), event.getMouseY())) event.setCanceled(true);
    }

    private static boolean query(Screen screen, InputConstants.Key key, double mouseX, double mouseY) {
        if (runtime == null || !canQuery(screen)) return false;
        RecipeIngredientRole role;
        if (runtime.getKeyMappings().getShowRecipe().isActiveAndMatches(key)) role = RecipeIngredientRole.OUTPUT;
        else if (runtime.getKeyMappings().getShowUses().isActiveAndMatches(key)) role = RecipeIngredientRole.INPUT;
        else return false;
        var hit = JeiScreenIngredients.underMouse(screen, mouseX, mouseY);
        if (hit.isEmpty()) return false;
        List<IFocus<?>> focuses = new ArrayList<>();
        // Multiple focuses represent OR alternatives, so a tag query includes all
        // valid items instead of silently reducing the tag to its first icon.
        for (var stack : hit.get().stacks()) {
            focuses.add(runtime.getJeiHelpers().getFocusFactory().createFocus(role, VanillaTypes.ITEM_STACK, stack));
        }
        if (focuses.isEmpty()) return false;
        if (screen instanceof JeiQueryReturn host) host.prepareJeiQuery();
        runtime.getRecipesGui().show(focuses);
        if (Minecraft.getInstance().screen != screen) returnScreen = new WeakReference<>(screen);
        else if (screen instanceof JeiQueryReturn host) host.cancelJeiQuery();
        return true;
    }

    private static boolean canQuery(Screen screen) {
        if (screen instanceof GachaScreen gacha) return gacha.canQueryJei();
        if (screen instanceof QuestJournalScreen journal) return journal.canQueryJei();
        if (screen instanceof AbstractTradeScreen trade) return trade.canQueryJei();
        if (screen instanceof GuideScreen guide) return guide.canQueryJei();
        if (screen instanceof GuideListScreen guides) return guides.canQueryJei();
        return false;
    }

    private static double mouseX() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
    }
    private static double mouseY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
    }

    private static void screenOpening(ScreenEvent.Opening event) {
        JeiScreenIngredients.screenChanged();
        Screen saved = returnScreen.get();
        if (saved == null) return;
        if (event.getNewScreen() == saved) returnScreen.clear();
        else if (event.getNewScreen() == null || isArcQuestScreen(event.getNewScreen())) abandonReturn();
        // JEI can open nested screens. Keep the handoff while it still reports the
        // saved screen as its parent; abandoning navigation is cleaned up below.
        else if (runtime == null || runtime.getRecipesGui().getParentScreen().orElse(null) != saved) abandonReturn();
    }

    private static boolean isArcQuestScreen(Screen screen) {
        return screen instanceof QuestJournalScreen || screen instanceof AbstractTradeScreen
                || screen instanceof GachaScreen || screen instanceof GuideScreen || screen instanceof GuideListScreen;
    }

    private static void abandonReturn() {
        Screen saved = returnScreen.get();
        if (saved instanceof JeiQueryReturn host) host.abandonJeiQuery();
        returnScreen.clear();
    }

    public static boolean navigate(JeiCatalogEntry entry) { return ArcQuestJeiNavigation.open(entry); }

    static Screen parentScreen() { return runtime == null ? null : runtime.getRecipesGui().getParentScreen().orElse(null); }

    private record FullScreenProperties(Screen screen) implements IGuiProperties {
        @Override public Class<? extends Screen> getScreenClass() { return screen.getClass(); }
        @Override public int getGuiLeft() { return 0; }
        @Override public int getGuiTop() { return 0; }
        @Override public int getGuiXSize() { return screen.width; }
        @Override public int getGuiYSize() { return screen.height; }
        @Override public int getScreenWidth() { return screen.width; }
        @Override public int getScreenHeight() { return screen.height; }
    }
}
