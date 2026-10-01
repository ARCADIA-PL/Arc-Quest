package org.arcadia.arc_quest.client.hud.gacha;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.arcadia.arc_quest.trade.offer.ItemTradeOffer;
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
    private int mouseX = -10000, mouseY = -10000;
    private final ClientGachaCache.DrawRecord result = new ClientGachaCache.DrawRecord(
            "gold_ingot", "RARE", 1, false, 0);

    public JeiGachaScreenProbe(String shop) { screen = new Native(shop); }
    public GachaScreen screen() { return screen; }
    public void point(double x, double y) { mouseX = (int) Math.round(x); mouseY = (int) Math.round(y); }
    public void clearPointer() { point(-10000, -10000); }
    private final class Native extends GachaScreen {
        Native(String shop) { super(shop, packets::add, clock::get); }
        @Override public void render(GuiGraphics graphics, int x, int y, float tick) {
            super.render(graphics, mouseX, mouseY, tick);
        }
    }
    private void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private long count(Class<?> type) { return packets.stream().filter(type::isInstance).count(); }
    public void assertNoDrawRequests() {
        check(count(C2SDrawGachaPacket.class) == 0 && count(C2SConfirmDrawPacket.class) == 0,
                "JEI ingredient clicks reached draw or confirm handling");
    }
    public double[] drawButtonCenter() {
        try {
            var getter = GachaPreviewPanel.class.getDeclaredMethod("getLayout");
            getter.setAccessible(true);
            Object layout = getter.invoke(screen.getPreviewPanel());
            return new double[]{layoutInt(layout, "btnX") + layoutInt(layout, "btnW") / 2.0,
                    layoutInt(layout, "btnY") + layoutInt(layout, "btnH") / 2.0};
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    /** The single fixture cost is positioned using the real layout and actual localized item text. */
    public double[] firstCostCenter() {
        var costs = screen.getShopDef().getDrawCosts();
        check(costs.size() == 1 && costs.get(0) instanceof ItemTradeOffer, "Expected the native single item cost fixture");
        var cost = (ItemTradeOffer) costs.get(0);
        var candidates = cost.getDisplayStacks();
        check(candidates.size() == 1, "Native cost lost its actual display item");
        var font = Minecraft.getInstance().font;
        int width = 22 + font.width(candidates.get(0).getHoverName().getString()) + 4 + font.width("x" + cost.getCount());
        var button = drawButtonCenter();
        return new double[]{button[0] - width / 2 + 8, button[1] - 28};
    }
    public void assertCostRenderedAndHovered() {
        try {
            var field = GachaPreviewPanel.class.getDeclaredField("costHoverAnims");
            field.setAccessible(true);
            float[] hover = (float[]) field.get(screen.getPreviewPanel());
            check(hover.length == 1 && hover[0] > .95f, "The native independent gacha cost did not render and hover");
            check(firstCostCenter()[1] + 10 < drawButtonCenter()[1] - 14, "Gacha cost overlaps the draw button");
            assertNoDrawRequests();
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private static int layoutInt(Object layout, String name) throws ReflectiveOperationException {
        var method = layout.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        return (int) method.invoke(layout);
    }

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
