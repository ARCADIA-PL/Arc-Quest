package org.arcadia.arc_quest.tutorial;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TutorialEvidenceGateTest {
    @Test void repeatedOrOutOfOrderNativeCallbacksNeverCountTwice(){
        var gate=new TutorialSessions.EvidenceGate();var source=ResourceLocation.parse("test:native");
        assertFalse(gate.accept(source,0));assertTrue(gate.accept(source,8));assertFalse(gate.accept(source,8));assertFalse(gate.accept(source,7));assertTrue(gate.accept(source,9));
        assertTrue(gate.accept(ResourceLocation.parse("other:native"),1));
        assertTrue(new TutorialSessions.EvidenceGate().accept(source,1));
    }
}
