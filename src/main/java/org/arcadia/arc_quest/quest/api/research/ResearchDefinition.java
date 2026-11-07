package org.arcadia.arc_quest.quest.api.research;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.arcadia.arc_quest.dialogue.api.*;
/** A lightweight composition of native conditions/actions. Content retains chapter gates and callbacks. */
public record ResearchDefinition(ResourceLocation id,String completionFlag,List<Sample> samples,DialogueCondition availability,List<String> unlockFlags) {
    public record Sample(String item,int count,boolean consumed) {
        public Sample(String item,int count){this(item,count,true);}
        public Sample { ResourceLocation.parse(item);if(count<1)throw new IllegalArgumentException("Invalid sample count"); }
    }
    public ResearchDefinition(ResourceLocation id,String flag,List<Sample> samples,DialogueCondition availability){this(id,flag,samples,availability,List.of());}
    public ResearchDefinition { Objects.requireNonNull(id);Objects.requireNonNull(completionFlag);if(completionFlag.isBlank())throw new IllegalArgumentException("A research receipt flag is required");samples=List.copyOf(samples);Objects.requireNonNull(availability);unlockFlags=List.copyOf(unlockFlags); }
    public DialogueCondition hasSamples() {
        var consumed=new LinkedHashMap<String,Integer>();var shown=new LinkedHashMap<String,Integer>();
        for(var sample:samples)if(sample.consumed())consumed.merge(sample.item(),sample.count(),Math::addExact);else shown.merge(sample.item(),sample.count(),Math::max);
        shown.forEach((id,count)->consumed.merge(id,count,Math::max));
        var conditions=consumed.entrySet().stream().map(s->(DialogueCondition)new DialogueCondition.HoldItem(s.getKey(),s.getValue(),DialogueCondition.ItemSource.INVENTORY)).toList();
        return new DialogueCondition.All(conditions);
    }
    public DialogueCondition offerCondition() { return new DialogueCondition.All(List.of(availability,hasSamples(),new DialogueCondition.Not(new DialogueCondition.HasFlag(completionFlag)))); }
    /** Content may append native objective notifications to these one-time completion effects. */
    public List<DialogueAction> completionActions(){var result=new ArrayList<DialogueAction>();result.add(new DialogueAction.SetFlag(completionFlag));for(var flag:unlockFlags)result.add(new DialogueAction.SetFlag(flag));return List.copyOf(result);}
    public List<Sample> consumedSamples(){return samples.stream().filter(Sample::consumed).toList();}
}
