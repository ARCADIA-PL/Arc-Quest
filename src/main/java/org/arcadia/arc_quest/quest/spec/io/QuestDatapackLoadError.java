package org.arcadia.arc_quest.quest.spec.io;

import java.nio.file.Path;

public record QuestDatapackLoadError(Path file, String message, Throwable cause) {
}
