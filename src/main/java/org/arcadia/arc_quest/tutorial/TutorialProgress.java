package org.arcadia.arc_quest.tutorial;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.arcadia.arc_quest.inventory.RewardClaimService;
/** Server-owned presentation preferences, separate from authoritative task/economic progress.
 * Each changed preference saves with vanilla playerdata; old addon flags can be imported once.
 */
public final class TutorialProgress {
    public static final String ROOT="ArcQuestTutorialProgress";
    private final ServerPlayer player;private final String key;
    TutorialProgress(ServerPlayer player,ResourceLocation definition,String course){
        if(!player.server.isSameThread())throw new IllegalStateException("Tutorial progress requires server thread");
        if(course.isBlank()||course.length()>100)throw new IllegalArgumentException("Invalid course");
        this.player=player;this.key=definition+"/"+course;
    }
    private CompoundTag root(){return player.getPersistentData().getCompound(ROOT);}
    private CompoundTag row(){return root().getCompound(key);}
    public boolean paused(){return row().getBoolean("Paused");}
    public boolean reviewRequested(){return row().getBoolean("Review");}
    public boolean read(){return row().getBoolean("Read");}
    public boolean imported(){return row().getBoolean("Imported");}
    public void paused(boolean value){set("Paused",value);}
    public void reviewRequested(boolean value){set("Review",value);}
    public void read(boolean value){set("Read",value);}
    public void importLegacy(boolean paused,boolean review,boolean read){if(imported())return;var row=row();row.putBoolean("Paused",paused);row.putBoolean("Review",review);row.putBoolean("Read",read);row.putBoolean("Imported",true);save(row);}
    private void set(String name,boolean value){var row=row();if(row.getBoolean(name)==value)return;row.putBoolean(name,value);save(row);}
    private void save(CompoundTag row){var root=root();if(!root.contains(key)&&root.size()>=4096)throw new IllegalStateException("Too many tutorial courses");root.put(key,row);player.getPersistentData().put(ROOT,root);RewardClaimService.save(player);}
}
