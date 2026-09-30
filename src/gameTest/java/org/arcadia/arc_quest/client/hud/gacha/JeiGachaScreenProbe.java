package org.arcadia.arc_quest.client.hud.gacha;

import org.arcadia.arc_quest.trade.gacha.network.C2SConfirmDrawPacket;
import org.arcadia.arc_quest.trade.gacha.network.C2SDrawGachaPacket;
import org.arcadia.arc_quest.trade.gacha.network.C2SGachaControlPacket;
import org.arcadia.arc_quest.trade.gacha.network.ClientGachaCache;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Drives the actual screen with a deterministic clock; records packets without touching server assets. */
public final class JeiGachaScreenProbe {
    private final AtomicLong clock = new AtomicLong(100_000);
    private final List<Object> packets = new ArrayList<>();
    private final GachaScreen screen;
    private final ClientGachaCache.DrawRecord result = new ClientGachaCache.DrawRecord(
            "gold_ingot", "RARE", 1, false, 0);

    public JeiGachaScreenProbe(String shop) { screen = new GachaScreen(shop, packets::add, clock::get); }
    public GachaScreen screen() { return screen; }
    private void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private long count(Class<?> type) { return packets.stream().filter(type::isInstance).count(); }

    public void waitPastTimeout() {
        check(screen.canQueryJei(), "Gacha preview must initially allow JEI");
        packets.clear(); // Ignore periodic preview refreshes before this request began.
        screen.startDrawRequest();
        check(!screen.canQueryJei(), "JEI must be blocked while awaiting a draw");
        clock.addAndGet(6000);
        screen.tick();
        screen.tick();
        screen.startDrawRequest();
        check(!screen.canQueryJei(), "Five-second timeout must not unlock JEI");
        check(count(C2SDrawGachaPacket.class) == 1, "Timeout or second click resent the draw");
        check(count(C2SGachaControlPacket.class) == 1, "Slow request must request one safe state refresh");
    }
    public void deliverLateResult() {
        check(!screen.canQueryJei(), "Pending draw was lost before late response");
        screen.triggerRollingAnimation(result);
        check(!screen.canQueryJei(), "JEI must be blocked while rolling");
    }
    public void finishRoll() {
        screen.onRollFinished(result);
        check(GachaResultRenderer.INSTANCE.isActive(), "Late result did not enter the actual result renderer");
        check(!screen.canQueryJei(), "JEI must be blocked while showing the confirmation screen");
    }
    public void confirmAndCloseResult() {
        check(!screen.canQueryJei(), "JEI became queryable during result confirmation");
        screen.confirmDrawAndSync();
        screen.confirmDrawAndSync();
        GachaResultRenderer.INSTANCE.forceCloseAndConfirm();
        check(count(C2SConfirmDrawPacket.class) == 1, "Result confirmation was emitted more than once");
    }
    public void verifyFailureAndCloseFallback() {
        check(screen.canQueryJei(), "Gacha preview did not recover after confirmation animation");
        screen.startDrawRequest();
        screen.onDrawFailedAndReturnToPreview();
        check(screen.canQueryJei(), "Explicit server failure must restore preview queries");
        screen.startDrawRequest();
        clock.addAndGet(6000);
        screen.tick();
        screen.onClose();
        screen.onClose();
        check(count(C2SDrawGachaPacket.class) == 3, "Unexpected draw retry during close fallback");
        check(count(C2SConfirmDrawPacket.class) == 2, "Close fallback must confirm its unanswered draw once");
        check(!screen.canQueryJei(), "Closing screen must never allow JEI");
        GachaResultRenderer.INSTANCE.discardResult();
    }
}
