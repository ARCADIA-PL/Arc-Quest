package org.arcadia.arc_quest.tutorial;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server-thread, menu-bound tutorial lifecycle. Definitions own course rules and progress;
 * the core owns identity, revision, deduplication, synchronization and cleanup. No request can
 * report an operation: evidence is accepted only through a trusted server-side adapter.
 */
@Mod.EventBusSubscriber(modid = "arc_quest")
public final class TutorialSessions {
    public static final TutorialSessions INSTANCE = new TutorialSessions();
    public record Definition(ResourceLocation id, Predicate<ServerPlayer> ready, Consumer<ServerPlayer> refresh,
                             BiConsumer<ServerPlayer, TutorialNetwork.Request> action) {
        public Definition { Objects.requireNonNull(id); Objects.requireNonNull(ready); Objects.requireNonNull(refresh); Objects.requireNonNull(action); }
    }
    /** Extensible ephemeral context. Only the owning definition may mutate presentation fields.
     * They never confer economic/progression authority and are discarded on menu close.
     */
    public static class Session {
        public final UUID token = UUID.randomUUID();
        public final AbstractContainerMenu menu;
        public final String course;
        public long revision;
        public boolean reviewed, automaticCollapsed;
        public String signature = "";
        public Component result = Component.empty();
        private ResourceLocation definition;
        private final EvidenceGate evidence = new EvidenceGate();
        public Session(AbstractContainerMenu menu, String course, boolean reviewed, boolean collapsed) {
            this.menu=Objects.requireNonNull(menu); this.course=Objects.requireNonNull(course);
            if(course.isBlank() || course.length()>100)throw new IllegalArgumentException("Invalid course id");
            this.reviewed=reviewed; automaticCollapsed=collapsed;
        }
    }
    public record Evidence(ResourceLocation source, long operationId, String kind, ItemStack before, ItemStack after, List<ItemStack> outputs) {
        public Evidence { Objects.requireNonNull(source);Objects.requireNonNull(kind);before=before.copy();after=after.copy();outputs=outputs.stream().map(ItemStack::copy).toList();if(operationId<=0)throw new IllegalArgumentException("Evidence requires a positive operation id"); }
        @Override public ItemStack before(){return before.copy();}
        @Override public ItemStack after(){return after.copy();}
        @Override public List<ItemStack> outputs(){return outputs.stream().map(ItemStack::copy).toList();}
    }
    /** Monotonic ids are scoped to one source and one menu session, never player-global. */
    static final class EvidenceGate {
        private final Map<ResourceLocation,Long> accepted=new HashMap<>();
        boolean accept(ResourceLocation source,long id){if(id<=0||id<=accepted.getOrDefault(source,0L)||!accepted.containsKey(source)&&accepted.size()>=16)return false;accepted.put(source,id);return true;}
    }
    private final Map<ResourceLocation,Definition> definitions=new LinkedHashMap<>();
    private final Map<UUID,Session> sessions=new HashMap<>();
    private TutorialSessions(){}
    public void register(Definition definition){if(definitions.putIfAbsent(definition.id(),definition)!=null)throw new IllegalArgumentException("Duplicate tutorial definition "+definition.id());}
    public Session get(UUID player){return sessions.get(player);}
    public TutorialProgress progress(ServerPlayer player,ResourceLocation definition,String course){return new TutorialProgress(player,definition,course);}
    public <T extends Session> T get(UUID player,Class<T> type){var value=get(player);return type.isInstance(value)?type.cast(value):null;}
    public void open(ServerPlayer player,ResourceLocation definition,Session session){requireThread(player);if(!definitions.containsKey(definition)||session.menu!=player.containerMenu)throw new IllegalStateException("Tutorial is not bound to the active menu");session.definition=definition;sessions.put(player.getUUID(),session);}
    public void close(UUID player){sessions.remove(player);}
    public boolean evidence(ServerPlayer player,ResourceLocation definition,AbstractContainerMenu menu,Evidence evidence){requireThread(player);var session=get(player.getUUID());return session!=null&&definition.equals(session.definition)&&session.menu==menu&&menu==player.containerMenu&&session.evidence.accept(evidence.source(),evidence.operationId());}
    public void publish(ServerPlayer player,Session session,TutorialNetwork.Snapshot snapshot,String signature,boolean force){requireThread(player);if(get(player.getUUID())!=session||session.menu!=player.containerMenu)return;if(!force&&signature.equals(session.signature))return;session.signature=signature;session.revision++;TutorialNetwork.send(player,snapshot.withRevision(session.revision));}
    public void publish(ServerPlayer player,Session session,TutorialStep step,boolean paused,boolean force){
        var snapshot=new TutorialNetwork.Snapshot(session.token,session.menu.containerId,session.revision,session.course,step.title(),step.text(),step.targets(),step.control(),step.acknowledge(),paused,step.warning(),step.complete(),step.sourceSlot(),step.ghostSlot(),step.ghost());
        publish(player,session,snapshot,step.signature(paused),force);
    }
    public void handle(ServerPlayer player,TutorialNetwork.Request request){
        requireThread(player);var session=get(player.getUUID());if(session==null)return;
        var definition=definitions.get(session.definition);
        if(definition==null||!definition.ready().test(player)||session.menu!=player.containerMenu||session.menu.containerId!=request.containerId()||!session.token.equals(request.token())||!session.menu.stillValid(player))return;
        if(session.revision!=request.revision()){session.signature="";definition.refresh().accept(player);return;}
        var progress=progress(player,session.definition,session.course);
        switch(request.action()) {
            case PAUSE -> progress.paused(true);
            case RESUME -> {progress.paused(false);session.automaticCollapsed=false;}
            case REPLAY -> {progress.paused(false);session.automaticCollapsed=false;session.reviewed=true;}
            case ACKNOWLEDGE -> {} // Only the definition can authorize a non-economic read/observe step.
        }
        definition.action().accept(player,request);
    }
    private void refresh(ServerPlayer player){
        var existing=get(player.getUUID());
        if(existing!=null) {
            var definition=definitions.get(existing.definition);
            if(existing.menu==player.containerMenu&&definition!=null&&definition.ready().test(player)){definition.refresh().accept(player);return;}
            close(player.getUUID());
        }
        for(var definition:definitions.values())if(definition.ready().test(player)){definition.refresh().accept(player);if(get(player.getUUID())!=null)break;}
    }
    @SubscribeEvent public static void opened(PlayerContainerEvent.Open event){if(event.getEntity() instanceof ServerPlayer p)INSTANCE.refresh(p);}
    @SubscribeEvent public static void closed(PlayerContainerEvent.Close event){INSTANCE.close(event.getEntity().getUUID());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){INSTANCE.close(event.getEntity().getUUID());}
    @SubscribeEvent public static void stop(ServerStoppedEvent event){INSTANCE.sessions.clear();}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){if(event.phase==TickEvent.Phase.END&&event.player instanceof ServerPlayer p&&p.tickCount%4==0)INSTANCE.refresh(p);}
    private static void requireThread(ServerPlayer p){if(!p.server.isSameThread())throw new IllegalStateException("Tutorials require server thread");}
}
