package org.arcadia.arc_quest.quest.api.collection;

import java.util.*;
import java.util.function.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.quest.api.*;
import org.arcadia.arc_quest.quest.logic.*;
import org.arcadia.arc_quest.quest.tracking.QuestEventManager;
import org.arcadia.arc_quest.questplayer.*;

/** Server-thread observation and information disclosure, never a research/payment command. */
@Mod.EventBusSubscriber(modid="arc_quest")
public final class CollectionObservations {
    public static final CollectionObservations INSTANCE=new CollectionObservations();
    public enum ResetPolicy { PRESERVE, REVISIT }
    public record Disclosure(ResourceLocation id,ResourceLocation dimension,String visitFlag,
            List<ResourceLocation> legacyAdvancements,List<ResourceLocation> historicalVisits,
            List<ResourceLocation> possessionEvidence,List<ResourceLocation> entries,List<String> historicalFlags,
            ResetPolicy resetPolicy,Predicate<ServerPlayer> eligible) {
        public Disclosure(ResourceLocation id,ResourceLocation dimension,String visitFlag,
                List<ResourceLocation> legacyAdvancements,List<ResourceLocation> historicalVisits,
                List<ResourceLocation> possessionEvidence,List<ResourceLocation> entries,
                ResetPolicy resetPolicy,Predicate<ServerPlayer> eligible) {
            this(id,dimension,visitFlag,legacyAdvancements,historicalVisits,possessionEvidence,entries,List.of(),resetPolicy,eligible);
        }
        public Disclosure { legacyAdvancements=List.copyOf(legacyAdvancements); historicalVisits=List.copyOf(historicalVisits);
            possessionEvidence=List.copyOf(possessionEvidence); entries=List.copyOf(entries); historicalFlags=List.copyOf(historicalFlags); Objects.requireNonNull(eligible); }
    }
    public record Region(ResourceLocation dimension, Vec3 center,double radius,double minY,double maxY) {
        public Region { if(radius<=0||!Double.isFinite(radius)||!Double.isFinite(center.lengthSqr())||!Double.isFinite(minY)||!Double.isFinite(maxY)||minY>maxY) throw new IllegalArgumentException("Invalid region"); }
        public boolean contains(Vec3 point) { return point.y>=minY&&point.y<=maxY&&Math.abs(point.x-center.x)<=radius&&Math.abs(point.z-center.z)<=radius; }
    }
    @FunctionalInterface public interface Sensor { boolean observed(ServerPlayer player,CollectionSubjectSpec subject); }
    private record Channel(Function<ServerPlayer,List<ItemStack>> snapshots,Predicate<ServerPlayer> eligible) {}
    private final Map<QuestDefinition,Map<String,List<CollectionEntryDefinition>>> indexes=new WeakHashMap<>();
    private List<CollectionEntryDefinition> candidates(QuestDefinition definition,String dimension){
        var index=indexes.computeIfAbsent(definition,d->{var result=new HashMap<String,List<CollectionEntryDefinition>>();
            for(var entry:d.getCollectionConfig().getEntries()){var subject=entry.getPresentation().subject();if(subject==null)continue;
                String dims=subject.type().getPath().equals("advancement")?"":subject.parameters().getOrDefault("dimension","");
                if(dims.isBlank())result.computeIfAbsent("*",ignored->new ArrayList<>()).add(entry);
                else for(String dim:dims.split(","))result.computeIfAbsent(dim.trim(),ignored->new ArrayList<>()).add(entry);
            }return result;});
        var result=new ArrayList<>(index.getOrDefault("*",List.of()));result.addAll(index.getOrDefault(dimension,List.of()));return result;
    }
    private final Map<ResourceLocation,Sensor> sensors=new LinkedHashMap<>();
    private final Map<ResourceLocation,Disclosure> policies=new LinkedHashMap<>();
    private final Map<ResourceLocation,Channel> channels=new LinkedHashMap<>();
    private CollectionObservations() {
        sensors.put(ResourceLocation.parse("arc_quest:dimension"),(p,s)->p.level().dimension().location().equals(s.id()));
        sensors.put(ResourceLocation.parse("arc_quest:structure"),(p,s)->insideLoadedPiece(p,s.id()));
        sensors.put(ResourceLocation.parse("arc_quest:structure_tag"),(p,s)->p.serverLevel().registryAccess().registryOrThrow(Registries.STRUCTURE)
            .getTag(net.minecraft.tags.TagKey.create(Registries.STRUCTURE,s.id())).map(set->set.stream().anyMatch(h->insideLoadedPiece(p,h.unwrapKey().orElseThrow().location()))).orElse(false));
        sensors.put(ResourceLocation.parse("arc_quest:advancement"),(p,s)->advancement(p,s.id()));
        sensors.put(ResourceLocation.parse("arc_quest:region"),(p,s)->new Region(s.id(),new Vec3(number(s,"x"),number(s,"y"),number(s,"z")),number(s,"radius"),number(s,"minY"),number(s,"maxY")).contains(p.position())&&p.level().dimension().location().equals(s.id()));
    }
    public void registerSensor(ResourceLocation id,Sensor sensor) { if(sensors.putIfAbsent(id,Objects.requireNonNull(sensor))!=null)throw new IllegalArgumentException("Duplicate sensor "+id); }
    public void alias(ResourceLocation id,ResourceLocation builtIn) { registerSensor(id,Objects.requireNonNull(sensors.get(builtIn),"Unknown sensor")); }
    public void registerDisclosure(Disclosure policy) { if(policies.putIfAbsent(policy.id(),policy)!=null)throw new IllegalArgumentException("Duplicate disclosure "+policy.id()); }
    public void registerItemChannel(ResourceLocation id,Function<ServerPlayer,List<ItemStack>> source,Predicate<ServerPlayer> eligible) {
        if(channels.putIfAbsent(id,new Channel(source,eligible))!=null)throw new IllegalArgumentException("Duplicate observation channel "+id);
    }
    public void observeItems(ServerPlayer player,ResourceLocation channel) {
        requireThread(player); var source=Objects.requireNonNull(channels.get(channel));
        if(source.eligible.test(player))CollectionRecordService.observeStackSnapshot(player,source.snapshots.apply(player).stream().map(ItemStack::copy).toList(),channel.toString());
    }
    public void scan(ServerPlayer player,String questId,ObjectiveType notification) {
        requireThread(player); var data=ArcQuestPlayerManager.getOrCreate(player);var run=data.getActiveQuest(questId);
        if(run==null)return;
        var definition=CollectionRunAccess.resolve(player.server,run);
        if(definition==null||definition.getCollectionConfig()==null)return;
        var visited=new LinkedHashSet<ResourceLocation>();
        for(var entry:candidates(definition,player.level().dimension().location().toString())) {
            if(data.getCollectionRecords().isDiscovered(entry.getEntryId()))continue;
            var subject=entry.getPresentation().subject();if(subject==null)continue;
            var sensor=sensors.get(subject.type());if(sensor==null)continue;
            // Historical advancement evidence is not restricted to the current dimension.
            String dimensions=subject.parameters().getOrDefault("dimension","");
            if(!subject.type().getPath().equals("advancement")&&!dimensions.isBlank()&&Arrays.stream(dimensions.split(",")).map(String::trim).noneMatch(player.level().dimension().location().toString()::equals))continue;
            if(sensor.observed(player,subject))visited.add(subject.id());
        }
        for(var target:visited)QuestEventManager.notifyObjective(player,notification,target,1);
    }
    public void reconcile(ServerPlayer player,Collection<ResourceLocation> ids) { apply(player,ids,null,false); }
    /** Called after trusted collection observation, including external inventory snapshots. */
    public void reconcilePossessions(ServerPlayer player,Set<ResourceLocation> freshEvidence) {
        if(policies.isEmpty())return;
        apply(player,List.copyOf(policies.keySet()),null,false,Set.copyOf(freshEvidence),false);
    }
    public void apply(ServerPlayer player,Collection<ResourceLocation> ids,ResourceLocation visited,boolean freshVisit) {
        apply(player,ids,visited,freshVisit,Set.of(),true);
    }
    private void apply(ServerPlayer player,Collection<ResourceLocation> ids,ResourceLocation visited,boolean freshVisit,Set<ResourceLocation> freshEvidence,boolean observeCurrentDimension) {
        requireThread(player);var data=ArcQuestPlayerManager.getOrCreate(player);boolean flagsChanged=false;
        for(var id:ids) {
            var policy=Objects.requireNonNull(policies.get(id),"Unknown disclosure "+id);if(!policy.eligible.test(player))continue;
            var records=data.getCollectionRecords();
            boolean dimensionEvidence=(visited!=null?policy.dimension.equals(visited):observeCurrentDimension&&policy.dimension.equals(player.level().dimension().location()))||data.hasFlag(policy.visitFlag)
                ||policy.legacyAdvancements.stream().anyMatch(a->advancement(player,a))||policy.historicalVisits.stream().anyMatch(records::isDiscovered);
            boolean possession=policy.possessionEvidence.stream().anyMatch(records::isDiscovered);
            boolean historical=policy.historicalFlags.stream().anyMatch(data::hasFlag);
            if(!dimensionEvidence&&!possession&&!historical)continue;
            if(dimensionEvidence&&!data.hasFlag(policy.visitFlag)){data.setFlag(policy.visitFlag);flagsChanged=true;}
            boolean resetAllowed=freshVisit&&policy.resetPolicy==ResetPolicy.REVISIT&&policy.dimension.equals(visited)
                ||policy.possessionEvidence.stream().anyMatch(e->freshEvidence.contains(e)&&records.isDiscovered(e));
            CollectionRecordService.revealEntries(player,policy.entries.stream().filter(e->resetAllowed||!records.isEntryReset(e)).toList());
        }
        if(flagsChanged){ArcQuestPlayerManager.persistSnapshotSynchronously(player,data);org.arcadia.arc_quest.quest.network.QuestSyncCoordinator.syncFlagsVarsAndPush(player,data);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if(e.getEntity() instanceof ServerPlayer p){INSTANCE.apply(p,List.copyOf(INSTANCE.policies.keySet()),e.getFrom().location(),false);INSTANCE.apply(p,List.copyOf(INSTANCE.policies.keySet()),e.getTo().location(),true);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(e.getEntity() instanceof ServerPlayer p)INSTANCE.reconcile(p,List.copyOf(INSTANCE.policies.keySet()));
    }
    public static boolean insideLoadedPiece(ServerPlayer player,ResourceLocation id) {
        var level=player.serverLevel();var structure=level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(id);if(structure==null)return false;
        var chunk=level.getChunkSource().getChunkNow(player.chunkPosition().x,player.chunkPosition().z);if(chunk==null)return false;
        var starts=new ArrayList<net.minecraft.world.level.levelgen.structure.StructureStart>();
        var own=chunk.getAllStarts().get(structure);if(own!=null)starts.add(own);
        for(long packed:chunk.getReferencesForStructure(structure)) {
            var pos=new net.minecraft.world.level.ChunkPos(packed);var loaded=level.getChunkSource().getChunkNow(pos.x,pos.z);
            if(loaded!=null){var start=loaded.getAllStarts().get(structure);if(start!=null)starts.add(start);}
        }
        return starts.stream().filter(net.minecraft.world.level.levelgen.structure.StructureStart::isValid).flatMap(s->s.getPieces().stream()).anyMatch(p->p.getBoundingBox().isInside(player.blockPosition()));
    }
    private static boolean advancement(ServerPlayer p,ResourceLocation id){var a=p.server.getAdvancements().getAdvancement(id);return a!=null&&p.getAdvancements().getOrStartProgress(a).isDone();}
    private static double number(CollectionSubjectSpec s,String key){return Double.parseDouble(s.parameters().get(key));}
    private static void requireThread(ServerPlayer player){if(!player.server.isSameThread())throw new IllegalStateException("Observations require server thread");}
}
