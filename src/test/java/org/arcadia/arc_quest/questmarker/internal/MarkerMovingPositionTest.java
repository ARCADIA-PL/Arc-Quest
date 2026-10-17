package org.arcadia.arc_quest.questmarker.internal;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MarkerMovingPositionTest {
    private static QuestMarkerData marker(double x,String dimension) {
        return new QuestMarkerData.Builder("npc",x,80,20,"NPC").dimension(dimension)
                .followEntity(17,"00000000-0000-0000-0000-000000000001",QuestMarkerData.EntityAttachPoint.HEAD).build();
    }
    @Test void movingEntityCoordinatesMustReachClientsOutsideEntityTrackingRange() {
        var first=marker(10,"minecraft:overworld");
        assertTrue(MarkerModelAdapter.sameMovingBinding(first,marker(10,"minecraft:overworld")));
        assertFalse(MarkerModelAdapter.sameMovingBinding(first,marker(210,"minecraft:overworld")));
        assertFalse(MarkerModelAdapter.sameMovingBinding(first,marker(10,"minecraft:the_end")));
    }
}
