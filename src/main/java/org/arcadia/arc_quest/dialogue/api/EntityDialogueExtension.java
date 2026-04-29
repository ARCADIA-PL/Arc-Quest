package org.arcadia.arc_quest.dialogue.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体对话扩展类，用于自动扫描和注册。
 * <p>
 * 使用此注解的类必须实现 {@link IEntityDialogueExtension} 接口。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @EntityDialogueExtension(modId = "arc_quest")
 * public class VillagerDialogueExtension implements IEntityDialogueExtension<Villager> {
 *     @Override
 *     public EntityType<Villager> getEntityType() {
 *         return EntityType.VILLAGER;
 *     }
 *
 *     // ... 其他方法实现
 * }
 * }</pre>
 *
 * @author Arc Quest Team
 * @since 2.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EntityDialogueExtension {

    /**
     * 模组 ID
     *
     * @return 模组标识符
     */
    String modId();
}
