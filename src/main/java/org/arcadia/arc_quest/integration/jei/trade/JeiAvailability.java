package org.arcadia.arc_quest.integration.jei.trade;

/** Pure projection of the state that an action will validate after its normal reset pass. */
public record JeiAvailability(int used, int remaining, boolean cooldownActive, boolean resetDue,
                              boolean qualified) {
    public static JeiAvailability project(int used, int limit, boolean cooldownActive,
                                          boolean resetDue, boolean qualified) {
        int projectedUsed = resetDue ? 0 : Math.max(0, used);
        int remaining = limit > 0 ? Math.max(0, limit - projectedUsed) : -1;
        // A limited offer only starts cooling down once its whole quota has been used.
        boolean active = !resetDue && cooldownActive && (limit <= 0 || remaining == 0);
        return new JeiAvailability(projectedUsed, remaining, active, resetDue, qualified);
    }

    /** Does not claim affordability, event acceptance, or authorization to execute an action. */
    public boolean meetsStateConditions() {
        return qualified && !cooldownActive && remaining != 0;
    }
}
