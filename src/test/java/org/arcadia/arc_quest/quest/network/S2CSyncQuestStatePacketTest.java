package org.arcadia.arc_quest.quest.network;

import org.arcadia.arc_quest.quest.data.QuestRuntimeData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class S2CSyncQuestStatePacketTest {

    @Test
    void snapshotsRuntimeDataBeforeDeferredEncoding() throws ReflectiveOperationException {
        QuestRuntimeData runtimeData = new QuestRuntimeData(
                "arc_quest:test", "initial", 1, 1L, 2L, 3L);
        S2CSyncQuestStatePacket packet = new S2CSyncQuestStatePacket(runtimeData, 4L, 5L, 6L);

        runtimeData.incrementProgress("initial", 0, 1, 1);
        runtimeData.activatePhase("later", 0);

        Field dataField = S2CSyncQuestStatePacket.class.getDeclaredField("data");
        dataField.setAccessible(true);
        QuestRuntimeData snapshot = (QuestRuntimeData) dataField.get(packet);

        assertEquals(0, snapshot.getAllProgress("initial")[0]);
        assertFalse(snapshot.getActivePhaseIds().contains("later"));
    }
}
