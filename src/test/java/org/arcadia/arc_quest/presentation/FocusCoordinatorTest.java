package org.arcadia.arc_quest.presentation;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FocusCoordinatorTest {
    private final AtomicLong clock=new AtomicLong();private final List<String> calls=new ArrayList<>();
    private final FocusCoordinator core=new FocusCoordinator(clock::get,e->calls.add("error"));
    private FocusCoordinator.Handle begin(String name,int priority){return core.begin(ResourceLocation.parse("test:"+name),priority,new FocusCoordinator.Lifecycle(){
        public void start(){calls.add(name+":start");}public void suspend(){calls.add(name+":pause");}public void resume(){calls.add(name+":resume");}public void end(FocusCoordinator.EndReason reason){calls.add(name+":"+reason);}
    });}
    @Test void higherPriorityPausesClockAndResumesOriginalOwner(){
        var a=begin("camera",10);clock.set(20);var b=begin("ponder",100);clock.set(120);
        assertFalse(a.active());assertEquals(20,a.elapsedNanos());b.close();clock.set(140);
        assertTrue(a.active());assertEquals(40,a.elapsedNanos());assertTrue(calls.contains("camera:resume"));
    }
    @Test void nestedSuspensionsRequireBothClosesAndCleanupIsIdempotent(){
        var a=begin("guide",10);var one=a.suspend();var two=a.suspend();one.close();one.close();assertFalse(a.active());two.close();assertTrue(a.active());
        a.close();a.close();assertEquals(1,calls.stream().filter("guide:COMPLETED"::equals).count());assertEquals(0,core.size());
    }
    @Test void replaceAndDisconnectDoNotResumeDyingOwners(){
        var a=begin("scene",10);var b=begin("scene",10);assertTrue(a.ended());assertTrue(b.active());begin("other",20);calls.clear();core.endAll(FocusCoordinator.EndReason.DISCONNECTED);
        assertFalse(calls.stream().anyMatch(s->s.endsWith(":resume")));assertEquals(0,core.size());
    }
    @Test void failedStartCleansUpAndReturnsFocusToPreviousOwner(){
        var a=begin("camera",10);var failed=core.begin(ResourceLocation.parse("test:bad"),20,new FocusCoordinator.Lifecycle(){public void start(){throw new IllegalStateException("unavailable adapter");}});
        assertTrue(failed.ended());assertTrue(a.active());assertEquals(1,core.size());assertTrue(calls.contains("error"));
    }
}
