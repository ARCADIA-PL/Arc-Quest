package org.arcadia.arc_quest.guide.spec;

import java.util.ArrayList;
import java.util.List;

public class GuideTextSpec {
    public String mode = "literal";
    public String value = "";
    public List<String> args = new ArrayList<>();

    public static GuideTextSpec literal(String value) {
        GuideTextSpec spec = new GuideTextSpec();
        spec.mode = "literal";
        spec.value = value == null ? "" : value;
        return spec;
    }
}
