package org.arcadia.arc_quest.guide.io;

import java.nio.file.Path;

public record GuideDatapackLoadError(Path file, String message, Throwable cause) {
}
