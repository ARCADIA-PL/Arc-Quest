package org.arcadia.arc_quest.client;

import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
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

    @Override public boolean query(QuestJournalScreen screen, ObjectiveIconContext context, IconFrameSelection selected) {
        if (runtime == null) return false;
        var point = screen.getObjectiveIcons().focusedTarget();
        ObjectiveIconClientAudit.check(point != null && screen.getObjectiveIcons().isFocused(context.key()),
                "JEI test requires a rendered, focused objective icon");
        var candidates = ObjectiveIconJeiHitProbe.at(screen, point.x(), point.y());
        if (candidates.isEmpty()) return false;
        ObjectiveIconClientAudit.check(candidates.size() == 1
                        && ItemStack.isSameItemSameTags(candidates.get(0), selected.stack()),
                "JEI icon hit queried the whole tag or a different displayed candidate");
        Minecraft mc = Minecraft.getInstance();
        KeyMapping mapping = Arrays.stream(mc.options.keyMappings)
                .filter(key -> key.getName().equals("key.jei.showUses")).findFirst().orElseThrow();
        var previousKey = mapping.getKey();
        var previousModifier = mapping.getKeyModifier();
        Runnable restore = () -> { mapping.setKeyModifierAndCode(previousModifier, previousKey); KeyMapping.resetMapping(); };
        try {
            mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.Type.MOUSE.getOrCreate(4));
            KeyMapping.resetMapping();
            var input = new ScreenEvent.MouseButtonPressed.Pre(screen, point.x(), point.y(), 4);
            MinecraftForge.EVENT_BUS.post(input);
            ObjectiveIconClientAudit.check(input.isCanceled(), "Actual JEI mouse binding did not handle objective lookup");
            ObjectiveIconClientAudit.LOG.info("{} JEI_QUERY candidate={} alternatives={} point={},{}",
                    ObjectiveIconClientAudit.MARKER, selected.candidateKey(), candidates.size(), point.x(), point.y());
            return true;
        } finally { restore.run(); }
    }
}
