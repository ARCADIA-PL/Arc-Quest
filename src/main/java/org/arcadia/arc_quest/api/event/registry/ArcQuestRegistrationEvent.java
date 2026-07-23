package org.arcadia.arc_quest.api.event.registry;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Base type for Arc Quest business-content registration events.
 *
 * <p>Arc Quest posts these events to every mod bus before its registries are frozen. Add-ons may
 * keep using the existing {@code ArcQuestAPI}, builders, and public registries from their event
 * listeners.</p>
 */
public abstract class ArcQuestRegistrationEvent extends Event implements IModBusEvent {

    private ArcQuestRegistrationEvent() {
    }

    /** Registers quest definitions. */
    public static final class Quest extends ArcQuestRegistrationEvent {
    }

    /** Registers trade shop definitions. */
    public static final class Trade extends ArcQuestRegistrationEvent {
    }

    /** Registers gacha shop definitions. */
    public static final class Gacha extends ArcQuestRegistrationEvent {
    }

    /** Registers NPC dialogue extensions and bindings. */
    public static final class Npc extends ArcQuestRegistrationEvent {
    }

    /** Registers dialogue tree definitions. */
    public static final class Dialogue extends ArcQuestRegistrationEvent {
    }
}
