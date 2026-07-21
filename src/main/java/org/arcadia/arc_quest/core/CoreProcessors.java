package org.arcadia.arc_quest.core;

public final class CoreProcessors {

    private static final CoreProcessor DEFAULT = new DefaultCoreProcessor();

    private CoreProcessors() {
    }

    public static CoreProcessor get() {
        return DEFAULT;
    }
}
