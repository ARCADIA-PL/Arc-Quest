package org.arcadia.arc_quest.presentation;

import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Single-thread optional presentation SPI. Never changes player state or camera pose.
 * Higher priority owns focus; equal priority uses the newest handle. Suspended time does not
 * advance its clock. Replacing an owner ends the previous handle once, including on failures.
 */
public final class FocusCoordinator {
    public enum EndReason { COMPLETED, CANCELLED, REPLACED, DISCONNECTED, DIMENSION_CHANGED, INVALID_PLAYER, FAILED }
    public interface Lifecycle {
        default void start(){}
        default void suspend(){}
        default void resume(){}
        default void end(EndReason reason){}
    }
    public final class Handle implements AutoCloseable {
        private final ResourceLocation owner; private final int priority; private final long order;
        private final Lifecycle lifecycle;
        private final Set<UUID> suspensions=new HashSet<>();
        private boolean running,started,ended;
        private long accumulated,last;
        private Handle(ResourceLocation owner,int priority,Lifecycle lifecycle){this.owner=owner;this.priority=priority;this.lifecycle=lifecycle;order=++sequence;}
        public ResourceLocation owner(){return owner;}
        public boolean active(){return running&&!ended;}
        public boolean ended(){return ended;}
        public long elapsedNanos(){return accumulated+(running?Math.max(0,clock.getAsLong()-last):0);}
        public Suspension suspend(){var id=UUID.randomUUID();if(!ended){suspensions.add(id);reconcile();}return new Suspension(this,id);}
        public void end(EndReason reason){if(ended)return;ended=true;stop();handles.remove(owner,this);invoke(()->lifecycle.end(reason));reconcile();}
        @Override public void close(){end(EndReason.COMPLETED);}
        private void stop(){if(!running)return;accumulated+=Math.max(0,clock.getAsLong()-last);running=false;invoke(lifecycle::suspend);}
    }
    public final class Suspension implements AutoCloseable {
        private Handle handle;private final UUID id;
        private Suspension(Handle handle,UUID id){this.handle=handle;this.id=id;}
        @Override public void close(){if(handle==null)return;var value=handle;handle=null;if(!value.ended&&value.suspensions.remove(id))reconcile();}
    }
    private final LongSupplier clock;private final Consumer<RuntimeException> failures;
    private final Map<ResourceLocation,Handle> handles=new LinkedHashMap<>();
    private long sequence;private boolean reconciling;
    public FocusCoordinator(){this(System::nanoTime,e->System.getLogger(FocusCoordinator.class.getName()).log(System.Logger.Level.ERROR,"Presentation callback failed",e));}
    public FocusCoordinator(LongSupplier clock,Consumer<RuntimeException> failures){this.clock=Objects.requireNonNull(clock);this.failures=Objects.requireNonNull(failures);}
    public Handle begin(ResourceLocation owner,int priority,Lifecycle lifecycle){Objects.requireNonNull(owner);Objects.requireNonNull(lifecycle);var old=handles.get(owner);if(old!=null)old.end(EndReason.REPLACED);var handle=new Handle(owner,priority,lifecycle);handles.put(owner,handle);reconcile();return handle;}
    public void endAll(EndReason reason){boolean previous=reconciling;reconciling=true;try{for(var handle:new ArrayList<>(handles.values()))handle.end(reason);}finally{reconciling=previous;}}
    public int size(){return handles.size();}
    private void reconcile(){
        if(reconciling)return;reconciling=true;
        try {
            var winner=handles.values().stream().filter(h->!h.ended&&h.suspensions.isEmpty()).max(Comparator.<Handle>comparingInt(h->h.priority).thenComparingLong(h->h.order)).orElse(null);
            for(var handle:new ArrayList<>(handles.values()))if(handle!=winner)handle.stop();
            if(winner!=null&&!winner.running&&!winner.ended){winner.running=true;winner.last=clock.getAsLong();boolean first=!winner.started;winner.started=true;
                try {if(first)winner.lifecycle.start();else winner.lifecycle.resume();}
                catch(RuntimeException failure){winner.end(EndReason.FAILED);failures.accept(failure);}
            }
        } finally {reconciling=false;}
        // Callback cleanup may have changed the winner.
        if(!handles.isEmpty()&&handles.values().stream().noneMatch(h->h.running)&&handles.values().stream().anyMatch(h->!h.ended&&h.suspensions.isEmpty()))reconcile();
    }
    private void invoke(Runnable action){try{action.run();}catch(RuntimeException failure){failures.accept(failure);}}
}
