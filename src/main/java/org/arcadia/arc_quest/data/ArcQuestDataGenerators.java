package org.arcadia.arc_quest.data;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.arcadia.arc_quest.Arc_Quest;

/**
 * Arc Quest 数据生成器注册入口。
 * <p>
 * 运行 Gradle 任务 {@code runData} 后，会自动生成：
 * <ul>
 *   <li>{@code src/generated/resources/assets/arc_quest/lang/en_us.json}</li>
 *   <li>{@code src/generated/resources/assets/arc_quest/lang/zh_cn.json}</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Arc_Quest.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ArcQuestDataGenerators {

    private ArcQuestDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

        // ── 英文（en_us）──
        generator.addProvider(
                event.includeClient(),
                new ArcQuestENLangProvider(output)
        );

        // ── 中文（zh_cn）──
        generator.addProvider(
                event.includeClient(),
                new ArcQuestZHLangProvider(output)
        );

        Arc_Quest.LOGGER.info("[ArcQuest] DataGen providers registered (en_us, zh_cn).");
    }
}