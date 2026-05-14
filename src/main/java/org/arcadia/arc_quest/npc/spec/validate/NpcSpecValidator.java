package org.arcadia.arc_quest.npc.spec.validate;

import org.arcadia.arc_quest.npc.spec.NpcBindingSpec;
import org.arcadia.arc_quest.npc.spec.NpcSpec;

import java.util.ArrayList;
import java.util.List;

public final class NpcSpecValidator {

    public NpcValidationReport validate(NpcSpec spec) {
        NpcValidationReport report = new NpcValidationReport();

        if (spec == null) {
            report.add(NpcValidationIssue.Severity.ERROR, "npc", "NpcSpec is null");
            return report;
        }

        if (spec.entityType == null || spec.entityType.isBlank()) {
            report.add(NpcValidationIssue.Severity.ERROR, "entity_type", "entity_type is required");
        }

        if (spec.dialogueDistance < 1.0) {
            report.add(NpcValidationIssue.Severity.WARN, "dialogue_distance",
                    "dialogue_distance should be at least 1.0");
        }

        if (spec.bindings != null) {
            for (int i = 0; i < spec.bindings.size(); i++) {
                validateBinding(report, spec.bindings.get(i), i);
            }
        }

        return report;
    }

    private void validateBinding(NpcValidationReport report, NpcBindingSpec binding, int idx) {
        String prefix = "bindings[" + idx + "]";

        boolean hasDialogue = (binding.dialogueId != null && !binding.dialogueId.isBlank())
                || (binding.dialogueIdFromNbt != null && !binding.dialogueIdFromNbt.isBlank());
        if (!hasDialogue) {
            report.add(NpcValidationIssue.Severity.ERROR, prefix + ".dialogue_id",
                    "dialogue_id or dialogue_id_from_nbt is required for binding");
        }
    }

    public static final class NpcValidationReport {
        private final List<NpcValidationIssue> issues = new ArrayList<>();

        public void add(NpcValidationIssue.Severity severity, String path, String message) {
            issues.add(new NpcValidationIssue(severity, path, message));
        }

        public List<NpcValidationIssue> getIssues() {
            return issues;
        }

        public boolean hasErrors() {
            return issues.stream().anyMatch(i -> i.severity == NpcValidationIssue.Severity.ERROR);
        }
    }
}