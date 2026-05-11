package org.arcadia.arc_quest.dialogue.io;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * dialogue datapack 热重载骨架。
 * <p>
 * 当前阶段只负责扫描并占住 reload 接线位，
 * 后续可在此处补上 JSON 解析、校验、编译与绑定注册。
 */
public final class DialogueDatapackHotReloadService {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final DialogueDatapackResourceLoader resourceLoader = new DialogueDatapackResourceLoader();

    public ReloadResult reload() {
        var report = resourceLoader.scan();

        if (!report.files().isEmpty()) {
            LOGGER.info("[DialogueRegistry] Found {} dialogue datapack JSON file(s) under {}. Parsing is not implemented yet; files are currently ignored.",
                    report.loadedCount(), report.rootDir());
        }

        if (!report.failedFiles().isEmpty()) {
            LOGGER.warn("[DialogueRegistry] Failed to inspect {} dialogue datapack file(s) under {}.",
                    report.failedCount(), report.rootDir());
        }

        return new ReloadResult(report.scannedFiles(), report.loadedCount(), report.failedCount(), 0);
    }

    public record ReloadResult(int scanned, int discovered, int failed, int activeDatapack) {
    }
}
