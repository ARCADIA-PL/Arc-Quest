package org.arcadia.arc_quest.inventory;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.tags.TagKey;

/** Synchronous server-authoritative joint allocation. A stack cannot satisfy two overlapping rows.
 * Quotes are informational; payment revalidates all resources and the original storage identities.
 * Callers own reversible business effects. This class does not claim crash atomicity.
 */
public final class CostPlan {
    public record Requirement(String id,int count,Predicate<ItemStack> matches) {
        public Requirement { if(id==null||id.isBlank()||count<1)throw new IllegalArgumentException("Invalid requirement");Objects.requireNonNull(matches); }
        public static Requirement item(String id,Item item,int count){return new Requirement(id,count,s->s.is(item));}
        public static Requirement tag(String id,TagKey<Item> tag,int count){return new Requirement(id,count,s->s.is(tag));}
        public static Requirement stack(String id,ItemStack template,int count){var copy=template.copy();return new Requirement(id,count,s->ItemStack.isSameItemSameTags(copy,s));}
    }
    public record Allocation(List<PlayerItemAccess.StackAmount> consumed,List<Integer> missing) {
        public Allocation { consumed=List.copyOf(consumed);missing=List.copyOf(missing); }
        public boolean affordable(){return missing.stream().allMatch(n->n==0);}
    }
    public static final class Quote {
        private final UUID owner; private final List<String> sources; private final CostPlan plan; private final Allocation allocation;
        private boolean used;
        private Quote(ServerPlayer p,CostPlan plan,Allocation allocation){owner=p.getUUID();sources=List.copyOf(PlayerItemAccess.externalIdentity(p));this.plan=plan;this.allocation=allocation;}
        public Allocation allocation(){return allocation;}
        public boolean affordable(){return allocation.affordable();}
        public PlayerItemAccess.Removal pay(ServerPlayer player) {
            requireThread(player);
            if(used||!owner.equals(player.getUUID())||!sources.equals(PlayerItemAccess.externalIdentity(player)))throw new IllegalStateException("Expired payment quote or storage selection changed");
            var current=plan.allocate(PlayerItemAccess.snapshot(player,false));if(!current.affordable())throw new IllegalStateException("Insufficient materials");
            used=true;var debits=new ArrayList<PlayerItemAccess.Removal>();
            try {
                for(var amount:current.consumed())debits.add(PlayerItemAccess.remove(player,s->ItemStack.isSameItemSameTags(s,amount.template()),Math.toIntExact(amount.amount()),false));
                return combined(debits);
            }catch(RuntimeException failure){try{combined(debits).rollback();}catch(RuntimeException rollback){failure.addSuppressed(rollback);}throw failure;}
        }
    }
    private final List<Requirement> requirements;
    public CostPlan(List<Requirement> requirements) {
        if(requirements.size()>128)throw new IllegalArgumentException("At most 128 requirements");
        this.requirements=List.copyOf(requirements);int total=0;var ids=new HashSet<String>();
        for(var r:requirements){total=Math.addExact(total,r.count());if(!ids.add(r.id()))throw new IllegalArgumentException("Duplicate requirement "+r.id());}
    }
    public Quote quote(ServerPlayer player){requireThread(player);return new Quote(player,this,allocate(PlayerItemAccess.snapshot(player,false)));}
    public Allocation allocate(List<PlayerItemAccess.StackAmount> available) {
        var supplies=new ArrayList<PlayerItemAccess.StackAmount>();
        for(var amount:available)if(amount.amount()>0&&requirements.stream().anyMatch(r->r.matches.test(amount.template()))) {
            int existing=-1;for(int i=0;i<supplies.size();i++)if(ItemStack.isSameItemSameTags(supplies.get(i).template(),amount.template())){existing=i;break;}
            if(existing<0)supplies.add(amount);else{var old=supplies.get(existing);supplies.set(existing,new PlayerItemAccess.StackAmount(old.template(),Math.min(Integer.MAX_VALUE,old.amount()+Math.min(Integer.MAX_VALUE,amount.amount()))));}
            if(supplies.size()>8192)throw new IllegalStateException("Too many distinct matching storage stacks");
        }
        int n=supplies.size(),m=requirements.size(),sink=n+m+1;
        var graph=new Flow(sink+1);var sourceEdges=new ArrayList<Flow.Edge>();var targetEdges=new ArrayList<Flow.Edge>();
        for(int i=0;i<n;i++){sourceEdges.add(graph.edge(0,i+1,Math.min(Integer.MAX_VALUE,supplies.get(i).amount())));for(int j=0;j<m;j++)if(requirements.get(j).matches.test(supplies.get(i).template()))graph.edge(i+1,n+j+1,Integer.MAX_VALUE);}
        for(int j=0;j<m;j++)targetEdges.add(graph.edge(n+j+1,sink,requirements.get(j).count));
        graph.run(0,sink);var consumed=new ArrayList<PlayerItemAccess.StackAmount>();var missing=new ArrayList<Integer>();
        for(int i=0;i<n;i++){long count=sourceEdges.get(i).initial-sourceEdges.get(i).remaining;if(count>0)consumed.add(new PlayerItemAccess.StackAmount(supplies.get(i).template(),count));}
        for(var edge:targetEdges)missing.add(Math.toIntExact(edge.remaining));
        return new Allocation(consumed,missing);
    }
    private static PlayerItemAccess.Removal combined(List<PlayerItemAccess.Removal> debits) {
        return new PlayerItemAccess.Removal(){boolean rolledBack;
            public List<PlayerItemAccess.StackAmount> removed(){return debits.stream().flatMap(d->d.removed().stream()).toList();}
            public void rollback(){if(rolledBack)return;RuntimeException failure=null;for(int i=debits.size()-1;i>=0;i--)try{debits.get(i).rollback();}catch(RuntimeException e){if(failure==null)failure=e;else failure.addSuppressed(e);}if(failure!=null)throw failure;rolledBack=true;}
        };
    }
    private static final class Flow {
        static final class Edge { final int to,reverse;final long initial;long remaining;Edge(int to,int reverse,long capacity){this.to=to;this.reverse=reverse;initial=remaining=capacity;} }
        final List<List<Edge>> graph=new ArrayList<>();Flow(int n){for(int i=0;i<n;i++)graph.add(new ArrayList<>());}
        Edge edge(int from,int to,long capacity){var e=new Edge(to,graph.get(to).size(),capacity);var back=new Edge(from,graph.get(from).size(),0);graph.get(from).add(e);graph.get(to).add(back);return e;}
        void run(int source,int sink){int n=graph.size();while(true){int[] prev=new int[n],indices=new int[n];Arrays.fill(prev,-1);var q=new ArrayDeque<Integer>();q.add(source);prev[source]=source;
            while(!q.isEmpty()&&prev[sink]<0){int at=q.removeFirst();for(int i=0;i<graph.get(at).size();i++){var e=graph.get(at).get(i);if(e.remaining>0&&prev[e.to]<0){prev[e.to]=at;indices[e.to]=i;q.add(e.to);}}}
            if(prev[sink]<0)return;long amount=Long.MAX_VALUE;for(int at=sink;at!=source;at=prev[at])amount=Math.min(amount,graph.get(prev[at]).get(indices[at]).remaining);
            for(int at=sink;at!=source;at=prev[at]){var e=graph.get(prev[at]).get(indices[at]);e.remaining-=amount;graph.get(at).get(e.reverse).remaining+=amount;}
        }}
    }
    private static void requireThread(ServerPlayer p){if(!p.server.isSameThread())throw new IllegalStateException("Payment requires server thread");}
}
