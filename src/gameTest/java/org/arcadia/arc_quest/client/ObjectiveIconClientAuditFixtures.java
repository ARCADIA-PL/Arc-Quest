package org.arcadia.arc_quest.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.api.event.registry.ArcQuestRegistrationEvent;
import org.arcadia.arc_quest.quest.api.ObjectiveEntry;
import org.arcadia.arc_quest.quest.api.ICondition;
import org.arcadia.arc_quest.quest.api.icon.ObjectiveIcons;
import org.arcadia.arc_quest.quest.builder.ObjectiveBuilder;
import org.arcadia.arc_quest.quest.builder.PhaseBuilder;
import org.arcadia.arc_quest.quest.builder.QuestBuilder;

import java.util.List;

/** Definitions only; player mutations are guarded by the exact disposable world name in the gate. */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ObjectiveIconClientAuditFixtures {
    public static final String SINGLE = "arc_quest:objective_icon_audit_single";
    public static final String PARALLEL = "arc_quest:objective_icon_audit_parallel";
    public static final String FADE = "arc_quest:objective_icon_audit_fade";
    public static final ResourceLocation LOGS = ResourceLocation.parse("minecraft:logs");
    private ObjectiveIconClientAuditFixtures() {}

    @SubscribeEvent
    public static void quests(ArcQuestRegistrationEvent.Quest event) {
        if (!ObjectiveIconClientAudit.enabled()) return;
        PhaseBuilder single = PhaseBuilder.create("items").displayName("Objective icons / single phase");
        policyObjectives().forEach(sample -> single.objective(sample.objective()));
        event.register(QuestBuilder.create(SINGLE).displayName("Objective ICON / single phase")
                .canBeAutoTrack(false).phase(single).build());
        event.register(QuestBuilder.create(FADE).displayName("Objective ICON / native fade").description("")
                .canBeAutoTrack(false).phase(PhaseBuilder.create("fade").displayName("Fade regression").description("")
                        .objective(ObjectiveBuilder.collect(Items.DIAMOND, 9999).id("fade_item").display("Fade"))).build());
        event.register(QuestBuilder.create(PARALLEL).displayName("Objective ICON / parallel phases")
                .canBeAutoTrack(false)
                .phase(PhaseBuilder.create("items").displayName("Items and real tags")
                        .objective(ObjectiveBuilder.collectTag(LOGS, 9999).id("logs").display("Collect any logs: real tag candidates"))
                        .objective(ObjectiveBuilder.craft(Items.CRAFTING_TABLE, 9999).id("craft").display("Craft a crafting table"))
                        .objective(ObjectiveBuilder.collect(Items.DIAMOND, 9999).id("extra").display("Third row: internal scrolling")))
                .phase(PhaseBuilder.create("portraits").displayName("Two dimensional portraits")
                        .enterWhen(ICondition.always())
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 9999).id("zombie").display("Zombie / head portrait"))
                        .objective(ObjectiveBuilder.kill(EntityType.COW, 9999).id("cow").display("Cow / texture portrait")))
                .phase(PhaseBuilder.create("policies").displayName("Explicit image and no icon")
                        .enterWhen(ICondition.always())
                        .objective(ObjectiveBuilder.collect(Items.DIAMOND, 9999).id("texture")
                                .iconTexture("minecraft:textures/item/diamond.png").display("Explicit image"))
                        .objective(ObjectiveBuilder.kill(EntityType.ZOMBIE, 9999).id("none").noIcon().display("No icon / no empty column")))
                // initialPhaseIds are random alternatives; true parallel phases use auto-enter conditions.
                .setInitialPhase("items").build());
    }

    static List<Sample> policyObjectives() {
        return List.of(
                sample("Tag: minecraft:logs", ObjectiveBuilder.collectTag(LOGS, 9999).id("logs").display("Collect any logs (rotating real candidates)"), true),
                sample("CRAFT", ObjectiveBuilder.craft(Items.CRAFTING_TABLE, 9999).id("craft").display("Craft a crafting table"), true),
                sample("COLLECT", ObjectiveBuilder.collect(Items.DIAMOND, 9999).id("collect").display("Collect diamonds"), true),
                sample("OFFER", ObjectiveBuilder.offer(Items.EMERALD, 9999).id("offer").display("Offer emeralds"), true),
                sample("OFFER tag", ObjectiveBuilder.offerTag(LOGS, 9999).id("offer_tag").display("Offer any logs"), true),
                sample("DELIVER", ObjectiveBuilder.deliver(Items.DIAMOND, 9999, ResourceLocation.parse("audit:recipient"))
                        .id("deliver").display("Deliver diamonds"), true),
                sample("Explicit texture", ObjectiveBuilder.collect(Items.DIAMOND, 9999).id("texture")
                        .iconTexture("minecraft:textures/item/diamond.png").display("Explicit diamond image"), true),
                sample("Explicit NONE", ObjectiveBuilder.kill(EntityType.ZOMBIE, 9999).id("none").noIcon().display("No icon: text uses the full row"), false),
                sample("Missing texture", ObjectiveBuilder.collect(Items.DIAMOND, 9999).id("missing")
                        .iconTexture("arc_quest:textures/audit/not_present.png").display("Missing image: no placeholder"), false),
                sample("Hidden objective", ObjectiveBuilder.collect(Items.EMERALD, 9999).id("hidden").hidden().display("Hidden objective"), false),
                sample("Unknown provider", ObjectiveBuilder.collect(Items.DIAMOND, 9999).id("provider")
                        .icon(ObjectiveIcons.provider("audit:not_registered")).display("Unknown provider: no placeholder"), false));
    }

    static List<Sample> portraits() {
        return List.of(portrait("Skeleton", EntityType.SKELETON), portrait("Wither skeleton", EntityType.WITHER_SKELETON),
                portrait("Zombie", EntityType.ZOMBIE), portrait("Creeper", EntityType.CREEPER),
                portrait("Piglin", EntityType.PIGLIN), portrait("Ender dragon", EntityType.ENDER_DRAGON),
                portrait("Cow / texture crop", EntityType.COW), portrait("Pig / texture crop", EntityType.PIG));
    }
    private static Sample portrait(String label, EntityType<?> type) {
        return sample(label, ObjectiveBuilder.kill(type, 9999).id(type.getDescriptionId()).display(label), true);
    }
    private static Sample sample(String label, ObjectiveBuilder builder, boolean available) {
        return new Sample(label, builder.build(), available);
    }
    record Sample(String label, ObjectiveEntry objective, boolean expectedAvailable) {}
}
