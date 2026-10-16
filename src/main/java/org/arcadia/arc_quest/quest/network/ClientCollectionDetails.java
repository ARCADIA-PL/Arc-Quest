package org.arcadia.arc_quest.quest.network;

import org.arcadia.arc_quest.quest.api.CollectionDetailDocument;
import java.util.UUID;

/** One open detail view. A response for a previous selection, run, reload or login cannot replace it. */
public final class ClientCollectionDetails {
    public static final ClientCollectionDetails INSTANCE = new ClientCollectionDetails();
    public record Key(long epoch, long reloadEpoch, String quest, String phase, String binding, String run) {}
    private Key key;
    private C2SCollectionDetailRequest pending;
    private long nextRequestAt;
    private long retryAt;
    private CollectionDetailDocument document;
    public C2SCollectionDetailRequest request(Key selected, long nowMs) {
        if (!selected.equals(key)) { clear(); key = selected; }
        if (nowMs < nextRequestAt || pending != null && nowMs < retryAt) return null;
        nextRequestAt = nowMs + 1000;
        retryAt = nowMs + 5000;
        pending = new C2SCollectionDetailRequest(UUID.randomUUID(), selected.epoch(), selected.quest(),
                selected.phase(), selected.binding(), selected.run());
        return pending;
    }
    public boolean accept(S2CCollectionDetailPacket packet) {
        if (pending == null || !pending.equals(packet.request())) return false;
        document = packet.document(); pending = null; return true;
    }
    public CollectionDetailDocument document() { return document; }
    public void clear() { key = null; pending = null; document = null; nextRequestAt = retryAt = 0; }
}
