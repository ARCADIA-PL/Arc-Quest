package org.arcadia.arc_quest.inventory;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Explicit replay protocol for durable participants, not an arbitrary-lambda transaction.
 * The content journal owns durable persistence of the plan BEFORE replay. Participant tokens must
 * atomically couple their effect with their receipt. Unavailable participants retain the journal.
 * World blocks are content-specific participants; this built-in participant covers external storage.
 */
public final class RecoverableSettlement {
    public static final ResourceLocation STORAGE=ResourceLocation.parse("arc_quest:durable_storage");
    public static final RecoverableSettlement INSTANCE=new RecoverableSettlement();
    public enum State { ABSENT, COMMITTED }
    public interface Participant {
        State state(MinecraftServer server,UUID owner,String receipt,CompoundTag payload);
        void commit(MinecraftServer server,UUID owner,String receipt,CompoundTag payload);
        void cancel(MinecraftServer server,UUID owner,String receipt,CompoundTag payload);
        void finish(MinecraftServer server,UUID owner,String receipt,CompoundTag payload);
    }
    public record Part(ResourceLocation type,CompoundTag payload) {
        public Part { payload=payload.copy();if(payload.toString().length()>2_000_000)throw new IllegalArgumentException("Settlement payload too large"); }
        @Override public CompoundTag payload(){return payload.copy();}
    }
    private final Map<ResourceLocation,Participant> participants=new LinkedHashMap<>();
    private RecoverableSettlement(){participants.put(STORAGE,new Participant(){
        private PlayerItemAccess.DurableStorage storage(MinecraftServer s,CompoundTag p){return PlayerItemAccess.recoverStorage(s,p.getString("Identity"));}
        public State state(MinecraftServer s,UUID o,String r,CompoundTag p){return storage(s,p).committed(r,o,costs(p))?State.COMMITTED:State.ABSENT;}
        public void commit(MinecraftServer s,UUID o,String r,CompoundTag p){
            // Recovery views can inspect/refund an old network without a connected player,
            // but a NEW debit must use the owner's currently authorized view of that exact source.
            var payer=s.getPlayerList().getPlayer(o);
            if(payer==null)throw new IllegalStateException("The recorded payer must be online for a new storage debit");
            var identity=p.getString("Identity");
            var authorized=PlayerItemAccess.durableSources(payer).stream().filter(source->source.identity().equals(identity))
                    .findFirst().orElseThrow(()->new IllegalStateException("Recorded storage is not authorized for this payer: "+identity));
            authorized.commit(r,o,costs(p));
        }
        public void cancel(MinecraftServer s,UUID o,String r,CompoundTag p){storage(s,p).cancel(r,o,costs(p));}
        public void finish(MinecraftServer s,UUID o,String r,CompoundTag p){storage(s,p).finish(r,o,costs(p));}
    });}
    public void register(ResourceLocation id,Participant p){if(participants.putIfAbsent(id,Objects.requireNonNull(p))!=null)throw new IllegalArgumentException("Duplicate settlement participant "+id);}
    public static Part storage(String identity,List<PlayerItemAccess.StackAmount> costs){
        if(identity.isBlank()||costs.isEmpty())throw new IllegalArgumentException("Storage settlement needs identity and costs");
        var payload=new CompoundTag();payload.putString("Identity",identity);var list=new ListTag();for(var cost:costs){var row=new CompoundTag();row.put("Stack",cost.template().save(new CompoundTag()));row.putLong("Count",cost.amount());list.add(row);}payload.put("Costs",list);return new Part(STORAGE,payload);
    }
    public void commit(MinecraftServer server,UUID owner,String receipt,List<Part> parts){check(server,receipt,parts);for(var part:parts){var p=participant(part);if(p.state(server,owner,receipt,part.payload())!=State.COMMITTED)p.commit(server,owner,receipt,part.payload());if(p.state(server,owner,receipt,part.payload())!=State.COMMITTED)throw new IllegalStateException("Participant did not persist its receipt");}}
    public boolean committed(MinecraftServer s,UUID owner,String receipt,List<Part> parts){check(s,receipt,parts);return parts.stream().allMatch(part->participant(part).state(s,owner,receipt,part.payload())==State.COMMITTED);}
    public void cancel(MinecraftServer s,UUID owner,String receipt,List<Part> parts){check(s,receipt,parts);RuntimeException failure=null;for(int i=parts.size()-1;i>=0;i--)try{var part=parts.get(i);participant(part).cancel(s,owner,receipt,part.payload());}catch(RuntimeException e){if(failure==null)failure=e;else failure.addSuppressed(e);}if(failure!=null)throw failure;}
    public void finish(MinecraftServer s,UUID owner,String receipt,List<Part> parts){check(s,receipt,parts);for(var part:parts)participant(part).finish(s,owner,receipt,part.payload());}
    private Participant participant(Part p){return Objects.requireNonNull(participants.get(p.type()),"Settlement participant unavailable: "+p.type());}
    private static List<PlayerItemAccess.StackAmount> costs(CompoundTag payload){var result=new ArrayList<PlayerItemAccess.StackAmount>();for(var t:payload.getList("Costs",Tag.TAG_COMPOUND)){var row=(CompoundTag)t;var stack=net.minecraft.world.item.ItemStack.of(row.getCompound("Stack"));long count=row.getLong("Count");if(stack.isEmpty()||count<1)throw new IllegalArgumentException("Invalid durable cost");result.add(new PlayerItemAccess.StackAmount(stack,count));}return List.copyOf(result);}
    private static void check(MinecraftServer s,String receipt,List<Part> parts){if(!s.isSameThread())throw new IllegalStateException("Settlement requires server thread");if(receipt.isBlank()||parts.size()>128)throw new IllegalArgumentException("Invalid settlement plan");}
}
