package org.shpytchuk.adminapi.event;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.HashMap;
import java.util.Map;

public final class EventTypeScanner {

    private EventTypeScanner() {
    }

    public static Map<String, Class<?>> scan() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EventType.class));

        Map<String, Class<?>> mapping = new HashMap<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage())) {
            Class<?> type = resolve(candidate.getBeanClassName());
            String id = type.getAnnotation(EventType.class).value();

            Class<?> previous = mapping.put(id, type);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate @EventType(\"" + id + "\"): " + previous.getName() + " and " + type.getName());
            }
        }
        return mapping;
    }

    private static String basePackage() {
        return EventTypeScanner.class.getPackageName();
    }

    private static Class<?> resolve(String className) {
        try {
            return ClassUtils.forName(className, EventTypeScanner.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
