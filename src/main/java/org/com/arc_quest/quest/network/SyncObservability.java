package org.com.arc_quest.quest.network;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * SYNC 观测工具：统一记录 request/sent/dropped 指标，并在 verbose 模式输出结构化日志。
 */
public final class SyncObservability {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean VERBOSE_LOG = Boolean.parseBoolean(
            System.getProperty("arcquest.sync.log.verbose", "false")
    );

    private static final Map<String, AtomicLong> REQUEST_COUNTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> SENT_COUNTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> DROPPED_COUNTS = new ConcurrentHashMap<>();

    private SyncObservability() {
    }

    public static void recordRequest(String domain, String shopId, String playerName) {
        String key = buildKey(domain, shopId);
        long req = increment(REQUEST_COUNTS, key);
        long sent = current(SENT_COUNTS, key);
        long dropped = current(DROPPED_COUNTS, key);
        logVerbose("request", domain, shopId, playerName, false, req, sent, dropped);
    }

    public static void recordSent(String domain, String shopId, String playerName, boolean fpChanged) {
        String key = buildKey(domain, shopId);
        long req = current(REQUEST_COUNTS, key);
        long sent = increment(SENT_COUNTS, key);
        long dropped = current(DROPPED_COUNTS, key);
        logVerbose("sent", domain, shopId, playerName, fpChanged, req, sent, dropped);
    }

    public static void recordDropped(String domain, String shopId, String playerName, boolean fpChanged) {
        String key = buildKey(domain, shopId);
        long req = current(REQUEST_COUNTS, key);
        long sent = current(SENT_COUNTS, key);
        long dropped = increment(DROPPED_COUNTS, key);
        logVerbose("dropped", domain, shopId, playerName, fpChanged, req, sent, dropped);
    }

    private static long increment(Map<String, AtomicLong> map, String key) {
        return map.computeIfAbsent(key, __ -> new AtomicLong(0)).incrementAndGet();
    }

    private static long current(Map<String, AtomicLong> map, String key) {
        AtomicLong value = map.get(key);
        return value == null ? 0L : value.get();
    }

    private static String buildKey(String domain, String shopId) {
        String d = domain == null ? "unknown" : domain;
        String s = shopId == null ? "unknown" : shopId;
        return d + "|" + s;
    }

    private static void logVerbose(String event,
                                   String domain,
                                   String shopId,
                                   String playerName,
                                   boolean fpChanged,
                                   long req,
                                   long sent,
                                   long dropped) {
        if (!VERBOSE_LOG) {
            return;
        }

        LOGGER.info("[SyncObs] event={} domain={} shopId={} player={} fpChanged={} req={} sent={} dropped={}",
                event,
                domain,
                shopId,
                playerName,
                fpChanged,
                req,
                sent,
                dropped);
    }
}
