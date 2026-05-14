package org.arcadia.arc_quest.quest.spec;

import org.arcadia.arc_quest.quest.api.CompareOp;

/**
 * @deprecated 仅用于 collection completion rules，条件请使用 {@link org.arcadia.arc_quest.condition.ConditionSpec}
 */
@Deprecated
public class ConditionSpec {
    public String type = "always";
    public String flag = "";
    public String questId = "";
    public String variable = "";
    public CompareOp compareOp = CompareOp.GREATER_OR_EQUAL;
    public int value = 0;
    public ConditionSpec left = null;
    public ConditionSpec right = null;
}
