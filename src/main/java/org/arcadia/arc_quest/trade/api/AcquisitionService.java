package org.arcadia.arc_quest.trade.api;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
import org.arcadia.arc_quest.trade.registry.TradeRegistry;

/** Read-only live shop projection. No truncated prices, implicit freebies or mutable trade handles. */
public final class AcquisitionService {
    public static final AcquisitionService INSTANCE=new AcquisitionService();
    public enum Status { AVAILABLE, MISSING_SHOP, NO_PRODUCT, TOO_MANY_PLANS }
    public enum Disclosure { VISIBLE_OFFERS, PUBLIC_CATALOGUE }
    private final Map<TradeShopDefinition,Map<net.minecraft.world.item.Item,List<TradeEntry>>> indexes=new WeakHashMap<>();
    private List<TradeEntry> candidates(TradeShopDefinition shop,ItemStack stack) {
        var index=indexes.computeIfAbsent(shop,key->{var map=new HashMap<net.minecraft.world.item.Item,List<TradeEntry>>();
            for(var entry:key.getAllEntries())for(var reward:entry.getRewards())if(reward instanceof ItemTradeOffer item)
                for(var product:item.getDisplayStacks()){var list=map.computeIfAbsent(product.getItem(),ignored->new ArrayList<>());if(!list.contains(entry))list.add(entry);}
            return map;});
        return index.getOrDefault(stack.getItem(),List.of());
    }
    private boolean visible(ServerPlayer player,TradeShopDefinition shop,TradeEntry entry,Disclosure disclosure){
        if(disclosure==Disclosure.PUBLIC_CATALOGUE)return true;
        var data=org.arcadia.arc_quest.questplayer.ArcQuestPlayerManager.getOrCreate(player);
        return shop.canOpen(player,data.getCompletedQuestLocations(),data.getAllFlags(),data.getAllVariables())
                && org.arcadia.arc_quest.trade.runtime.TradeEntryStateResolver.isVisible(player,data,entry);
    }
    public record Cost(List<ItemStack> alternatives,Component label,int required) {
        public Cost { alternatives=alternatives.stream().map(ItemStack::copy).toList();label=label.copy(); }
        @Override public List<ItemStack> alternatives(){return alternatives.stream().map(ItemStack::copy).toList();}
        @Override public Component label(){return label.copy();}
    }
    public record Plan(String entryId,List<Cost> costs) { public Plan { costs=List.copyOf(costs); } }
    public record Result(Status status,List<Plan> plans) { public Result { plans=List.copyOf(plans); } }
    private AcquisitionService(){}
    public Result plans(ServerPlayer player,String shopId,ItemStack product,int limit) {
        return plans(player,shopId,product,limit,Disclosure.VISIBLE_OFFERS);
    }
    /** PUBLIC_CATALOGUE is an explicit content-author disclosure policy, never selected by a client packet. */
    public Result plans(ServerPlayer player,String shopId,ItemStack product,int limit,Disclosure disclosure) {
        if(!player.server.isSameThread())throw new IllegalStateException("Acquisition queries require server thread");
        if(limit<1||limit>128)throw new IllegalArgumentException("Plan limit 1..128");
        var shop=TradeRegistry.get(shopId);if(shop==null)return new Result(Status.MISSING_SHOP,List.of());
        var found=new ArrayList<Plan>();
        for(var entry:candidates(shop,product)) {
            if(!visible(player,shop,entry,disclosure))continue;
            if(entry.getRewards().stream().noneMatch(r->rewards(r,product)))continue;
            var costs=entry.getCosts().stream().map(c->project(c,player)).toList();
            found.add(new Plan(entry.getEntryId(),costs));
            if(found.size()>limit)return new Result(Status.TOO_MANY_PLANS,List.of());
        }
        return new Result(found.isEmpty()?Status.NO_PRODUCT:Status.AVAILABLE,found);
    }
    public List<ItemStack> productsUsing(ServerPlayer player,String shopId,ItemStack material) {
        return productsUsing(player,shopId,material,Disclosure.VISIBLE_OFFERS);
    }
    public List<ItemStack> productsUsing(ServerPlayer player,String shopId,ItemStack material,Disclosure disclosure) {
        if(!player.server.isSameThread())throw new IllegalStateException("Acquisition queries require server thread");
        var shop=TradeRegistry.get(shopId);if(shop==null)return List.of();
        var result=new ArrayList<ItemStack>();
        for(var entry:shop.getAllEntries()) if(visible(player,shop,entry,disclosure)&&entry.getCosts().stream().anyMatch(c->c instanceof ItemTradeOffer item&&item.matchesStack(material)))
            for(var reward:entry.getRewards())if(reward instanceof ItemTradeOffer item)
                for(var stack:item.getDisplayStacks())if(result.stream().noneMatch(s->ItemStack.isSameItemSameTags(s,stack)))result.add(stack.copy());
        return List.copyOf(result);
    }
    public static Cost project(ITradeOffer offer,ServerPlayer player) {
        if(offer instanceof ItemTradeOffer item){var p=item.previewCost(player);return new Cost(p.alternatives(),p.label(),p.required());}
        return new Cost(List.of(),offer.describe(),0);
    }
    private static boolean rewards(ITradeOffer offer,ItemStack requested) {
        return offer instanceof ItemTradeOffer item&&item.getDisplayStacks().stream().anyMatch(s->s.is(requested.getItem())&&(!item.requiresExactNbt()||ItemStack.isSameItemSameTags(s,requested)));
    }
}
