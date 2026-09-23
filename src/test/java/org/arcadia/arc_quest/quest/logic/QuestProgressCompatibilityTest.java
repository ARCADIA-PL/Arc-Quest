package org.arcadia.arc_quest.quest.logic;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 检查实际字节码契约，覆盖附属已编译调用和 Mixin 的方法内注入位置。 */
class QuestProgressCompatibilityTest {
    private static final String OWNER = "org/arcadia/arc_quest/quest/logic/QuestProgressHandler";
    private static final String PLAYER = "Lnet/minecraft/server/level/ServerPlayer;";
    private static final String STRING = "Ljava/lang/String;";
    private static final String DATA = "Lorg/arcadia/arc_quest/questplayer/ArcQuestPlayer;";
    private static final String RUNTIME = "Lorg/arcadia/arc_quest/quest/data/QuestRuntimeData;";
    private static final String DEFINITION = "Lorg/arcadia/arc_quest/quest/api/QuestDefinition;";
    private static final String PHASE = "Lorg/arcadia/arc_quest/quest/api/PhaseDefinition;";
    private static final String OBJECTIVE = "Lorg/arcadia/arc_quest/quest/api/ObjectiveEntry;";
    private static final String CODE = "Lorg/arcadia/arc_quest/quest/network/QuestRejectCodeDictionary$Code;";

    @Test
    void retainsEveryPublicStaticDescriptorFromTheOriginalFacade() throws IOException {
        Set<String> actual = new HashSet<>();
        readFacade(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ((access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC))
                        == (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)) {
                    actual.add(name + descriptor);
                }
                return null;
            }
        });
        // 来自重构前 javap -public -s 的 26 个入口；新方法可增加，旧描述符不可消失。
        Set<String> expected = Set.of(
                method("acceptQuest", "Z", PLAYER, STRING),
                method("acceptQuestWithCode", CODE, PLAYER, STRING),
                method("incrementObjective", "V", PLAYER, STRING, STRING, "I", "I"),
                method("incrementCollectionEntry", "V", PLAYER, STRING, STRING, "I"),
                method("revealCollectionEntry", "V", PLAYER, STRING, STRING),
                method("refreshCollectionVisibility", "V", PLAYER, STRING),
                method("discoverCollectionEntry", "V", PLAYER, STRING, STRING),
                method("addCollectionUniqueKey", "V", PLAYER, STRING, STRING, STRING),
                method("advanceToPhase", "V", PLAYER, DATA, RUNTIME, DEFINITION, STRING),
                method("confirmManualPhaseAdvance", CODE, PLAYER, STRING, STRING),
                method("handlePlayerChoice", "Z", PLAYER, STRING, STRING, "I"),
                method("handlePlayerChoice", "Z", PLAYER, STRING, "I"),
                method("handlePlayerChoiceWithCode", CODE, PLAYER, STRING, STRING, "I"),
                method("failQuest", "V", PLAYER, STRING),
                method("abandonQuest", "Z", PLAYER, STRING),
                method("abandonQuestWithCode", CODE, PLAYER, STRING),
                method("abandonPhase", CODE, PLAYER, STRING, STRING),
                method("forceComplete", "V", PLAYER, STRING),
                method("forceCompleteResult", CODE, PLAYER, STRING),
                method("forceCompletePhase", "V", PLAYER, STRING, STRING),
                method("forceCompletePhaseResult", CODE, PLAYER, STRING, STRING),
                method("syncToClient", "V", PLAYER, STRING),
                method("rebuildTrackingIndex", "V", PLAYER, DATA),
                method("registerPhaseObjectives", "V", PLAYER, DEFINITION, PHASE),
                method("resolveRequiredCount", "I", PLAYER, OBJECTIVE, DATA),
                method("objectiveKeyTargets", "Ljava/util/List;", OBJECTIVE));
        Set<String> missing = new HashSet<>(expected);
        missing.removeAll(actual);
        assertTrue(missing.isEmpty(), () -> "Removed or changed public descriptors: " + missing);
    }

    @Test
    void retainsTheThirdAcceptanceRuleTargetedByTheAddonMixin() throws IOException {
        List<String> ruleFailures = new ArrayList<>();
        readFacade(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!name.equals("acceptQuestWithCode")) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    private String failure;

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        if (opcode == Opcodes.GETSTATIC && descriptor.equals(CODE)) failure = name;
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        if (owner.equals("org/arcadia/arc_quest/core/execution/CoreRule")
                                && name.equals("require")
                                && descriptor.equals("(Ljava/util/function/Predicate;Ljava/lang/Object;)"
                                + "Lorg/arcadia/arc_quest/core/execution/CoreRule;")) {
                            ruleFailures.add(failure);
                        }
                    }
                };
            }
        });
        assertEquals(List.of("ALREADY_ACTIVE", "ALREADY_COMPLETED_NOT_REPEATABLE",
                "UNLOCK_CONDITION_NOT_MET", "NO_INITIAL_PHASE"), ruleFailures);
    }

    @Test
    void retainsTheRewardRedirectAndRoutesServiceRewardsThroughIt() throws IOException {
        List<String> grantCalls = new ArrayList<>();
        List<String> rewardCallbacks = new ArrayList<>();
        readFacade(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (name.equals("grantRewards")) {
                    assertEquals(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                            access & (Opcodes.ACC_PRIVATE | Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));
                    assertEquals("(" + PLAYER + "Ljava/util/List;" + STRING + ")V", descriptor);
                }
                String enclosingMethod = name;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name,
                                                String descriptor, boolean isInterface) {
                        if (enclosingMethod.equals("grantRewards")
                                && owner.equals("org/arcadia/arc_quest/quest/api/IReward")
                                && name.equals("grant")) {
                            grantCalls.add(descriptor);
                        }
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String name, String descriptor,
                                                       Handle bootstrap, Object... arguments) {
                        if (!enclosingMethod.equals("<clinit>")) return;
                        for (Object argument : arguments) {
                            if (argument instanceof Handle handle && handle.getOwner().equals(OWNER)) {
                                rewardCallbacks.add(handle.getName());
                            }
                        }
                    }
                };
            }
        });
        assertEquals(List.of("(" + PLAYER + ")V"), grantCalls);
        assertTrue(rewardCallbacks.contains("grantRewards"));
    }

    private static String method(String name, String result, String... parameters) {
        return name + "(" + String.join("", parameters) + ")" + result;
    }

    private static void readFacade(ClassVisitor visitor) throws IOException {
        try (InputStream input = QuestProgressCompatibilityTest.class.getResourceAsStream("/" + OWNER + ".class")) {
            if (input == null) throw new IOException("Missing facade bytecode: " + OWNER);
            new ClassReader(input).accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }
}
