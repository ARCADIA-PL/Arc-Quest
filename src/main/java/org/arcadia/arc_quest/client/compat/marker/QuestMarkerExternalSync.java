package org.arcadia.arc_quest.client.compat.marker;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.arcadia.arc_quest.client.compat.xaero.XaeroQuestMarkerSink;
import org.arcadia.arc_quest.config.ArcQuestConfig;
import org.arcadia.arc_quest.questmarker.api.QuestMarkerData;
import org.slf4j.Logger;

import java.util.Collection;

public final class QuestMarkerExternalSync {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String XAERO_MOD_ID = "xaerominimap";
    private static final Sink SINK = createSink();

    private QuestMarkerExternalSync() {
    }

    public static void replaceAll(Collection<QuestMarkerData> markers) {
        if (ArcQuestConfig.shouldSyncQuestMarkersToXaeroMinimap()) {
            SINK.replaceAll(markers);
        } else {
            SINK.clear();
        }
    }

    public static void clear() {
        SINK.clear();
    }

    private static Sink createSink() {
        if (!ModList.get().isLoaded(XAERO_MOD_ID)) return Sink.NOOP;
        try {
            return new XaeroQuestMarkerSink();
        } catch (LinkageError exception) {
            LOGGER.error("[ArcQuest/Xaero] Xaero API is incompatible; waypoint integration is disabled", exception);
            return Sink.NOOP;
        }
    }

    public interface Sink {
        Sink NOOP = new Sink() {
            @Override
            public void replaceAll(Collection<QuestMarkerData> markers) {
            }

            @Override
            public void clear() {
            }
        };

        void replaceAll(Collection<QuestMarkerData> markers);

        void clear();
    }
}
