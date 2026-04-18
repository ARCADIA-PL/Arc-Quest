package org.com.arc_quest.dialogue.runtime;

import org.com.arc_quest.dialogue.api.DialogueCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 条件评估缓存。
 * 
 * <p>在单次条件评估周期内缓存结果,避免重复查询。</p>
 * 
 * <h2>使用场景</h2>
 * <p>当 DialogueSession.evaluateVisibleChoices() 评估多个选项时,
 * 如果多个选项共享相同的条件(如 "hasFlag('talked_to_villager')"),
 * 缓存可以避免重复查询 IQuestCapability。</p>
 * 
 * <h2>生命周期</h2>
 * <ul>
 *   <li>每次 evaluateVisibleChoices() 开始时调用 {@link #beginCycle()}</li>
 *   <li>评估过程中调用 {@link #computeIfAbsent(DialogueCondition, java.util.function.Supplier)}</li>
 *   <li>评估结束后自动清理(下次 beginCycle 时)</li>
 * </ul>
 * 
 * <h2>线程安全</h2>
 * <p>使用 ThreadLocal 确保每个对话会话独立,无并发问题。</p>
 */
public final class EvalCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(EvalCache.class);
    
    private static final ThreadLocal<EvalCache> CURRENT = ThreadLocal.withInitial(EvalCache::new);
    
    private final Map<DialogueCondition, Boolean> cache = new ConcurrentHashMap<>();
    private boolean inCycle = false;
    
    private EvalCache() {}
    
    /**
     * 获取当前线程的缓存实例。
     */
    public static EvalCache current() {
        return CURRENT.get();
    }
    
    /**
     * 开始新的评估周期,清空旧缓存。
     */
    public void beginCycle() {
        cache.clear();
        inCycle = true;
        LOGGER.debug("[EvalCache] New evaluation cycle started.");
    }
    
    /**
     * 结束当前评估周期。
     */
    public void endCycle() {
        inCycle = false;
        LOGGER.debug("[EvalCache] Evaluation cycle ended. Cache size was: {}", cache.size());
    }
    
    /**
     * 计算或从缓存获取条件评估结果。
     * 
     * @param condition 要评估的条件
     * @param evaluator 评估函数(仅在缓存未命中时调用)
     * @return 评估结果
     */
    public boolean computeIfAbsent(DialogueCondition condition, 
                                   java.util.function.Supplier<Boolean> evaluator) {
        if (!inCycle) {
            // 如果不在周期内,直接评估(不缓存)
            LOGGER.warn("[EvalCache] Not in evaluation cycle, evaluating without cache.");
            return evaluator.get();
        }
        
        return cache.computeIfAbsent(condition, k -> {
            LOGGER.debug("[EvalCache] Cache miss for condition: {}", condition.getClass().getSimpleName());
            return evaluator.get();
        });
    }
    
    /**
     * 检查是否在评估周期内。
     */
    public boolean isInCycle() {
        return inCycle;
    }
    
    /**
     * 获取当前缓存大小。
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * 清除所有缓存(用于测试)。
     */
    public static void clearForTesting() {
        CURRENT.remove();
    }
}
