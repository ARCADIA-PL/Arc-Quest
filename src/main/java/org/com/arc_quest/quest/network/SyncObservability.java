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

    public enum Stage {
        OPEN("open"),
        SYNC_REQUEST("sync_request"),
        SYNC_SENT("sync_sent"),
        SYNC_DROPPED("sync_dropped"),
        ACTION("action"),
        RESULT("result");

        private final String value;

        Stage(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean VERBOSE_LOG = Boolean.parseBoolean(
            System.getProperty("arcquest.sync.log.verbose", "false")
    );
    private static final boolean TRACE_LOG = Boolean.parseBoolean(
            System.getProperty("arcquest.sync.trace", "false")
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

    /**
     * 轻量链路追踪：用于串联 open -> sync -> action -> result。
     */
    public static void trace(String domain,
                             String shopId,
                             String playerName,
                             Stage stage,
                             String reason) {
        if (!TRACE_LOG) {
            return;
        }
        LOGGER.info("[SyncTrace] domain={} shopId={} player={} stage={} reason={}",
                domain,
                shopId,
                playerName,
                stage != null ? stage.value() : "-",
                reason == null ? "-" : reason);
    }

    public static void trace(String domain,
                             String shopId,
                             String playerName,
                             String stage,
                             String reason) {
        Stage mapped = null;
        if (stage != null) {
            for (Stage s : Stage.values()) {
                if (s.value().equals(stage)) {
                    mapped = s;
                    break;
                }
            }
        }
        if (mapped != null) {
            trace(domain, shopId, playerName, mapped, reason);
            return;
        }

        if (!TRACE_LOG) {
            return;
        }
        LOGGER.info("[SyncTrace] domain={} shopId={} player={} stage={} reason={}",
                domain,
                shopId,
                playerName,
                stage == null ? "-" : stage,
                reason == null ? "-" : reason);
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
