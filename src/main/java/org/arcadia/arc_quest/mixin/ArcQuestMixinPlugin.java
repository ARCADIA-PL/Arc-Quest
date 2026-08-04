package org.arcadia.arc_quest.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class ArcQuestMixinPlugin implements IMixinConfigPlugin {

    private static final String XAERO_WORLD_FILTER_MIXIN =
            "org.arcadia.arc_quest.mixin.client.compat.MixinXaeroWaypointRenderProvider";
    private static final String XAERO_WORLD_PROVIDER =
            "xaero.hud.minimap.waypoint.render.world.WaypointWorldRenderProvider";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !XAERO_WORLD_FILTER_MIXIN.equals(mixinClassName) || isClassPresent(XAERO_WORLD_PROVIDER);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean isClassPresent(String className) {
        String resourcePath = className.replace('.', '/') + ".class";
        return ArcQuestMixinPlugin.class.getClassLoader().getResource(resourcePath) != null;
    }
}
