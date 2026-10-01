package org.arcadia.arc_quest.client.hud.quest.icon;

/** Client-only extension. An icon never grants permission to query a JEI ingredient. */
@FunctionalInterface
public interface ObjectiveIconProvider {
    ResolvedObjectiveIcon resolve(ObjectiveIconContext context);
}
