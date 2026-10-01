package org.arcadia.arc_quest.sync;

import org.arcadia.arc_quest.core.identity.PlayerSessionRef;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** 有界的会话请求结果缓存。业务回调不持锁；占位与完成发布分别受锁保护。 */
public final class BoundedProcessedRequestStore<R> {
    private static final int DEFAULT_MAX_SESSIONS = 4096;
    private final int maxRequestsPerSession;
    private final int maxSessions;
    private final Map<PlayerSessionRef, LinkedHashMap<UUID, Entry<R>>> resultsBySession = new HashMap<>();
    private final Map<UUID, Entry<R>> inFlightByPlayer = new HashMap<>();

    public BoundedProcessedRequestStore(int maxRequestsPerSession) {
        this(maxRequestsPerSession, DEFAULT_MAX_SESSIONS);
    }

    public BoundedProcessedRequestStore(int maxRequestsPerSession, int maxSessions) {
        if (maxRequestsPerSession <= 0 || maxSessions <= 0) {
            throw new IllegalArgumentException("Request and session capacities must be positive");
        }
        this.maxRequestsPerSession = maxRequestsPerSession;
        this.maxSessions = maxSessions;
    }

    public ProcessedResult<R> process(PlayerSessionRef sessionRef, UUID requestId, Supplier<R> operation) {
        return process(sessionRef, requestId, LegacyKey.INSTANCE, operation);
    }

    /** requestKey 必须是不可变的请求内容，防止同一 ID 被换成另一笔操作。 */
    public ProcessedResult<R> process(PlayerSessionRef sessionRef, UUID requestId,
                                      Object requestKey, Supplier<R> operation) {
        Objects.requireNonNull(sessionRef, "sessionRef");
        Objects.requireNonNull(requestKey, "requestKey");
        Objects.requireNonNull(operation, "operation");
        boolean legacy = requestId == null || RequestIdempotencyStore.LEGACY_REQUEST_ID.equals(requestId);
        Entry<R> entry;
        synchronized (this) {
            var results = resultsBySession.get(sessionRef);
            Entry<R> existing = !legacy && results != null ? results.get(requestId) : null;
            if (existing != null) {
                if (!existing.requestKey.equals(requestKey)) throw rejected(Rejection.CONTENT_MISMATCH);
                if (existing.state == State.RUNNING) throw rejected(Rejection.IN_PROGRESS);
                if (existing.state == State.FAILED) throw rejected(Rejection.PREVIOUS_FAILURE);
                return new ProcessedResult<>(existing.result, true);
            }
            if (inFlightByPlayer.containsKey(sessionRef.playerUuid())) throw rejected(Rejection.IN_PROGRESS);
            if (inFlightByPlayer.size() >= maxSessions) throw rejected(Rejection.CAPACITY);
            entry = new Entry<>(requestKey);
            if (!legacy) {
                if (results == null) {
                    if (resultsBySession.size() >= maxSessions) throw rejected(Rejection.CAPACITY);
                    results = new LinkedHashMap<>();
                    resultsBySession.put(sessionRef, results);
                }
                reserveSpace(results);
                results.put(requestId, entry);
            }
            inFlightByPlayer.put(sessionRef.playerUuid(), entry);
        }

        try {
            R result = Objects.requireNonNull(operation.get(), "operation result");
            synchronized (this) {
                entry.result = result;
                entry.state = State.COMPLETE;
            }
            return new ProcessedResult<>(result, false);
        } catch (RuntimeException | Error failure) {
            synchronized (this) {
                // 异常可能发生在副作用之后；保留失败占位，不长期持有附属异常中的世界/玩家引用。
                entry.state = State.FAILED;
            }
            throw failure;
        } finally {
            synchronized (this) {
                inFlightByPlayer.remove(sessionRef.playerUuid(), entry);
            }
        }
    }

    private void reserveSpace(LinkedHashMap<UUID, Entry<R>> results) {
        if (results.size() < maxRequestsPerSession) return;
        Iterator<Entry<R>> iterator = results.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().state == State.COMPLETE) {
                iterator.remove();
                return;
            }
        }
        // 未决失败不能被缓存逐出悄悄变回“未执行”。
        throw rejected(Rejection.CAPACITY);
    }

    public synchronized void clearPlayer(UUID playerUuid) {
        resultsBySession.keySet().removeIf(sessionRef -> sessionRef.playerUuid().equals(playerUuid));
        // 正在执行的回调必须自己释放占位；清理回调不能为重入打开窗口。
    }

    public synchronized void clear() {
        resultsBySession.clear();
    }

    synchronized int trackedRequestCount(PlayerSessionRef sessionRef) {
        var results = resultsBySession.get(sessionRef);
        return results != null ? results.size() : 0;
    }

    private static RequestRejectedException rejected(Rejection reason) {
        return new RequestRejectedException(reason);
    }

    public enum Rejection { IN_PROGRESS, CONTENT_MISMATCH, PREVIOUS_FAILURE, CAPACITY }

    public static final class RequestRejectedException extends IllegalStateException {
        private final Rejection reason;

        private RequestRejectedException(Rejection reason) {
            super("Request rejected: " + reason);
            this.reason = reason;
        }

        public Rejection reason() { return reason; }
    }

    private enum State { RUNNING, COMPLETE, FAILED }
    private enum LegacyKey { INSTANCE }

    private static final class Entry<R> {
        final Object requestKey;
        State state = State.RUNNING;
        R result;

        Entry(Object requestKey) { this.requestKey = requestKey; }
    }

    public record ProcessedResult<R>(R result, boolean replayed) { }
}
