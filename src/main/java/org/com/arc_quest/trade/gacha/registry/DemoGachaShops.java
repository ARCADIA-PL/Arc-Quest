package org.com.arc_quest.trade.gacha.registry;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.com.arc_quest.trade.gacha.api.GachaItem;
import org.com.arc_quest.trade.gacha.builder.GachaShopBuilder;
import org.com.arc_quest.trade.offer.ItemTradeOffer;

/**
 * 示范抽奖商店注册器。
 * <p>
 * 展示抽奖系统的全部功能：
 * - 多稀有度配置（LEGENDARY/EPIC/RARE）
 * - 动态权重修改器
 * - 保底机制
 * - 冷却/限购系统
 * - 自定义主题色和音效
 */
public class DemoGachaShops {

    /**
     * 注册所有示范抽奖商店。
     */
    public static void registerDemoShops() {
        registerWeaponGacha();
        registerMaterialGacha();
        registerSpecialEventGacha();
    }

    /**
     * 示例1：武器抽奖池
     * - 包含 LEGENDARY/EPIC/RARE 三个稀有度
     * - 设置保底机制（10次必出LEGENDARY）
     * - 自定义主题色（金色）
     * - cooldown: 1小时
     * - 限购: 50次/天
     */
    private static void registerWeaponGacha() {
        GachaShopBuilder.create("arc_quest:weapon_gacha", Component.literal("§6§l传说武器抽奖"))
            .description(Component.literal("抽取史诗级武器，10次必出LEGENDARY！"))
            
            // 抽奖成本：10个钻石
            .drawCost(new ItemTradeOffer(Items.DIAMOND, 10, true))
            
            // 限购：每天5次（6点刷新）
            .maxDraws(5)
            .cooldownGameTick(0)
            .drawResetByCooldown()

            // 默认主题色：金色
            .themeColor(0xFFD700)
            
            // 稀有度配置
            .rarityConfig(GachaItem.Rarity.LEGENDARY, 0xFFD700, null) // 金色
            .rarityConfig(GachaItem.Rarity.EPIC, 0xA020F0, null)      // 紫色
            .rarityConfig(GachaItem.Rarity.RARE, 0x4169E1, null)      // 蓝色
            
            // 保底配置：10次必出LEGENDARY，触发后重置
            .pitySystem(10, GachaItem.Rarity.LEGENDARY, true)
            
            // LEGENDARY 物品（权重5）
            .addItem("legendary_diamond_sword", 
                new ItemStack(Items.DIAMOND_SWORD), 
                5, 
                GachaItem.Rarity.LEGENDARY
            )
            
            .addItem("legendary_elytra",
                new ItemStack(Items.ELYTRA),
                3,
                GachaItem.Rarity.LEGENDARY
            )
            
            // EPIC 物品（权重20）
            .addItem("epic_iron_sword",
                new ItemStack(Items.IRON_SWORD),
                20,
                GachaItem.Rarity.EPIC
            )
            
            .addItem("epic_shield",
                new ItemStack(Items.SHIELD),
                20,
                GachaItem.Rarity.EPIC
            )
            
            // RARE 物品（权重75，随机数量 1-3）
            .addItem("rare_stone_sword",
                new ItemStack(Items.STONE_SWORD),
                75,
                GachaItem.Rarity.RARE,
                1, 3
            )
            
            .addItem("rare_arrow",
                new ItemStack(Items.ARROW),
                75,
                GachaItem.Rarity.RARE,
                8, 16
            )
            
            .buildAndRegister();
    }

