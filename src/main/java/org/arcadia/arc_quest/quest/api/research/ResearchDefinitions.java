package org.arcadia.arc_quest.quest.api.research;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
/** Common-side author registry. Register during common setup, then query immutable definitions.
 * Native conditions, submission transactions and shop projections compose the definition; this
 * registry never runs callbacks on reload or grants progress merely from observing a sample.
 */
public final class ResearchDefinitions {
    public static final ResearchDefinitions INSTANCE=new ResearchDefinitions();
    private final Map<ResourceLocation,ResearchDefinition> definitions=new LinkedHashMap<>();
    private ResearchDefinitions(){}
    public void register(ResearchDefinition definition){if(definitions.putIfAbsent(definition.id(),definition)!=null)throw new IllegalArgumentException("Duplicate research "+definition.id());}
    public ResearchDefinition get(ResourceLocation id){return definitions.get(id);}
    public List<ResearchDefinition> using(ResourceLocation item){return definitions.values().stream().filter(d->d.samples().stream().anyMatch(s->s.item().equals(item.toString()))).toList();}
}
