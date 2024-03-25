package com.raitonbl.hermes.smsc.config;

import com.raitonbl.hermes.smsc.config.integration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.util.Optional;
import java.util.Properties;

@Component
public class IntegrationBeanFactory implements BeanDefinitionRegistryPostProcessor {
    private static final String HOME_DIRECTORY = Optional
            .ofNullable(System.getenv("HERMES_SMSC_HOME"))
            .map(each -> each.endsWith("/") ? each.substring(0, each.length() - 1) : each).orElse("./");

    private static final Yaml YAML = new Yaml();

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        File homeDir = new File(HOME_DIRECTORY + "/conf");

        if (!homeDir.isDirectory()) {
            return;
        }
        File[] files = homeDir.listFiles();
        if (files == null) {
            return;
        }
        String[] suffixes = new String[]{".integrations.yml", ".integrations.yaml"};
        for (File file : files) {
            if (file.isDirectory()) {
                continue;
            }
            if (!StringUtils.endsWithAny(file.getName(), suffixes)) {
                return;
            }
            try {
                Configuration cfg = YAML.loadAs(new FileInputStream(file), Configuration.class);

                Properties properties = loadPropertiesFromFile(file);
                String componentName = properties.getProperty("component.name");
                BeanDefinition beanDefinition = BeanDefinitionBuilder
                        .genericBeanDefinition(MyComponent.class)
                        .addConstructorArgValue(properties)
                        .getBeanDefinition();
                registry.registerBeanDefinition(componentName, beanDefinition);
            } catch (Exception e) {
                throw new BeanCreationException(file.getName(), e);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // Nothing to do here
    }

    private Properties loadPropertiesFromFile(File file) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(file)) {
            properties.load(input);
        }
        return properties;
    }
}
