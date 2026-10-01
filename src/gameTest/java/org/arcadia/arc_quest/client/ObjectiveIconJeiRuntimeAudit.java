package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import org.arcadia.arc_quest.client.compat.jei.screen.ObjectiveIconJeiHitProbe;
import org.arcadia.arc_quest.client.hud.quest.icon.IconFrameSelection;
import org.arcadia.arc_quest.client.hud.quest.icon.ObjectiveIconContext;
import org.arcadia.arc_quest.client.hud.quest.journal.QuestJournalScreen;

import java.util.Arrays;
import java.util.List;

/** Independent optional plugin: never depends on the old arc_quest.jei.audit switch. */
@JeiPlugin
public final class ObjectiveIconJeiRuntimeAudit implements IModPlugin, ObjectiveIconClientAudit.JeiBridge {
    private IJeiRuntime runtime;
    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.parse("arc_quest:objective_icon_runtime_audit");
    }
    @Override public void onRuntimeAvailable(IJeiRuntime value) {
        runtime = value;
        if (ObjectiveIconClientAudit.enabled()) ObjectiveIconClientAudit.installJei(this);
    }
    @Override public void onRuntimeUnavailable() { runtime = null; }
    @Override public boolean ready() { return runtime != null; }

    @Override public boolean query(QuestJournalScreen screen, ObjectiveIconContext context, IconFrameSelection selected, int scenario) {
        if (runtime == null) return false;
        var point = screen.getObjectiveIcons().focusedTarget();
        ObjectiveIconClientAudit.check(point != null && screen.getObjectiveIcons().isFocused(context.key()),
                "JEI test requires a rendered, focused objective icon");
        var candidates = ObjectiveIconJeiHitProbe.at(screen, point.x(), point.y());
        if (candidates.isEmpty()) return false;
        ObjectiveIconClientAudit.check(candidates.size() == 1
                        && ItemStack.isSameItemSameTags(candidates.get(0), selected.stack()),
                "JEI icon hit queried the whole tag or a different displayed candidate");
        ObjectiveIconClientAudit.check(ObjectiveIconJeiHitProbe.allowsPrimaryClick(screen, point.x(), point.y()),
                "Rendered objective icon did not opt into primary-click lookup");
        Minecraft mc = Minecraft.getInstance();
        List<Binding> bindings = List.of(binding(mc, "key.jei.showRecipe"), binding(mc, "key.jei.showRecipe2"),
                binding(mc, "key.jei.showUses"), binding(mc, "key.jei.showUses2"));
        try {
            // JEI has separate R/U and left/right mappings. Exercise the actual default mouse
            // mappings, then mutate each real alternative temporarily; never save user options.
            bind(bindings.get(0), InputConstants.Type.KEYSYM, 82);
            bind(bindings.get(1), InputConstants.Type.MOUSE, 0);
            bind(bindings.get(2), InputConstants.Type.KEYSYM, 85);
            bind(bindings.get(3), InputConstants.Type.MOUSE, 1);
            KeyMapping.resetMapping();
            ObjectiveIconClientAudit.check(runtime.getKeyMappings().getShowRecipe()
                            .isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(0))
                            && runtime.getKeyMappings().getShowUses()
                            .isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(1)),
                    "JEI default left/right mouse alternatives are unavailable");

            var row = ObjectiveIconJeiHitProbe.ordinaryRowAt(screen, point.x(), point.y()).orElseThrow(
                    () -> new IllegalStateException("No actual ordinary objective row beside its rendered icon"));
            assertUnconsumed(screen, mouse(screen, row.x(), row.y(), 0),
                    "Primary click over ordinary " + context.objective().getType() + " row was swallowed by JEI");

            RecipeIngredientRole role = scenario % 2 == 0 ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT;
            ScreenEvent input;
            switch (scenario) {
                case 0, 1 -> input = mouse(screen, point.x(), point.y(), scenario);
                case 2 -> {
                    bind(bindings.get(1), InputConstants.Type.MOUSE, 4); KeyMapping.resetMapping();
                    assertUnconsumed(screen, mouse(screen, point.x(), point.y(), 0), "Rebound recipe lookup still consumed old left mouse");
                    input = mouse(screen, point.x(), point.y(), 4);
                }
                case 3 -> {
                    bind(bindings.get(3), InputConstants.Type.MOUSE, 5); KeyMapping.resetMapping();
                    assertUnconsumed(screen, mouse(screen, point.x(), point.y(), 1), "Rebound usage lookup still consumed old right mouse");
                    input = mouse(screen, point.x(), point.y(), 5);
                }
                case 4 -> {
                    bind(bindings.get(0), InputConstants.Type.KEYSYM, 80); KeyMapping.resetMapping();
                    assertUnconsumed(screen, keyboard(screen, 82), "Rebound recipe lookup still consumed old R key");
                    input = keyboard(screen, 80);
                }
                case 5 -> {
                    bind(bindings.get(2), InputConstants.Type.KEYSYM, 79); KeyMapping.resetMapping();
                    assertUnconsumed(screen, keyboard(screen, 85), "Rebound usage lookup still consumed old U key");
                    input = keyboard(screen, 79);
                }
                default -> throw new IllegalArgumentException("Unknown query scenario " + scenario);
            }
            MinecraftForge.EVENT_BUS.post(input);
            ObjectiveIconClientAudit.check(input.isCanceled(), "Actual JEI binding did not handle scenario " + scenario);
            ObjectiveIconClientAudit.check(mc.screen != null && mc.screen.getClass().getName().startsWith("mezz.jei."),
                    "Query did not open the actual JEI recipes screen");
            ObjectiveIconClientAudit.check(runtime.getRecipesGui().getParentScreen().orElse(null) == screen,
                    "JEI query lost its exact journal parent");
            verifyActualFocus(role, selected.stack());
            ObjectiveIconClientAudit.LOG.info("{} JEI_QUERY scenario={} role={} quest={} objective={} candidate={} alternatives={} point={},{} rowPrimaryPassthrough=true",
                    ObjectiveIconClientAudit.MARKER, scenario, role, context.questId(), context.objective().getObjectiveId(),
                    selected.candidateKey(), candidates.size(), point.x(), point.y());
            return true;
        } finally {
            bindings.forEach(Binding::restore);
            KeyMapping.resetMapping();
            ObjectiveIconClientAudit.check(bindings.stream().allMatch(Binding::restored), "JEI key mappings were not restored");
        }
    }

    private void verifyActualFocus(RecipeIngredientRole role, ItemStack selected) {
        // The public IRecipesGui has no focus getter. Read the pinned JEI runtime's active
        // lookup state only; do not intercept show(), replace runtime, or synthesize a focus.
        try {
            Object logic = field(runtime.getRecipesGui(), "logic");
            Object state = field(logic, "state");
            var getter = state.getClass().getMethod("getFocuses");
            getter.setAccessible(true);
            IFocusGroup group = (IFocusGroup) getter.invoke(state);
            var all = group.getAllFocuses();
            var items = group.getItemStackFocuses().map(focus -> focus.getTypedValue().getIngredient()).toList();
            ObjectiveIconClientAudit.check(all.size() == 1 && all.get(0).getRole() == role
                            && items.size() == 1 && ItemStack.isSameItemSameTags(items.get(0), selected),
                    "Actual JEI focus has wrong direction or displayed candidate: expected=" + role + " actual=" + all);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot inspect pinned JEI runtime focus", error);
        }
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }
    private static ScreenEvent.MouseButtonPressed.Pre mouse(QuestJournalScreen screen, double x, double y, int button) {
        return new ScreenEvent.MouseButtonPressed.Pre(screen, x, y, button);
    }
    private static ScreenEvent.KeyPressed.Pre keyboard(QuestJournalScreen screen, int key) {
        return new ScreenEvent.KeyPressed.Pre(screen, key, 0, 0);
    }
    private static void assertUnconsumed(QuestJournalScreen screen, ScreenEvent input, String reason) {
        MinecraftForge.EVENT_BUS.post(input);
        ObjectiveIconClientAudit.check(!input.isCanceled() && Minecraft.getInstance().screen == screen, reason);
    }
    private static Binding binding(Minecraft mc, String name) {
        KeyMapping mapping = Arrays.stream(mc.options.keyMappings).filter(key -> key.getName().equals(name))
                .findFirst().orElseThrow(() -> new IllegalStateException("Missing actual JEI mapping " + name));
        return new Binding(mapping, mapping.getKey(), mapping.getKeyModifier());
    }
    private static void bind(Binding binding, InputConstants.Type type, int code) {
        binding.mapping().setKeyModifierAndCode(KeyModifier.NONE, type.getOrCreate(code));
    }
    private record Binding(KeyMapping mapping, InputConstants.Key key, KeyModifier modifier) {
        void restore() { mapping.setKeyModifierAndCode(modifier, key); }
        boolean restored() { return mapping.getKey().equals(key) && mapping.getKeyModifier() == modifier; }
    }
}
