package org.com.arc_quest.quest.condition;

import net.minecraft.resources.ResourceLocation;
import org.com.arc_quest.quest.api.ICondition;

import java.util.Map;
import java.util.Set;

public final class FlagSetCondition implements ICondition {

    private final String flag;

    public FlagSetCondition(String flag) {
        this.flag = flag;
    }

    @Override
    public boolean test(Set<ResourceLocation> completedQuests,
                        Set<String> flags,
                        Map<String, Integer> variables) {
        return flags.contains(this.flag);
    }

    @Override
    public String describe() {
        return "FlagSet(" + this.flag + ")";
    }
}