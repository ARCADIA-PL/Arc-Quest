package org.arcadia.arc_quest.quest.spec.io;

import com.mojang.logging.LogUtils;
import org.arcadia.arc_quest.quest.spec.QuestSpec;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class QuestDatapackWriter {
    private static final Logger LOGGER = LogUtils.getLogger();

    public Path write(String questFileName, QuestSpec spec) throws IOException {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
        DatapackPathResolver.ensureDatapackDirsExist();

        return write(DatapackPathResolver.resolveQuestFile(questFileName), spec);
    }

    public Path write(Path target, QuestSpec spec) throws IOException {
        Transaction transaction = replace(target, spec);
        transaction.commit();
        return transaction.target();
    }

    public Transaction replace(Path target, QuestSpec spec) throws IOException {
        if (spec == null) throw new IllegalArgumentException("spec must not be null");
        DatapackPathResolver.ensureInsideDatapackRoot(target);
        Path normalizedTarget = target.toAbsolutePath().normalize();
        Path parent = normalizedTarget.getParent();
        Files.createDirectories(parent);
        boolean hadOriginal = Files.isRegularFile(normalizedTarget);
        Path backup = null;
        Path temp = null;
        try {
            if (hadOriginal) {
                backup = Files.createTempFile(parent, "." + normalizedTarget.getFileName() + ".", ".editor-backup");
                Files.copy(normalizedTarget, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            temp = Files.createTempFile(parent, "." + normalizedTarget.getFileName() + ".", ".tmp");
            Files.writeString(temp, QuestSpecJsonWriter.write(spec), StandardCharsets.UTF_8);
            moveReplacing(temp, normalizedTarget);
            temp = null;
            return new Transaction(normalizedTarget, backup, hadOriginal);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temp);
            Files.deleteIfExists(backup);
            throw exception;
        }
    }

    public Transaction delete(Path target) throws IOException {
        DatapackPathResolver.ensureInsideDatapackRoot(target);
        Path normalizedTarget = target.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedTarget)) throw new IOException("Quest file does not exist: " + normalizedTarget);
        Path parent = normalizedTarget.getParent();
        Path backup = Files.createTempFile(parent, "." + normalizedTarget.getFileName() + ".", ".editor-backup");
        try {
            moveReplacing(normalizedTarget, backup);
            return new Transaction(normalizedTarget, backup, true);
        } catch (IOException exception) {
            Files.deleteIfExists(backup);
            throw exception;
        }
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            LOGGER.warn("[QuestEditor] Atomic move unavailable, falling back to replace move: {} -> {}", source, target);
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static final class Transaction {
        private final Path target;
        private final Path backup;
        private final boolean hadOriginal;
        private boolean completed;

        private Transaction(Path target, Path backup, boolean hadOriginal) {
            this.target = target;
            this.backup = backup;
            this.hadOriginal = hadOriginal;
        }

        public Path target() { return target; }

        public void commit() throws IOException {
            if (completed) return;
            if (backup != null) Files.deleteIfExists(backup);
            completed = true;
        }

        public void rollback() throws IOException {
            if (completed) return;
            if (hadOriginal && backup != null && Files.exists(backup)) moveReplacing(backup, target);
            else {
                Files.deleteIfExists(target);
                if (backup != null) Files.deleteIfExists(backup);
            }
            completed = true;
        }
    }
}