    /**
     * 示例2：材料抽奖池
     * - 展示动态权重功能
     * - 完成任务后提升特定物品概率
     * - 无保底机制
     * - 无冷却限制
     */
    private static void registerMaterialGacha() {
        GachaShopBuilder.create("arc_quest:material_gacha", Component.literal("§b§l资源材料抽奖"))
            .description(Component.literal("抽取建筑材料，完成任务可提升稀有材料概率！"))
            
            // 抽奖成本：5个绿宝石
            .drawCost(new ItemTradeOffer(Items.EMERALD, 5, true))
            
            // 无限抽奖次数
            .maxDraws(-1)
            
            // 默认主题色：青色
            .themeColor(0x00FFFF)
            
            // 稀有度配置
            .rarityConfig(GachaItem.Rarity.LEGENDARY, 0xFFD700, null)
            .rarityConfig(GachaItem.Rarity.EPIC, 0x00FF00, null)
            .rarityConfig(GachaItem.Rarity.RARE, 0xAAAAAA, null)
            
            // 无保底配置
            
            // LEGENDARY 物品（基础权重2）
            .addItem("legendary_beacon",
                new ItemStack(Items.BEACON),
                2,
                GachaItem.Rarity.LEGENDARY
            )
            // TODO: 添加动态权重修改器（需要实现具体的 ICondition）
            // .addWeightModifier(new QuestCompletedCondition("arc_quest:mining_master"), 50)
            
            // EPIC 物品（权重15）
            .addItem("epic_diamond_block",
                new ItemStack(Items.DIAMOND_BLOCK),
                15,
                GachaItem.Rarity.EPIC
            )
            
            .addItem("epic_gold_block",
                new ItemStack(Items.GOLD_BLOCK),
                15,
                GachaItem.Rarity.EPIC
            )
            
            // RARE 物品（权重83，随机数量）
            .addItem("rare_iron_ingot",
                new ItemStack(Items.IRON_INGOT),
                83,
                GachaItem.Rarity.RARE,
                4, 8
            )
            
            .addItem("rare_coal",
                new ItemStack(Items.COAL),
                83,
                GachaItem.Rarity.RARE,
                8, 16
            )
            
            .buildAndRegister();
    }

    /**
     * 示例3：特殊活动抽奖池
     * - 展示高级功能组合
     * - 极低概率的隐藏物品
     * - 多重保底机制
     * - 严格的限购和冷却
     */
    private static void registerSpecialEventGacha() {
        GachaShopBuilder.create("arc_quest:special_event_gacha", Component.literal("§c§l限时活动抽奖"))
            .description(Component.literal("§4§k!!!§r §c神秘宝箱，内含传说宝物！§4§k!!!§r"))
            
            // 抽奖成本：1个下界之星
            .drawCost(new ItemTradeOffer(Items.NETHER_STAR, 1, true))
            
            // 冷却：每个游戏日只能抽1次
            .cooldownGameDay()
            
            // 限购：总共只能抽100次
            .maxDraws(100)
            .drawResetByCooldown()
            
            // 默认主题色：红色
            .themeColor(0xFF0000)
            
            // 稀有度配置
            .rarityConfig(GachaItem.Rarity.LEGENDARY, 0xFF0000, null)
            .rarityConfig(GachaItem.Rarity.EPIC, 0xFF6600, null)
            .rarityConfig(GachaItem.Rarity.RARE, 0xFFFF00, null)
            
            // 强力保底：20次必出LEGENDARY，触发后重置
            .pitySystem(20, GachaItem.Rarity.LEGENDARY, true)
            
            // 隐藏LEGENDARY物品（极低权重1）
            .addItem("hidden_dragon_egg",
                new ItemStack(Items.DRAGON_EGG),
                1,
                GachaItem.Rarity.LEGENDARY
            )
            
            // 普通LEGENDARY（权重5）
            .addItem("legendary_totem",
                new ItemStack(Items.TOTEM_OF_UNDYING),
                5,
                GachaItem.Rarity.LEGENDARY
            )
            
            // EPIC 物品（权重30）
            .addItem("epic_enchanted_golden_apple",
                new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
                30,
                GachaItem.Rarity.EPIC
            )
            
            // RARE 物品（权重64，随机数量 2-4）
            .addItem("rare_golden_apple",
                new ItemStack(Items.GOLDEN_APPLE),
                64,
                GachaItem.Rarity.RARE,
                2, 4
            )
            
            .buildAndRegister();
    }
}
