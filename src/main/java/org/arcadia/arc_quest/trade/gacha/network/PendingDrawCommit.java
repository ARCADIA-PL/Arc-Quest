package org.arcadia.arc_quest.trade.gacha.network;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.arcadia.arc_quest.trade.gacha.network.PendingDrawJournal.Stage.*;

/** 非阻塞持久化顺序；业务只在 PREPARED 写入确认后获得一次扣费机会。 */
final class PendingDrawCommit {
    enum Decision { WAIT, CANCEL, PAID }

    private final PendingDrawJournal journal;
    private PendingDrawJournal.Entry entry;
    private CompletableFuture<Void> write;
    private boolean canceling;
    private boolean finished;
    private RuntimeException failure;
    private boolean polling;

    PendingDrawCommit(PendingDrawJournal journal, PendingDrawJournal.Entry entry) {
        this.journal = journal;
        this.entry = entry;
        write = journal.prepare(entry);
    }

    boolean poll(Supplier<Decision> payment) {
        if (failure != null) throw failure;
        if (polling) return false;
        polling = true;
        try { return advance(payment); }
        catch (RuntimeException rejected) {
            failure = rejected;
            throw rejected;
        } finally { polling = false; }
    }

    private boolean advance(Supplier<Decision> payment) {
        if (finished) return true;
        if (!write.isDone()) return false;
        write.join();
        if (canceling || entry.stage() == DELIVERING) {
            finished = true;
            return true;
        }
        if (entry.stage() == PREPARED) {
            switch (payment.get()) {
                case WAIT -> { return false; }
                case CANCEL -> {
                    canceling = true;
                    write = journal.remove(entry.transactionId(), PREPARED);
                }
                case PAID -> advance(PAID);
            }
        } else if (entry.stage() == PAID) {
            advance(DELIVERING);
        } else throw new IllegalStateException("Unexpected draw commit stage " + entry.stage());
        return false;
    }

    private void advance(PendingDrawJournal.Stage next) {
        write = journal.advance(entry.transactionId(), entry.stage(), next);
        entry = entry.at(next);
    }

    PendingDrawJournal.Entry entry() { return entry; }
    boolean canceled() { return canceling; }

    void cancelUnpaid() {
        if (failure != null || polling || finished || canceling || entry.stage() != PREPARED) return;
        canceling = true;
        // 与 prepare 共用单写线程，允许停止时把尚未扣费的撤销排在 prepare 后面。
        write = journal.remove(entry.transactionId(), PREPARED);
    }
}
