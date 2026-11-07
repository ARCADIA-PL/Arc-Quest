package org.arcadia.arc_quest.tutorial;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
/** Immutable presentation step. Completion remains authoritative in the content definition.
 * sourceSlot/ghostSlot are visual guidance; no inventory mutation is performed by the renderer.
 */
public record TutorialStep(String id,String title,Component text,int[] targets,String control,
                           boolean acknowledge,boolean warning,boolean complete,
                           int sourceSlot,int ghostSlot,ItemStack ghost) {
    public TutorialStep {
        if(id==null||id.isBlank()||id.length()>256||title==null||control==null||control.length()>100||targets.length>18)throw new IllegalArgumentException("Invalid tutorial step");
        targets=targets.clone();text=text.copy();ghost=ghost.copy();
    }
    @Override public int[] targets(){return targets.clone();}
    @Override public Component text(){return text.copy();}
    @Override public ItemStack ghost(){return ghost.copy();}
    String signature(boolean paused){return title+id+Component.Serializer.toJson(text)+Arrays.toString(targets)+control+acknowledge+warning+complete+paused+sourceSlot+":"+ghostSlot+":"+ghost.save(new net.minecraft.nbt.CompoundTag());}
}
