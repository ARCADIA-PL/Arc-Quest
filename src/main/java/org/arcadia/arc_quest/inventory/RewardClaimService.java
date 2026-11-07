package org.arcadia.arc_quest.inventory;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.arcadia.arc_quest.mixin.PlayerListSaveInvoker;
import org.arcadia.arc_quest.trade.runtime.InventoryDelivery;

/** Entitlements and delivery receipts share vanilla playerdata with inventory. No global auto-claim.
 * Lifetime/run-scoped keys are explicit. Full inventory keeps pending items; ESC has no effect.
 * Callers may opt into automatic grants by calling claim after entitle, e.g. earned quest rewards.
 */
public final class RewardClaimService {
    public static final RewardClaimService INSTANCE=new RewardClaimService();
    public static final String ROOT="ArcQuestRewardClaims";
    public enum Scope { LIFETIME, RUN }
    public record Key(ResourceLocation namespace,String receipt,Scope scope,String runId) {
        public Key { Objects.requireNonNull(namespace);Objects.requireNonNull(scope);if(receipt==null||receipt.isBlank()||receipt.length()>1024||scope==Scope.RUN&&(runId==null||runId.isBlank()))throw new IllegalArgumentException("Invalid claim key");runId=scope==Scope.LIFETIME?"":runId; }
        public static Key lifetime(String namespace,String receipt){return new Key(ResourceLocation.parse(namespace),receipt,Scope.LIFETIME,"");}
        public String storageKey(){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((namespace+"\n"+receipt+"\n"+scope+"\n"+runId).getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    }
    public enum State { ABSENT, PENDING, DELIVERED }
    private RewardClaimService(){}
    public State state(ServerPlayer p,Key key){requireThread(p);var row=root(p).getCompound(key.storageKey());return row.isEmpty()?State.ABSENT:row.getList("Items",Tag.TAG_COMPOUND).isEmpty()?State.DELIVERED:State.PENDING;}
    public State entitle(ServerPlayer player,Key key,List<ItemStack> stacks) {
        requireThread(player);var root=root(player);String id=key.storageKey();if(root.contains(id))return state(player,key);
        if(root.size()>=8192||stacks.size()>256)throw new IllegalStateException("Claim record capacity exceeded");
        var items=new ListTag();for(var stack:stacks){if(stack.isEmpty())continue;if(stack.save(new CompoundTag()).toString().length()>65536)throw new IllegalArgumentException("Claim stack NBT too large");items.add(stack.copy().save(new CompoundTag()));}
        var row=new CompoundTag();row.putString("Namespace",key.namespace.toString());row.putString("Receipt",key.receipt);row.putString("Scope",key.scope.name());row.putString("RunId",key.runId);row.put("Items",items);
        root.put(id,row);player.getPersistentData().put(ROOT,root);save(player);return items.isEmpty()?State.DELIVERED:State.PENDING;
    }
    public boolean claim(ServerPlayer player,Key key) {requireThread(player);return claimRows(player,row->row.getString("Namespace").equals(key.namespace.toString())&&row.getString("Receipt").equals(key.receipt)&&row.getString("Scope").equals(key.scope.name())&&row.getString("RunId").equals(key.runId));}
    public boolean claimAll(ServerPlayer p,ResourceLocation namespace){requireThread(p);return claimRows(p,row->namespace.toString().equals(row.getString("Namespace")));}
    public boolean hasPending(ServerPlayer p,ResourceLocation namespace){requireThread(p);var root=root(p);return root.getAllKeys().stream().map(root::getCompound).anyMatch(row->namespace.toString().equals(row.getString("Namespace"))&&!row.getList("Items",Tag.TAG_COMPOUND).isEmpty());}
    /** Detached server-side inspection, for shortage previews and legacy import coordination. */
    public List<ItemStack> pendingItems(ServerPlayer player,ResourceLocation namespace) {
        requireThread(player);var result=new ArrayList<ItemStack>();var root=root(player);
        for(String id:root.getAllKeys()) {
            var row=root.getCompound(id);if(!namespace.toString().equals(row.getString("Namespace")))continue;
            for(var tag:row.getList("Items",Tag.TAG_COMPOUND)) {
                var encoded=(CompoundTag)tag;var item=ResourceLocation.tryParse(encoded.getString("id"));
                if(item==null||!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item)||ItemStack.of(encoded).isEmpty())
                    throw new IllegalStateException("Pending claim item unavailable; retain its record");
                result.add(ItemStack.of(encoded));
            }
        }
        return List.copyOf(result);
    }
    private boolean claimRows(ServerPlayer player,java.util.function.Predicate<CompoundTag> selected) {
        if(!player.isAlive()||player.isRemoved())return false;
        var root=root(player);boolean changed=false;
        // Validate the entire selected batch before the first delivery. A removed registry item
        // must never throw after earlier stacks were delivered but their pending row stayed intact.
        for(String id:root.getAllKeys()) {
            var row=root.getCompound(id);if(!selected.test(row))continue;
            for(var tag:row.getList("Items",Tag.TAG_COMPOUND)) {
                var encoded=(CompoundTag)tag;
                var item=ResourceLocation.tryParse(encoded.getString("id"));
                if(item==null||!net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item)||ItemStack.of(encoded).isEmpty())
                    throw new IllegalStateException("Pending claim item unavailable; retain its record");
            }
        }
        for(String id:root.getAllKeys()) {
            var row=root.getCompound(id);if(!selected.test(row))continue;
            var remaining=new ListTag();for(var tag:row.getList("Items",Tag.TAG_COMPOUND)){
                var stack=ItemStack.of((CompoundTag)tag);if(stack.isEmpty())throw new IllegalStateException("Pending claim item unavailable; retain its record");
                try{InventoryDelivery.deliver(player,stack);changed=true;}catch(IllegalStateException full){remaining.add(tag.copy());}
            }
            row.put("Items",remaining);
        }
        if(changed){player.getPersistentData().put(ROOT,root);save(player);player.containerMenu.broadcastChanges();}
        return changed;
    }
    private static CompoundTag root(ServerPlayer p){return p.getPersistentData().getCompound(ROOT);}
    public static void save(ServerPlayer player){((PlayerListSaveInvoker)player.server.getPlayerList()).arcq$savePlayer(player);}
    private static void requireThread(ServerPlayer p){if(!p.server.isSameThread())throw new IllegalStateException("Claims require server thread");}
}
