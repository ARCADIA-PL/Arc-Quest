package org.arcadia.arc_quest.tutorial;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.arcadia.arc_quest.client.tutorial.WorkstationTutorialOverlay;

import java.util.UUID;

/** Menu-bound presentation messages. No client message can assert an economic operation. */
public final class TutorialNetwork {
    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.parse("arc_quest:tutorial"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;

    private TutorialNetwork() {}

    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Snapshot.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Snapshot::encode).decoder(Snapshot::new)
                .consumerMainThread((packet, context) -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> WorkstationTutorialOverlay.receive(packet)))
                .add();
        CHANNEL.messageBuilder(Request.class, 1, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Request::encode).decoder(Request::new)
                .consumerMainThread((packet, context) -> {
                    var player = context.get().getSender();
                    if (player != null) TutorialSessions.INSTANCE.handle(player, packet);
                }).add();
    }

    public enum Action { ACKNOWLEDGE, PAUSE, RESUME, REPLAY }

    public record Request(UUID token, int containerId, long revision, Action action) {
        public Request(FriendlyByteBuf buffer) {
            this(buffer.readUUID(), buffer.readVarInt(), buffer.readVarLong(), buffer.readEnum(Action.class));
        }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(token); buffer.writeVarInt(containerId); buffer.writeVarLong(revision); buffer.writeEnum(action);
        }
    }

    public record Snapshot(UUID token, int containerId, long revision, String course, String title,
                           Component textComponent, int[] targets, String control, boolean acknowledge, boolean paused,
                           boolean warning, boolean complete, int sourceSlot, int ghostSlot, ItemStack ghost) {
        public Snapshot {
            targets = targets.clone();
            ghost = ghost.copy();
            // Item names can be changed by players; don't let long custom names overflow this UI packet.
            if (title.length() > 100) title = title.substring(0, 99) + "…";
            // Keep translation arguments intact until the receiving client's language is applied.
            textComponent = textComponent.copy();
            if (Component.Serializer.toJson(textComponent).length() > 24000)
                textComponent = Component.literal(textComponent.getString(599) + "…");
        }
        public Snapshot withRevision(long value) {
            return new Snapshot(token,containerId,value,course,title,textComponent,targets,control,acknowledge,paused,warning,complete,sourceSlot,ghostSlot,ghost);
        }
        public String text() { return textComponent.getString(); }
        @Override public Component textComponent() { return textComponent.copy(); }
        @Override public int[] targets() { return targets.clone(); }
        @Override public ItemStack ghost() { return ghost.copy(); }
        public Snapshot(UUID token, int containerId, long revision, String course, String title, Component text,
                        int[] targets, String control, boolean acknowledge, boolean paused, boolean warning, boolean complete) {
            this(token, containerId, revision, course, title, text, targets, control, acknowledge, paused, warning, complete,
                    -1, -1, ItemStack.EMPTY);
        }
        public Snapshot(FriendlyByteBuf buffer) {
            this(buffer.readUUID(), buffer.readVarInt(), buffer.readVarLong(), buffer.readUtf(100), buffer.readUtf(100),
                    buffer.readComponent(), buffer.readVarIntArray(18), buffer.readUtf(100), buffer.readBoolean(),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readItem());
        }
        public void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(token); buffer.writeVarInt(containerId); buffer.writeVarLong(revision);
            buffer.writeUtf(course, 100); buffer.writeUtf(title, 100); buffer.writeComponent(textComponent);
            buffer.writeVarIntArray(targets); buffer.writeUtf(control, 100); buffer.writeBoolean(acknowledge);
            buffer.writeBoolean(paused); buffer.writeBoolean(warning); buffer.writeBoolean(complete);
            buffer.writeVarInt(sourceSlot); buffer.writeVarInt(ghostSlot); buffer.writeItem(ghost);
        }
    }

    public static void send(ServerPlayer player, Snapshot snapshot) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }
}
