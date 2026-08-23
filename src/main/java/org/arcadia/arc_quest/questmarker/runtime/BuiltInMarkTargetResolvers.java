package org.arcadia.arc_quest.questmarker.runtime;

import org.arcadia.arc_quest.questmarker.api.MarkTargetResolverRegistry;

public final class BuiltInMarkTargetResolvers {

    public static final String NEAREST_BLOCK_TAG = "arc_quest:nearest_block_tag";

    private BuiltInMarkTargetResolvers() {
    }

    public static void registerAll() {
        MarkTargetResolverRegistry.register(NEAREST_BLOCK_TAG, NearestBlockTagMarkerResolver::resolve);
    }
}
