package org.arcadia.arc_quest.inventory;
import java.util.*;
import net.minecraft.world.item.*;
import org.arcadia.arc_quest.testsupport.MinecraftRegistryTestBootstrap;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class CostPlanTest {
    @BeforeAll static void bootstrap(){MinecraftRegistryTestBootstrap.initialize();}
    private PlayerItemAccess.StackAmount stock(ItemStack stack,long count){return new PlayerItemAccess.StackAmount(stack,count);}
    @Test void overlappingRowsCannotSpendTheSameItemsTwice(){
        var plan=new CostPlan(List.of(CostPlan.Requirement.item("one",Items.DIAMOND,4),CostPlan.Requirement.item("two",Items.DIAMOND,4)));
        var allocation=plan.allocate(List.of(stock(new ItemStack(Items.DIAMOND),7)));
        assertFalse(allocation.affordable());assertEquals(1,allocation.missing().stream().mapToInt(Integer::intValue).sum());
    }
    @Test void residualAllocationReservesScarceExactMatchForSpecificRow(){
        var tagged=new ItemStack(Items.DIAMOND);tagged.getOrCreateTag().putString("kind","specific");
        var plan=new CostPlan(List.of(CostPlan.Requirement.item("broad",Items.DIAMOND,1),CostPlan.Requirement.stack("exact",tagged,1)));
        assertTrue(plan.allocate(List.of(stock(tagged,1),stock(new ItemStack(Items.DIAMOND),1))).affordable());
        assertFalse(plan.allocate(List.of(stock(new ItemStack(Items.DIAMOND),2))).affordable());
    }
    @Test void sourcesWithSameTagsCombineAndAllocationIsDefensive(){
        var plan=new CostPlan(List.of(CostPlan.Requirement.item("total",Items.DIAMOND,5)));
        var result=plan.allocate(List.of(stock(new ItemStack(Items.DIAMOND),2),stock(new ItemStack(Items.DIAMOND),3)));
        assertTrue(result.affordable());assertEquals(1,result.consumed().size());assertEquals(5,result.consumed().get(0).amount());
        assertThrows(UnsupportedOperationException.class,()->result.missing().add(1));
    }
    @Test void duplicateAndOverflowRequirementsAreRejected(){
        var row=CostPlan.Requirement.item("same",Items.DIAMOND,1);
        assertThrows(IllegalArgumentException.class,()->new CostPlan(List.of(row,row)));
        assertThrows(ArithmeticException.class,()->new CostPlan(List.of(CostPlan.Requirement.item("a",Items.DIAMOND,Integer.MAX_VALUE),CostPlan.Requirement.item("b",Items.DIAMOND,1))));
    }
}
