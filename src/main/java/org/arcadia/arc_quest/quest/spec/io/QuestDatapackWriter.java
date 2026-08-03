package org.arcadia.arc_quest.quest.spec.io;

import org.arcadia.arc_quest.quest.spec.QuestSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class QuestDatapackWriter {

    public Path write(String questFileName, QuestSpec spec) throws IOException {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
        DatapackPathResolver.ensureDatapackDirsExist();

        return write(DatapackPathResolver.resolveQuestFile(questFileName), spec);
    }

    public Path write(Path target, QuestSpec spec) throws IOException {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
        DatapackPathResolver.ensureInsideDatapackRoot(target);
        Files.createDirectories(target.toAbsolutePath().normalize().getParent());
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");

        String payload = QuestSpecJsonWriter.write(spec);
        Files.writeString(temp, payload, StandardCharsets.UTF_8);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target;
    }
}
