package org.arcadia.arc_quest.quest.api.icon;
import com.google.gson.GsonBuilder;
import net.minecraft.world.item.*;
import org.arcadia.arc_quest.quest.spec.io.ObjectiveIconSpecAdapter;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class StackIconTest {
    @BeforeAll static void bootstrap(){MinecraftRegistryTestBootstrap.initialize();}
    @Test void nbtRoundTripsWithoutMutatingSubjectOrSharingStacks(){
        var source=new ItemStack(Items.DIAMOND_SWORD);source.getOrCreateTag().putInt("CustomModelData",17);
        var icon=ObjectiveIcons.stack(source);var gson=new GsonBuilder().registerTypeAdapter(ObjectiveIconSpec.class,new ObjectiveIconSpecAdapter()).create();
        var decoded=gson.fromJson(gson.toJson(icon,ObjectiveIconSpec.class),ObjectiveIconSpec.class);
        assertEquals(icon,decoded);var stack=decoded.resolveStack();assertEquals(17,stack.getTag().getInt("CustomModelData"));
        stack.getTag().putInt("CustomModelData",99);assertEquals(17,decoded.resolveStack().getTag().getInt("CustomModelData"));assertEquals(17,source.getTag().getInt("CustomModelData"));
    }
    @Test void invalidModesDepthAndSizeAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new ObjectiveIconSpec(ObjectiveIconSpec.Mode.NONE,null,null,null,null,"{x:1}"));
        assertThrows(IllegalArgumentException.class,()->ObjectiveIcons.stack(net.minecraft.resources.ResourceLocation.parse("minecraft:stone"),"{x:\""+"a".repeat(33000)+"\"}"));
        assertThrows(IllegalArgumentException.class,()->ObjectiveIcons.stack(net.minecraft.resources.ResourceLocation.parse("minecraft:stone"),"{x:".repeat(65)+"1"+"}".repeat(65)));
    }
}
