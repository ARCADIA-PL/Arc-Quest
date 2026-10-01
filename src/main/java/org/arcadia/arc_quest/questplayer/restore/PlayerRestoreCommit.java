package org.arcadia.arc_quest.questplayer.restore;

import org.arcadia.arc_quest.questplayer.ArcQuestPlayer;

import java.util.function.Consumer;

/** 在服务端主线程保存候选数据后再发布；保存失败时尝试补偿已发生的存储写入。 */
final class PlayerRestoreCommit {
    private PlayerRestoreCommit() { }

    static void apply(ArcQuestPlayer target, ArcQuestPlayer candidate, Consumer<ArcQuestPlayer> persist) {
        try {
            persist.accept(candidate);
        } catch (RuntimeException failure) {
            try {
                persist.accept(target);
            } catch (RuntimeException rollbackFailure) {
                if (rollbackFailure != failure) failure.addSuppressed(rollbackFailure);
            }
            throw failure;
        }
        target.copyFrom(candidate);
    }
}
