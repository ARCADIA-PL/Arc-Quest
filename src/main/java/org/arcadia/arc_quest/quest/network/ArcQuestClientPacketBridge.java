package org.arcadia.arc_quest.quest.network;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.arcadia.arc_quest.dialogue.network.S2COpenDialoguePacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptDeltaPacket;
import org.arcadia.arc_quest.dialogue.network.S2CDialogueTranscriptSnapshotPacket;
import org.arcadia.arc_quest.quest.editor.network.S2COpenQuestEditorPacket;
import org.arcadia.arc_quest.quest.editor.network.S2CQuestEditorResultPacket;
import org.arcadia.arc_quest.trade.gacha.network.S2CDrawFailedPacket;
import org.arcadia.arc_quest.trade.gacha.network.S2CDrawResultPacket;
import org.arcadia.arc_quest.trade.gacha.network.S2CGachaStatePacket;
import org.arcadia.arc_quest.trade.network.S2COpenTradePacket;
import org.arcadia.arc_quest.trade.network.S2CSyncTradeStatePacket;
import org.arcadia.arc_quest.trade.network.S2CTradeUpdatesPacket;
import org.arcadia.arc_quest.trade.network.S2CTestTradeShopPacket;

import java.util.function.Supplier;

public final class ArcQuestClientPacketBridge {

    private ArcQuestClientPacketBridge() {
    }

    public static void handleQuestState(S2CSyncQuestStatePacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleQuestState(packet, context));
    }

    public static void handleOfferResult(S2COfferSubmitResultPacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleOfferResult(packet, context));
    }

    public static void handleQuestActionResult(S2CQuestActionResultPacket packet,
                                               Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleQuestActionResult(packet, context));
    }

    public static void handleDialogue(S2COpenDialoguePacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleDialogue(packet, context));
    }

    public static void handleTranscriptDelta(S2CDialogueTranscriptDeltaPacket packet,
                                               Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleTranscriptDelta(packet, context));
    }

    public static void handleTranscriptSnapshot(S2CDialogueTranscriptSnapshotPacket packet,
                                               Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleTranscriptSnapshot(packet, context));
    }

    public static void handleOpenTrade(S2COpenTradePacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleOpenTrade(packet, context));
    }

    public static void handleTradeState(S2CSyncTradeStatePacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleTradeState(packet, context));
    }

    public static void handleGachaState(S2CGachaStatePacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleGachaState(packet, context));
    }

    public static void handleDrawResult(S2CDrawResultPacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleDrawResult(packet, context));
    }

    public static void handleDrawFailed(S2CDrawFailedPacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleDrawFailed(packet, context));
    }

    public static void handleMarkers(S2CSyncMarkersPacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleMarkers(packet, context));
    }

    public static void handleOpenEditor(S2COpenQuestEditorPacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleOpenEditor(packet, context));
    }

    public static void handleEditorResult(S2CQuestEditorResultPacket packet, Supplier<NetworkEvent.Context> context) {
        run(() -> ClientOnly.handleEditorResult(packet, context));
    }

    private static void run(Runnable action) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> action);
    }

    public static void handleTradeUpdates(S2CTradeUpdatesPacket packet,
                                           Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> run(() -> ClientOnly.handleTradeUpdates(packet)));
        context.setPacketHandled(true);
    }

    public static void handleTestTradeShop(S2CTestTradeShopPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> run(() -> ClientOnly.handleTestTradeShop(packet)));
        context.setPacketHandled(true);
    }

    private static final class ClientOnly {
        private static void handleTestTradeShop(S2CTestTradeShopPacket packet) {
            org.arcadia.arc_quest.client.hud.shop.ClientRefreshingTestShop.accept(packet);
        }
        private static void handleTradeUpdates(S2CTradeUpdatesPacket packet) {
            org.arcadia.arc_quest.client.hud.shop.TradeUpdateHighlights.accept(packet);
        }
        private static void handleQuestState(S2CSyncQuestStatePacket packet,
                                             Supplier<NetworkEvent.Context> context) {
            S2CSyncQuestStatePacket.handle(packet, context);
        }

        private static void handleOfferResult(S2COfferSubmitResultPacket packet,
                                              Supplier<NetworkEvent.Context> context) {
            S2COfferSubmitResultPacket.handle(packet, context);
        }

        private static void handleQuestActionResult(S2CQuestActionResultPacket packet,
                                                    Supplier<NetworkEvent.Context> context) {
            S2CQuestActionResultPacket.handle(packet, context);
        }

        private static void handleDialogue(S2COpenDialoguePacket packet, Supplier<NetworkEvent.Context> context) {
            S2COpenDialoguePacket.handle(packet, context);
        }

        private static void handleTranscriptDelta(S2CDialogueTranscriptDeltaPacket packet,
                                                    Supplier<NetworkEvent.Context> context) {
            S2CDialogueTranscriptDeltaPacket.handle(packet, context);
        }

        private static void handleTranscriptSnapshot(S2CDialogueTranscriptSnapshotPacket packet,
                                                    Supplier<NetworkEvent.Context> context) {
            S2CDialogueTranscriptSnapshotPacket.handle(packet, context);
        }

        private static void handleOpenTrade(S2COpenTradePacket packet, Supplier<NetworkEvent.Context> context) {
            S2COpenTradePacket.handle(packet, context);
        }

        private static void handleTradeState(S2CSyncTradeStatePacket packet, Supplier<NetworkEvent.Context> context) {
            S2CSyncTradeStatePacket.handle(packet, context);
        }

        private static void handleGachaState(S2CGachaStatePacket packet, Supplier<NetworkEvent.Context> context) {
            S2CGachaStatePacket.handle(packet, context);
        }

        private static void handleDrawResult(S2CDrawResultPacket packet, Supplier<NetworkEvent.Context> context) {
            S2CDrawResultPacket.handle(packet, context);
        }

        private static void handleDrawFailed(S2CDrawFailedPacket packet, Supplier<NetworkEvent.Context> context) {
            S2CDrawFailedPacket.handle(packet, context);
        }

        private static void handleMarkers(S2CSyncMarkersPacket packet, Supplier<NetworkEvent.Context> context) {
            S2CSyncMarkersPacket.handle(packet, context);
        }

        private static void handleOpenEditor(S2COpenQuestEditorPacket packet, Supplier<NetworkEvent.Context> context) {
            S2COpenQuestEditorPacket.handle(packet, context);
        }

        private static void handleEditorResult(S2CQuestEditorResultPacket packet,
                                               Supplier<NetworkEvent.Context> context) {
            S2CQuestEditorResultPacket.handle(packet, context);
        }
    }
}
