package org.com.arc_quest.trade.api;

import net.minecraft.network.chat.Component;

/**
 * 服务端权威的成本缺口摘要，用于失败回包与客户端 HUD 展示。
 */
public record CostShortfallLine(Component label, int required, int owned, int missing) {
}
