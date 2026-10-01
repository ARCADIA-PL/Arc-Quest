package org.arcadia.arc_quest.port;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.arcadia.arc_quest.Arc_Quest;
import org.arcadia.arc_quest.condition.ConditionBridge;
import org.arcadia.arc_quest.condition.ConditionEvaluator;
import org.arcadia.arc_quest.condition.ConditionSpec;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Real-registry regression coverage for the Minecraft 1.21 migration. */
@GameTestHolder(Arc_Quest.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeoForgePortGameTests {
    private NeoForgePortGameTests() {}

    @GameTest(template = "jei_empty", batch = "arcq_port", timeoutTicks = 100)
    public static void registryBackedVanillaPredicatesPreserveTheirMeaning(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "ArcQPredicate"), ClientInformation.createDefault());
        ConditionSpec spec = new ConditionSpec();
        spec.condition = "minecraft:entity_properties";
        spec.predicate = JsonParser.parseString("{\"type\":\"minecraft:player\"}").getAsJsonObject();
        helper.assertTrue(new ConditionEvaluator().evaluate(spec, player),
                "Registry-aware predicate must recognize a player");
        helper.assertTrue(ConditionBridge.toQuestCondition(spec).test(player, Set.of(), Set.of(), Map.of()),
                "Quest conditions must use the same registry-aware predicate");
        spec.predicate = JsonParser.parseString("{\"type\":\"minecraft:pig\"}").getAsJsonObject();
        helper.assertTrue(!new ConditionEvaluator().evaluate(spec, player),
                "Registry-aware predicate must still reject the wrong entity type");
        helper.assertTrue(!ConditionBridge.toQuestCondition(spec).test(player, Set.of(), Set.of(), Map.of()),
                "Quest conditions must not broaden the predicate");
        helper.succeed();
    }
}
