import os

fp = 'd:/Arc Quest/src/main/java/org/arcadia/arc_quest/dialogue/capability/EntityDialogueExtensionHandler.java'
with open(fp, 'r', encoding='utf-8') as f:
    content = f.read()
orig = content

# 1. Fix imports
content = content.replace(
    'import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;\n', '')
content = content.replace(
    'import net.minecraftforge.event.AttachCapabilitiesEvent;\n', '')
content = content.replace(
    'import org.arcadia.arc_quest.dialogue.capability.DialogueNpcPatch;\n', '')
content = content.replace(
    'import org.arcadia.arc_quest.quest.capability.DialogueNpcPatchProvider;\n', '')

# 2. Delete onAttachCapabilities method (lines ~53-62)
old_attach = '''    /**
     * 附着 Capability 到实体
     */
    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity) {
            if (!event.getObject().getCapability(DialogueNpcPatch.CAPABILITY).isPresent()) {
                event.addCapability(CAP_ID, new DialogueNpcPatchProvider(event.getObject()));
            }
        }
    }

'''
content = content.replace(old_attach, '')

# 3. Delete onRegisterCapabilities from ModBusEvents
old_reg = '''        @SubscribeEvent
        public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
            event.register(DialogueNpcPatch.class);
        }

'''
content = content.replace(old_reg, '')

# 4. Rewrite onLivingTick
old_tick = '''    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        LivingEntity livingEntity = event.getEntity();
        livingEntity.getCapability(DialogueNpcPatch.CAPABILITY).ifPresent(patch -> {
            if (!patch.isConversing()) return;

            var player = patch.getConversingPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            // 检查距离
            checkDistance(patch, livingEntity, serverPlayer);
            if (!patch.isConversing()) return;

            // 控制 NPC 行为（注视/停止移动）
            controlNpcBehavior(patch, livingEntity, serverPlayer);

            // 调用扩展的 onTalkingTick
            callExtensionOnTick(livingEntity, serverPlayer);

            // 调用 patch.tick()
            patch.tick();
        });
    }'''

new_tick = '''    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        LivingEntity livingEntity = event.getEntity();
        DialogueNpcStateManager.State state = DialogueNpcStateManager.get(livingEntity);
        if (state == null || state.conversingPlayer() == null) return;
        if (!state.conversingPlayer().isAlive()) {
            DialogueNpcStateManager.clear(livingEntity);
            return;
        }

        ServerPlayer serverPlayer = (ServerPlayer) state.conversingPlayer();

        checkDistance(livingEntity, serverPlayer);
        state = DialogueNpcStateManager.get(livingEntity);
        if (state == null || state.conversingPlayer() == null) return;

        controlNpcBehavior(livingEntity, serverPlayer);
        callExtensionOnTick(livingEntity, serverPlayer);
    }'''

content = content.replace(old_tick, new_tick)

# 5. Fix checkDistance signature
content = content.replace(
    'private static void checkDistance(DialogueNpcPatch patch, LivingEntity entity, ServerPlayer player) {',
    'private static void checkDistance(LivingEntity entity, ServerPlayer player) {')
content = content.replace('patch.clearConversing();', 'DialogueNpcStateManager.clear(entity);')

# 6. Fix controlNpcBehavior signature
content = content.replace(
    'private static void controlNpcBehavior(DialogueNpcPatch patch, LivingEntity entity, ServerPlayer player) {',
    'private static void controlNpcBehavior(LivingEntity entity, ServerPlayer player) {')
content = content.replace(
    'if (!patch.isConversing()) return;',
    '{\n        DialogueNpcStateManager.State state = DialogueNpcStateManager.get(entity);\n        if (state == null || state.conversingPlayer() == null) return;')

# 7. Fix ensureDialogueNpcPatch
old_ensure = '''    private static void ensureDialogueNpcPatch(Entity entity, ServerPlayer player) {
        entity.getCapability(DialogueNpcPatch.CAPABILITY).ifPresent(patch -> {
            patch.setConversing(player);
        });
    }'''
new_ensure = '''    private static void ensureDialogueNpcPatch(Entity entity, ServerPlayer player) {
        DialogueNpcStateManager.setConversing(entity, player);
    }'''
content = content.replace(old_ensure, new_ensure)

with open(fp, 'w', encoding='utf-8') as f:
    f.write(content)
print('OK' if content != orig else 'NOCHANGE')
