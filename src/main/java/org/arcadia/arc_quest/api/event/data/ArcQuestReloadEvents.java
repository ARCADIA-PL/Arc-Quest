package org.arcadia.arc_quest.api.event.data;

import net.minecraftforge.eventbus.api.Event;
import org.arcadia.arc_quest.data.reload.ReloadDiagnostic;
import org.arcadia.arc_quest.data.reload.ReloadSummary;

import java.util.List;

/** 数据包统一重载事件集合。 */
public final class ArcQuestReloadEvents {
    private ArcQuestReloadEvents() {
    }

    /**
     * 扫描、解析和跨模块校验完成后触发。
     * <p>可能运行在资源重载准备线程，不得访问世界对象。</p>
     */
    public static final class Prepared extends Event {
        private final int scannedFiles;
        private final List<ReloadDiagnostic> diagnostics;
        private final long durationMillis;

        public Prepared(int scannedFiles, List<ReloadDiagnostic> diagnostics, long durationMillis) {
            this.scannedFiles = scannedFiles;
            this.diagnostics = List.copyOf(diagnostics);
            this.durationMillis = durationMillis;
        }

        public int getScannedFiles() { return scannedFiles; }
        public List<ReloadDiagnostic> getDiagnostics() { return diagnostics; }
        public long getDurationMillis() { return durationMillis; }
        public boolean hasBlockingErrors() {
            return diagnostics.stream().anyMatch(ReloadDiagnostic::blocksReload);
        }
    }

    /** 快照提交或回滚结束后触发。 */
    public static final class Completed extends Event {
        private final ReloadSummary summary;

        public Completed(ReloadSummary summary) { this.summary = summary; }
        public ReloadSummary getSummary() { return summary; }
        public boolean isApplied() { return summary.applied(); }
    }
}
