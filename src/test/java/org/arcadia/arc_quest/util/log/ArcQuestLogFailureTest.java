package org.arcadia.arc_quest.util.log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArcQuestLogFailureTest {
    @Test
    void persistenceFailureRemainsVisibleWhenDiagnosticCategoryIsDisabled() {
        assertFalse(ArcQuestLog.isEnabled(ArcQuestLog.Category.PERSISTENCE));
        List<LogEvent> captured = new ArrayList<>();
        AbstractAppender appender = new AbstractAppender("arc-quest-failure-test", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) { captured.add(event.toImmutable()); }
        };
        Logger logger = (Logger) LogManager.getLogger(ArcQuestLog.rawLogger().getName());
        Level previous = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.ERROR);
        IllegalStateException failure = new IllegalStateException("disk unavailable");
        try {
            ArcQuestLog.error(ArcQuestLog.Category.PERSISTENCE, "Failed to save player {}", "fixture", failure);
            LogEvent event = captured.stream()
                    .filter(value -> value.getMessage().getFormattedMessage().contains("Failed to save player fixture"))
                    .findFirst().orElseThrow();
            assertEquals(Level.ERROR, event.getLevel());
            assertSame(failure, event.getThrown());
            assertTrue(event.getMessage().getFormattedMessage().startsWith("[PERSISTENCE]"));
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(previous);
            appender.stop();
        }
    }
}
