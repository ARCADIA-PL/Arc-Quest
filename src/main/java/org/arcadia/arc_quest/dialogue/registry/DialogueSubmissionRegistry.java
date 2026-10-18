package org.arcadia.arc_quest.dialogue.registry;

import org.arcadia.arc_quest.dialogue.api.DialogueSubmissionDefinition;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Code-defined, server-authoritative submission catalogues. Re-registration replaces a reload. */
public final class DialogueSubmissionRegistry {
    private static final Map<String, DialogueSubmissionDefinition> DEFINITIONS = new ConcurrentHashMap<>();
    private DialogueSubmissionRegistry() {}
    public static void register(DialogueSubmissionDefinition definition) { DEFINITIONS.put(definition.id(), definition); }
    public static DialogueSubmissionDefinition get(String id) { return DEFINITIONS.get(id); }
}
