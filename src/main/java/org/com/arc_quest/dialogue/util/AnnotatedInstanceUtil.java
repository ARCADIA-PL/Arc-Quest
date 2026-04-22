package org.com.arc_quest.dialogue.util;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.com.arc_quest.dialogue.api.EntityDialogueExtension;
import org.com.arc_quest.dialogue.api.IEntityDialogueExtension;
import org.objectweb.asm.Type;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * 注解实例化工具 - 自动扫描并实例化带有特定注解的类。
 * <p>
 * 参考自 <a href="https://github.com/mezz/JustEnoughItems">JEI</a> 和 DialogueLib。
 * 用于实现基于注解的自动注册系统。
 *
 * @author Arc Quest Team
 * @since 2.0
 */
@SuppressWarnings("rawtypes")
public final class AnnotatedInstanceUtil {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 获取所有带有 {@link EntityDialogueExtension} 注解的扩展实例。
     *
     * @return 扩展实例列表
     */
    @SuppressWarnings("unchecked")
    public static List<IEntityDialogueExtension<?>> getModEntityExtensions() {
        return (List<IEntityDialogueExtension<?>>) (List<?>) getInstances(
                EntityDialogueExtension.class,
                IEntityDialogueExtension.class
        );
    }

    /**
     * 通用方法：获取所有带有指定注解的类的实例。
     *
     * @param annotationClass 注解类型
     * @param instanceClass   实例类型
     * @param <T>             实例泛型
     * @return 实例列表
     */
    @SuppressWarnings("unchecked")
    private static <T> List<T> getInstances(Class<?> annotationClass, Class<T> instanceClass) {
        Type annotationType = Type.getType(annotationClass);
        List<ModFileScanData> allScanData = ModList.get().getAllScanData();
        Set<String> extensionClassNames = new LinkedHashSet<>();

        // 扫描所有模组的注解数据
        for (ModFileScanData scanData : allScanData) {
            for (ModFileScanData.AnnotationData data : scanData.getAnnotations()) {
                if (Objects.equals(data.annotationType(), annotationType)) {
                    String memberName = data.memberName();
                    extensionClassNames.add(memberName);
                }
            }
        }

        // 实例化所有找到的类
        List<T> instances = new ArrayList<>();
        for (String className : extensionClassNames) {
            try {
                Class<?> asmClass = Class.forName(className);
                Class<? extends T> asmInstanceClass = asmClass.asSubclass(instanceClass);
                T instance = asmInstanceClass.getDeclaredConstructor().newInstance();
                instances.add(instance);
                LOGGER.debug("[AnnotatedInstanceUtil] Loaded: {}", className);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                     InvocationTargetException | NoSuchMethodException | LinkageError e) {
                LOGGER.error("[AnnotatedInstanceUtil] Failed to load: {}", className, e);
            }
        }

        if (!instances.isEmpty()) {
            LOGGER.info("[AnnotatedInstanceUtil] Found {} instances of {} with @{}",
                    instances.size(),
                    instanceClass.getSimpleName(),
                    annotationClass.getSimpleName());
        }

        return instances;
    }
}
