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
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class IntegrationBeanFactory implements BeanDefinitionRegistryPostProcessor {
    private static final Yaml OBJECT_MAPPER = new Yaml();
    private static final String HOME_DIRECTORY = Optional
            .ofNullable(System.getenv("HERMES_SMSC_HOME"))
            .map(each -> each.endsWith("/") ? each.substring(0, each.length() - 1) : each)
            .orElse("./");

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
                Configuration cfg = OBJECT_MAPPER.loadAs(new FileInputStream(file), Configuration.class);
                String beanName = String.format("#" + BeanFactory.INTEGRATION_CLIENT_F, cfg.getName());
                BeanDefinition beanDefinition = BeanDefinitionBuilder
                        .genericBeanDefinition(cfg.getType().javaType, getProducerFrom(cfg))
                        .getBeanDefinition();
                registry.registerBeanDefinition(beanName, beanDefinition);
            } catch (Exception e) {
                throw new BeanCreationException(file.getName(), e);
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    private <Y> Supplier<Y> getProducerFrom(Configuration cfg) {
        return () -> {
            switch (cfg.getType()) {
                case HASHICORP_VAULT -> {
                    VaultEndpoint endpoint = new VaultEndpoint();
                    endpoint.setHost(cfg.getHost());
                    endpoint.setPath(cfg.getPath());
                    endpoint.setPort(cfg.getPort());
                    endpoint.setScheme(cfg.getScheme());

                    VaultTemplate instance = cfg.getToken() == null ?
                            new VaultTemplate(endpoint) :
                            new VaultTemplate(endpoint, new TokenAuthentication(cfg.getToken()));
                    return (Y) instance;
                }
                default -> throw new IllegalArgumentException("type=" + cfg.getType() + " isn't supported");
            }
        };
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // Nothing to do here
    }

}
