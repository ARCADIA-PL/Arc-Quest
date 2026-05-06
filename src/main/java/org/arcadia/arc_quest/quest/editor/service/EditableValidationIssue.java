package org.arcadia.arc_quest.quest.editor.service;

public record EditableValidationIssue(EditableValidationSeverity severity, String path, String message) {
}
