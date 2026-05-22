package org.arcadia.arc_quest.guide.api;

import javax.annotation.Nullable;
import java.util.Map;

public record GuideTextContext(Map<String, Object> vars) {
    public GuideTextContext {
        vars = vars == null ? Map.of() : Map.copyOf(vars);
    }

    public static GuideTextContext empty() {
        return new GuideTextContext(Map.of());
    }

    @Nullable
    public Object get(String key) {
        return vars.get(key);
    }
}
