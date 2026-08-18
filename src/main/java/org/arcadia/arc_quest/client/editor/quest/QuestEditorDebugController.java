package org.arcadia.arc_quest.client.editor.quest;

import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.quest.spec.PhaseSpec;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.arcadia.arc_quest.quest.spec.TransitionSpec;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

final class QuestEditorDebugController {
    private static final int MAX_CHECKPOINTS = 32;
    private static final int MAX_STEPS = 256;
    private final long seed;
    private final Random random;
    private final Deque<Snapshot> checkpoints = new ArrayDeque<>();
    private final Set<String> breakpoints = new LinkedHashSet<>();
    private final Set<String> flags = new LinkedHashSet<>();
    private final Map<String, Integer> variables = new LinkedHashMap<>();
    private final Map<ResourceLocation, Integer> inventory = new LinkedHashMap<>();
    private String currentPhaseId;
    private int steps;

    QuestEditorDebugController(long seed) {
        this.seed = seed;
        this.random = new Random(seed);
    }

    String currentPhaseId() { return currentPhaseId; }
    Map<String, Integer> variables() { return variables; }
    Set<String> flags() { return flags; }
    Map<ResourceLocation, Integer> inventory() { return inventory; }

    String start(QuestSpec spec, String preferredPhaseId) {
        reset(spec);
        if (preferredPhaseId != null && find(spec, preferredPhaseId) != null) currentPhaseId = preferredPhaseId;
        return "Debug sandbox started at " + currentPhaseId;
    }

    String step(QuestSpec spec) {
        if (currentPhaseId == null) return start(spec, null);
        if (++steps > MAX_STEPS) return "Debug stopped: 256 step loop guard";
        PhaseSpec phase = find(spec, currentPhaseId);
        if (phase == null) return "Debug stopped: phase not found " + currentPhaseId;
        if (breakpoints.contains(currentPhaseId)) return "Breakpoint: " + currentPhaseId;
        if (phase.transitions.isEmpty()) return "Terminal phase: " + currentPhaseId;
        TransitionSpec transition = phase.transitions.get(0);
        List<String> targets = transition.targetPhaseIds == null || transition.targetPhaseIds.isEmpty()
                ? List.of(transition.targetPhaseId) : transition.targetPhaseIds;
        targets = targets.stream().filter(id -> id != null && !id.isBlank() && find(spec, id) != null).toList();
        if (targets.isEmpty()) return "No valid transition from " + currentPhaseId;
        checkpoint();
        currentPhaseId = targets.get(random.nextInt(targets.size()));
        return transition.condition == null ? "Stepped to " + currentPhaseId
                : "Stepped to " + currentPhaseId + " (condition simulated as pass)";
    }

    void checkpoint() {
        checkpoints.addLast(snapshot());
        while (checkpoints.size() > MAX_CHECKPOINTS) checkpoints.removeFirst();
    }

    String rollback() {
        if (checkpoints.isEmpty()) return "No debug checkpoint";
        restore(checkpoints.removeLast());
        return "Rolled back to " + currentPhaseId;
    }

    void reset(QuestSpec spec) {
        currentPhaseId = !spec.initialPhaseIds.isEmpty() ? spec.initialPhaseIds.get(0) : spec.initialPhaseId;
        if ((currentPhaseId == null || currentPhaseId.isBlank()) && !spec.phases.isEmpty()) currentPhaseId = spec.phases.get(0).phaseId;
        steps = 0;
        checkpoints.clear();
        flags.clear();
        variables.clear();
        inventory.clear();
        random.setSeed(seed);
    }

    void toggleBreakpoint(String phaseId) { if (!breakpoints.remove(phaseId)) breakpoints.add(phaseId); }
    void setVariable(String key, int value) { variables.put(key, value); }
    void setFlag(String key, boolean value) { if (value) flags.add(key); else flags.remove(key); }
    void setInventory(ResourceLocation itemId, int count) { if (count <= 0) inventory.remove(itemId); else inventory.put(itemId, count); }

    private Snapshot snapshot() { return new Snapshot(currentPhaseId, steps, new LinkedHashSet<>(flags),
            new LinkedHashMap<>(variables), new LinkedHashMap<>(inventory)); }
    private void restore(Snapshot snapshot) { currentPhaseId = snapshot.phaseId; steps = snapshot.steps;
        flags.clear(); flags.addAll(snapshot.flags); variables.clear(); variables.putAll(snapshot.variables);
        inventory.clear(); inventory.putAll(snapshot.inventory); }
    private static PhaseSpec find(QuestSpec spec, String id) { return spec.phases.stream().filter(phase -> id.equals(phase.phaseId)).findFirst().orElse(null); }
    private record Snapshot(String phaseId, int steps, Set<String> flags, Map<String,Integer> variables,
                            Map<ResourceLocation,Integer> inventory) { }
}
