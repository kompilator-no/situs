package no.testframework.runner.discovery;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import no.testframework.runner.config.RunnerProperties;
import no.testframework.runner.model.TestDefinition;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

@Component
public class TestDefinitionRegistry {

    private final Map<String, DiscoveredTestDefinition> definitionsById;

    public TestDefinitionRegistry(RunnerProperties properties, ListableBeanFactory beanFactory) {
        this.definitionsById = discover(properties.getDiscoveryPackages(), beanFactory);
    }

    public Collection<DiscoveredTestDefinition> definitions() {
        return definitionsById.values();
    }

    public DiscoveredTestDefinition requireDefinition(String id) {
        DiscoveredTestDefinition definition = definitionsById.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown test definition: " + id);
        }
        return definition;
    }

    private Map<String, DiscoveredTestDefinition> discover(List<String> basePackages, ListableBeanFactory beanFactory) {
        Map<String, DiscoveredTestDefinition> discovered = new LinkedHashMap<>();

        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TestDefinitionComponent.class));

        for (String basePackage : basePackages) {
            scanner.findCandidateComponents(basePackage).forEach(candidate -> {
                try {
                    Class<?> clazz = Class.forName(candidate.getBeanClassName());
                    if (!TestDefinition.class.isAssignableFrom(clazz)) {
                        return;
                    }
                    TestDefinitionComponent annotation = clazz.getAnnotation(TestDefinitionComponent.class);
                    discovered.put(annotation.id(), new DiscoveredTestDefinition(annotation.id(), annotation.description(), clazz));
                } catch (ClassNotFoundException ignored) {
                }
            });
        }

        beanFactory.getBeansOfType(TestDefinition.class).values().forEach(definition ->
            discovered.putIfAbsent(definition.id(), new DiscoveredTestDefinition(definition.id(), definition.description(), definition.getClass()))
        );

        return Map.copyOf(discovered);
    }
}
